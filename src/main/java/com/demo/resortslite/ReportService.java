package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService — cloud-ready version.
 *
 * Changes applied:
 *  - blocker-1/2/3  (cr-java-0061): Replaced hard-coded file paths
 *    (/var/legacy/reports/ and C:\ResortBackups\nightly\) with GCS bucket/prefix
 *    values read from environment variables GCS_BUCKET_NAME and GCS_REPORT_PREFIX.
 *  - blocker-4      (cr-java-0062): Replaced local FileWriter write with
 *    GCS Storage.create() so data is durable across container restarts.
 *  - blocker-5/6/7  (cr-java-0063): Removed java.io.File usage entirely;
 *    all storage operations now go through the GCS Java client library.
 *  - blocker-11     (cr-java-0077): Replaced hard-coded SERVER_PORT constant
 *    with an @Value-injected field backed by the SERVER_PORT environment variable
 *    (defaults to 8080 if not set).
 */
@Service
public class ReportService {

    // GCS bucket and prefix are externalised — set GCS_BUCKET_NAME and
    // GCS_REPORT_PREFIX environment variables (or application.properties entries).
    @Value("${gcs.bucket.name:${GCS_BUCKET_NAME:resorts-lite-reports}}")
    private String gcsBucketName;

    @Value("${gcs.report.prefix:${GCS_REPORT_PREFIX:reports/}}")
    private String gcsReportPrefix;

    @Value("${gcs.backup.prefix:${GCS_BACKUP_PREFIX:backups/nightly/}}")
    private String gcsBackupPrefix;

    // Port is now externalised — no hard-coded constant.
    @Value("${server.port:${SERVER_PORT:8080}}")
    private int serverPort;

    private Storage getStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String objectName = gcsReportPrefix + "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            Storage storage = getStorage();
            BlobId blobId = BlobId.of(gcsBucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            storage.create(blobInfo, csvContent.getBytes(StandardCharsets.UTF_8));

            String gcsUri = "gs://" + gcsBucketName + "/" + objectName;
            result.put("status", "generated");
            result.put("path", gcsUri);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        return "https://storage.googleapis.com/" + gcsBucketName + "/" + gcsReportPrefix + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", gcsBucketName);
        info.put("reportPrefix", gcsReportPrefix);
        info.put("backupPrefix", gcsBackupPrefix);
        info.put("serverPort", serverPort);
        return info;
    }
}
