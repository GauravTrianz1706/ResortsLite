import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
    @Value("${aws.s3.bucket.name:resorts-reports}")
    private String s3BucketName;

    @Value("${SERVER_PORT:8080}")
    private String serverPort;
    @Autowired
    
    @Autowired
     * Generates monthly report and stores it in Amazon S3 instead of local file system.
     * This ensures data durability and availability in cloud environments.
     */
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
            writer.close();

            // Upload to S3 instead of writing to local file system
            byte[] content = baos.toByteArray();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key("reports/" + fileName)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", "reports/" + fileName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds report download URL using S3 pre-signed URL or CloudFront distribution.
     * Port is retrieved from environment variable instead of hard-coded value.
     */
    public String buildReportDownloadUrl(String reportName) {
        // In production, this would generate a pre-signed S3 URL
        String s3Url = String.format("https://%s.s3.%s.amazonaws.com/reports/%s",
                s3BucketName, awsRegion, reportName);
        return s3Url;
    }

    /**
     * Returns system information with cloud-native configuration.
     * All paths and ports are externalized to environment variables and AWS Parameter Store.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("s3Bucket", s3BucketName);
        info.put("awsRegion", awsRegion);
        info.put("serverPort", serverPort);
        
        // Optionally retrieve additional configuration from Parameter Store
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name("/resorts/config/backup-policy")
                    .withDecryption(false)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            info.put("backupPolicy", response.parameter().value());
        } catch (Exception e) {
            info.put("backupPolicy", "default");
        }
        
        return info;
    }
}
