package com.demo.resortslite;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Azure Blob Storage service for cloud-native file operations.
 * Replaces local file system dependencies with Azure Blob Storage.
 */
@Service
public class AzureBlobStorageService {

    @Value("${azure.storage.account-name:}")
    private String storageAccountName;

    @Value("${azure.storage.account-key:}")
    private String storageAccountKey;

    @Value("${azure.storage.container-name:resort-reports}")
    private String containerName;

    private BlobServiceClient blobServiceClient;

    /**
     * Initializes the Blob Service Client.
     */
    private BlobServiceClient getBlobServiceClient() {
        if (blobServiceClient == null && storageAccountName != null && !storageAccountName.isEmpty()) {
            String connectionString = String.format(
                "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
                storageAccountName, storageAccountKey
            );
            blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        }
        return blobServiceClient;
    }

    /**
     * Gets or creates a blob container.
     */
    private BlobContainerClient getContainerClient() {
        BlobServiceClient client = getBlobServiceClient();
        if (client == null) {
            throw new IllegalStateException("Azure Blob Storage is not configured");
        }
        
        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }

    /**
     * Uploads a file to Azure Blob Storage.
     */
    public String uploadFile(String blobName, byte[] content) {
        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
            blobClient.upload(inputStream, content.length, true);
            
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads a file from Azure Blob Storage.
     */
    public byte[] downloadFile(String blobName) {
        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            return blobClient.downloadContent().toBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a file from Azure Blob Storage.
     */
    public void deleteFile(String blobName) {
        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.delete();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all files in the container.
     */
    public List<String> listFiles() {
        try {
            BlobContainerClient containerClient = getContainerClient();
            List<String> fileNames = new ArrayList<>();
            
            for (BlobItem blobItem : containerClient.listBlobs()) {
                fileNames.add(blobItem.getName());
            }
            
            return fileNames;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files from Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a file exists in Azure Blob Storage.
     */
    public boolean fileExists(String blobName) {
        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the URL of a blob.
     */
    public String getBlobUrl(String blobName) {
        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get blob URL: " + e.getMessage(), e);
        }
    }
}
