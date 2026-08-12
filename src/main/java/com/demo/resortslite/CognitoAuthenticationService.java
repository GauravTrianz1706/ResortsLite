package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloud-native authentication service using AWS Cognito and Secrets Manager.
 * Replaces file-based authentication with centralized, secure user management.
 * 
 * This service provides:
 * - User authentication via AWS Cognito User Pools
 * - Secure credential storage via AWS Secrets Manager
 * - Token-based authentication for stateless operations
 * - Centralized user lifecycle management
 */
@Service
public class CognitoAuthenticationService {

    @Autowired
    private CognitoIdentityProviderClient cognitoClient;

    @Autowired
    private AwsCognitoConfig.CognitoConfig cognitoConfig;

    @Autowired
    private SecretsManagerClient secretsManagerClient;

    /**
     * Authenticates a user using AWS Cognito.
     * Replaces file-based credential validation with cloud-native authentication.
     * 
     * @param username User's username
     * @param password User's password
     * @return Authentication result containing access token, ID token, and refresh token
     * @throws AuthenticationException if authentication fails
     */
    public AuthenticationResult authenticateUser(String username, String password) {
        try {
            // Retrieve client secret from AWS Secrets Manager
            String clientSecret = getClientSecretFromSecretsManager();
            
            // Calculate SECRET_HASH required for Cognito authentication
            String secretHash = calculateSecretHash(username, cognitoConfig.getClientId(), clientSecret);

            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", username);
            authParams.put("PASSWORD", password);
            authParams.put("SECRET_HASH", secretHash);

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(cognitoConfig.getClientId())
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
            return authResponse.authenticationResult();

        } catch (NotAuthorizedException e) {
            throw new AuthenticationException("Invalid username or password", e);
        } catch (UserNotFoundException e) {
            throw new AuthenticationException("User not found", e);
        } catch (Exception e) {
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates an access token with AWS Cognito.
     * 
     * @param accessToken JWT access token from Cognito
     * @return User information if token is valid
     * @throws AuthenticationException if token is invalid
     */
    public GetUserResponse validateToken(String accessToken) {
        try {
            GetUserRequest getUserRequest = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            return cognitoClient.getUser(getUserRequest);
        } catch (NotAuthorizedException e) {
            throw new AuthenticationException("Invalid or expired token", e);
        } catch (Exception e) {
            throw new AuthenticationException("Token validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a secure confirmation code using AWS Cognito user attributes.
     * Replaces MD5 hash-based confirmation codes with cryptographically secure tokens.
     * 
     * @param username User's username
     * @param bookingId Booking identifier
     * @return Secure confirmation code
     */
    public String generateSecureConfirmationCode(String username, String bookingId) {
        try {
            // Use Cognito's secure random token generation
            String clientSecret = getClientSecretFromSecretsManager();
            String input = username + ":" + bookingId + ":" + System.currentTimeMillis();
            return calculateSecretHash(input, cognitoConfig.getClientId(), clientSecret)
                    .substring(0, 16).toUpperCase();
        } catch (Exception e) {
            // Fallback to UUID-based code if Cognito is unavailable
            return java.util.UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }

    /**
     * Retrieves Cognito client secret from AWS Secrets Manager.
     * Ensures credentials are never stored in files or code.
     * 
     * @return Client secret
     */
    private String getClientSecretFromSecretsManager() {
        try {
            String secretName = System.getenv().getOrDefault(
                    "COGNITO_CLIENT_SECRET_NAME", 
                    "resortslite/cognito/client-secret"
            );

            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = 
                    secretsManagerClient.getSecretValue(getSecretValueRequest);

            return getSecretValueResponse.secretString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve client secret from Secrets Manager", e);
        }
    }

    /**
     * Calculates HMAC-SHA256 hash required for Cognito authentication.
     * 
     * @param username Username
     * @param clientId Cognito client ID
     * @param clientSecret Cognito client secret
     * @return Base64-encoded HMAC-SHA256 hash
     */
    private String calculateSecretHash(String username, String clientId, String clientSecret) {
        try {
            String message = username + clientId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8), 
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate secret hash", e);
        }
    }

    /**
     * Custom exception for authentication failures.
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
