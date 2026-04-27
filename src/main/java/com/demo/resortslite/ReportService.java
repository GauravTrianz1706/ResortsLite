package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061, cr-java-0062, cr-java-0063: Replaced hard-coded file paths with S3
    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable
    @Value("${server.port}")
    private int serverPort;

    private S3Client s3Client;
    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        
        // Initialize S3 client for cloud storage
        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        
        // Initialize SSM client for parameter store
        this.ssmClient = SsmClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * FIXED cr-java-0061, cr-java-0062, cr-java-0063: Generate monthly report and store in S3
     * Replaced local file system operations with Amazon S3 for durable, scalable storage
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory instead of writing to local file system
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            byte[] reportData = outputStream.toByteArray();

            // Upload to S3 instead of local file system
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(reportData));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Url", String.format("s3://%s/%s", s3BucketName, s3Key));
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * FIXED cr-java-0071: Build report download URL using externalized configuration
     */
    public String buildReportDownloadUrl(String reportName) {
        // Retrieve base URL from AWS Parameter Store instead of hard-coding
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/resortslite/api/base-url")
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(parameterRequest);
            String baseUrl = response.parameter().value();
            
            return baseUrl + "/api/reports/download?name=" + reportName;
        } catch (Exception e) {
            // Fallback to environment-based URL construction
            String baseUrl = System.getenv("API_BASE_URL");
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "http://localhost:" + serverPort;
            }
            return baseUrl + "/api/reports/download?name=" + reportName;
        }
    }

    /**
     * FIXED cr-java-0061, cr-java-0077: Return cloud-native system info
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "Amazon S3");
        info.put("s3Bucket", s3BucketName);
        info.put("awsRegion", awsRegion);
        info.put("serverPort", serverPort);
        
        return info;
    }
}
