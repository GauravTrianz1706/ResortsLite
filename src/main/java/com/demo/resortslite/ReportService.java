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

    private final Storage storage;

    @Value("${gcs.bucket.reports:resort-reports-bucket}")
    private String reportsBucketName;

    @Value("${server.port:8080}")
    private int serverPort;

    public ReportService() {
        // Initialize GCS client
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n".getBytes(StandardCharsets.UTF_8));

            byte[] content = outputStream.toByteArray();

            // Upload to Google Cloud Storage
            BlobId blobId = BlobId.of(reportsBucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            storage.create(blobInfo, content);

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
        // Generate signed URL or public URL for GCS object
        return "https://storage.googleapis.com/" + reportsBucketName + "/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", reportsBucketName);
        info.put("storageType", "Google Cloud Storage");
        info.put("serverPort", serverPort);
        return info;
    }
}
