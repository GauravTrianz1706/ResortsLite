package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint}")
    private String inventoryEndpoint;

    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.ssm.parameter.prefix}")
    private String ssmParameterPrefix;

    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        // Initialize AWS Systems Manager client for parameter store
        this.ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis instead of HTTP session
        if (sessionId != null && !sessionId.isEmpty()) {
            String sessionKey = "session:" + sessionId + ":lastBooking";
            String guestKey = "session:" + sessionId + ":guestName";
            
            // Store with TTL of 30 minutes
            redisTemplate.opsForValue().set(sessionKey, booking, 30, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(guestKey, guestName, 30, TimeUnit.MINUTES);
        }

        // Store in distributed cache with TTL instead of in-memory cache
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, 1, TimeUnit.HOURS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookingStatus", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve session data from Redis instead of HTTP session
        String lastGuest = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            String guestKey = "session:" + sessionId + ":guestName";
            Object guestObj = redisTemplate.opsForValue().get(guestKey);
            if (guestObj != null) {
                lastGuest = guestObj.toString();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory endpoint from Parameter Store or environment variable
        String inventoryUrl = getParameterFromStore("inventory.endpoint", inventoryEndpoint);

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use S3 path instead of local file system
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", 
                s3BucketName, awsRegion, s3Key);

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3BucketName);
        response.put("s3Key", s3Key);
        response.put("downloadUrl", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    @CacheEvict(value = "bookingStatus", key = "#bookingId")
    @DeleteMapping("/{bookingId}")
    public Map<String, Object> cancelBooking(@PathVariable String bookingId) {
        // Clear from Redis cache
        String cacheKey = "booking:" + bookingId;
        redisTemplate.delete(cacheKey);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "cancelled");
        response.put("bookingId", bookingId);
        return response;
    }

    private String getParameterFromStore(String parameterName, String defaultValue) {
        try {
            String fullParameterName = ssmParameterPrefix + "/" + parameterName;
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(fullParameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Return default value if parameter not found
            System.err.println("Failed to retrieve parameter: " + parameterName + ", using default: " + defaultValue);
            return defaultValue;
        }
    }
}
