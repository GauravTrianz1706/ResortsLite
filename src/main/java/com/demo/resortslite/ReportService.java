package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private Storage storage;

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    @Value("${server.port:8080}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "reports/resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
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
        // Generate signed URL or public URL for GCS object
        String gcsPath = "gs://" + bucketName + "/reports/" + reportName;
        return gcsPath;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageBucket", bucketName);
        info.put("storageType", "Google Cloud Storage");
        info.put("serverPort", serverPort);
        return info;
    }
}
