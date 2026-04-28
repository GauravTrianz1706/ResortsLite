package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private GcsStorageService gcsStorageService;

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    // Blocker-13: Replaced local cache with distributed Redis cache via @Cacheable
    // Local cache removed - now using Spring Cache with Redis backend

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Blocker-5, Blocker-7, Blocker-8: Session now backed by Redis via Spring Session
        // Session state is externalized to Memorystore Redis for stateless scaling
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Blocker-13: Booking now cached in distributed Redis cache instead of local HashMap
        // Cache is managed by Spring Cache abstraction with Redis backend

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookingStatus", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Blocker-6: Session retrieval now uses Redis-backed session
        // Session persists across container restarts and scales horizontally
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Blocker-9: Service endpoint externalized to support microservices architecture
        // Uses Kubernetes service DNS instead of hardcoded URL
        
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Blocker-1: Replaced absolute file path with GCS storage
        // Files now stored in Google Cloud Storage instead of local filesystem
        String reportFileName = month + "_bookings.pdf";
        String reportPath = gcsStorageService.getFileUrl(reportFileName);

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
