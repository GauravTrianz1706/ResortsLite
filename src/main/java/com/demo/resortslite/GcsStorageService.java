package com.demo.resortslite;

import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for Google Cloud Storage operations.
 * Replaces local filesystem access with cloud-native storage.
 */
@Service
public class GcsStorageService {

    @Value("${gcs.bucket.name}")
    private String bucketName;

    @Value("${gcs.project.id:}")
    private String projectId;

    private Storage storage;

    public GcsStorageService() {
        // Initialize GCS client - uses Application Default Credentials in GKE
        try {
            this.storage = StorageOptions.getDefaultInstance().getService();
        } catch (Exception e) {
            // Fallback for local development
            this.storage = null;
        }
    }

    /**
     * Upload content to GCS bucket
     * @param fileName Name of the file
     * @param content Content to upload
     * @return GCS object path
     */
    public String uploadFile(String fileName, String content) throws IOException {
        if (storage == null) {
            throw new IOException("GCS Storage not initialized. Set GOOGLE_APPLICATION_CREDENTIALS environment variable.");
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("text/plain")
                .build();

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.create(blobInfo, bytes);

        return String.format("gs://%s/%s", bucketName, fileName);
    }

    /**
     * Get signed URL for file download
     * @param fileName Name of the file
     * @param durationMinutes URL validity duration
     * @return Signed URL
     */
    public String getSignedUrl(String fileName, int durationMinutes) throws IOException {
        if (storage == null) {
            throw new IOException("GCS Storage not initialized.");
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        // Generate signed URL valid for specified duration
        java.util.concurrent.TimeUnit timeUnit = java.util.concurrent.TimeUnit.MINUTES;
        return storage.signUrl(blobInfo, durationMinutes, timeUnit).toString();
    }

    /**
     * Check if file exists in GCS
     * @param fileName Name of the file
     * @return true if exists
     */
    public boolean fileExists(String fileName) {
        if (storage == null) {
            return false;
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }
}
