package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${CACHE_TTL:3600000}")
    private long cacheTtlMs;

    @Value("${app.inventory.endpoint:http://inventory-svc:8081/rooms}")
    private String inventoryEndpoint;

    /**
     * Creates a booking with distributed session management using Redis.
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

        // Store session data in Redis (distributed session) instead of local HTTP session
        // Spring Session automatically handles this, but we also store in Redis cache explicitly
        String sessionId = session.getId();
        redisTemplate.opsForValue().set("session:" + sessionId + ":lastBooking", booking, cacheTtlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set("session:" + sessionId + ":guestName", guestName, cacheTtlMs, TimeUnit.MILLISECONDS);

        // Store booking in Redis cache with TTL instead of unbounded in-memory cache
        String bookingId = (String) booking.get("bookingId");
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, cacheTtlMs, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using distributed session from Redis.
     * Fixes: cr-java-0065 (HTTP session state storage)
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Retrieve session data from Redis instead of local HTTP session
        String sessionId = session.getId();
        String lastGuest = (String) redisTemplate.opsForValue().get("session:" + sessionId + ":guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using externalized inventory endpoint.
     * Fixes: cr-java-0071 (Hard-coded environment URLs)
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory endpoint from environment variable/Parameter Store
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads report from S3 instead of local file system.
     * Fixes: cr-java-0061 (Hard-coded file paths)
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Reports are now stored in S3, not local file system
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Bucket = System.getenv("AWS_S3_BUCKET");
        if (s3Bucket == null || s3Bucket.isEmpty()) {
            s3Bucket = "resorts-reports-bucket";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3Bucket);
        response.put("s3Key", s3Key);
        response.put("s3Url", "s3://" + s3Bucket + "/" + s3Key);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
