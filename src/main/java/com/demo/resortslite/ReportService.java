package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private GcsStorageService gcsStorageService;

    // Blocker-2: Replaced hardcoded absolute path with externalized configuration
    @Value("${app.report.base-path}")
    private String reportBasePath;

    // Blocker-3: Replaced hardcoded absolute path with externalized configuration
    @Value("${app.backup.path}")
    private String backupPath;

    // Blocker-11: Replaced hardcoded port with externalized configuration
    @Value("${server.port}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate report content
            StringBuilder reportContent = new StringBuilder();
            reportContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            reportContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            reportContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Blocker-2, Blocker-3: Upload to GCS instead of local filesystem
            String gcsPath = gcsStorageService.uploadFile(fileName, reportContent.toString());

            result.put("status", "generated");
            result.put("path", gcsPath);
            result.put("serverPort", serverPort); // Blocker-11: Now uses externalized port

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Blocker-2: Use GCS URL instead of local file path
        return gcsStorageService.getFileUrl(reportName);
    }

    public Map<String, Object> getSystemInfo() {
        // Blocker-2, Blocker-3, Blocker-11: All paths and ports now externalized
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);
        info.put("backupPath", backupPath);
        info.put("serverPort", serverPort);
        
        return info;
    }
}
