package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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

    @Value("${app.inventory.endpoint:http://inventory-svc:8081/rooms}")
    private String inventoryEndpoint;

    @Value("${aws.s3.bucket.reports:resort-reports-bucket}")
    private String s3ReportsBucket;

    @Value("${spring.cache.redis.time-to-live:3600000}")
    private long cacheTtl;

    /**
     * Creates a new booking with distributed session management via Redis
     * Replaces HTTP session storage with Amazon ElastiCache for Redis
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis instead of HTTP session
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            
            // Store last booking in Redis with TTL
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            redisTemplate.expire(sessionKey, 30, TimeUnit.MINUTES);
        }

        // Store booking in Redis cache with TTL instead of unbounded in-memory cache
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtl, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status with distributed session support
     * Replaces HTTP session with Redis-backed session storage
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String lastGuest = null;
        
        // Retrieve session data from Redis instead of HTTP session
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            Object guestNameObj = redisTemplate.opsForHash().get(sessionKey, "guestName");
            if (guestNameObj != null) {
                lastGuest = guestNameObj.toString();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using externalized service endpoint
     * Replaces hard-coded environment URLs with AWS Parameter Store configuration
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads report from S3 instead of local file system
     * Replaces hard-coded file paths with Amazon S3 object storage
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Generate S3 path instead of local file system path
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = "s3://" + s3ReportsBucket + "/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3ReportsBucket);
        response.put("s3Key", s3Key);
        response.put("s3Url", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    /**
     * Cached method demonstrating Redis-based caching with TTL
     * Replaces unbounded in-memory caching with managed Redis cache
     */
    @Cacheable(value = "bookingCache", key = "#bookingId")
    @GetMapping("/cached/{bookingId}")
    public Map<String, Object> getCachedBooking(@PathVariable String bookingId) {
        return bookingService.getBookingById(bookingId);
    }
}
