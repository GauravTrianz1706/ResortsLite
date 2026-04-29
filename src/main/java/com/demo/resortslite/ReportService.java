package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    @Value("${gcp.storage.project-id:}")
    private String projectId;

    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired(required = false)
    private Storage storage;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n".getBytes(StandardCharsets.UTF_8));

            byte[] content = outputStream.toByteArray();

            // Upload to Google Cloud Storage
            if (storage != null && bucketName != null && !bucketName.isEmpty()) {
                BlobId blobId = BlobId.of(bucketName, "reports/" + fileName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("text/csv")
                        .build();
                storage.create(blobInfo, content);

                result.put("status", "generated");
                result.put("path", "gs://" + bucketName + "/reports/" + fileName);
                result.put("fileName", fileName);
                result.put("serverPort", serverPort);
            } else {
                result.put("status", "error");
                result.put("message", "GCS not configured. Set GCS_BUCKET_NAME environment variable.");
            }

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate signed URL or public URL for GCS object
        if (bucketName != null && !bucketName.isEmpty()) {
            return "gs://" + bucketName + "/reports/" + reportName;
        }
        return null;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "Google Cloud Storage");
        info.put("bucketName", bucketName);
        info.put("serverPort", serverPort);
        return info;
    }
}
