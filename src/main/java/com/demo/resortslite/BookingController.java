package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
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

    @Value("${app.inventory.endpoint}")
    private String inventoryEndpoint;

    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${CACHE_TTL:3600000}")
    private long cacheTtl;

    /**
     * Creates a booking and stores session data in Redis (Amazon ElastiCache).
     * This enables stateless application instances with distributed session management.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store in Redis-backed session (Spring Session automatically handles this)
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Store in Redis cache with TTL instead of unbounded in-memory cache
        String bookingId = (String) booking.get("bookingId");
        String cacheKey = "booking:" + bookingId;
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtl, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using Redis-backed session.
     * Session data is stored in Amazon ElastiCache for Redis, enabling horizontal scaling.
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
            Map<String, Object> details = bookingService.getBookingById(bookingId);
            result.put("details", details);
            result.put("source", "database");
            
            // Cache the result with TTL
            if (!details.containsKey("error")) {
                redisTemplate.opsForValue().set(cacheKey, details, cacheTtl, TimeUnit.MILLISECONDS);
            }
        }
        
        return result;
    }

    /**
     * Checks room availability using externalized inventory service endpoint.
     * Service URLs are retrieved from environment variables or AWS Parameter Store.
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
     * Downloads report from Amazon S3 instead of local file system.
     * Reports are stored in S3 for durability and availability.
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Reports are now stored in S3, not local file system
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
     * Retrieves cached booking data from Redis with TTL.
     * This replaces unbounded in-memory caching with distributed cache management.
     */
    @GetMapping("/cache/{bookingId}")
    @Cacheable(value = "bookings", key = "#bookingId")
    public Map<String, Object> getCachedBooking(@PathVariable String bookingId) {
        // Spring Cache with Redis automatically handles caching with TTL
        return bookingService.getBookingById(bookingId);
    }
}
