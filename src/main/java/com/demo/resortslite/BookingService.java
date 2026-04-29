package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Cloud-native: Replace hard-coded credentials with AWS Secrets Manager
    @Value("${aws.secrets.database.name:resort-db-credentials}")
    private String databaseSecretName;

    @Value("${aws.secrets.payment.name:resort-payment-credentials}")
    private String paymentSecretName;

    private String dbHost;
    private String dbUser;
    @Autowired
    
    public BookingService(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    @PostConstruct
    public void init() {
        // Load database credentials from AWS Secrets Manager at startup
        loadDatabaseCredentials();
        loadPaymentApiConfiguration();
    }

    private void loadDatabaseCredentials() {
        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(databaseSecretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            String secret = getSecretValueResponse.secretString();

            // Parse JSON secret
            JsonNode secretJson = objectMapper.readTree(secret);
            this.dbHost = secretJson.get("host").asText();
            this.dbUser = secretJson.get("username").asText();
            this.dbPass = secretJson.get("password").asText();

        } catch (Exception e) {
            // Fallback to environment variables if Secrets Manager is not available
            this.dbHost = System.getenv().getOrDefault("DB_HOST", "localhost");
            this.dbUser = System.getenv().getOrDefault("DB_USER", "sa");
            this.dbPass = System.getenv().getOrDefault("DB_PASS", "");
        }
    }

    private void loadPaymentApiConfiguration() {
        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(paymentSecretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            String secret = getSecretValueResponse.secretString();

            // Parse JSON secret
            JsonNode secretJson = objectMapper.readTree(secret);
            this.paymentApiUrl = secretJson.get("apiUrl").asText();

        } catch (Exception e) {
            // Fallback to environment variable
            this.paymentApiUrl = System.getenv().getOrDefault("PAYMENT_API_URL", "http://localhost:9090/payments/charge");
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Use SHA-256 instead of MD5 for better security
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
        return "Report generation triggered for: " + month + " via " + paymentApiUrl;
    }

    // Replace MD5 with SHA-256 for better security
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
