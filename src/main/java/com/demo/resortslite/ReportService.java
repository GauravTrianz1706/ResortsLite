package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private S3Service s3Service;

    // Blocker-2 (cz-java-0057): Replaced absolute file path with S3 bucket configuration
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    // Blocker-3 (cz-java-0057): Replaced absolute file path with S3 bucket configuration
    @Value("${aws.s3.bucket.name}")
    private String backupBucketName;

    // Blocker-11 (cz-java-0061): Externalized port configuration using environment variable
    @Value("${server.port}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // Blocker-2 (cz-java-0057): Using S3 key instead of absolute file path
        String reportKey = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Blocker-2 (cz-java-0057): Upload to S3 instead of local file system
            String s3Url = s3Service.uploadFile(reportKey, csvContent.toString());

            result.put("status", "generated");
            result.put("path", s3Url);
            // Blocker-11 (cz-java-0061): Using externalized port configuration
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Blocker-2 (cz-java-0057): Build S3 URL instead of local file path
        String reportKey = "reports/" + reportName;
        return s3Service.getFileUrl(reportKey);
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        // Blocker-2 (cz-java-0057): Return S3 bucket name instead of absolute path
        info.put("reportBucket", s3BucketName);
        // Blocker-3 (cz-java-0057): Return S3 bucket name instead of absolute path
        info.put("backupBucket", backupBucketName);
        // Blocker-11 (cz-java-0061): Using externalized port configuration
        info.put("serverPort", serverPort);
        
        return info;
    }
}
