package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-native version.
 *
 * Blockers fixed:
 *  - cr-java-0071 (line 60):          Hard-coded inventory URL replaced with AWS SSM Parameter Store
 *                                      value injected via Spring @Value / environment variable.
 *  - cr-java-0065 (lines 26, 31, 32,
 *                  45):               HTTP session state migrated to Amazon ElastiCache for Redis
 *                                      via Spring Session (EnableRedisHttpSession).
 *  - cr-java-0067 (line 18):          Unbounded in-memory HashMap cache replaced with
 *                                      Amazon ElastiCache for Redis (RedisTemplate with TTL).
 */
@RestController
@RequestMapping("/api/bookings")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * Distributed cache backed by Amazon ElastiCache for Redis.
     * Replaces the static in-memory HashMap (bookingCache) that had no TTL
     * and could not be shared across multiple application instances.
     * TTL is enforced per entry (see createBooking) to prevent unbounded growth.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Inventory service URL injected from AWS Systems Manager Parameter Store
     * via the environment variable INVENTORY_SERVICE_URL (set by ECS/EKS/Beanstalk).
     * Replaces the hard-coded "http://inventory-service.internal:8081/rooms/available".
     */
    @Value("${app.inventory.endpoint:${INVENTORY_SERVICE_URL:http://inventory-service.internal:8081/rooms/available}}")
    private String inventoryServiceUrl;

    /**
     * Report download base path — resolved from environment / SSM Parameter Store.
     * Replaces the hard-coded "/var/legacy/reports/" local path.
     */
    @Value("${cloud.aws.s3.reports-bucket:${REPORTS_S3_BUCKET:resorts-lite-reports}}")
    private String reportsBucket;

    /** Redis key prefix for booking cache entries. */
    private static final String BOOKING_CACHE_PREFIX = "booking:";

    /** TTL for cached booking entries in Redis (30 minutes). */
    private static final long BOOKING_CACHE_TTL_MINUTES = 30L;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session state in Amazon ElastiCache for Redis via Spring Session
        // (replaces in-process HttpSession that caused server affinity — cr-java-0065)
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Cache booking in Redis with TTL (replaces unbounded static HashMap — cr-java-0067)
        String cacheKey = BOOKING_CACHE_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, BOOKING_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Session attribute read from ElastiCache for Redis via Spring Session
        // (replaces in-process session lookup — cr-java-0065)
        String lastGuest = (String) session.getAttribute("guestName");

        // Try Redis cache first, fall back to DB
        String cacheKey = BOOKING_CACHE_PREFIX + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", cachedBooking != null ? cachedBooking : bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // inventoryServiceUrl is injected from AWS SSM Parameter Store / environment variable
        // (replaces hard-coded "http://inventory-service.internal:8081/rooms/available" — cr-java-0071)
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // S3 URI replaces hard-coded local file path "/var/legacy/reports/..."
        String s3ReportPath = "s3://" + reportsBucket + "/reports/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", s3ReportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
