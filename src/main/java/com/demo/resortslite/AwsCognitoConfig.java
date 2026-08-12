package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS Cognito Configuration for cloud-native user authentication.
 * Replaces file-based authentication with AWS Cognito User Pools.
 * 
 * Configuration properties should be set via environment variables:
 * - AWS_REGION: AWS region for Cognito (e.g., us-east-1)
 * - COGNITO_USER_POOL_ID: Cognito User Pool ID
 * - COGNITO_CLIENT_ID: Cognito App Client ID
 * - COGNITO_CLIENT_SECRET: Cognito App Client Secret (stored in AWS Secrets Manager)
 */
@Configuration
public class AwsCognitoConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.cognito.userPoolId:#{environment.COGNITO_USER_POOL_ID}}")
    private String userPoolId;

    @Value("${aws.cognito.clientId:#{environment.COGNITO_CLIENT_ID}}")
    private String clientId;

    /**
     * Creates AWS Cognito Identity Provider client for user authentication operations.
     * Uses DefaultCredentialsProvider which supports:
     * - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * - EC2 instance profile credentials
     * - ECS task role credentials
     * - EKS pod identity
     */
    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Configuration holder for Cognito settings.
     * These values are injected from environment variables or application properties.
     */
    @Bean
    public CognitoConfig cognitoConfig() {
        return new CognitoConfig(userPoolId, clientId);
    }

    /**
     * Immutable configuration class for Cognito settings.
     */
    public static class CognitoConfig {
        private final String userPoolId;
        private final String clientId;

        public CognitoConfig(String userPoolId, String clientId) {
            this.userPoolId = userPoolId;
            this.clientId = clientId;
        }

        public String getUserPoolId() {
            return userPoolId;
        }

        public String getClientId() {
            return clientId;
        }
    }
}
