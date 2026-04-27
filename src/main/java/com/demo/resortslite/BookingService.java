package com.demo.resortslite;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${gcp.storage.project-id:}")
    private String projectId;

    @Value("${app.payment.endpoint}")
    private String paymentApiEndpoint;

    @Value("${app.auth.credentials-path:/config/credentials}")
    private String credentialsPath;

    // Credentials loaded from Secret Manager or environment variables
    private String dbHost;
    private String dbUser;
    private String dbPass;

    public BookingService() {
        // Load credentials from Secret Manager in GCP environment
        loadCredentialsFromSecretManager();
    }

    private void loadCredentialsFromSecretManager() {
        try {
            // In production, these would be loaded from GCP Secret Manager
            // For now, use environment variables as fallback
            this.dbHost = System.getenv().getOrDefault("DB_HOST", "localhost");
            this.dbUser = System.getenv().getOrDefault("DB_USER", "admin");
            this.dbPass = System.getenv().getOrDefault("DB_PASS", "");
            
            // Attempt to load from Secret Manager if project ID is configured
            if (projectId != null && !projectId.isEmpty()) {
                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                    // Load DB credentials from Secret Manager
                    this.dbHost = getSecretValue(client, projectId, "db-host", this.dbHost);
                    this.dbUser = getSecretValue(client, projectId, "db-user", this.dbUser);
                    this.dbPass = getSecretValue(client, projectId, "db-password", this.dbPass);
                } catch (Exception e) {
                    System.err.println("Warning: Could not load secrets from Secret Manager: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Using default credentials: " + e.getMessage());
        }
    }

    private String getSecretValue(SecretManagerServiceClient client, String projectId, String secretId, String defaultValue) {
        try {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate confirmation code using secure hash
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbHost);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Use parameterized query to prevent SQL injection
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
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    public boolean authenticateFromCredentials(String username, String password) {
        // Load credentials from Secret Manager or external configuration
        // instead of local file system
        try {
            if (projectId != null && !projectId.isEmpty()) {
                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                    String storedPassword = getSecretValue(client, projectId, "user-" + username, null);
                    if (storedPassword != null) {
                        return sha256Hash(password).equals(storedPassword);
                    }
                }
            }
            
            // Fallback: check environment variable or configuration
            String envPassword = System.getenv("USER_" + username.toUpperCase() + "_PASSWORD");
            if (envPassword != null) {
                return sha256Hash(password).equals(envPassword);
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            return false;
        }
    }

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
