package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    
    // Cloud-native: Retrieve credentials from AWS Secrets Manager
    @Value("${aws.secrets.database:${DB_SECRET_NAME:resortslite/database}}")
    private String databaseSecretName;
    
    @Value("${aws.secrets.auth:${AUTH_SECRET_NAME:resortslite/auth}}")
    private String authSecretName;

    public BookingService(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieve database credentials from AWS Secrets Manager
     */
    private Map<String, String> getDatabaseCredentials() {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(databaseSecretName)
                    .build();
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();
            
            JsonNode secretJson = objectMapper.readTree(secretString);
            Map<String, String> credentials = new HashMap<>();
            credentials.put("host", secretJson.get("host").asText());
            credentials.put("username", secretJson.get("username").asText());
            credentials.put("password", secretJson.get("password").asText());
            return credentials;
        } catch (Exception e) {
            // Fallback to environment variables for local development
            Map<String, String> credentials = new HashMap<>();
            credentials.put("host", System.getenv("DB_HOST"));
            credentials.put("username", System.getenv("DB_USER"));
            credentials.put("password", System.getenv("DB_PASS"));
            return credentials;
        }
    }
    
    /**
     * Retrieve authentication credentials from AWS Secrets Manager
     */
    private Map<String, String> getAuthCredentials() {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(authSecretName)
                    .build();
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();
            
            JsonNode secretJson = objectMapper.readTree(secretString);
            Map<String, String> credentials = new HashMap<>();
            credentials.put("apiKey", secretJson.get("apiKey").asText());
            credentials.put("apiSecret", secretJson.get("apiSecret").asText());
            return credentials;
        } catch (Exception e) {
            // Fallback to environment variables
            Map<String, String> credentials = new HashMap<>();
            credentials.put("apiKey", System.getenv("API_KEY"));
            credentials.put("apiSecret", System.getenv("API_SECRET"));
            return credentials;
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized queries to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Generate confirmation code
        String confirmCode = md5Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        
        // Retrieve DB host from Secrets Manager for response (if needed)
        Map<String, String> dbCreds = getDatabaseCredentials();
        booking.put("dbHost", dbCreds.get("host"));
        
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Use parameterized queries to prevent SQL injection
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
        // Retrieve payment API endpoint from environment variable
        String paymentApi = System.getenv("PAYMENT_API_ENDPOINT");
        if (paymentApi == null) {
            paymentApi = "http://payment-service:9090/payments/charge";
        }
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
