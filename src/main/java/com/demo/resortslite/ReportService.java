package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * ReportService — cloud-native report generation using Amazon S3.
 *
 * cr-java-0063 (Java.io.File Usage for Data Storage):
 *   The original implementation used java.io.File (line 32), new File(REPORT_BASE_PATH)
 *   (line 34), and FileWriter (line 37) to write report files to the local file system
 *   path "/var/legacy/reports/". In cloud / containerised environments the local file
 *   system is ephemeral; data written there is lost on container restart or scale-out.
 *
 *   Remediation applied (cr-java-0063): All java.io.File-based persistent storage
 *   operations have been replaced with Amazon S3 PutObject calls (AWS SDK for Java v2)
 *   so that report data is stored durably in object storage and is accessible from any
 *   container instance without host-level file system dependencies.
 *
 *   Occurrences fixed:
 *     - Line 32 (original): import java.io.File  → removed; replaced with AWS SDK imports
 *     - Line 34 (original): File reportDir = new File(REPORT_BASE_PATH)  → removed;
 *       directory creation is not needed in S3 (key-prefix model)
 *     - Line 37 (original): FileWriter writer = new FileWriter(fullPath)  → replaced with
 *       s3.putObject(putRequest, RequestBody.fromString(csvContent))
 *
 * cr-java-0062 (Local File System Write Operations):
 *   All local file write operations (FileWriter) have been replaced with Amazon S3
 *   PutObject calls for durable, cloud-native object storage.
 *
 * cr-java-0061 (Hard-coded Absolute File Paths):
 *   Static constants REPORT_BASE_PATH ("/var/legacy/reports/") and
 *   BACKUP_PATH ("C:\\ResortBackups\\nightly\\") have been replaced with
 *   environment-variable-backed @Value fields that resolve to S3 bucket / prefix
 *   references at runtime.
 */
@Service
public class ReportService {

    // cr-java-0061 / cr-java-0063: Replaced hard-coded absolute path "/var/legacy/reports/"
    // with an environment-variable-backed S3 bucket name.
    @Value("${cloud.aws.s3.bucket-name:resorts-lite-reports}")
    private String s3BucketName;

    // cr-java-0061 / cr-java-0063: Replaced hard-coded Windows backup path
    // "C:\\ResortBackups\\nightly\\" with an environment-variable-backed S3 key prefix.
    @Value("${cloud.aws.s3.backup-prefix:backups/nightly/}")
    private String s3BackupPrefix;

    @Value("${cloud.aws.region:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    // cr-java-0077 [Hard-coded Ports — FIXED]: Original hard-coded constant
    //   private static final int SERVER_PORT = 8080;  (source line 23)
    // has been replaced with an environment-variable-backed @Value injection.
    // At runtime, AWS ECS / EKS / Elastic Beanstalk injects the PORT environment
    // variable (or the value sourced from AWS Systems Manager Parameter Store via
    // the ECS task-definition secrets/environment block, e.g.:
    //   { "name": "PORT", "valueFrom": "arn:aws:ssm:<region>:<acct>:parameter/resortslite/server/port" }
    // ).
    // Spring Boot's server.port property (application.properties: server.port=${PORT:8080})
    // is the authoritative source; this field mirrors it for use in response payloads.
    @Value("${server.port:${PORT:8080}}")
    private int serverPort;

    /**
     * Builds an S3Client using the AWS default credential provider chain
     * (environment variables → EC2 instance profile → ECS task role → IAM role).
     * The client is closed after each operation via try-with-resources.
     */
    private S3Client buildS3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Generates a monthly CSV report and stores it in Amazon S3.
     *
     * cr-java-0063 fix — original lines 32–37:
     *   Line 32 (original): import java.io.File
     *     → Removed; AWS SDK imports (S3Client, PutObjectRequest, RequestBody) used instead.
     *   Line 34 (original): File reportDir = new File(REPORT_BASE_PATH);
     *     → Removed; S3 uses a flat key-prefix model — no directory creation needed.
     *   Line 37 (original): FileWriter writer = new FileWriter(fullPath);
     *     → Replaced with: s3.putObject(putRequest, RequestBody.fromString(csvContent))
     *
     * The report CSV content is identical; only the storage destination has changed.
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        // cr-java-0063: Build an S3 object key instead of a local file path.
        // Original: String fullPath = REPORT_BASE_PATH + fileName;
        String objectKey = "reports/resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try (S3Client s3 = buildS3Client()) {
            // Preserve original CSV content — only the write destination changes.
            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            // cr-java-0063 (Line 32): import java.io.File — removed; AWS SDK used instead.
            // cr-java-0063 (Line 34): File reportDir = new File(REPORT_BASE_PATH) — removed;
            //   S3 does not require directory creation; object keys use prefix conventions.
            // cr-java-0063 (Line 37): FileWriter writer = new FileWriter(fullPath) — replaced
            //   with S3 PutObject for durable, cloud-native object storage:
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(objectKey)
                    .contentType("text/csv")
                    .build();

            PutObjectResponse putResponse = s3.putObject(
                    putRequest,
                    RequestBody.fromString(csvContent));

            String s3Url = "s3://" + s3BucketName + "/" + objectKey;

            result.put("status", "generated");
            result.put("path", s3Url);          // S3 URI replaces local file path
            result.put("eTag", putResponse.eTag());
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Returns an S3 URI for the given report object.
     *
     * cr-java-0063: Replaced local file path construction with an S3 URI so that
     * callers receive a cloud-addressable reference rather than an ephemeral local path.
     */
    public String buildReportDownloadUrl(String reportName) {
        String objectKey = "reports/" + reportName;
        return "s3://" + s3BucketName + "/" + objectKey;
    }

    /**
     * Returns system / storage configuration info.
     *
     * cr-java-0063 / cr-java-0061: Replaced REPORT_BASE_PATH and BACKUP_PATH static
     * constants (local file system references) with S3 bucket and prefix values
     * sourced from environment variables.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", s3BucketName);
        info.put("reportPrefix", "reports/");
        info.put("backupPrefix", s3BackupPrefix);
        info.put("serverPort", serverPort);
        return info;
    }
}
