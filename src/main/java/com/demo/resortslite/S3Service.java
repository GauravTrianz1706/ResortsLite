package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * S3 Service for handling file storage operations in AWS S3
 * Replaces local file system operations for containerized environments
 */
@Service
public class S3Service {

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.region}")
    private String awsRegion;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Upload content to S3
     * @param key S3 object key (file path)
     * @param content Content to upload
     * @return S3 object URL
     */
    public String uploadFile(String key, String content) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromString(content));
        
        return String.format("s3://%s/%s", bucketName, key);
    }

    /**
     * Download content from S3
     * @param key S3 object key (file path)
     * @return File content as string
     */
    public String downloadFile(String key) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (InputStream inputStream = s3Client.getObject(getObjectRequest);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            
            return outputStream.toString("UTF-8");
        }
    }

    /**
     * Check if file exists in S3
     * @param key S3 object key (file path)
     * @return true if file exists
     */
    public boolean fileExists(String key) {
        try {
            s3Client.headObject(builder -> builder.bucket(bucketName).key(key));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get S3 object URL
     * @param key S3 object key (file path)
     * @return S3 URL
     */
    public String getFileUrl(String key) {
        return String.format("s3://%s/%s", bucketName, key);
    }
}
