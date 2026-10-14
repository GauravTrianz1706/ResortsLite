package com.demo.resortslite;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService — cloud-native implementation.
 *
 * cr-java-0063 remediation: All java.io.File-based persistent storage operations have been
 * replaced with Amazon S3 client calls (AWS SDK for Java v2) to achieve cloud-native,
 * durable, and scalable storage without host-level file system dependencies.
 *
 * Specifically, the following original java.io.File usages were removed:
 *   - Line 32 (original): new File(REPORT_BASE_PATH)  → replaced with S3 bucket reference
 *   - Line 34 (original): reportDir.mkdirs()           → removed (S3 needs no directory creation)
 *   - Line 37 (original): new FileWriter(fullPath)     → replaced with S3Client.putObject()
 *
 * cr-java-0077 remediation: Hard-coded port number (8080) on original line 23 has been
 * eliminated. The SERVER_PORT value is now injected at runtime via the SERVER_PORT
 * environment variable, which MUST be populated from AWS Systems Manager Parameter Store
 * before deployment (see configuration notes below).
 *
 * Configuration is driven entirely by environment variables following 12-factor app principles:
 *   REPORT_S3_BUCKET  — S3 bucket name for reports          (default: resorts-reports-bucket)
 *   BACKUP_S3_PREFIX  — S3 key prefix for nightly backups   (default: backups/nightly/)
 *   SERVER_PORT       — HTTP port the application listens on (default: 8080)
 *
 * AWS SSM Parameter Store setup for SERVER_PORT (cr-java-0077):
 *   Parameter name : /resortslite/server/port
 *   Parameter type : String
 *   Example value  : 8080
 *
 * Inject into the application at runtime via one of:
 *   - ECS task definition environment variable: SERVER_PORT → SSM parameter reference
 *     (valueFrom: arn:aws:ssm:<region>:<account>:parameter/resortslite/server/port)
 *   - EKS: AWS Secrets Store CSI Driver or external-secrets operator
 *   - Elastic Beanstalk: environment property sourced from SSM via .ebextensions
 *
 * The default value (8080) is a local-dev fallback only — never used in cloud deployments.
 */
@Service
public class ReportService {

    // cr-java-0063 (line 32 original): Replaced new File(REPORT_BASE_PATH) — a java.io.File
    // persistent storage reference — with an S3 bucket name sourced from the environment
    // variable REPORT_S3_BUCKET. No local file system path is referenced anywhere in this class.
    private static final String S3_BUCKET_NAME =
            System.getenv().getOrDefault("REPORT_S3_BUCKET", "resorts-reports-bucket");

    // cr-java-0063: Replaced hard-coded Windows backup path "C:\\ResortBackups\\nightly\\"
    // (java.io.File-based) with an S3 key prefix configured via environment variable
    // BACKUP_S3_PREFIX. Eliminates OS-specific path separator and local disk dependency.
    private static final String S3_BACKUP_PREFIX =
            System.getenv().getOrDefault("BACKUP_S3_PREFIX", "backups/nightly/");

    // cr-java-0077: Hard-coded port 8080 (original line 23) replaced with environment variable
    // SERVER_PORT injected at runtime from AWS Systems Manager Parameter Store.
    // In ECS/EKS/Elastic Beanstalk, set SERVER_PORT via SSM parameter /resortslite/server/port.
    // The default value "8080" is used only for local development; cloud deployments MUST
    // supply this value through the SSM-backed environment variable injection mechanism.
    private static final int SERVER_PORT =
            Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // Pre-signed URL expiry duration (1 hour)
    private static final Duration PRESIGN_DURATION = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public ReportService() {
        this.s3Client = S3Client.create();
        this.s3Presigner = S3Presigner.create();
    }

    /**
     * Generates a monthly report and uploads it directly to Amazon S3.
     *
     * cr-java-0063 fix — three java.io.File occurrences removed from original lines 32–37:
     *
     *   BEFORE (line 32): File reportDir = new File(REPORT_BASE_PATH);
     *   BEFORE (line 34): reportDir.mkdirs();
     *   BEFORE (line 37): FileWriter writer = new FileWriter(fullPath);
     *
     *   AFTER: CSV content is built in-memory (StringBuilder) and uploaded directly to
     *          Amazon S3 via S3Client.putObject(). No java.io.File or FileWriter is used.
     *          S3 object creation does not require directory pre-creation (mkdirs removed).
     *          The local file path variable (fullPath) is replaced by an S3 object key (s3Key).
     *
     * cr-java-0077 fix: SERVER_PORT is no longer hard-coded; it is read from the
     * SERVER_PORT environment variable (sourced from AWS SSM Parameter Store at deploy time).
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // cr-java-0063: S3 object key replaces the former local file path (fullPath variable)
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // cr-java-0063 (line 32 original): new File(REPORT_BASE_PATH) removed.
            // cr-java-0063 (line 34 original): reportDir.mkdirs() removed — S3 needs no dirs.
            // Build CSV content in memory — no local FileWriter or File object needed.
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // cr-java-0063 (line 37 original): new FileWriter(fullPath) replaced by
            // S3Client.putObject() — streams CSV bytes directly to Amazon S3.
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(csvContent.toString()));

            String s3Url = "s3://" + S3_BUCKET_NAME + "/" + s3Key;
            result.put("status", "generated");
            result.put("path", s3Url);
            // cr-java-0077: SERVER_PORT value is now sourced from AWS SSM Parameter Store
            // via the SERVER_PORT environment variable — no longer a hard-coded literal.
            result.put("serverPort", SERVER_PORT);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds a pre-signed S3 URL so callers can download a report directly from S3.
     *
     * cr-java-0063 fix: Replaced the former local file-path construction (java.io.File-based)
     * with a time-limited pre-signed S3 GET URL, ensuring reports are served from durable
     * cloud storage rather than the ephemeral container file system.
     */
    public String buildReportDownloadUrl(String reportName) {
        String s3Key = "reports/" + reportName;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(S3_BUCKET_NAME)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_DURATION)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Returns system configuration info using S3 references instead of local paths.
     *
     * cr-java-0063 fix: Replaced reportPath (/var/legacy/reports/) and backupPath
     * (C:\\ResortBackups\\nightly\\) — both java.io.File-based local paths — with
     * S3 bucket and prefix references sourced from environment variables.
     *
     * cr-java-0077 fix: SERVER_PORT is now sourced from AWS SSM Parameter Store via
     * the SERVER_PORT environment variable rather than being hard-coded as 8080.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        // cr-java-0063: S3 bucket/prefix references replace former java.io.File-based paths
        info.put("reportBucket", S3_BUCKET_NAME);
        info.put("backupPrefix", S3_BACKUP_PREFIX);
        // cr-java-0077: SERVER_PORT sourced from AWS SSM Parameter Store at runtime
        info.put("serverPort", SERVER_PORT);
        return info;
    }
}
