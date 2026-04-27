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

    @Value("${app.report.use-blob-storage:true}")
    private boolean useBlobStorage;

    /**
     * Generates a monthly report and stores it in Azure Blob Storage.
     * Replaces hard-coded file paths with cloud-native storage.
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            if (useBlobStorage && storageAccountName != null && !storageAccountName.isEmpty()) {
                // Upload to Azure Blob Storage
                String blobUrl = uploadToAzureBlob(fileName, csvContent.toString());
                result.put("status", "generated");
                result.put("path", blobUrl);
                result.put("storage", "azure-blob");
            } else {
                // Fallback to local storage (for development/testing only)
                result.put("status", "generated");
                result.put("path", "/tmp/reports/" + fileName);
                result.put("storage", "local-fallback");
                result.put("warning", "Using local storage - configure Azure Blob Storage for production");
            }

            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Uploads content to Azure Blob Storage.
     */
    private String uploadToAzureBlob(String blobName, String content) throws IOException {
        try {
            // Create BlobServiceClient
            String connectionString = String.format(
                "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
                storageAccountName, storageAccountKey
            );

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

            // Get or create container
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }

            // Upload blob
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(contentBytes);
            blobClient.upload(inputStream, contentBytes.length, true);

            // Return blob URL
            return blobClient.getBlobUrl();

        } catch (Exception e) {
            throw new IOException("Failed to upload to Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a report download URL using Azure Blob Storage.
     * Replaces hard-coded file paths with cloud storage URLs.
     */
    public String buildReportDownloadUrl(String reportName) {
        if (useBlobStorage && storageAccountName != null && !storageAccountName.isEmpty()) {
            return String.format("https://%s.blob.core.windows.net/%s/%s",
                storageAccountName, containerName, reportName);
        } else {
            return "/api/reports/download/" + reportName;
        }
    }

    /**
     * Returns system information with externalized configuration.
     * Replaces hard-coded paths with environment-based configuration.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        
        if (useBlobStorage) {
            info.put("storageType", "azure-blob");
            info.put("storageAccount", storageAccountName);
            info.put("containerName", containerName);
        } else {
            info.put("storageType", "local-fallback");
            info.put("reportPath", "/tmp/reports");
        }
        
        info.put("serverPort", serverPort);
        
        return info;
    }
}
