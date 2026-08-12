package com.demo.resortslite;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

/**
 * AWS Secrets Manager configuration for retrieving database credentials securely.
 * This replaces hard-coded credentials with cloud-native secret management.
 */
@Configuration
public class AwsSecretsManagerConfig {

    @Value("${aws.secretsmanager.secret.name:resorts-lite/db-credentials}")
    private String secretName;

    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    @Bean
    public DatabaseCredentials databaseCredentials(SecretsManagerClient client) {
        return retrieveSecretFromSecretsManager(client);
    }

    private DatabaseCredentials retrieveSecretFromSecretsManager(SecretsManagerClient client) {
                .build();

        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
            String secret = getSecretValueResponse.secretString();

            // Parse JSON secret
            Gson gson = new Gson();
            Map<String, String> secretMap = gson.fromJson(secret, Map.class);

            DatabaseCredentials credentials = new DatabaseCredentials();
            credentials.setHost(secretMap.getOrDefault("host", "localhost"));
            credentials.setUsername(secretMap.getOrDefault("username", "sa"));
            credentials.setPassword(secretMap.getOrDefault("password", ""));
            credentials.setPort(secretMap.getOrDefault("port", "3306"));
            credentials.setDatabase(secretMap.getOrDefault("database", "resortdb"));

            return credentials;
        } catch (Exception e) {
            // Fallback to default values for local development
            System.err.println("Warning: Unable to retrieve secrets from AWS Secrets Manager: " + e.getMessage());
            System.err.println("Using default credentials for local development");
            
            // Don't close the client here as it's a Spring-managed bean
            return fallbackCredentials;
        } finally {
            client.close();
        }
    }

    /**
     * POJO to hold database credentials retrieved from AWS Secrets Manager
     */
    public static class DatabaseCredentials {
        private String host;
        private String username;
        private String password;
        private String port;
        private String database;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getJdbcUrl() {
            return String.format("jdbc:mysql://%s:%s/%s", host, port, database);
        }
    }
}
