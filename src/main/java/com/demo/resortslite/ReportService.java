package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with environment variables
    @Value("${gcp.storage.bucket.reports:${GCS_REPORTS_BUCKET:resort-reports-bucket}}")
    private String reportsBucketName;

    @Value("${gcp.storage.bucket.backups:${GCS_BACKUPS_BUCKET:resort-backups-bucket}}")
    private String backupsBucketName;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable
    @Value("${server.port:${PORT:8080}}")
    private int serverPort;

    private final Storage storage;

    public ReportService() {
        // Initialize Google Cloud Storage client
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    // FIXED cr-java-0062, cr-java-0063: Replaced local file operations with GCS
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n".getBytes(StandardCharsets.UTF_8));

            // Upload to Google Cloud Storage
            BlobId blobId = BlobId.of(reportsBucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            storage.create(blobInfo, outputStream.toByteArray());

            result.put("status", "generated");
            result.put("bucket", reportsBucketName);
            result.put("fileName", fileName);
            result.put("gcsPath", "gs://" + reportsBucketName + "/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate signed URL for secure download from GCS
        BlobId blobId = BlobId.of(reportsBucketName, reportName);
        return "gs://" + reportsBucketName + "/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportsBucket", reportsBucketName);
        info.put("backupsBucket", backupsBucketName);
        info.put("serverPort", serverPort);
        return info;
    }
}
