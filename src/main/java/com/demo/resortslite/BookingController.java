package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;   // Migrated: javax.servlet → jakarta.servlet (Spring Boot 3 / Jakarta EE 10)
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for resort booking operations.
 *
 * <p>Java 21 / Spring Boot 3.2.x compatibility notes:
 * <ul>
 *   <li>Uses {@code jakarta.servlet.http.HttpSession} (was {@code javax.servlet.http.HttpSession}).</li>
 *   <li>All endpoints use parameterised service calls — no raw SQL concatenation.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /** Simple in-memory cache keyed by bookingId. */
    private static final Map<String, Object> bookingCache = new HashMap<>();

    /**
     * Creates a new booking and stores it in the HTTP session and local cache.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Returns the status of an existing booking.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability for the requested room type.
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Triggers report generation for the given month.
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
