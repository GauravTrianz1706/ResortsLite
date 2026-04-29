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

@RestController
@RequestMapping("/api/bookings")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint:http://inventory-svc:8081/rooms}")
    private String inventoryEndpoint;

    @Value("${aws.s3.bucket.name:resorts-lite-reports}")
    private String s3BucketName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    // Cache TTL in seconds (30 minutes)
    private static final long CACHE_TTL = 1800;

    /**
     * Create booking with distributed session management using Redis
     * Fixes: cr-java-0065 (HTTP session state storage)
     * Fixes: cr-java-0067 (In-memory caching without TTL)
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store in Redis-backed session instead of in-memory HTTP session
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Store in Redis cache with TTL instead of unbounded in-memory cache
        String bookingId = (String) booking.get("bookingId");
        String cacheKey = "booking:" + bookingId;
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Get booking status using Redis-backed session
     * Fixes: cr-java-0065 (HTTP session state storage)
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Retrieve from Redis-backed session
        String lastGuest = (String) session.getAttribute("guestName");

        // Try to get from Redis cache first
        String cacheKey = "booking:" + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        
        if (cachedBooking != null) {
            result.put("details", cachedBooking);
            result.put("source", "cache");
        } else {
            Map<String, Object> bookingDetails = bookingService.getBookingById(bookingId);
            result.put("details", bookingDetails);
            result.put("source", "database");
            
            // Cache the result with TTL
            redisTemplate.opsForValue().set(cacheKey, bookingDetails, CACHE_TTL, TimeUnit.SECONDS);
        }
        
        return result;
    }

    /**
     * Check room availability using externalized inventory endpoint
     * Fixes: cr-java-0071 (Hard-coded environment URLs)
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint); // Now externalized
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Download report from S3 instead of local file system
     * Fixes: cr-java-0061 (Hard-coded file paths)
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Generate S3 URL instead of local file path
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = "https://" + s3BucketName + ".s3." + awsRegion + ".amazonaws.com/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3BucketName);
        response.put("s3Key", s3Key);
        response.put("s3Url", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    /**
     * Clear cache entry (utility method)
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
