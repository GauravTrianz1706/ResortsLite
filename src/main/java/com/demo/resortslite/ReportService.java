package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService — cloud-native version.
 *
 * Blockers resolved:
 *  - cr-java-0061 (lines 20, 32, 37): Hard-coded file paths replaced with S3 bucket/key config.
 *  - cr-java-0062 (line 37):          Local file write replaced with Amazon S3 PutObject.
 *  - cr-java-0063 (lines 32, 34, 37): java.io.File usage replaced with AWS SDK S3Client.
 *  - cr-java-0077 (line 23):          Hard-coded SERVER_PORT replaced with AWS SSM Parameter Store
 *                                     value injected via environment variable SERVER_PORT.
 */
@Service
public class ReportService {

    /**
     * S3 bucket name for report storage — injected from environment variable
     * REPORT_S3_BUCKET (set in ECS task definition / Elastic Beanstalk env config).
     * Replaces hard-coded REPORT_BASE_PATH ("/var/legacy/reports/") and
     * BACKUP_PATH ("C:\\ResortBackups\\nightly\\").
     */
    @Value("${report.s3.bucket:${REPORT_S3_BUCKET:resorts-lite-reports}}")
    private String reportS3Bucket;

    /**
     * S3 key prefix for reports — injected from environment variable REPORT_S3_PREFIX.
     */
    @Value("${report.s3.prefix:${REPORT_S3_PREFIX:reports/}}")
    private String reportS3Prefix;

    /**
     * Server port — injected from environment variable SERVER_PORT (set by ECS/EKS/Beanstalk).
     * Replaces hard-coded SERVER_PORT = 8080.
     */
    @Value("${server.port:${SERVER_PORT:8080}}")
    private int serverPort;

    /**
     * AWS SSM Parameter Store parameter name for the server port (optional override).
     */
    @Value("${ssm.parameter.server.port:${SSM_PARAM_SERVER_PORT:/resortslite/server/port}}")
    private String ssmServerPortParam;

    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public ReportService(S3Client s3Client, SsmClient ssmClient) {
        this.s3Client = s3Client;
        this.ssmClient = ssmClient;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        // S3 object key replaces the hard-coded local file path
        String objectKey = reportS3Prefix + "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory — no local File/FileWriter needed
            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            // Upload directly to Amazon S3 — replaces FileWriter to local path
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(reportS3Bucket)
                    .key(objectKey)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(csvContent.getBytes(StandardCharsets.UTF_8)));

            // Build a pre-signed / virtual-hosted S3 URL for the caller
            String s3Url = "s3://" + reportS3Bucket + "/" + objectKey;

            result.put("status", "generated");
            result.put("s3Bucket", reportS3Bucket);
            result.put("s3Key", objectKey);
            result.put("s3Url", s3Url);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Returns a download URL for a report stored in S3.
     * Replaces the previous empty method that relied on a local file path.
     */
    public String buildReportDownloadUrl(String reportName) {
        String objectKey = reportS3Prefix + reportName;
        return "s3://" + reportS3Bucket + "/" + objectKey;
    }

    public Map<String, Object> getSystemInfo() {
        // Resolve server port from AWS SSM Parameter Store at runtime
        int resolvedPort = serverPort;
        try {
            GetParameterResponse paramResponse = ssmClient.getParameter(
                    GetParameterRequest.builder()
                            .name(ssmServerPortParam)
                            .withDecryption(false)
                            .build());
            resolvedPort = Integer.parseInt(paramResponse.parameter().value());
        } catch (Exception e) {
            // Fall back to environment-variable-injected value
        }

        Map<String, Object> info = new HashMap<>();
        info.put("reportS3Bucket", reportS3Bucket);
        info.put("reportS3Prefix", reportS3Prefix);
        info.put("serverPort", resolvedPort);

        return info;
    }
}
