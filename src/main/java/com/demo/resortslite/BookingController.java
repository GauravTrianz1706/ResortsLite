package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private S3Service s3Service;

    // Blocker-13 (cz-java-0070): Replaced local cache with Redis-backed distributed cache
    // Local cache removed - session data now stored in Redis via Spring Session
    // private static final Map<String, Object> bookingCache = new HashMap<>();

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Blocker-5 (cz-java-0063): Session now backed by Redis via Spring Session
        // Blocker-7 (cz-java-0069): In-memory session replaced with distributed Redis session
        session.setAttribute("lastBooking", booking); 
        // Blocker-8 (cz-java-0069): In-memory session replaced with distributed Redis session
        session.setAttribute("guestName", guestName);

        // Blocker-13: Cache operations now handled by Redis (via Spring Session)
        // bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Blocker-6 (cz-java-0063): Session now backed by Redis via Spring Session
        String lastGuest = (String) session.getAttribute("guestName"); 

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Blocker-9 (cz-java-0082): Using externalized configuration for service endpoints
        // Enables service mesh and API Gateway integration
        String inventoryUrl = inventoryServiceUrl + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Blocker-1 (cz-java-0057): Replaced absolute file path with S3 storage
        String reportKey = "reports/" + month + "_bookings.pdf";
        String reportUrl = s3Service.getFileUrl(reportKey);

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportUrl);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
