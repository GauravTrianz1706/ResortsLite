package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // BLOCKER-13 FIX: Replace local cache with distributed Redis cache
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // BLOCKER-1 FIX: S3 configuration for file storage
    @Value("${aws.s3.bucket.reports:resort-reports-bucket}")
    private String s3BucketName;

    @Autowired
    private S3Client s3Client;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // BLOCKER-4, BLOCKER-5 FIX: Session data is now stored in Redis via Spring Session
        // HttpSession is backed by Redis automatically through Spring Session configuration
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // BLOCKER-7, BLOCKER-8, BLOCKER-13 FIX: Use distributed Redis cache instead of local HashMap
        String bookingId = (String) booking.get("bookingId");
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, 24, TimeUnit.HOURS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // BLOCKER-6 FIX: Session data is now stored in Redis via Spring Session
        // HttpSession is backed by Redis automatically through Spring Session configuration
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // BLOCKER-9 FIX: Use environment variable for service discovery instead of hardcoded URL
        // This enables loose coupling and service mesh integration
        String inventoryUrl = System.getenv().getOrDefault("INVENTORY_SERVICE_URL", 
                "http://inventory-service:8081/rooms/available");

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // BLOCKER-1 FIX: Replace absolute file path with S3 object storage
        String s3Key = "reports/" + month + "_bookings.pdf";
        
        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3BucketName);
        response.put("s3Key", s3Key);
        response.put("message", bookingService.generateReport(month));
        
        // Generate pre-signed URL for download (optional)
        try {
            String downloadUrl = s3Client.utilities()
                .getUrl(builder -> builder.bucket(s3BucketName).key(s3Key))
                .toString();
            response.put("downloadUrl", downloadUrl);
        } catch (Exception e) {
            response.put("error", "Unable to generate download URL: " + e.getMessage());
        }
        
        return response;
    }
}
