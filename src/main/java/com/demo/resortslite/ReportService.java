package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.ssm.parameter.prefix}")
    private String ssmParameterPrefix;

    @Value("${SERVER_PORT:8080}")
    private int serverPort;

    private S3Client s3Client;
    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        this.s3Client = S3Client.builder()
                .region(region)
                .build();
        this.ssmClient = SsmClient.builder()
                .region(region)
                .build();
    }

    /**
     * Generates monthly report and stores it in Amazon S3 instead of local file system.
     * This ensures data durability and availability in cloud environments.
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3
            byte[] content = baos.toByteArray();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("fileName", fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds report download URL using S3 pre-signed URL or CloudFront distribution.
     */
    public String buildReportDownloadUrl(String reportName) {
        // In production, this would generate a pre-signed S3 URL
        String s3Key = "reports/" + reportName;
        return "https://" + s3BucketName + ".s3." + awsRegion + ".amazonaws.com/" + s3Key;
    }

    /**
     * Returns system information from AWS Systems Manager Parameter Store.
     * This replaces hard-coded configuration values with cloud-native configuration management.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Retrieve configuration from AWS Systems Manager Parameter Store
            String reportPathParam = getParameter(ssmParameterPrefix + "/report-path");
            String backupPathParam = getParameter(ssmParameterPrefix + "/backup-path");
            
            info.put("reportPath", reportPathParam != null ? reportPathParam : "s3://" + s3BucketName + "/reports/");
            info.put("backupPath", backupPathParam != null ? backupPathParam : "s3://" + s3BucketName + "/backups/");
            info.put("serverPort", serverPort);
            info.put("s3Bucket", s3BucketName);
            info.put("awsRegion", awsRegion);
        } catch (Exception e) {
            // Fallback to default values if Parameter Store is not available
            info.put("reportPath", "s3://" + s3BucketName + "/reports/");
            info.put("backupPath", "s3://" + s3BucketName + "/backups/");
            info.put("serverPort", serverPort);
            info.put("error", "Could not retrieve parameters from SSM: " + e.getMessage());
        }
        
        return info;
    }

    /**
     * Helper method to retrieve parameter from AWS Systems Manager Parameter Store.
     */
    private String getParameter(String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            return null;
        }
    }
}
