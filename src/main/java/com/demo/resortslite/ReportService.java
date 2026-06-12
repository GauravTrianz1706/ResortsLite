package com.demo.resortslite;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Configuration should be externalized to application.properties
    private static final String REPORT_BASE_PATH = "/var/legacy/reports/";
    private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\";
    private static final int SERVER_PORT = 8080;

    // Using Java 8+ DateTimeFormatter instead of SimpleDateFormat
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = REPORT_BASE_PATH + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(REPORT_BASE_PATH);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            FileWriter writer = new FileWriter(fullPath);
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.close();

            result.put("status", "generated");
            result.put("path", fullPath);
            result.put("serverPort", SERVER_PORT);
            result.put("generatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    // Fixed method signature - added return statement
    public String buildReportDownloadUrl(String reportName) {
        return "http://localhost:" + SERVER_PORT + "/api/reports/download/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", REPORT_BASE_PATH);
        info.put("backupPath", BACKUP_PATH);
        info.put("serverPort", SERVER_PORT);
        info.put("currentTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return info;
    }
}
