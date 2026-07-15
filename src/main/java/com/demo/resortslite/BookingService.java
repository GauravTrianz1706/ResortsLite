package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for resort booking business logic.
 *
 * <p>Java 21 / Spring Boot 3.2.x compatibility notes:
 * <ul>
 *   <li>All SQL queries use parameterised statements — SQL injection risk eliminated.</li>
 *   <li>Confirmation codes use SHA-256 (was MD5 — cryptographically broken, deprecated for
 *       security use since Java 9 security guidelines).</li>
 *   <li>Credentials and endpoints are externalised via {@code application.properties} /
 *       environment variables — no hardcoded secrets in source.</li>
 * </ul>
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Creates a new booking record in the database.
     *
     * <p>Fixed: parameterised INSERT prevents SQL injection (was string concatenation).
     * Fixed: confirmation code uses SHA-256 (was MD5).
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Parameterised query — prevents SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // SHA-256 confirmation code (replaces deprecated MD5)
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        return booking;
    }

    /**
     * Retrieves a booking by its ID.
     *
     * <p>Fixed: parameterised SELECT prevents SQL injection (was string concatenation).
     */
    public Map<String, Object> getBookingById(String bookingId) {
        // Parameterised query — prevents SQL injection
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates the total room price based on type, duration, season, and loyalty tier.
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice = switch (roomType) {
            case "DELUXE" -> 200.0;
            case "SUITE"  -> 350.0;
            case "VILLA"  -> 600.0;
            default       -> 120.0; // STANDARD and unknown types
        };

        // Season multiplier
        if ("PEAK".equals(season))     { basePrice *= 1.5; }
        else if ("OFF".equals(season)) { basePrice *= 0.8; }

        // Loyalty discount
        if ("GOLD".equals(loyalty))          { basePrice *= 0.9; }
        else if ("PLATINUM".equals(loyalty)) { basePrice *= 0.8; }
        else if ("DIAMOND".equals(loyalty))  { basePrice *= 0.7; }

        // Long-stay discount (7+ nights takes priority over 14+ for the first bracket)
        if (nights >= 14)     { basePrice *= 0.90; }
        else if (nights >= 7) { basePrice *= 0.95; }

        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    /**
     * Returns {@code true} if the requested room type is offered by the resort.
     */
    public boolean isRoomAvailable(String roomType) {
        return switch (roomType) {
            case "STANDARD", "DELUXE", "SUITE", "VILLA" -> true;
            default -> false;
        };
    }

    /**
     * Triggers report generation for the given month.
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes a SHA-256 hex digest of the given input string.
     *
     * <p>Replaces the previous MD5 implementation — MD5 is cryptographically broken
     * and must not be used for security-sensitive hashing (Java security guidelines,
     * NIST SP 800-131A).
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every Java SE implementation
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
