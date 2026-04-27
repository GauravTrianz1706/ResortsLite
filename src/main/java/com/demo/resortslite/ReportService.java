package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    @Value("${azure.storage.container-name:resort-reports}")
    private String containerName;

    @Value("${app.report.server-port:8080}")
    private int serverPort;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        // Initialize Azure Blob Storage client using DefaultAzureCredential
        // This supports managed identity in Azure environments
        if (blobEndpoint != null && !blobEndpoint.isEmpty()) {
            this.blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(blobEndpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();

            // Get or create container
            this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
        }
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

            // Upload to Azure Blob Storage instead of local file system
            if (containerClient != null) {
                BlobClient blobClient = containerClient.getBlobClient(fileName);
                byte[] data = csvContent.toString().getBytes(StandardCharsets.UTF_8);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                blobClient.upload(inputStream, data.length, true);

                result.put("status", "generated");
                result.put("blobName", fileName);
                result.put("blobUrl", blobClient.getBlobUrl());
                result.put("storageType", "Azure Blob Storage");
            } else {
                // Fallback for local development without Azure configuration
                result.put("status", "generated");
                result.put("fileName", fileName);
                result.put("storageType", "in-memory (Azure Blob Storage not configured)");
                result.put("content", csvContent.toString());
            }

            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        if (containerClient != null) {
            BlobClient blobClient = containerClient.getBlobClient(reportName);
            return blobClient.getBlobUrl();
        }
        return "Azure Blob Storage not configured";
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "Azure Blob Storage");
        info.put("containerName", containerName);
        info.put("blobEndpoint", blobEndpoint);
        info.put("serverPort", serverPort);
        return info;
    }
}
