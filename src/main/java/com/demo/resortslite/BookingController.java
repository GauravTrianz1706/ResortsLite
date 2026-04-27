package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint:http://inventory-svc:8081/rooms/available}")
    private String inventoryServiceUrl;

    @Value("${aws.s3.bucket:resorts-lite-reports}")
    private String s3BucketName;

    @Value("${spring.cache.redis.time-to-live:3600000}")
    private long cacheTtlMs;

    /**
     * Creates a booking and stores session data in Redis instead of HTTP session.
     * This enables stateless application instances with distributed session management.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis (Amazon ElastiCache) instead of HTTP session
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            
            // Store last booking in Redis with TTL
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            redisTemplate.expire(sessionKey, 30, TimeUnit.MINUTES);
        }

        // Store booking in Redis cache with TTL instead of unbounded in-memory cache
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtlMs, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using Redis for session management.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Redis instead of HTTP session
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            Object guestNameObj = redisTemplate.opsForHash().get(sessionKey, "guestName");
            lastGuest = guestNameObj != null ? guestNameObj.toString() : null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using externalized inventory service URL.
     * URL is retrieved from AWS Parameter Store via environment variables.
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads report from Amazon S3 instead of local file system.
     * Replaces hard-coded file paths with S3 object keys.
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Generate S3 path instead of local file system path
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = "https://" + s3BucketName + ".s3.amazonaws.com/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3BucketName);
        response.put("s3Key", s3Key);
        response.put("downloadUrl", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    /**
     * Clears cached booking data from Redis.
     */
    @DeleteMapping("/cache/{bookingId}")
    public Map<String, Object> clearCache(@PathVariable String bookingId) {
        String cacheKey = "booking:" + bookingId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        
        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", bookingId);
        response.put("cacheCleared", deleted != null && deleted);
        return response;
    }
}
