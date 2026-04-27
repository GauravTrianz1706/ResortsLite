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
    private String inventoryUrl;

    // Blocker-13: Replaced local cache with distributed cache via Spring Cache
    // Local cache removed - now using @Cacheable annotation with Redis backend

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Blocker-5, Blocker-7, Blocker-8: Session now backed by Redis via Spring Session
        // HttpSession is now externalized to Memorystore Redis automatically
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Blocker-13: Caching now handled by @Cacheable on service methods with Redis

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

        // Blocker-6: Session now backed by Redis via Spring Session
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Blocker-9: Service endpoint externalized to configuration
        // inventoryUrl now injected from application.properties

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Blocker-1: Replaced absolute file path with GCS storage
        String reportFileName = month + "_bookings.pdf";
        
        Map<String, Object> response = new HashMap<>();
        try {
            // Check if report exists in GCS
            if (gcsStorageService.fileExists(reportFileName)) {
                String signedUrl = gcsStorageService.getSignedUrl(reportFileName, 60);
                response.put("reportUrl", signedUrl);
                response.put("storage", "gcs");
            } else {
                response.put("message", "Report not found in cloud storage");
            }
        } catch (Exception e) {
            response.put("error", "Failed to access cloud storage: " + e.getMessage());
        }
        
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
