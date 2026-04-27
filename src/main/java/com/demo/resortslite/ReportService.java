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

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    @Value("${gcp.storage.project-id:}")
    private String projectId;

    @Value("${server.port:8080}")
    private int serverPort;

    private Storage storage;

    public ReportService() {
        // Initialize GCS client - will use application default credentials in GCP
        try {
            this.storage = StorageOptions.getDefaultInstance().getService();
        } catch (Exception e) {
            // Fallback for local development
            System.err.println("Warning: Could not initialize GCS client. File operations will fail: " + e.getMessage());
        }
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
            BlobId blobId = BlobId.of(bucketName, "reports/" + fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            if (storage != null) {
                storage.create(blobInfo, content);
                
                result.put("status", "generated");
                result.put("bucket", bucketName);
                result.put("path", "reports/" + fileName);
                result.put("gcsUri", "gs://" + bucketName + "/reports/" + fileName);
                result.put("serverPort", serverPort);
            } else {
                result.put("status", "error");
                result.put("message", "GCS client not initialized");
            }

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Build GCS signed URL or public URL
        if (storage != null && bucketName != null) {
            return "https://storage.googleapis.com/" + bucketName + "/reports/" + reportName;
        }
        return null;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageBucket", bucketName);
        info.put("storageType", "Google Cloud Storage");
        info.put("serverPort", serverPort);
        info.put("cloudProvider", "GCP");
        
        return info;
    }
}
