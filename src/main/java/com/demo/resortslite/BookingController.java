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

    @Value("${app.inventory.endpoint:http://inventory-svc:8081/rooms}")
    private String inventoryServiceUrl;

    @Value("${azure.storage.container-name:resort-reports}")
    private String reportStorageContainer;

    /**
     * Stores data in Azure Cache for Redis with TTL instead of in-memory cache.
     * This enables distributed caching across multiple instances.
     */
    private void cacheBooking(String bookingId, Map<String, Object> booking) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("booking:" + bookingId, booking, 1, TimeUnit.HOURS);
            } catch (Exception e) {
                System.err.println("Failed to cache booking in Redis: " + e.getMessage());
            }
        }
    }

    /**
     * Retrieves cached booking from Azure Cache for Redis.
     */
    private Map<String, Object> getCachedBooking(String bookingId) {
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get("booking:" + bookingId);
                if (cached instanceof Map) {
                    return (Map<String, Object>) cached;
                }
            } catch (Exception e) {
                System.err.println("Failed to retrieve cached booking from Redis: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Stores session data in Azure Cache for Redis instead of HTTP session.
     * This enables stateless application architecture and horizontal scaling.
     */
    private void storeSessionData(String sessionKey, Object value) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("session:" + sessionKey, value, 30, TimeUnit.MINUTES);
            } catch (Exception e) {
                System.err.println("Failed to store session data in Redis: " + e.getMessage());
            }
        }
    }

    /**
     * Retrieves session data from Azure Cache for Redis.
     */
    private Object getSessionData(String sessionKey) {
        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get("session:" + sessionKey);
            } catch (Exception e) {
                System.err.println("Failed to retrieve session data from Redis: " + e.getMessage());
            }
        }
        return null;
    }

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Azure Cache for Redis instead of HTTP session
        if (sessionId != null && !sessionId.isEmpty()) {
            storeSessionData(sessionId + ":lastBooking", booking);
            storeSessionData(sessionId + ":guestName", guestName);
        }

        // Cache booking in Azure Cache for Redis with TTL
        String bookingId = (String) booking.get("bookingId");
        cacheBooking(bookingId, booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Azure Cache for Redis
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            Object guestObj = getSessionData(sessionId + ":guestName");
            if (guestObj instanceof String) {
                lastGuest = (String) guestObj;
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
        // Use externalized inventory service URL from Azure App Configuration
        String inventoryUrl = inventoryServiceUrl + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use Azure Blob Storage instead of local file system
        String reportName = month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportName", reportName);
        response.put("storageContainer", reportStorageContainer);
        response.put("message", bookingService.generateReport(month));
        response.put("storageType", "Azure Blob Storage");
        return response;
    }
}
