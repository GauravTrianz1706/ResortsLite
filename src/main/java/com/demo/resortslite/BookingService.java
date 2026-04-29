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

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.secrets.db-credentials:resort/db/credentials}")
    private String dbCredentialsSecretName;

    private SecretsManagerClient secretsManagerClient;
    private Map<String, String> dbCredentials;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        
        // Load database credentials from AWS Secrets Manager
        this.dbCredentials = loadDatabaseCredentials();
    }

    /**
     * Loads database credentials from AWS Secrets Manager
     * Replaces hard-coded credentials with secure, rotatable secrets
     */
    private Map<String, String> loadDatabaseCredentials() {
        Map<String, String> credentials = new HashMap<>();
        
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(dbCredentialsSecretName)
                    .build();
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();
            
            // Parse JSON secret
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secretString);
            
            credentials.put("host", secretJson.get("host").asText());
            credentials.put("username", secretJson.get("username").asText());
            credentials.put("password", secretJson.get("password").asText());
            
        } catch (Exception e) {
            // Fallback to environment variables for local development
            credentials.put("host", System.getenv().getOrDefault("DB_HOST", "localhost"));
            credentials.put("username", System.getenv().getOrDefault("DB_USERNAME", "sa"));
            credentials.put("password", System.getenv().getOrDefault("DB_PASSWORD", ""));
        }
        
        return credentials;
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate confirmation code using secure hash
        String confirmCode = generateSecureHash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbCredentials.get("host"));
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

    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE") 
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) { 
            return false;
        }
        return true;
    }

    public String generateReport(String month) {
        String paymentEndpoint = System.getenv().getOrDefault("PAYMENT_ENDPOINT", "http://payment-svc:9090/payments/charge");
        return "Report generation triggered for: " + month + " via " + paymentEndpoint;
    }

    /**
     * Generates secure hash using SHA-256 instead of MD5
     * Replaces weak MD5 hash with secure alternative
     */
    private String generateSecureHash(String input) {
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
}
