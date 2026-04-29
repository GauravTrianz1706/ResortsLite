package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket.name:resorts-lite-reports}")
    private String s3BucketName;

    @Value("${aws.ssm.server.port:/resorts/config/server-port}")
    private String serverPortParameterName;

    private S3Client s3Client;
    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        // Initialize AWS SDK clients with default credentials provider
        Region region = Region.of(awsRegion);
        
        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        
        this.ssmClient = SsmClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Generates monthly report and stores it in Amazon S3 instead of local file system.
     * This ensures data durability and availability across container restarts.
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to S3 instead of writing to local file system
            String s3Key = "reports/" + year + "/" + month + "/" + fileName;
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, 
                    RequestBody.fromString(csvContent.toString(), StandardCharsets.UTF_8));

            // Get server port from AWS Systems Manager Parameter Store
            String serverPort = getParameterFromSSM(serverPortParameterName, "8080");

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Url", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds report download URL using environment-specific configuration from Parameter Store.
     */
    public String buildReportDownloadUrl(String reportName) {
        try {
            String serverPort = getParameterFromSSM(serverPortParameterName, "8080");
            return "https://api.resorts.example.com:" + serverPort + "/reports/download/" + reportName;
        } catch (Exception e) {
            return "https://api.resorts.example.com:8080/reports/download/" + reportName;
        }
    }

    /**
     * Returns system information with cloud-native configuration sources.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            String serverPort = getParameterFromSSM(serverPortParameterName, "8080");
            
            info.put("storageType", "Amazon S3");
            info.put("s3Bucket", s3BucketName);
            info.put("awsRegion", awsRegion);
            info.put("serverPort", serverPort);
            info.put("configSource", "AWS Systems Manager Parameter Store");
        } catch (Exception e) {
            info.put("error", "Failed to retrieve system info: " + e.getMessage());
        }
        
        return info;
    }

    /**
     * Helper method to retrieve parameters from AWS Systems Manager Parameter Store.
     */
    private String getParameterFromSSM(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Return default value if parameter not found or error occurs
            return defaultValue;
        }
    }
}
