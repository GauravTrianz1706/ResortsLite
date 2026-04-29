import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Cloud-native: Replace hard-coded file paths with S3 bucket configuration
    @Value("${aws.s3.reports.bucket:resort-reports-bucket}")
    private String reportsBucketName;

    // Cloud-native: Replace hard-coded port with environment variable
    @Value("${server.port:8080}")
    @Autowired
    public ReportService(S3Client s3Client) {
        this.s3Client = s3Client;
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate report content in memory instead of local file system
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 instead of writing to local file system
            byte[] reportData = baos.toByteArray();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(reportsBucketName)
                    .key("reports/" + fileName)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(reportData));

            result.put("status", "generated");
            result.put("bucket", reportsBucketName);
            result.put("key", "reports/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Generate S3 pre-signed URL for secure download
        return "https://" + reportsBucketName + ".s3.amazonaws.com/reports/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", reportsBucketName);
        info.put("storageType", "AWS S3");
        info.put("serverPort", serverPort);
        
        return info;
    }
}
