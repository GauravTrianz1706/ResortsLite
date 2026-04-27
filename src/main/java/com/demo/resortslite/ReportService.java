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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final Storage storage;
    
    @Value("${gcp.storage.bucket-name}")
    private String bucketName;
    
    @Value("${gcp.storage.project-id}")
    private String projectId;
    
    @Value("${PORT:8080}")
    private int serverPort;

    public ReportService() {
        // Initialize Google Cloud Storage client
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "reports/resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Create CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            
            // Upload to Google Cloud Storage
            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            byte[] content = csvContent.toString().getBytes(StandardCharsets.UTF_8);
            storage.create(blobInfo, content);

            result.put("status", "generated");
            result.put("gcsPath", "gs://" + bucketName + "/" + fileName);
            result.put("fileName", fileName);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate signed URL for secure download from GCS
        try {
            BlobId blobId = BlobId.of(bucketName, "reports/" + reportName);
            // Return GCS path - in production, generate signed URL with expiration
            return "gs://" + bucketName + "/reports/" + reportName;
        } catch (Exception e) {
            return "Error generating download URL: " + e.getMessage();
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageBucket", bucketName);
        info.put("storageType", "Google Cloud Storage");
        info.put("serverPort", serverPort);
        info.put("projectId", projectId);
        
        return info;
    }
}
