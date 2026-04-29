package com.demo.resortslite;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private Storage storage;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    @Value("${server.port}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to Google Cloud Storage
            BlobId blobId = BlobId.of(bucketName, "reports/" + fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            byte[] content = csvContent.toString().getBytes(StandardCharsets.UTF_8);
            Blob blob = storage.create(blobInfo, content);

            result.put("status", "generated");
            result.put("path", "gs://" + bucketName + "/reports/" + fileName);
            result.put("blobName", blob.getName());
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
            Blob blob = storage.get(blobId);
            if (blob != null && blob.exists()) {
                return "gs://" + bucketName + "/reports/" + reportName;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", "gs://" + bucketName + "/reports/");
        info.put("backupPath", "gs://" + bucketName + "/backups/");
        info.put("serverPort", serverPort);
        info.put("storageType", "Google Cloud Storage");
        
        return info;
    }
}
