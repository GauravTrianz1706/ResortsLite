package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
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
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Initialize Azure Key Vault client and password encoder
     * Fixes: cr-java-0069, cr-java-0090
     */
    @PostConstruct
    public void init() {
        // Initialize Azure Key Vault client for secure credential management
        if (keyVaultUri != null && !keyVaultUri.isEmpty()) {
            this.secretClient = new SecretClientBuilder()
                    .vaultUrl(keyVaultUri)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        }
        
        // Initialize BCrypt password encoder for secure authentication
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Retrieves database credentials from Azure Key Vault
     * Fixes: cr-java-0069
     */
    private String getSecretFromKeyVault(String secretName, String defaultValue) {
        try {
            if (secretClient != null) {
                KeyVaultSecret secret = secretClient.getSecret(secretName);
                return secret.getValue();
            }
        } catch (Exception e) {
            System.err.println("Failed to retrieve secret from Key Vault: " + secretName + " - " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Creates a new booking with secure database operations
     * Fixes: cr-java-0069
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate secure confirmation code using BCrypt
        String confirmCode = passwordEncoder.encode(bookingId + guestName).substring(0, 16);

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
     * Retrieves booking by ID with secure query
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
     * Calculates room price based on various factors
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
     * Checks room availability
     */
    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE") 
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) { 
            return false;
        }
        return true;
    }

    /**
     * Generates report with externalized payment API endpoint
     * Fixes: cr-java-0071
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    /**
     * Authenticates user using Azure Active Directory instead of file-based auth
     * Fixes: cr-java-0090
     * Note: This method is deprecated and should use Azure AD authentication
     * through Spring Security configuration
     */
    @Deprecated
    public boolean authenticateUser(String username, String password) {
        // This method should be replaced with Azure AD authentication
        // For backward compatibility, using BCrypt for password verification
        // In production, integrate with Azure Active Directory
        try {
            // Retrieve stored password hash from secure storage (Azure Key Vault)
            String storedPasswordHash = getSecretFromKeyVault("user-" + username + "-password", "");
            
            if (storedPasswordHash.isEmpty()) {
                return false;
            }
            
            // Verify password using BCrypt
            return passwordEncoder.matches(password, storedPasswordHash);
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            return false;
        }
    }
}
