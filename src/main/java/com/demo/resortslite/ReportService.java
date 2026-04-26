package com.demo.resortslite;

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

    
    private static final String REPORT_BASE_PATH = "/var/legacy/reports/"; // czr-java-001

    
    private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\"; // czr-java-001

    
    private static final int SERVER_PORT = 8080; // czr-port-001

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = REPORT_BASE_PATH + fileName; // czr-java-001

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(REPORT_BASE_PATH); // czr-java-001
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
            result.put("serverPort", SERVER_PORT); // czr-port-001

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    
    public String buildReportDownloadUrl(String reportName) { 
    }

    public Map<String, Object> getSystemInfo() { 
        
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", REPORT_BASE_PATH);  
        info.put("backupPath", BACKUP_PATH);       
        info.put("serverPort", SERVER_PORT);        
        
        return info;
    }
}
