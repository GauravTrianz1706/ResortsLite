package com.demo.resortslite;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for Google Cloud Storage operations.
 * Replaces local file system access with cloud storage.
 */
@Service
public class GcsStorageService {

    @Autowired(required = false)
    private Storage storage;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    /**
     * Upload content to GCS bucket
     */
    public String uploadFile(String fileName, String content) {
        if (storage == null) {
            // Fallback for local development without GCS credentials
            return "local://" + fileName;
        }

        try {
            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/plain")
                    .build();
            
            storage.create(blobInfo, content.getBytes(StandardCharsets.UTF_8));
            
            return String.format("gs://%s/%s", bucketName, fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to GCS: " + e.getMessage(), e);
        }
    }

    /**
     * Download content from GCS bucket
     */
    public String downloadFile(String fileName) {
        if (storage == null) {
            return "File content (local mode)";
        }

        try {
            Blob blob = storage.get(BlobId.of(bucketName, fileName));
            if (blob == null) {
                throw new RuntimeException("File not found: " + fileName);
            }
            
            return new String(blob.getContent(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from GCS: " + e.getMessage(), e);
        }
    }

    /**
     * Get public URL for a file in GCS
     */
    public String getFileUrl(String fileName) {
        if (storage == null) {
            return "local://" + fileName;
        }
        return String.format("gs://%s/%s", bucketName, fileName);
    }

    /**
     * Check if file exists in GCS
     */
    public boolean fileExists(String fileName) {
        if (storage == null) {
            return false;
        }
        
        try {
            Blob blob = storage.get(BlobId.of(bucketName, fileName));
            return blob != null && blob.exists();
        } catch (Exception e) {
            return false;
        }
    }
}
