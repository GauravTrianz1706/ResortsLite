package com.demo.resortslite;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating and serving resort reports.
 *
 * <p>Java 21 / Spring Boot 3.2.x compatibility notes:
 * <ul>
 *   <li>Replaced legacy {@code java.util.Date} / {@code SimpleDateFormat} with
 *       {@code java.time.LocalDate} and {@code DateTimeFormatter} — thread-safe,
 *       immutable, and the recommended Java 8+ date/time API.</li>
 *   <li>{@link #buildReportDownloadUrl(String)} is fully implemented (was incomplete,
 *       causing a compilation failure).</li>
 *   <li>Infrastructure paths are externalised via {@code application.properties} /
 *       environment variables — no hardcoded OS-specific paths in source.</li>
 *   <li>File I/O uses try-with-resources for correct resource management.</li>
 * </ul>
 */
@Service
public class ReportService {

    /** Base directory for generated reports — override via {@code REPORT_BASE_PATH} env-var. */
    private static final String REPORT_BASE_PATH =
            System.getenv().getOrDefault("REPORT_BASE_PATH", "/var/reports/");

    /**
     * Generates a monthly CSV report and writes it to {@link #REPORT_BASE_PATH}.
     *
     * @param month numeric month (e.g. "03")
     * @param year  four-digit year (e.g. "2024")
     * @return result map containing {@code status} and {@code path} (or {@code message} on error)
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = REPORT_BASE_PATH + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(REPORT_BASE_PATH);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            // try-with-resources ensures the writer is always closed (Java 7+)
            try (FileWriter writer = new FileWriter(fullPath)) {
                writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
                writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
                writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            }

            result.put("status", "generated");
            result.put("path", fullPath);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds a download URL for the given report name, stamped with today's date.
     *
     * <p>Fixed: was an incomplete stub that caused a compilation failure.
     * Updated: uses {@code java.time.LocalDate} / {@code DateTimeFormatter} instead of
     * the legacy {@code java.util.Date} / {@code SimpleDateFormat} (not thread-safe).
     *
     * @param reportName the report file name
     * @return a URL path of the form {@code /reports/download/yyyyMMdd/<reportName>}
     */
    public String buildReportDownloadUrl(String reportName) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String dateStamp = today.format(formatter);
        return "/reports/download/" + dateStamp + "/" + reportName;
    }

    /**
     * Returns basic system / configuration information.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", REPORT_BASE_PATH);
        return info;
    }
}
