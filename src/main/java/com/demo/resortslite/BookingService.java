package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BookingService — cloud-ready implementation.
 *
 * <p>cr-java-0090: File-based authentication replaced with AWS Secrets Manager
 * and Amazon Cognito.</p>
 *
 * <ul>
 *   <li>Authentication credentials and user data are no longer stored in local
 *       files or generated via local hashing algorithms (MD5).</li>
 *   <li>Booking confirmation tokens are now issued by Amazon Cognito, providing
 *       centralized, encrypted, and auditable identity management.</li>
 *   <li>Any secrets required at runtime (e.g. Cognito client secrets) are
 *       retrieved from AWS Secrets Manager — never hard-coded.</li>
 * </ul>
 *
 * <p>Required environment variables (injected via ECS task definition / EKS
 * secret / EC2 instance profile — never hard-coded):</p>
 * <ul>
 *   <li>{@code COGNITO_USER_POOL_ID}  – Amazon Cognito User Pool ID
 *       (default: {@code us-east-1_PLACEHOLDER})</li>
 *   <li>{@code AWS_REGION}            – AWS region (default: {@code us-east-1})</li>
 *   <li>{@code DB_HOST}               – Logical DB hostname (default: {@code localhost})</li>
 * </ul>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // cr-java-0069: DB_USER and DB_PASS hard-coded credentials removed.
    // Database credentials are now retrieved from AWS Secrets Manager via
    // SecretsManagerConfig and injected into the DataSource by Spring Boot.
    // The DB_HOST value is externalised to the environment variable DB_HOST.
    @Value("${app.db.host:localhost}")
    private String dbHost;

    // cr-java-0090: Cognito User Pool ID externalised to environment variable.
    // Set COGNITO_USER_POOL_ID in ECS task definition / EKS secret / EC2 instance profile.
    @Value("${COGNITO_USER_POOL_ID:us-east-1_PLACEHOLDER}")
    private String cognitoUserPoolId;

    // cr-java-0090: AWS region for Cognito and Secrets Manager clients.
    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;

    @Value("${app.payment.endpoint:http://payment-svc.internal:9090/charge}")
    private String paymentApi;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES ('"
                + bookingId + "', '" + guestName + "', '" + roomType
                + "', '" + checkIn + "', '" + checkOut + "')";
        jdbcTemplate.execute(sql);

        // cr-java-0090: Confirmation code is now generated via Amazon Cognito
        // instead of a local MD5 hash.  Cognito issues a sub (subject) UUID for
        // each user entry, which serves as the booking confirmation token.
        // This replaces the previous file-based, locally-computed MD5 approach.
        String confirmCode = generateCognitoConfirmationToken(bookingId, guestName);

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

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    // -------------------------------------------------------------------------
    // cr-java-0090: Amazon Cognito — cloud-native authentication token generation
    // -------------------------------------------------------------------------

    /**
     * Generates a booking confirmation token using Amazon Cognito.
     *
     * <p>A transient Cognito user entry is created for the booking, and the
     * Cognito-assigned {@code sub} (subject UUID) is returned as the
     * confirmation token.  This replaces the previous local MD5 hash approach,
     * providing a centrally managed, auditable, and cryptographically secure
     * token without storing any credentials in local files.</p>
     *
     * <p>If Cognito is unavailable (e.g. local development without AWS
     * credentials), the method falls back to a random UUID so that the
     * application continues to function.</p>
     *
     * @param bookingId  the generated booking identifier
     * @param guestName  the guest's name (used as the Cognito username)
     * @return a Cognito sub UUID, or a random UUID fallback
     */
    private String generateCognitoConfirmationToken(String bookingId, String guestName) {
        // Derive a deterministic, Cognito-safe username from the booking ID.
        String cognitoUsername = "booking-" + bookingId.toLowerCase();

        try (CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(cognitoUserPoolId)
                    .username(cognitoUsername)
                    .userAttributes(
                            AttributeType.builder().name("email").value(cognitoUsername + "@resortslite.internal").build(),
                            AttributeType.builder().name("custom:bookingId").value(bookingId).build(),
                            AttributeType.builder().name("custom:guestName").value(guestName).build()
                    )
                    .messageAction("SUPPRESS") // suppress welcome email for booking tokens
                    .build();

            AdminCreateUserResponse response = cognitoClient.adminCreateUser(createUserRequest);

            // Extract the Cognito sub (subject UUID) as the confirmation token.
            String sub = response.user().attributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(UUID.randomUUID().toString());

            log.info("Cognito confirmation token generated for booking '{}' (username: '{}')",
                    bookingId, cognitoUsername);
            return sub;

        } catch (CognitoIdentityProviderException ex) {
            // Cognito user pool not configured or unreachable — fall back to UUID.
            log.warn("Could not generate Cognito token for booking '{}': {}. "
                    + "Falling back to random UUID — suitable for local/dev only.",
                    bookingId, ex.awsErrorDetails().errorMessage());
            return UUID.randomUUID().toString();
        } catch (Exception ex) {
            // Generic fallback for local environments without AWS credentials.
            log.warn("Cognito unavailable for booking '{}': {}. "
                    + "Falling back to random UUID — suitable for local/dev only.",
                    bookingId, ex.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    // -------------------------------------------------------------------------
    // cr-java-0090: AWS Secrets Manager — retrieve auth-related secrets at runtime
    // -------------------------------------------------------------------------

    /**
     * Retrieves a named secret string from AWS Secrets Manager.
     *
     * <p>This utility method is provided for any component within BookingService
     * that needs to access authentication-related secrets (e.g. API keys,
     * third-party auth tokens) at runtime.  Secrets are never stored in local
     * files or hard-coded in source code.</p>
     *
     * @param secretName the name or ARN of the Secrets Manager secret
     * @return the secret string value, or {@code null} if unavailable
     */
    protected String retrieveSecret(String secretName) {
        try (SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            log.info("Successfully retrieved secret '{}' from AWS Secrets Manager", secretName);
            return response.secretString();

        } catch (Exception ex) {
            log.warn("Could not retrieve secret '{}' from AWS Secrets Manager: {}",
                    secretName, ex.getMessage());
            return null;
        }
    }
}
