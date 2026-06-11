package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import java.util.UUID;

import javax.annotation.PostConstruct;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SecretsManagerClient secretsManagerClient;
    private final SsmClient ssmClient;

    // Replaced hard-coded DB credentials with AWS Secrets Manager references (cr-java-0069)
    @Value("${cloud.aws.secrets.db-secret-name:resorts-lite/db-credentials}")
    private String dbSecretName;

    // Replaced hard-coded payment API URL with AWS SSM Parameter Store reference (cr-java-0071)
    @Value("${cloud.aws.ssm.payment-api-param:/resorts-lite/payment-api-url}")
    private String paymentApiParam;

    // Resolved payment API URL loaded at startup from SSM Parameter Store
    private String paymentApiUrl;

    public BookingService(SecretsManagerClient secretsManagerClient, SsmClient ssmClient) {
        this.secretsManagerClient = secretsManagerClient;
        this.ssmClient = ssmClient;
    }

    /**
     * Loads the payment API URL from AWS SSM Parameter Store at startup,
     * replacing the hard-coded environment-specific URL (cr-java-0071).
     */
    @PostConstruct
    public void loadConfiguration() {
        try {
            GetParameterResponse paramResponse = ssmClient.getParameter(
                    GetParameterRequest.builder()
                            .name(paymentApiParam)
                            .withDecryption(true)
                            .build());
            this.paymentApiUrl = paramResponse.parameter().value();
        } catch (Exception e) {
            // Fall back to environment variable if SSM is unavailable (e.g., local dev)
            this.paymentApiUrl = System.getenv().getOrDefault("PAYMENT_API_URL",
                    "http://localhost:9090/payments/charge");
        }
    }

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     * Replaces hard-coded DB_HOST, DB_USER, DB_PASS constants (cr-java-0069).
     */
    private Map<String, String> getDbCredentials() {
        try {
            GetSecretValueResponse secretResponse = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder()
                            .secretId(dbSecretName)
                            .build());
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, String> credentials = mapper.readValue(
                    secretResponse.secretString(), Map.class);
            return credentials;
        } catch (Exception e) {
            // Return empty map; datasource is configured via Spring Boot properties
            return new HashMap<>();
        }
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES ('"
                + bookingId + "', '" + guestName + "', '" + roomType
                + "', '" + checkIn + "', '" + checkOut + "')";
        jdbcTemplate.execute(sql);

        String confirmCode = md5Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        // Removed hard-coded DB_HOST from response; credentials now managed by Secrets Manager
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        String sql = "SELECT * FROM bookings WHERE id = '" + bookingId + "'";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql);
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

    /**
     * Replaced file-based authentication credential storage with AWS Secrets Manager (cr-java-0090).
     * Credentials are now retrieved securely from Secrets Manager rather than local files.
     */
    public boolean validateUserCredentials(String username, String providedPassword) {
        try {
            Map<String, String> credentials = getDbCredentials();
            String storedPassword = credentials.get("app_user_password");
            if (storedPassword == null) {
                return false;
            }
            // Compare using constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    storedPassword.getBytes(),
                    providedPassword.getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiUrl;
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
