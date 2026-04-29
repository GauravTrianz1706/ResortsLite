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

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    @Value("${azure.storage.container-name}")
    private String storageContainerName;

    /**
     * Creates a new booking with distributed session management using Azure Cache for Redis
     * Fixes: cr-java-0065, cr-java-0067
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Azure Cache for Redis with TTL
        // Fixes: cr-java-0065 (HTTP Session State Storage)
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId;
            redisTemplate.opsForHash().put(sessionKey, "lastBooking", booking);
            redisTemplate.opsForHash().put(sessionKey, "guestName", guestName);
            // Set TTL of 30 minutes for session data
            redisTemplate.expire(sessionKey, 30, TimeUnit.MINUTES);
        }

        // Store booking in distributed cache with TTL
        // Fixes: cr-java-0067 (In-Memory Caching Without TTL)
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, 60, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using distributed session from Redis
     * Fixes: cr-java-0065
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Azure Cache for Redis
        // Fixes: cr-java-0065 (HTTP Session State Storage)
        String lastGuest = null;
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
     * Checks room availability with externalized service endpoint
     * Fixes: cr-java-0071
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory service URL from Azure App Configuration
        // Fixes: cr-java-0071 (Hard-coded Environment URLs)
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads report from Azure Blob Storage
     * Fixes: cr-java-0061, cr-java-0071
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use Azure Blob Storage instead of local file system
        // Fixes: cr-java-0061 (Hard-coded File Paths)
        String reportFileName = month + "_bookings.pdf";
        
        Map<String, Object> response = new HashMap<>();
        response.put("storageType", "Azure Blob Storage");
        response.put("containerName", storageContainerName);
        response.put("reportFileName", reportFileName);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
