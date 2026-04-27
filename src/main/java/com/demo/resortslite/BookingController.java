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

    // Externalized configuration using AWS Systems Manager Parameter Store
    @Value("${app.inventory.endpoint:http://inventory-service:8081/rooms/available}")
    private String inventoryServiceUrl;

    @Value("${aws.s3.report.bucket:resort-reports-bucket}")
    private String reportBucket;

    @Value("${app.cache.ttl.seconds:3600}")
    private long cacheTtlSeconds;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Replace HTTP session with Redis-backed distributed session storage
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId + ":lastBooking";
            String guestKey = "session:" + sessionId + ":guestName";
            
            // Store in Amazon ElastiCache for Redis with TTL
            redisTemplate.opsForValue().set(sessionKey, booking, cacheTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(guestKey, guestName, cacheTtlSeconds, TimeUnit.SECONDS);
        }

        // Replace unbounded in-memory cache with Redis cache with TTL
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtlSeconds, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Redis instead of HTTP session
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            String guestKey = "session:" + sessionId + ":guestName";
            lastGuest = (String) redisTemplate.opsForValue().get(guestKey);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized configuration from AWS Parameter Store
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Replace hard-coded file path with S3 bucket reference
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Uri = "s3://" + reportBucket + "/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("reportBucket", reportBucket);
        response.put("reportKey", s3Key);
        response.put("s3Uri", s3Uri);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
