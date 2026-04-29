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

    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.ssm.parameter.prefix}")
    private String ssmParameterPrefix;

    @Value("${SERVER_PORT:8080}")
    private int serverPort;

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

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = "reports/" + fileName;

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
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Url", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Retrieve URL configuration from AWS Systems Manager Parameter Store
        String parameterName = ssmParameterPrefix + "/report/base-url";
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            String baseUrl = response.parameter().value();
            return baseUrl + "/reports/" + reportName;
        } catch (Exception e) {
            // Fallback to S3 URL
            return "s3://" + s3BucketName + "/reports/" + reportName;
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("s3Bucket", s3BucketName);
        info.put("awsRegion", awsRegion);
        info.put("serverPort", serverPort);
        info.put("storageType", "AWS S3");
        return info;
    }
}
