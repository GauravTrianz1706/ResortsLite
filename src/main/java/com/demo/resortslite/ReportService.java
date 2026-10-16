package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // ── Externalised configuration ────────────────────────────────────────────
    // Hardcoded OS-specific paths replaced with injectable properties so the
    // service runs correctly on both Linux containers and Windows developer
    // machines without code changes.
    @Value("${app.report.base-path:${java.io.tmpdir}/resorts/reports/}")
    private String reportBasePath;

    // Windows-specific backup path removed; backup location is now an
    // environment-specific concern handled outside the application.
    @Value("${app.report.backup-path:${java.io.tmpdir}/resorts/backups/}")
    private String backupPath;

    // Server port is managed by Spring Boot; no need to duplicate it here.
    @Value("${server.port:8080}")
    private int serverPort;

    // ── Monthly report generation ─────────────────────────────────────────────
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = reportBasePath + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(reportBasePath);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            // try-with-resources ensures the writer is always closed even on
            // exception (Java 11 best practice).
            try (FileWriter writer = new FileWriter(fullPath)) {
                writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
                writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
                writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            }

            result.put("status", "generated");
            result.put("path", fullPath);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    // ── Report download URL builder ───────────────────────────────────────────
    /**
     * Returns a portable relative URL for downloading a named report.
     * The former empty method body (compilation error) has been corrected.
     */
    public String buildReportDownloadUrl(String reportName) {
        return "/api/reports/download?name=" + reportName;
    }

    // ── System info ───────────────────────────────────────────────────────────
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);
        info.put("backupPath", backupPath);
        info.put("serverPort", serverPort);
        return info;
    }
}
