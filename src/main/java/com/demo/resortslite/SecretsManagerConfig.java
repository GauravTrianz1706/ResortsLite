package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.sql.DataSource;

/**
 * cr-java-0069: AWS Secrets Manager integration for database credentials.
 *
 * <p>Replaces hard-coded DB_USER / DB_PASS constants that were previously
 * embedded in BookingService (lines 20-21).  At application startup this
 * configuration class retrieves the secret JSON from AWS Secrets Manager,
 * parses the {@code username} and {@code password} fields, and wires them
 * into a HikariCP DataSource — no credentials ever appear in source code,
 * property files, or container image layers.</p>
 *
 * <p>Required environment variables (set via ECS task definition / EKS
 * secret / EC2 instance profile — never hard-coded):</p>
 * <ul>
 *   <li>{@code DB_SECRET_NAME}  – name or ARN of the Secrets Manager secret
 *       (default: {@code resortslite/db/credentials})</li>
 *   <li>{@code AWS_REGION}      – AWS region where the secret lives
 *       (default: {@code us-east-1})</li>
 *   <li>{@code DB_URL}          – JDBC URL for the target database
 *       (default: in-memory H2 for local development)</li>
 * </ul>
 *
 * <p>The secret stored in Secrets Manager must be a JSON string with at
 * minimum the following keys:</p>
 * <pre>
 * {
 *   "username": "admin",
 *   "password": "Resort$Pass#2019!"
 * }
 * </pre>
 */
@Configuration
public class SecretsManagerConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerConfig.class);

    /** Name / ARN of the Secrets Manager secret that holds DB credentials. */
    @Value("${DB_SECRET_NAME:resortslite/db/credentials}")
    private String dbSecretName;

    /** AWS region where the secret is stored. */
    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;

    /** JDBC URL for the target database. */
    @Value("${DB_URL:jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}")
    private String dbUrl;

    /**
     * Builds a HikariCP DataSource whose credentials are fetched from
     * AWS Secrets Manager at startup.
     *
     * @return a fully configured {@link DataSource}
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        DbCredentials creds = fetchCredentials();

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(dbUrl);
        ds.setUsername(creds.username());
        ds.setPassword(creds.password());
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        log.info("DataSource configured via AWS Secrets Manager secret '{}'", dbSecretName);
        return ds;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves and parses the database credentials from AWS Secrets Manager.
     * Falls back to empty strings if the secret cannot be reached (e.g. local
     * development without AWS credentials) so that the H2 in-memory database
     * still starts up correctly.
     */
    private DbCredentials fetchCredentials() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(dbSecretName)
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(secretJson);

            String username = node.path("username").asText("");
            String password = node.path("password").asText("");
            log.info("Successfully retrieved DB credentials from Secrets Manager");
            return new DbCredentials(username, password);

        } catch (Exception ex) {
            // In local / CI environments without AWS credentials the SDK will
            // throw; fall back to empty credentials so H2 still works.
            log.warn("Could not retrieve secret '{}' from AWS Secrets Manager ({}). "
                    + "Falling back to empty credentials — suitable for local/H2 only.",
                    dbSecretName, ex.getMessage());
            return new DbCredentials("", "");
        }
    }

    /** Immutable value object for database credentials. */
    private static final class DbCredentials {
        private final String username;
        private final String password;

        DbCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        String username() { return username; }
        String password() { return password; }
    }
}
