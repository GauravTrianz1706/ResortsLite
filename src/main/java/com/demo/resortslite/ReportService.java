package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // BLOCKER-2, BLOCKER-3 FIX: Replace absolute file paths with S3 configuration
    @Value("${aws.s3.bucket.reports:resort-reports-bucket}")
    private String s3BucketName;

    @Value("${aws.s3.bucket.backups:resort-backups-bucket}")
    private String s3BackupBucket;

    // BLOCKER-11 FIX: Replace hardcoded port with environment variable
    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired
    private S3Client s3Client;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // BLOCKER-2, BLOCKER-3 FIX: Use S3 key instead of absolute file path
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 instead of writing to local filesystem
            byte[] reportData = outputStream.toByteArray();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(reportData));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("serverPort", serverPort); // BLOCKER-11 FIX: Use externalized port

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    
    public String buildReportDownloadUrl(String reportName) {
        // BLOCKER-2, BLOCKER-3 FIX: Generate S3 URL instead of file path
        String s3Key = "reports/" + reportName;
        try {
            return s3Client.utilities()
                    .getUrl(builder -> builder.bucket(s3BucketName).key(s3Key))
                    .toString();
        } catch (Exception e) {
            return "Error generating URL: " + e.getMessage();
        }
    }

    public Map<String, Object> getSystemInfo() { 
        // BLOCKER-2, BLOCKER-3, BLOCKER-11 FIX: Return S3 configuration instead of file paths
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", s3BucketName);  
        info.put("backupBucket", s3BackupBucket);       
        info.put("serverPort", serverPort);        
        
        return info;
    }
}
