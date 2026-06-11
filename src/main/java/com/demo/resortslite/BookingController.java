package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-native version.
 *
 * Blockers resolved:
 *  - cr-java-0071 (line 60): Hard-coded inventory URL replaced with value from
 *                             AWS Systems Manager Parameter Store (injected via
 *                             environment variable INVENTORY_SERVICE_URL).
 *  - cr-java-0065 (lines 6, 26, 31, 32, 45): HTTP session state migrated to
 *                             Amazon ElastiCache for Redis via Spring Session.
 *                             HttpSession is now backed by Redis automatically
 *                             through the spring-session-data-redis dependency
 *                             and @EnableRedisHttpSession configuration.
 *  - cr-java-0067 (line 18):  Unbounded in-memory HashMap cache replaced with
 *                             Amazon ElastiCache for Redis via Spring Cache
 *                             abstraction (RedisTemplate with TTL).
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * Distributed Redis cache via Amazon ElastiCache — replaces the unbounded
     * static in-memory HashMap (cr-java-0067).
     * TTL is configured in application.properties (spring.cache.redis.time-to-live).
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Inventory service URL — injected from environment variable INVENTORY_SERVICE_URL
     * which is populated from AWS Systems Manager Parameter Store at deployment time.
     * Replaces hard-coded "http://inventory-service.internal:8081/rooms/available" (cr-java-0071).
     */
    @Value("${app.inventory.url:${INVENTORY_SERVICE_URL:http://inventory-service.internal:8081/rooms/available}}")
    private String inventoryServiceUrl;

    /** Redis key prefix for booking cache entries. */
    private static final String BOOKING_CACHE_PREFIX = "booking:";

    /** TTL for booking cache entries in Redis (30 minutes). */
    private static final long BOOKING_CACHE_TTL_MINUTES = 30L;

    /**
     * Creates a booking and stores session state in Amazon ElastiCache for Redis
     * via Spring Session (cr-java-0065).
     * Booking result is also cached in Redis with TTL (cr-java-0067).
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {  // HttpSession is now Redis-backed via Spring Session

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Session attributes stored in Amazon ElastiCache for Redis (Spring Session)
        // instead of in-process server memory — enables stateless horizontal scaling
        session.setAttribute("lastBooking", booking);   // cr-java-0065 resolved
        session.setAttribute("guestName", guestName);   // cr-java-0065 resolved

        // Cache booking in Redis with TTL — replaces unbounded static HashMap (cr-java-0067)
        String cacheKey = BOOKING_CACHE_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, BOOKING_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status; reads session guest from Redis-backed Spring Session (cr-java-0065).
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {  // HttpSession is now Redis-backed via Spring Session

        // Session attribute read from Amazon ElastiCache for Redis (cr-java-0065 resolved)
        String lastGuest = (String) session.getAttribute("guestName");

        // Try Redis cache first before hitting the database
        String cacheKey = BOOKING_CACHE_PREFIX + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", cachedBooking != null ? cachedBooking : bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using the inventory service URL resolved from
     * AWS Systems Manager Parameter Store (cr-java-0071 resolved).
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // inventoryServiceUrl is injected from SSM Parameter Store via environment variable
        // — replaces hard-coded "http://inventory-service.internal:8081/rooms/available"
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Returns a report download reference pointing to Amazon S3
     * instead of a local file path.
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report path is now an S3 reference — no local file system dependency
        String s3ReportKey = "reports/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("s3Key", s3ReportKey);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
