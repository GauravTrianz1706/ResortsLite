package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket.name:resorts-lite-reports}")
    private String s3BucketName;

    @Value("${SERVER_PORT:8080}")
    private String serverPort;

    private S3Client s3Client;
    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        Region region = Region.of(awsRegion);
        this.s3Client = S3Client.builder()
                .region(region)
                .build();
        this.ssmClient = SsmClient.builder()
                .region(region)
                .build();
    }

    /**
     * Generates monthly report and stores it in Amazon S3
     * Fixes: cr-java-0061, cr-java-0062, cr-java-0063 (Hard-coded file paths, local file writes, java.io.File usage)
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

            byte[] csvBytes = outputStream.toByteArray();

            // Upload to S3 instead of local file system
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(csvBytes));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Url", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", getServerPortFromParameterStore());

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "S3 upload failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Builds report download URL using environment configuration
     * Fixes: cr-java-0071 (Hard-coded environment URLs)
     */
    public String buildReportDownloadUrl(String reportName) {
        // Use S3 pre-signed URL or CloudFront distribution URL
        String s3Key = "reports/" + reportName;
        return "https://" + s3BucketName + ".s3." + awsRegion + ".amazonaws.com/" + s3Key;
    }

    /**
     * Gets system info from AWS Parameter Store instead of hard-coded values
     * Fixes: cr-java-0077 (Hard-coded ports)
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("s3Bucket", s3BucketName);
        info.put("awsRegion", awsRegion);
        info.put("serverPort", getServerPortFromParameterStore());
        info.put("storageType", "Amazon S3");
        return info;
    }

    /**
     * Retrieves server port from AWS Systems Manager Parameter Store
     * Fixes: cr-java-0077 (Hard-coded ports)
     */
    private String getServerPortFromParameterStore() {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name("/resorts-lite/server/port")
                    .withDecryption(false)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Fallback to environment variable if Parameter Store is not available
            return serverPort;
        }
    }
}
