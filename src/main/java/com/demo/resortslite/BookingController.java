package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
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
    private String inventoryServiceUrl;

    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    // Cache TTL in seconds (1 hour)
    private static final long CACHE_TTL = 3600;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store session data in Redis (Spring Session handles this automatically)
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // Store booking in Redis cache with TTL
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Retrieve session data from Redis (Spring Session handles this automatically)
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized inventory service URL from configuration
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Use S3 for report storage instead of local file system
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Url = "s3://" + s3BucketName + "/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("s3Bucket", s3BucketName);
        response.put("s3Key", s3Key);
        response.put("s3Url", s3Url);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    @GetMapping("/cached/{bookingId}")
    @Cacheable(value = "bookings", key = "#bookingId")
    public Map<String, Object> getCachedBooking(@PathVariable String bookingId) {
        // This method uses Spring Cache with Redis backend and automatic TTL
        return bookingService.getBookingById(bookingId);
    }
}
