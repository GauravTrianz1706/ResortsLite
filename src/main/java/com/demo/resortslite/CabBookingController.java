package com.demo.resortslite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for cab booking operations.
 * Provides endpoints to check availability, create, retrieve, and update cab bookings
 * linked to hotel reservations.
 */
@RestController
@RequestMapping("/api/bookings")
public class CabBookingController {

    private static final Logger logger = LoggerFactory.getLogger(CabBookingController.class);

    @Autowired
    private CabService cabService;

    @Autowired
    private BookingService bookingService;

    /**
     * GET /api/bookings/{bookingId}/cabs/availability
     * Checks available cabs for a booking based on pickup time and passenger count.
     *
     * @param bookingId Hotel booking ID
     * @param pickupTime ISO-8601 formatted timestamp
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @return List of available cab options with type, capacity, price, and API reference
     */
    @GetMapping("/{bookingId}/cabs/availability")
    public ResponseEntity<?> checkCabAvailability(
            @PathVariable String bookingId,
            @RequestParam String pickupTime,
            @RequestParam int passengerCount,
            @RequestParam String tripType) {

        try {
            // Validate passenger count
            if (passengerCount <= 0) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Passenger count must be a positive integer");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate trip type
            if (!tripType.equals("AIRPORT_TO_HOTEL") && !tripType.equals("HOTEL_TO_AIRPORT")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Trip type must be 'AIRPORT_TO_HOTEL' or 'HOTEL_TO_AIRPORT'");
                return ResponseEntity.badRequest().body(error);
            }

            // Default pickup/dropoff locations based on trip type
            String pickupLocation = tripType.equals("AIRPORT_TO_HOTEL") ? "Airport" : "Hotel";
            String dropoffLocation = tripType.equals("AIRPORT_TO_HOTEL") ? "Hotel" : "Airport";

            List<Map<String, Object>> availableCabs = cabService.checkAvailability(
                pickupLocation, pickupTime, passengerCount, tripType);

            Map<String, Object> response = new HashMap<>();
            response.put("bookingId", bookingId);
            response.put("availableCabs", availableCabs);
            response.put("pickupTime", pickupTime);
            response.put("passengerCount", passengerCount);
            response.put("tripType", tripType);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error checking cab availability for booking {}: {}", bookingId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to check cab availability: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/bookings/{bookingId}/cabs
     * Adds a cab booking to an existing hotel reservation.
     *
     * @param bookingId Hotel booking ID
     * @param cabType Type of cab (SEDAN, SUV, VAN)
     * @param pickupTime ISO-8601 formatted timestamp
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @return Cab booking confirmation with details
     */
    @PostMapping("/{bookingId}/cabs")
    public ResponseEntity<?> createCabBooking(
            @PathVariable String bookingId,
            @RequestParam String cabType,
            @RequestParam String pickupTime,
            @RequestParam int passengerCount,
            @RequestParam String tripType) {

        try {
            // Validate that hotel booking exists
            Map<String, Object> hotelBooking = bookingService.getBookingById(bookingId);
            if (hotelBooking == null || hotelBooking.containsKey("error")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Hotel booking not found: " + bookingId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            // Determine pickup and dropoff locations based on trip type
            String pickupLocation = tripType.equals("AIRPORT_TO_HOTEL") ? "Airport" : "Hotel";
            String dropoffLocation = tripType.equals("AIRPORT_TO_HOTEL") ? "Hotel" : "Airport";

            // Calculate price based on cab type (mock pricing)
            double price = calculateCabPrice(cabType);

            // Create cab booking
            Map<String, Object> cabBooking = cabService.createCabBooking(
                bookingId, cabType, pickupTime, pickupLocation, dropoffLocation,
                passengerCount, tripType, price);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "confirmed");
            response.put("cabBooking", cabBooking);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Validation error creating cab booking for {}: {}", bookingId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            logger.error("Error creating cab booking for {}: {}", bookingId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to create cab booking: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * GET /api/bookings/{bookingId}/cabs
     * Retrieves cab booking details for a hotel reservation.
     *
     * @param bookingId Hotel booking ID
     * @return Cab booking details or empty if no cab booked
     */
    @GetMapping("/{bookingId}/cabs")
    public ResponseEntity<?> getCabBooking(@PathVariable String bookingId) {
        try {
            Map<String, Object> cabBooking = cabService.getCabBookingByBookingId(bookingId);

            if (cabBooking.isEmpty()) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "No cab booking found for this reservation");
                response.put("bookingId", bookingId);
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.ok(cabBooking);

        } catch (Exception e) {
            logger.error("Error retrieving cab booking for {}: {}", bookingId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to retrieve cab booking: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * PUT /api/bookings/{bookingId}/cabs/{cabId}
     * Updates an existing cab booking and synchronizes with third-party API.
     *
     * @param bookingId Hotel booking ID
     * @param cabId Cab booking ID
     * @param cabType Updated cab type (optional)
     * @param pickupTime Updated pickup time (optional)
     * @param passengerCount Updated passenger count (optional)
     * @param session HTTP session for ownership validation
     * @return Updated cab booking details
     */
    @PutMapping("/{bookingId}/cabs/{cabId}")
    public ResponseEntity<?> updateCabBooking(
            @PathVariable String bookingId,
            @PathVariable String cabId,
            @RequestParam(required = false) String cabType,
            @RequestParam(required = false) String pickupTime,
            @RequestParam(required = false) Integer passengerCount,
            HttpSession session) {

        try {
            // Validate session and booking ownership
            String sessionGuest = (String) session.getAttribute("guestName");
            if (sessionGuest == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No active session. Please log in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            // Verify hotel booking exists and belongs to session user
            Map<String, Object> hotelBooking = bookingService.getBookingById(bookingId);
            if (hotelBooking == null || hotelBooking.containsKey("error")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Hotel booking not found: " + bookingId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            String bookingGuest = (String) hotelBooking.get("guest");
            if (bookingGuest != null && !bookingGuest.equals(sessionGuest)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You do not have permission to modify this booking");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            // Calculate new price if cab type changed
            double price = calculateCabPrice(cabType != null ? cabType : "SEDAN");

            // Update cab booking
            Map<String, Object> updated = cabService.updateCabBooking(
                cabId, bookingId, cabType, pickupTime, passengerCount, price);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "updated");
            response.put("cabBooking", updated);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Validation error updating cab booking {}: {}", cabId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            logger.error("Error updating cab booking {}: {}", cabId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to update cab booking: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Calculates cab price based on cab type (mock pricing logic).
     *
     * @param cabType Type of cab
     * @return Price for the cab
     */
    private double calculateCabPrice(String cabType) {
        if (cabType == null) {
            return 45.00;
        }

        switch (cabType.toUpperCase()) {
            case "SEDAN":
                return 45.00;
            case "SUV":
                return 65.00;
            case "VAN":
                return 85.00;
            default:
                return 45.00;
        }
    }
}
