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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${azure.storage.connection-string}")
    private String azureStorageConnectionString;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${app.server.port}")
    private String serverPort;

    /**
     * Generates monthly report and stores it in Azure Blob Storage
     * Fixes: cr-java-0061, cr-java-0062, cr-java-0063, cr-java-0077
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        Map<String, Object> result = new HashMap<>();

        try {
            // Create blob service client
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(azureStorageConnectionString)
                    .buildClient();

            // Get container client
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            
            // Create container if it doesn't exist
            if (!containerClient.exists()) {
                containerClient.create();
            }

            // Get blob client
            BlobClient blobClient = containerClient.getBlobClient(fileName);

            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to Azure Blob Storage
            byte[] data = csvContent.toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            blobClient.upload(inputStream, data.length, true);

            result.put("status", "generated");
            result.put("blobName", fileName);
            result.put("containerName", containerName);
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds report download URL from Azure Blob Storage
     * Fixes: cr-java-0071
     */
    public String buildReportDownloadUrl(String reportName) {
        try {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(azureStorageConnectionString)
                    .buildClient();

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(reportName);

            return blobClient.getBlobUrl();
        } catch (Exception e) {
            return "Error generating URL: " + e.getMessage();
        }
    }

    /**
     * Returns system information with externalized configuration
     * Fixes: cr-java-0061, cr-java-0077
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "Azure Blob Storage");
        info.put("containerName", containerName);
        info.put("serverPort", serverPort);
        return info;
    }
}
