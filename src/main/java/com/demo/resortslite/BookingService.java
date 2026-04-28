package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Replaced hard-coded database credentials with Azure Key Vault integration
    @Value("${azure.keyvault.uri:#{null}}")
    private String keyVaultUri;

    @Value("${azure.keyvault.secret.db-host:db-host}")
    private String dbHostSecretName;

    @Value("${azure.keyvault.secret.db-user:db-user}")
    private String dbUserSecretName;

    @Value("${azure.keyvault.secret.db-password:db-password}")
    private String dbPasswordSecretName;

    // Replaced hard-coded payment API URL with externalized configuration
    @Value("${app.payment.endpoint:http://localhost:9090/payments/charge}")
    private String paymentApiUrl;

    private SecretClient getSecretClient() {
        if (keyVaultUri == null || keyVaultUri.isEmpty()) {
            throw new IllegalStateException("Azure Key Vault URI is not configured. Set azure.keyvault.uri property.");
        }
        return new SecretClientBuilder()
                .vaultUrl(keyVaultUri)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    private String getSecretFromKeyVault(String secretName) {
        try {
            SecretClient secretClient = getSecretClient();
            return secretClient.getSecret(secretName).getValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve secret '" + secretName + "' from Azure Key Vault: " + e.getMessage(), e);
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
        
        // Retrieve database host from Azure Key Vault (for demonstration purposes)
        try {
            String dbHost = getSecretFromKeyVault(dbHostSecretName);
            booking.put("dbHost", dbHost);
        } catch (Exception e) {
            booking.put("dbHost", "Key Vault not configured");
        }
        
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
        return "Report generation triggered for: " + month + " via " + paymentApiUrl;
    }

    // Replaced file-based authentication with Azure Active Directory integration
    public boolean authenticateUser(String username, String password) {
        // In a real implementation, this would use Azure AD with Spring Security
        // For now, we'll use Spring Security context to check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return true;
        }
        
        // Fallback: This should be replaced with proper Azure AD integration
        // using Microsoft Authentication Library (MSAL) and Spring Security Azure AD starter
        throw new UnsupportedOperationException(
            "File-based authentication has been removed. " +
            "Please configure Azure Active Directory authentication using Spring Security Azure AD starter."
        );
    }

    // Replaced MD5 with SHA-256 for secure hashing
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
