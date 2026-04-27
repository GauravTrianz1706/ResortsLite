package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private GcsStorageService gcsStorageService;

    // Blocker-2: Replaced absolute file path with GCS bucket configuration
    @Value("${gcs.bucket.name}")
    private String gcsBucketName;

    // Blocker-3: Replaced absolute Windows path with environment variable
    @Value("${backup.path:gs://${gcs.bucket.name}/backups}")
    private String backupPath;

    // Blocker-11: Externalized port configuration
    @Value("${server.port:8080}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Blocker-2, Blocker-3: Generate report content and upload to GCS
            StringBuilder reportContent = new StringBuilder();
            reportContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            reportContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            reportContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            String gcsPath = gcsStorageService.uploadFile(fileName, reportContent.toString());

            result.put("status", "generated");
            result.put("path", gcsPath);
            result.put("storage", "gcs");
            result.put("bucket", gcsBucketName);
            result.put("serverPort", serverPort); // Blocker-11: Now from configuration

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Blocker-2: Use GCS signed URLs instead of local file paths
        try {
            return gcsStorageService.getSignedUrl(reportName, 60);
        } catch (IOException e) {
            return "Error generating download URL: " + e.getMessage();
        }
    }

    public Map<String, Object> getSystemInfo() {
        // Blocker-2, Blocker-3, Blocker-11: All paths and ports now externalized
        Map<String, Object> info = new HashMap<>();
        info.put("reportStorage", "gs://" + gcsBucketName);
        info.put("backupPath", backupPath);
        info.put("serverPort", serverPort);
        
        return info;
    }
}
