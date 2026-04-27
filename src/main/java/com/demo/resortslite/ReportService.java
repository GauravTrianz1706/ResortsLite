package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final S3Client s3Client;
    private final SsmClient ssmClient;
    
    // Cloud-native: Use environment variables instead of hard-coded paths
    @Value("${aws.s3.reports.bucket:${REPORTS_BUCKET:resort-reports-bucket}}")
    private String reportsBucket;
    
    @Value("${server.port:${SERVER_PORT:8080}}")
    private int serverPort;

    public ReportService(S3Client s3Client, SsmClient ssmClient) {
        this.s3Client = s3Client;
        this.ssmClient = ssmClient;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory instead of writing to local file system
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 instead of local file system
            byte[] content = baos.toByteArray();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(reportsBucket)
                    .key("reports/" + fileName)
                    .contentType("text/csv")
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            result.put("status", "generated");
            result.put("bucket", reportsBucket);
            result.put("key", "reports/" + fileName);
            result.put("s3Uri", "s3://" + reportsBucket + "/reports/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Retrieve URL configuration from AWS Systems Manager Parameter Store
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name("/resortslite/report/base-url")
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            String baseUrl = response.parameter().value();
            return baseUrl + "/reports/" + reportName;
        } catch (Exception e) {
            // Fallback to environment variable
            String baseUrl = System.getenv("REPORT_BASE_URL");
            if (baseUrl == null) {
                baseUrl = "https://reports.resortslite.example.com";
            }
            return baseUrl + "/reports/" + reportName;
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportsBucket", reportsBucket);
        info.put("serverPort", serverPort);
        info.put("storageType", "AWS S3");
        return info;
    }
}
