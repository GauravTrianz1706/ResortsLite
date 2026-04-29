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

    @Value("${azure.storage.account-name:}")
    private String storageAccountName;

    @Value("${azure.storage.account-key:}")
    private String storageAccountKey;

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    @Value("${azure.storage.container-name:resort-reports}")
    private String containerName;

    @Value("${server.port:8080}")
    private int serverPort;

    private BlobContainerClient getBlobContainerClient() {
        // Use Azure Blob Storage instead of local file system
        String connectionString = String.format(
            "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
            storageAccountName, storageAccountKey
        );
        
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient();
        
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }
        
        return containerClient;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to Azure Blob Storage instead of writing to local file system
            BlobContainerClient containerClient = getBlobContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            
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

    public String buildReportDownloadUrl(String reportName) {
        try {
            BlobContainerClient containerClient = getBlobContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(reportName);
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            return "Error generating download URL: " + e.getMessage();
        }
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageAccountName", storageAccountName);
        info.put("containerName", containerName);
        info.put("serverPort", serverPort);
        info.put("storageType", "Azure Blob Storage");
        
        return info;
    }
}
