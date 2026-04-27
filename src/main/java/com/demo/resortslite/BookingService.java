package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${azure.keyvault.uri:}")
    private String keyVaultUri;

    @Value("${app.payment.endpoint:http://payment-svc:9090/charge}")
    private String paymentApiEndpoint;

    private SecretClient secretClient;
    private String dbHost;
    private String dbUser;
    private String dbPass;

    @PostConstruct
    public void init() {
        // Initialize Azure Key Vault client using DefaultAzureCredential
        // This supports managed identity in Azure environments
        if (keyVaultUri != null && !keyVaultUri.isEmpty()) {
            try {
                this.secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultUri)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();

                // Retrieve database credentials from Azure Key Vault
                KeyVaultSecret dbHostSecret = secretClient.getSecret("db-host");
                KeyVaultSecret dbUserSecret = secretClient.getSecret("db-user");
                KeyVaultSecret dbPassSecret = secretClient.getSecret("db-password");

                this.dbHost = dbHostSecret.getValue();
                this.dbUser = dbUserSecret.getValue();
                this.dbPass = dbPassSecret.getValue();
            } catch (Exception e) {
                // Fallback for local development without Azure Key Vault
                System.err.println("Azure Key Vault not configured, using default values: " + e.getMessage());
                this.dbHost = "localhost";
                this.dbUser = "sa";
                this.dbPass = "";
            }
        } else {
            // Fallback for local development
            this.dbHost = "localhost";
            this.dbUser = "sa";
            this.dbPass = "";
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate confirmation code using secure hash (SHA-256 instead of MD5)
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

    // Replace MD5 with SHA-256 for secure hashing
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

    // Azure AD authentication validation method
    public boolean validateUserAuthentication(String userId) {
        // This method would integrate with Azure Active Directory
        // For now, it's a placeholder that can be extended with MSAL integration
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        // In production, this would validate against Azure AD using MSAL
        return true;
    }
}
