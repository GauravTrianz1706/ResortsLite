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
 * BookingController — cr-java-0067 remediation applied (in addition to cr-java-0065).
 *
 * PREVIOUS FIX (cr-java-0065):
 *   HTTP session state is backed by Amazon ElastiCache for Redis via
 *   Spring Session Data Redis (@EnableRedisHttpSession).
 *
 * THIS FIX (cr-java-0067 — In-Memory Caching Without TTL):
 *   The unbounded static HashMap used as a local in-memory booking cache:
 *
 *       private static final Map<String, Object> bookingCache = new HashMap<>();
 *
 *   has been replaced with a RedisTemplate-backed distributed cache that writes
 *   entries to Amazon ElastiCache for Redis with a configurable TTL
 *   (default: 60 minutes, controlled by the BOOKING_CACHE_TTL_MINUTES env var).
 *
 *   Benefits:
 *   - Controlled expiration prevents indefinite memory growth and stale data.
 *   - Cache is shared across all application instances — no per-instance divergence.
 *   - Entries are automatically evicted by Redis after the TTL expires.
 *   - Horizontal scaling is fully supported without server affinity.
 *
 * Required runtime configuration (application.properties / env vars):
 *   spring.redis.host              — ElastiCache primary endpoint  (default: localhost)
 *   spring.redis.port              — ElastiCache port               (default: 6379)
 *   spring.session.store-type=redis
 *   BOOKING_CACHE_TTL_MINUTES      — TTL for booking cache entries  (default: 60)
 */
@EnableRedisHttpSession
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * cr-java-0067: Replaces the unbounded static HashMap in-memory cache.
     * RedisTemplate writes booking entries to Amazon ElastiCache for Redis
     * with a TTL so that entries expire automatically and memory growth is bounded.
     * The cache is shared across all application instances, eliminating stale-data
     * inconsistencies that arise when each instance maintains its own local copy.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * TTL (in minutes) for booking cache entries stored in Redis.
     * Defaults to 60 minutes; override via the BOOKING_CACHE_TTL_MINUTES
     * environment variable or the booking.cache.ttl.minutes property.
     */
    @Value("${booking.cache.ttl.minutes:${BOOKING_CACHE_TTL_MINUTES:60}}")
    private long bookingCacheTtlMinutes;

    /** Redis key prefix for booking cache entries. */
    private static final String BOOKING_CACHE_PREFIX = "resortslite:booking:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cr-java-0065: session is now backed by Amazon ElastiCache for Redis
        // via Spring Session Data Redis — no server affinity, fully distributed.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // cr-java-0067: Store booking in Redis-backed distributed cache with TTL.
        // Replaces: bookingCache.put((String) booking.get("bookingId"), booking)
        // The entry will be automatically evicted after bookingCacheTtlMinutes minutes,
        // preventing unbounded memory growth and stale data across instances.
        String cacheKey = BOOKING_CACHE_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // cr-java-0065: session.getAttribute reads from Redis-backed distributed session.
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
       
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available"; 

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
       
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; 

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
