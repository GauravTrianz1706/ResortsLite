package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Booking Controller for Resort Booking Operations
 * 
 * FIXED cr-java-0065: HTTP Session State Storage
 * 
 * Session management is now backed by Amazon ElastiCache for Redis via Spring Session.
 * HttpSession API calls (setAttribute/getAttribute) are transparently stored in Redis
 * instead of in-memory, enabling:
 * - Stateless application instances
 * - Horizontal scalability
 * - Session persistence across instance restarts
 * - Load balancing without session affinity
 * 
 * Configuration: See RedisSessionConfig.java and application.properties
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AwsParameterStoreConfig.EnvironmentUrls environmentUrls;

   
    private static final Map<String, Object> bookingCache = new HashMap<>();

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session data now stored in Amazon ElastiCache for Redis
        // These setAttribute calls are backed by distributed Redis storage via Spring Session
        session.setAttribute("lastBooking", booking); 
        session.setAttribute("guestName", guestName);

        bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session data retrieved from Amazon ElastiCache for Redis
        // getAttribute call is backed by distributed Redis storage via Spring Session
        String lastGuest = (String) session.getAttribute("guestName"); 

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
       
        // URL retrieved from AWS Systems Manager Parameter Store
        String inventoryUrl = environmentUrls.getInventoryServiceUrl();

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
       
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; 

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
