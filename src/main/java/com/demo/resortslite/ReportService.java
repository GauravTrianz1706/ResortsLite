package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final S3Client s3Client;

    @Value("${aws.s3.reports.bucket:resort-reports-bucket}")
    private String reportsBucketName;

    @Value("${aws.s3.backups.bucket:resort-backups-bucket}")
    private String backupsBucketName;

    @Value("${server.port:8080}")
    private int serverPort;

    public ReportService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3
            byte[] content = baos.toByteArray();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(reportsBucketName)
                    .key("reports/" + fileName)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            result.put("status", "generated");
            result.put("bucket", reportsBucketName);
            result.put("key", "reports/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate pre-signed URL or return S3 path
        return "s3://" + reportsBucketName + "/reports/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportsBucket", reportsBucketName);
        info.put("backupsBucket", backupsBucketName);
        info.put("serverPort", serverPort);
        return info;
    }
}
