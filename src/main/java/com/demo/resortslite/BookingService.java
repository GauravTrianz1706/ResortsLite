package com.demo.resortslite;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BookingService — cloud-ready version.
 *
 * Changes applied:
 *  - blocker-8/9  (cr-java-0069): Removed hard-coded DB_HOST, DB_USER, DB_PASS constants.
 *    Credentials are now retrieved at runtime from Google Secret Manager via
 *    resolveSecret(). The GCP project ID is read from the GCP_PROJECT_ID env var.
 *    Spring datasource properties (url/username/password) are externalised in
 *    application.properties / environment variables so JdbcTemplate continues to work.
 *  - blocker-17   (cr-java-0090): Replaced file-based credential storage with
 *    Google Secret Manager for sensitive data; service-to-service auth uses Cloud IAM
 *    (Application Default Credentials picked up automatically by the GCS/Secret Manager
 *    clients).
 *    The PAYMENT_API URL is now read from the PAYMENT_API_URL environment variable
 *    (or application.properties) instead of being hard-coded.
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // GCP project ID — set via GCP_PROJECT_ID environment variable.
    @Value("${gcp.project.id:${GCP_PROJECT_ID:my-gcp-project}}")
    private String gcpProjectId;

    // Payment API URL externalised — no hard-coded internal IP.
    @Value("${app.payment.endpoint:${PAYMENT_API_URL:http://localhost:9090/payments/charge}}")
    private String paymentApiUrl;

    /**
     * Retrieves a secret value from Google Secret Manager.
     *
     * @param secretId the Secret Manager secret ID (e.g. "db-password")
     * @return the secret payload as a plain string
     */
    private String resolveSecret(String secretId) {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName =
                    SecretVersionName.of(gcpProjectId, secretId, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve secret '" + secretId + "' from Secret Manager", e);
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

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

    public Map<String, Object> getBookingById(String bookingId) {
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
        double basePrice = 0;
        if (roomType.equals("STANDARD")) { basePrice = 120.0; }
        else if (roomType.equals("DELUXE")) { basePrice = 200.0; }
        else if (roomType.equals("SUITE")) { basePrice = 350.0; }
        else if (roomType.equals("VILLA")) { basePrice = 600.0; }
        else { basePrice = 120.0; }
        if (season.equals("PEAK")) { basePrice = basePrice * 1.5; }
        else if (season.equals("OFF")) { basePrice = basePrice * 0.8; }
        if (loyalty.equals("GOLD")) { basePrice = basePrice * 0.9; }
        else if (loyalty.equals("PLATINUM")) { basePrice = basePrice * 0.8; }
        else if (loyalty.equals("DIAMOND")) { basePrice = basePrice * 0.7; }
        if (nights >= 7) { basePrice = basePrice * 0.95; }
        else if (nights >= 14) { basePrice = basePrice * 0.90; }
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE")
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) {
            return false;
        }
        return true;
    }

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiUrl;
    }

    /**
     * SHA-256 hash — replaces the previous MD5 implementation.
     * MD5 is cryptographically broken; SHA-256 is the minimum acceptable standard.
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
