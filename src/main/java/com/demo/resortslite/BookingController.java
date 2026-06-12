package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // Using Map.of() for immutable collections (Java 9+)
    // Removed static mutable cache - not thread-safe and violates stateless REST principles

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Session usage for tracking
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        return Map.of(
            "status", "confirmed",
            "booking", booking
        );
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        String lastGuest = (String) session.getAttribute("guestName");

        return Map.of(
            "bookingId", bookingId,
            "sessionGuest", lastGuest != null ? lastGuest : "unknown",
            "details", bookingService.getBookingById(bookingId)
        );
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";

        return Map.of(
            "roomType", roomType,
            "inventoryEndpoint", inventoryUrl,
            "available", bookingService.isRoomAvailable(roomType)
        );
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf";

        return Map.of(
            "reportPath", reportPath,
            "message", bookingService.generateReport(month)
        );
    }
}
