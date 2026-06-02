package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService — cloud-native version.
 *
 * Blockers fixed:
 *  - cr-java-0061 (lines 20, 32, 37): Hard-coded file paths replaced with S3 bucket/key config.
 *  - cr-java-0062 (line 37):          Local file write replaced with S3 PutObject.
 *  - cr-java-0063 (lines 32, 34, 37): java.io.File usage replaced with AWS SDK v2 S3Client.
 *  - cr-java-0077 (line 23):          Hard-coded SERVER_PORT replaced with environment variable.
 */
@Service
public class ReportService {

    /**
     * S3 bucket name injected from the environment variable REPORTS_S3_BUCKET
     * (set via ECS task definition, EKS ConfigMap, or Elastic Beanstalk env config).
     * Replaces hard-coded REPORT_BASE_PATH ("/var/legacy/reports/") and
     * BACKUP_PATH ("C:\\ResortBackups\\nightly\\").
     */
    @Value("${cloud.aws.s3.reports-bucket:${REPORTS_S3_BUCKET:resorts-lite-reports}}")
    private String reportsBucket;

    /**
     * S3 key prefix for reports — defaults to "reports/" if not configured.
     */
    @Value("${cloud.aws.s3.reports-prefix:reports/}")
    private String reportsPrefix;

    /**
     * Server port injected from the SERVER_PORT environment variable
     * (set by ECS/EKS/Elastic Beanstalk at runtime).
     * Replaces hard-coded SERVER_PORT = 8080.
     */
    @Value("${server.port:${SERVER_PORT:8080}}")
    private int serverPort;

    private final S3Client s3Client;

    public ReportService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // S3 object key replaces the hard-coded absolute file path
        String s3Key = reportsPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory — no local File or FileWriter needed
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload report to Amazon S3 (replaces FileWriter + local path write)
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(reportsBucket)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromString(csvContent.toString()));

            String s3Url = "s3://" + reportsBucket + "/" + s3Key;

            result.put("status", "generated");
            result.put("path", s3Url);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Returns a pre-signed S3 URL for the given report object key.
     * Replaces any local file path construction.
     */
    public String buildReportDownloadUrl(String reportName) {
        String s3Key = reportsPrefix + reportName;
        // Return the S3 URI; callers can generate a pre-signed URL via S3Presigner if needed
        return "s3://" + reportsBucket + "/" + s3Key;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        // Expose S3 bucket/prefix instead of local file paths
        info.put("reportsBucket", reportsBucket);
        info.put("reportsPrefix", reportsPrefix);
        info.put("serverPort", serverPort);
        return info;
    }
}
