package com.demo.resortslite;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Replaced hard-coded file paths with Azure Blob Storage configuration
    @Value("${azure.storage.connection-string:#{null}}")
    private String azureStorageConnectionString;

    @Value("${azure.storage.container.reports:reports}")
    private String reportsContainerName;

    @Value("${azure.storage.container.backups:backups}")
    private String backupsContainerName;

    // Replaced hard-coded port with environment variable
    @Value("${server.port:8080}")
    private int serverPort;

    private BlobServiceClient getBlobServiceClient() {
        if (azureStorageConnectionString == null || azureStorageConnectionString.isEmpty()) {
            throw new IllegalStateException("Azure Storage connection string is not configured. Set azure.storage.connection-string property.");
        }
        return new BlobServiceClientBuilder()
                .connectionString(azureStorageConnectionString)
                .buildClient();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Create CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to Azure Blob Storage
            BlobServiceClient blobServiceClient = getBlobServiceClient();
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(reportsContainerName);
            
            // Create container if it doesn't exist
            if (!containerClient.exists()) {
                containerClient.create();
            }

            BlobClient blobClient = containerClient.getBlobClient(fileName);
            byte[] data = outputStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            blobClient.upload(inputStream, data.length, true);

            result.put("status", "generated");
            result.put("blobName", fileName);
            result.put("containerName", reportsContainerName);
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Failed to generate report: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to upload to Azure Blob Storage: " + e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        try {
            BlobServiceClient blobServiceClient = getBlobServiceClient();
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(reportsContainerName);
            BlobClient blobClient = containerClient.getBlobClient(reportName);
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            return "Error building download URL: " + e.getMessage();
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportsContainer", reportsContainerName);
        info.put("backupsContainer", backupsContainerName);
        info.put("serverPort", serverPort);
        info.put("storageType", "Azure Blob Storage");
        return info;
    }
}
