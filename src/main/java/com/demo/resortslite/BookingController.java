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

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint:http://inventory-service:8081/rooms/available}")
    private String inventoryUrl;

    @Value("${cache.ttl.minutes:30}")
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
        if (redisTemplate != null && sessionId != null) {
            String sessionKey = "session:" + sessionId;
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("lastBooking", booking);
            sessionData.put("guestName", guestName);
            redisTemplate.opsForValue().set(sessionKey, sessionData, cacheTtlMinutes, TimeUnit.MINUTES);
        }

        // Store booking in Redis cache with TTL
        if (redisTemplate != null) {
            String cacheKey = "booking:" + booking.get("bookingId");
            redisTemplate.opsForValue().set(cacheKey, booking, cacheTtlMinutes, TimeUnit.MINUTES);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String lastGuest = null;
        
        // Retrieve session data from Redis instead of HTTP session
        if (redisTemplate != null && sessionId != null) {
            String sessionKey = "session:" + sessionId;
            Object sessionData = redisTemplate.opsForValue().get(sessionKey);
            if (sessionData instanceof Map) {
                lastGuest = (String) ((Map<?, ?>) sessionData).get("guestName");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use GCS bucket path instead of local file system
        String reportPath = "gs://resort-reports-bucket/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
