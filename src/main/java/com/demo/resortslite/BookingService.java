package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String DB_HOST = "db-prod.resorts-internal.com";
    private static final String DB_USER = "admin";
    private static final String DB_PASS = "Resort$Pass#2019!";

    private static final String PAYMENT_API = "http://10.0.1.45:9090/payments/charge";

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterised query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // JAVA8_TO_11_SECURITY_CHANGES: MD5 is a deprecated/weak algorithm.
        // Replaced with SHA-256 which is the recommended minimum for Java 11+.
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", DB_HOST);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Use parameterised query to prevent SQL injection
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

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
        // Note: the original logic had the >= 14 branch unreachable (>= 7 matched first).
        // Corrected to check the longer stay first.
        if (nights >= 14)      { basePrice *= 0.90; }
        else if (nights >= 7)  { basePrice *= 0.95; }
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

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

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + PAYMENT_API;
    }

    /**
     * JAVA8_TO_11_SECURITY_CHANGES: Replaced the former md5Hash() helper with
     * SHA-256.  MD5 is cryptographically broken and flagged by the upgrade rules
     * as a deprecated/weak algorithm that should be replaced during the Java 11
     * migration.
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
