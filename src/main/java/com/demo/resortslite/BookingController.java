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

    // Replaced hard-coded URL with externalized configuration from Azure App Configuration
    @Value("${app.inventory.endpoint:http://localhost:8081/rooms/available}")
    private String inventoryServiceUrl;

    @Value("${azure.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${azure.redis.ttl.seconds:3600}")
    private long redisTtlSeconds;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam(required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Replaced HTTP session with Azure Cache for Redis for stateless architecture
        if (redisEnabled && redisTemplate != null && sessionId != null) {
            String sessionKey = "session:" + sessionId;
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            redisTemplate.expire(sessionKey, redisTtlSeconds, TimeUnit.SECONDS);
        }

        // Replaced in-memory cache with Azure Cache for Redis with TTL
        String bookingId = (String) booking.get("bookingId");
        if (redisEnabled && redisTemplate != null) {
            String cacheKey = "booking:" + bookingId;
            redisTemplate.opsForValue().set(cacheKey, booking, redisTtlSeconds, TimeUnit.SECONDS);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestParam(required = false) String sessionId) {

        // Replaced HTTP session with Azure Cache for Redis
        String lastGuest = null;
        if (redisEnabled && redisTemplate != null && sessionId != null) {
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

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Replaced hard-coded URL with externalized configuration from Azure App Configuration
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Replaced hard-coded file path with Azure Blob Storage reference
        String reportName = month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportName", reportName);
        response.put("storageType", "Azure Blob Storage");
        response.put("message", bookingService.generateReport(month));
        response.put("note", "Report will be available in Azure Blob Storage container");
        return response;
    }
}
