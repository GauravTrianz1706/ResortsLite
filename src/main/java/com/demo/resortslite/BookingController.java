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

    @Value("${app.inventory.endpoint:http://inventory-service:8081/rooms/available}")
    private String inventoryServiceUrl;

    @Value("${aws.s3.reports.bucket:resort-reports-bucket}")
    private String reportsBucketName;

    @Value("${app.cache.ttl.minutes:30}")
    private long cacheTtlMinutes;

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
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            redisTemplate.expire(sessionKey, cacheTtlMinutes, TimeUnit.MINUTES);
        }

        // Store booking in Redis cache with TTL
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, cacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Redis
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

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory service URL from configuration
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use S3 bucket path instead of local file system
        String reportKey = "reports/" + month + "_bookings.pdf";
        String s3Path = "s3://" + reportsBucketName + "/" + reportKey;

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", s3Path);
        response.put("bucket", reportsBucketName);
        response.put("key", reportKey);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
