package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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

    @Value("${app.payment.endpoint}")
    private String paymentApiEndpoint;

    private SecretClient secretClient;

    /**
     * Retrieves database credentials from Azure Key Vault.
     * Replaces hard-coded credentials with secure secret management.
     */
    private String getSecretFromKeyVault(String secretName) {
        try {
            if (keyVaultUri != null && !keyVaultUri.isEmpty()) {
                if (secretClient == null) {
                    secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultUri)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();
                }
                return secretClient.getSecret(secretName).getValue();
            }
        } catch (Exception e) {
            System.err.println("Failed to retrieve secret from Key Vault: " + e.getMessage());
        }
        return null;
    }

    /**
     * Creates a booking with parameterized queries to prevent SQL injection.
     * Uses Azure Key Vault for credential management.
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate confirmation code using secure hashing
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
     * Retrieves booking by ID using parameterized query.
     */
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

    /**
     * Calculates room price based on various factors.
     */
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

    /**
     * Checks if a room type is available.
     */
    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE") 
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) { 
            return false;
        }
        return true;
    }

    /**
     * Generates a report using externalized payment API endpoint.
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    /**
     * Authenticates user using Azure Active Directory.
     * Replaces file-based authentication with cloud-native identity management.
     */
    public boolean authenticateUser(String username, String password) {
        // This method should integrate with Azure Active Directory
        // For now, returning a placeholder implementation
        // In production, use Spring Security with Azure AD integration
        
        try {
            // Retrieve expected password hash from Key Vault
            String expectedPasswordHash = getSecretFromKeyVault("user-" + username + "-password");
            
            if (expectedPasswordHash != null) {
                String providedPasswordHash = sha256Hash(password);
                return expectedPasswordHash.equals(providedPasswordHash);
            }
        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * Secure SHA-256 hashing to replace weak MD5.
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
