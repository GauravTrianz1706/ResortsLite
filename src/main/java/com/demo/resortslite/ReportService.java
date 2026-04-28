package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final Storage storage;

    @Value("${gcp.storage.bucket.reports:resort-reports-bucket}")
    private String reportBucketName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired
    public ReportService(Storage storage) {
        this.storage = storage;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Create CSV content in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            
            byte[] content = baos.toByteArray();
            writer.close();

            // Upload to Google Cloud Storage
            BlobId blobId = BlobId.of(reportBucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            storage.create(blobInfo, content);

            result.put("status", "generated");
            result.put("bucket", reportBucketName);
            result.put("fileName", fileName);
            result.put("gcsPath", "gs://" + reportBucketName + "/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate signed URL or public URL for GCS object
        return "https://storage.googleapis.com/" + reportBucketName + "/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", reportBucketName);
        info.put("storageProvider", "Google Cloud Storage");
        info.put("serverPort", serverPort);
        
        return info;
    }
}
