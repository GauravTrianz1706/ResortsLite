package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Externalised configuration ────────────────────────────────────────────
    // Hardcoded DB_HOST / DB_USER / DB_PASS constants removed; connection
    // details are now managed exclusively through application.properties /
    // environment variables and the DataSource bean configured by Spring Boot.
    // Hardcoded PAYMENT_API IP replaced with an injected property value.
    @Value("${app.payment.endpoint:http://payment-svc:9090/charge}")
    private String paymentApi;

    // ── Booking creation ──────────────────────────────────────────────────────
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // PostgreSQL-compatible parameterised INSERT (ANSI SQL — no dialect-
        // specific syntax required for a simple INSERT).
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // SHA-256 confirmation code (MD5 replaced — weak/deprecated algorithm).
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        // dbHost removed from response — never expose infrastructure details
        return booking;
    }

    // ── Booking retrieval ─────────────────────────────────────────────────────
    public Map<String, Object> getBookingById(String bookingId) {
        // PostgreSQL-compatible parameterised SELECT.
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    // ── Pricing logic ─────────────────────────────────────────────────────────
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice;
        switch (roomType) {
            case "STANDARD": basePrice = 120.0; break;
            case "DELUXE":   basePrice = 200.0; break;
            case "SUITE":    basePrice = 350.0; break;
            case "VILLA":    basePrice = 600.0; break;
            default:         basePrice = 120.0; break;
        }
        switch (season) {
            case "PEAK": basePrice *= 1.5; break;
            case "OFF":  basePrice *= 0.8; break;
            default: break;
        }
        switch (loyalty) {
            case "GOLD":     basePrice *= 0.9; break;
            case "PLATINUM": basePrice *= 0.8; break;
            case "DIAMOND":  basePrice *= 0.7; break;
            default: break;
        }
        if (nights >= 14)      { basePrice *= 0.90; }
        else if (nights >= 7)  { basePrice *= 0.95; }
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    // ── Availability check ────────────────────────────────────────────────────
    public boolean isRoomAvailable(String roomType) {
        switch (roomType) {
            case "STANDARD":
            case "DELUXE":
            case "SUITE":
            case "VILLA":
                return true;
            default:
                return false;
        }
    }

    // ── Report trigger ────────────────────────────────────────────────────────
    public String generateReport(String month) {
        // paymentApi is now injected from application.properties, not hardcoded.
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /**
     * SHA-256 hash helper. MD5 was replaced because it is cryptographically
     * broken and flagged as a deprecated/weak algorithm for Java 11+ migrations.
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
