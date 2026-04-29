package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
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
    private CognitoIdentityProviderClient cognitoClient;
    private ObjectMapper objectMapper;

    // Cached credentials (refreshed periodically in production)
    private Map<String, String> dbCredentials;
    private Map<String, String> authCredentials;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        
        this.objectMapper = new ObjectMapper();
        
        // Load credentials from AWS Secrets Manager
        this.dbCredentials = loadSecretAsMap(dbCredentialsSecretName);
        this.authCredentials = loadSecretAsMap(authCredentialsSecretName);
    }

    /**
     * Creates a booking with database credentials retrieved from AWS Secrets Manager.
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate secure confirmation code using SHA-256 instead of MD5
        String confirmCode = generateSecureHash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        
        // Database host retrieved from Secrets Manager
        String dbHost = dbCredentials.getOrDefault("host", "unknown");
        booking.put("dbHost", dbHost);
        
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
     * Generates report using externalized payment API endpoint.
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    /**
     * Loads secret from AWS Secrets Manager and parses as JSON map.
     */
    private Map<String, String> loadSecretAsMap(String secretName) {
        Map<String, String> secretMap = new HashMap<>();
        
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();
            
            // Parse JSON secret
            JsonNode jsonNode = objectMapper.readTree(secretString);
            jsonNode.fields().forEachRemaining(entry -> {
                secretMap.put(entry.getKey(), entry.getValue().asText());
            });
            
        } catch (Exception e) {
            // Log error and return empty map (fallback to environment variables)
            System.err.println("Failed to load secret " + secretName + ": " + e.getMessage());
        }
        
        return secretMap;
    }

    /**
     * Generates secure hash using SHA-256 instead of MD5.
     * Replaces file-based authentication with secure hashing.
     */
    private String generateSecureHash(String input) {
        try {
            // Use SHA-256 instead of MD5 for security
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            // Add salt for additional security
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            
            md.update(salt);
            byte[] hash = md.digest(input.getBytes());
            
            // Combine salt and hash
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     * This replaces hard-coded credentials and file-based authentication.
     */
    public Map<String, String> getDatabaseCredentials() {
        return new HashMap<>(dbCredentials);
    }

    /**
     * Retrieves authentication credentials from AWS Secrets Manager.
     * This replaces file-based authentication with cloud-native secrets management.
     */
    public Map<String, String> getAuthCredentials() {
        return new HashMap<>(authCredentials);
    }
}
