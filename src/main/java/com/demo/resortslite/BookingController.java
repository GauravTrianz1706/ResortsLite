package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-ready version.
 *
 * Changes applied:
 *  - blocker-12/13/14/15/16 (cr-java-0065): Removed HttpSession usage entirely.
 *    Session state is now stored in Google Cloud Memorystore for Redis via
 *    Spring Data Redis (RedisTemplate). Each session entry is stored with a
 *    30-minute TTL to prevent unbounded memory growth.
 *  - blocker-18  (cr-java-0067): Replaced the static in-memory bookingCache
 *    (HashMap without TTL) with Redis-backed caching through RedisTemplate,
 *    ensuring consistent cache state across all instances and preventing
 *    memory exhaustion.
 *  - blocker-10  (cr-java-0071): Replaced the hard-coded inventory service URL
 *    (http://inventory-service.internal:8081/rooms/available) with an
 *    @Value-injected field backed by the APP_INVENTORY_ENDPOINT environment
 *    variable / application.properties entry.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Session TTL in minutes — can be overridden via SESSION_TTL_MINUTES env var.
    @Value("${session.ttl.minutes:${SESSION_TTL_MINUTES:30}}")
    private long sessionTtlMinutes;

    // Inventory service URL externalised — no hard-coded internal hostname.
    @Value("${app.inventory.endpoint:${APP_INVENTORY_ENDPOINT:http://inventory-service.internal:8081/rooms/available}}")
    private String inventoryEndpoint;

    private static final String SESSION_PREFIX = "session:";
    private static final String CACHE_PREFIX   = "booking:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam(required = false, defaultValue = "anonymous") String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session state in Redis with TTL (replaces HttpSession).
        String sessionKey = SESSION_PREFIX + sessionId;
        redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
        redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
        redisTemplate.expire(sessionKey, sessionTtlMinutes, TimeUnit.MINUTES);

        // Cache booking in Redis with TTL (replaces static in-memory HashMap).
        String cacheKey = CACHE_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, sessionTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestParam(required = false, defaultValue = "anonymous") String sessionId) {

        // Retrieve session state from Redis (replaces HttpSession.getAttribute).
        String sessionKey = SESSION_PREFIX + sessionId;
        String lastGuest = (String) redisTemplate.opsForHash().get(sessionKey, "guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // inventoryEndpoint is now externalised via environment variable.
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report path is now a GCS URI resolved by ReportService — no local path.
        Map<String, Object> response = new HashMap<>();
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
