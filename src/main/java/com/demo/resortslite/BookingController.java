package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
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

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    @Value("${azure.storage.container-name:resort-reports}")
    private String reportContainerName;

    // Cache TTL in seconds (1 hour)
    private static final long CACHE_TTL = 3600;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis instead of HTTP session for distributed session management
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId + ":lastBooking";
            String guestKey = "session:" + sessionId + ":guestName";
            
            redisTemplate.opsForValue().set(sessionKey, booking, CACHE_TTL, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(guestKey, guestName, CACHE_TTL, TimeUnit.SECONDS);
        }

        // Store booking in Redis cache with TTL instead of in-memory cache
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookingStatus", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Redis instead of HTTP session
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            String guestKey = "session:" + sessionId + ":guestName";
            Object guestObj = redisTemplate.opsForValue().get(guestKey);
            if (guestObj != null) {
                lastGuest = guestObj.toString();
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
        // Use externalized configuration for service URLs instead of hard-coded values
        String inventoryUrl = inventoryServiceUrl + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use Azure Blob Storage instead of local file system paths
        String reportBlobName = month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportBlobName", reportBlobName);
        response.put("containerName", reportContainerName);
        response.put("message", bookingService.generateReport(month));
        response.put("storageType", "Azure Blob Storage");
        return response;
    }

    @CacheEvict(value = "bookingStatus", key = "#bookingId")
    @DeleteMapping("/{bookingId}")
    public Map<String, Object> cancelBooking(@PathVariable String bookingId) {
        // Clear cache when booking is cancelled
        String cacheKey = "booking:" + bookingId;
        redisTemplate.delete(cacheKey);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "cancelled");
        response.put("bookingId", bookingId);
        return response;
    }
}
