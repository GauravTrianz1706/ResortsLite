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

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceEndpoint;

    @Value("${azure.storage.container-name:resort-reports}")
    private String reportContainerName;

    /**
     * Creates a booking with distributed session management using Azure Redis Cache.
     * Replaces in-memory session storage with cloud-native distributed cache.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis for distributed session management
        String sessionId = session.getId();
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                    "session:" + sessionId + ":lastBooking", 
                    booking, 
                    30, 
                    TimeUnit.MINUTES
                );
                redisTemplate.opsForValue().set(
                    "session:" + sessionId + ":guestName", 
                    guestName, 
                    30, 
                    TimeUnit.MINUTES
                );
            } catch (Exception e) {
                System.err.println("Failed to store session in Redis: " + e.getMessage());
                // Fallback to HTTP session for development
                session.setAttribute("lastBooking", booking);
                session.setAttribute("guestName", guestName);
            }
        } else {
            // Fallback to HTTP session when Redis is not configured
            session.setAttribute("lastBooking", booking);
            session.setAttribute("guestName", guestName);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Retrieves booking status using distributed session from Redis.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        String lastGuest = null;
        String sessionId = session.getId();

        // Retrieve session data from Redis
        if (redisTemplate != null) {
            try {
                lastGuest = (String) redisTemplate.opsForValue().get("session:" + sessionId + ":guestName");
            } catch (Exception e) {
                System.err.println("Failed to retrieve session from Redis: " + e.getMessage());
                // Fallback to HTTP session
                lastGuest = (String) session.getAttribute("guestName");
            }
        } else {
            // Fallback to HTTP session when Redis is not configured
            lastGuest = (String) session.getAttribute("guestName");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using externalized inventory service endpoint.
     * Replaces hard-coded URLs with environment-based configuration.
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Downloads report using Azure Blob Storage.
     * Replaces hard-coded file paths with cloud storage references.
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        String reportFileName = month + "_bookings.pdf";
        String reportUrl = String.format(
            "https://%s.blob.core.windows.net/%s/%s",
            System.getenv("AZURE_STORAGE_ACCOUNT_NAME"),
            reportContainerName,
            reportFileName
        );

        Map<String, Object> response = new HashMap<>();
        response.put("reportUrl", reportUrl);
        response.put("message", bookingService.generateReport(month));
        response.put("storage", "azure-blob");
        return response;
    }
}
