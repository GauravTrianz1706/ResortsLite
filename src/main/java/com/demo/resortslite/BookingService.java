package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.secrets.db-credentials:resorts/db/credentials}")
    private String dbCredentialsSecretName;

    @Value("${aws.secrets.auth-credentials:resorts/auth/credentials}")
    private String authCredentialsSecretName;

    @Value("${app.payment.endpoint:http://payment-svc:9090/charge}")
    private String paymentApiEndpoint;

    private SecretsManagerClient secretsManagerClient;
    private Map<String, String> dbCredentials;
    private Map<String, String> authCredentials;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        
        // Load credentials from AWS Secrets Manager
        this.dbCredentials = loadSecretsFromSecretsManager(dbCredentialsSecretName);
        this.authCredentials = loadSecretsFromSecretsManager(authCredentialsSecretName);
    }

    /**
     * Loads secrets from AWS Secrets Manager.
     * Fixes: cr-java-0069 (Hard-coded database credentials)
     * Fixes: cr-java-0090 (File-based authentication)
     */
    private Map<String, String> loadSecretsFromSecretsManager(String secretName) {
        Map<String, String> secrets = new HashMap<>();
        
        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();

            // Parse JSON secret
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secretString);
            
            secretJson.fields().forEachRemaining(entry -> {
                secrets.put(entry.getKey(), entry.getValue().asText());
            });

        } catch (Exception e) {
            // Fallback to environment variables if Secrets Manager is not available
            System.err.println("Failed to load secrets from AWS Secrets Manager: " + e.getMessage());
            secrets.put("host", System.getenv("DB_HOST"));
            secrets.put("username", System.getenv("DB_USERNAME"));
            secrets.put("password", System.getenv("DB_PASSWORD"));
        }
        
        return secrets;
    }

    /**
     * Creates a booking with proper parameterized queries to prevent SQL injection.
     * Uses credentials from AWS Secrets Manager instead of hard-coded values.
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
        
        // Return database host from Secrets Manager (not hard-coded)
        booking.put("dbHost", dbCredentials.getOrDefault("host", "unknown"));
        
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
        
        if (roomType.equals("STANDARD")) { 
            basePrice = 120.0; 
        } else if (roomType.equals("DELUXE")) { 
            basePrice = 200.0; 
        } else if (roomType.equals("SUITE")) { 
            basePrice = 350.0; 
        } else if (roomType.equals("VILLA")) { 
            basePrice = 600.0; 
        } else { 
            basePrice = 120.0; 
        }
        
        if (season.equals("PEAK")) { 
            basePrice = basePrice * 1.5; 
        } else if (season.equals("OFF")) { 
            basePrice = basePrice * 0.8; 
        }
        
        if (loyalty.equals("GOLD")) { 
            basePrice = basePrice * 0.9; 
        } else if (loyalty.equals("PLATINUM")) { 
            basePrice = basePrice * 0.8; 
        } else if (loyalty.equals("DIAMOND")) { 
            basePrice = basePrice * 0.7; 
        }
        
        if (nights >= 7) { 
            basePrice = basePrice * 0.95; 
        } else if (nights >= 14) { 
            basePrice = basePrice * 0.90; 
        }
        
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
     * Generates report using externalized payment API endpoint.
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    /**
     * Secure SHA-256 hashing instead of weak MD5.
     * Replaces file-based authentication with secure hashing.
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

    /**
     * Authenticates user using credentials from AWS Secrets Manager.
     * Fixes: cr-java-0090 (File-based authentication)
     */
    public boolean authenticateUser(String username, String password) {
        try {
            String storedUsername = authCredentials.getOrDefault("username", "");
            String storedPasswordHash = authCredentials.getOrDefault("passwordHash", "");
            
            String providedPasswordHash = sha256Hash(password);
            
            return username.equals(storedUsername) && providedPasswordHash.equals(storedPasswordHash);
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            return false;
        }
    }
}
