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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${aws.secrets.db.secret.name}")
    private String dbSecretName;

    @Value("${aws.secrets.auth.secret.name}")
    private String authSecretName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${app.payment.endpoint}")
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
        this.dbCredentials = loadSecretAsMap(dbSecretName);
        this.authCredentials = loadSecretAsMap(authSecretName);
    }

    /**
     * Loads secret from AWS Secrets Manager and parses it as JSON map.
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
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(secretString);
            jsonNode.fields().forEachRemaining(entry -> 
                secretMap.put(entry.getKey(), entry.getValue().asText())
            );
        } catch (Exception e) {
            // Log error and use fallback for development
            System.err.println("Failed to load secret " + secretName + ": " + e.getMessage());
            // In production, this should fail fast
        }
        return secretMap;
    }

    /**
     * Creates a booking with proper parameterized queries to prevent SQL injection.
     * Database credentials are retrieved from AWS Secrets Manager.
     */
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
        
        // Database host is retrieved from Secrets Manager, not hard-coded
        if (dbCredentials != null && dbCredentials.containsKey("host")) {
            booking.put("dbHost", dbCredentials.get("host"));
        }
        
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
     * Checks if room type is available.
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
     * Secure hash function using SHA-256 instead of MD5.
     * Authentication credentials are stored in AWS Secrets Manager.
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
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
     * This replaces file-based authentication with cloud-native secret management.
     */
    public boolean authenticateUser(String username, String password) {
        if (authCredentials == null || authCredentials.isEmpty()) {
            // Fallback for development - in production this should fail
            return false;
        }
        
        String storedPassword = authCredentials.get(username);
        if (storedPassword == null) {
            return false;
        }
        
        // In production, use proper password hashing (bcrypt, argon2, etc.)
        String hashedPassword = sha256Hash(password);
        return storedPassword.equals(hashedPassword);
    }
}
