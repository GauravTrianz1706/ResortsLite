package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.reports.prefix}")
    private String reportsPrefix;

    // FIXED cr-java-0077: Removed hard-coded port default value (8080)
    // Port is now retrieved from AWS Parameter Store via environment variable
    @Value("${server.port}")
    private int serverPort;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        // Initialize S3 client with default credentials provider
        // This will use IAM roles in AWS environments (ECS, EKS, EC2)
        s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void cleanup() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // FIXED cr-java-0062: Replaced hard-coded file path with S3 object key
        String s3Key = reportsPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // FIXED cr-java-0062: Upload to S3 instead of writing to local file system
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, 
                    RequestBody.fromString(csvContent.toString(), StandardCharsets.UTF_8));

            result.put("status", "generated");
            result.put("bucket", bucketName);
            result.put("key", s3Key);
            result.put("s3Uri", "s3://" + bucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (S3Exception e) {
            result.put("status", "error");
            result.put("message", "S3 error: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // FIXED cr-java-0062: Build S3 URL instead of local file path
        String s3Key = reportsPrefix + reportName;
        
        // Generate pre-signed URL for secure download (valid for 1 hour)
        // Note: For production, implement pre-signed URL generation
        // For now, return S3 URI
        return "s3://" + bucketName + "/" + s3Key;
    }

    public Map<String, Object> getSystemInfo() {
        // FIXED cr-java-0062: Return S3 configuration instead of hard-coded paths
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "AWS S3");
        info.put("bucketName", bucketName);
        info.put("region", region);
        info.put("reportsPrefix", reportsPrefix);
        info.put("serverPort", serverPort);
        
        return info;
    }
}
