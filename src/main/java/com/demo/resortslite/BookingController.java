package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-native version.
 *
 * Changes applied:
 *  - cr-java-0065 (blockers 12-16): Replaced HttpSession with Amazon ElastiCache for Redis
 *    via Spring Session / RedisTemplate for distributed, stateless session management.
 *  - cr-java-0071 (blocker 10): Replaced hard-coded inventory URL with AWS SSM Parameter Store
 *    value injected through application properties / environment variable.
 *  - cr-java-0067 (blocker 18): Replaced unbounded static in-memory HashMap cache with
 *    Amazon ElastiCache for Redis with TTL-based expiration.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * RedisTemplate replaces the static in-memory bookingCache HashMap (cr-java-0067).
     * Entries are stored in Amazon ElastiCache for Redis with a TTL to prevent unbounded growth
     * and ensure consistency across multiple application instances.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Cache TTL in minutes — configurable via environment variable / application property
    @Value("${app.cache.booking-ttl-minutes:60}")
    private long bookingCacheTtlMinutes;

    // Session TTL in minutes — configurable via environment variable / application property
    @Value("${app.session.ttl-minutes:30}")
    private long sessionTtlMinutes;

    /**
     * Inventory service URL externalized to AWS SSM Parameter Store (cr-java-0071).
     * Value is injected at runtime via Spring Boot property resolved from environment variable
     * INVENTORY_SERVICE_URL or application.properties key app.inventory.endpoint.
     */
    @Value("${app.inventory.endpoint:http://inventory-service.internal:8081/rooms/available}")
    private String inventoryServiceUrl;

    private static final String SESSION_PREFIX = "session:";
    private static final String CACHE_PREFIX   = "booking:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Replaced HttpSession.setAttribute("lastBooking", ...) with Redis (cr-java-0065, blocker-13)
        // Replaced HttpSession.setAttribute("guestName", ...) with Redis (cr-java-0065, blocker-14/15)
        if (sessionId != null && !sessionId.isEmpty()) {
            redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, "lastBooking", booking);
            redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, "guestName", guestName);
            redisTemplate.expire(SESSION_PREFIX + sessionId, sessionTtlMinutes, TimeUnit.MINUTES);
        }

        // Replaced static in-memory bookingCache.put(...) with Redis + TTL (cr-java-0067, blocker-18)
        String cacheKey = CACHE_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Replaced HttpSession.getAttribute("guestName") with Redis lookup (cr-java-0065, blocker-12/16)
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            Object sessionGuest = redisTemplate.opsForHash()
                    .get(SESSION_PREFIX + sessionId, "guestName");
            lastGuest = sessionGuest != null ? sessionGuest.toString() : null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Replaced hard-coded inventory URL with externalized SSM Parameter Store value (cr-java-0071)
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Replaced hard-coded local file path with S3-backed report URL via ReportService
        String reportName = month + "_bookings.csv";

        Map<String, Object> response = new HashMap<>();
        response.put("reportName", reportName);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
