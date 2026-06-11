package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Replaced hard-coded file path with S3 bucket name from environment variable (cr-java-0061)
    @Value("${cloud.aws.s3.reports-bucket:resorts-lite-reports}")
    private String reportsBucket;

    // Replaced hard-coded backup path with S3 bucket name from environment variable (cr-java-0061)
    @Value("${cloud.aws.s3.backup-bucket:resorts-lite-backups}")
    private String backupBucket;

    // Replaced hard-coded port with environment variable injection (cr-java-0077)
    @Value("${SERVER_PORT:${server.port:8080}}")
    private int serverPort;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final SsmClient ssmClient;

    public ReportService(S3Client s3Client, S3Presigner s3Presigner, SsmClient ssmClient) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.ssmClient = ssmClient;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        // Replaced hard-coded file path with S3 object key (cr-java-0061, cr-java-0062, cr-java-0063)
        String objectKey = "reports/resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory instead of writing to local file system
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload report content directly to Amazon S3 (replaces FileWriter to local path)
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(reportsBucket)
                    .key(objectKey)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromString(csvContent.toString()));

            result.put("status", "generated");
            result.put("s3Bucket", reportsBucket);
            result.put("s3Key", objectKey);
            // Replaced hard-coded SERVER_PORT constant with injected environment variable value
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Generates a pre-signed S3 URL for downloading a report.
     * Replaces the previous local file path reference with a cloud-native S3 pre-signed URL.
     */
    public String buildReportDownloadUrl(String reportName) {
        String objectKey = "reports/" + reportName;

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(reportsBucket)
                        .key(objectKey)
                        .build())
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        // Replaced hard-coded file paths with S3 bucket references (cr-java-0061)
        info.put("reportsBucket", reportsBucket);
        info.put("backupBucket", backupBucket);
        // Replaced hard-coded SERVER_PORT constant with injected environment variable value (cr-java-0077)
        info.put("serverPort", serverPort);
        return info;
    }
}
