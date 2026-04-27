package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // FIXED cr-java-0067: Replace unbounded in-memory cache with Redis
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // FIXED cr-java-0071: Externalize inventory service URL to environment variable
    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    // Cache TTL in seconds (1 hour)
    private static final long CACHE_TTL = 3600;

    /**
     * FIXED cr-java-0065: HTTP session state migrated to Redis via Spring Session
     * Spring Session automatically stores session data in Redis (ElastiCache)
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session data now stored in Redis (configured via Spring Session)
        // Spring Session automatically handles Redis storage when spring.session.store-type=redis
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cr-java-0067: Store in Redis with TTL instead of unbounded in-memory cache
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * FIXED cr-java-0065: Session retrieval now uses Redis-backed session
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session data retrieved from Redis via Spring Session
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * FIXED cr-java-0071: Use externalized inventory service URL from Parameter Store
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Use environment-based configuration instead of hard-coded URL
        String inventoryUrl = inventoryServiceUrl + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * FIXED cr-java-0061: Report path now uses S3 instead of local file system
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // FIXED cr-java-0061: Reports are now stored in S3, not local file system
        String s3Key = "reports/" + month + "_bookings.pdf";
        String s3Bucket = System.getenv("S3_BUCKET_NAME");
        if (s3Bucket == null || s3Bucket.isEmpty()) {
            s3Bucket = "resortslite-reports";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("storageType", "S3");
        response.put("s3Bucket", s3Bucket);
        response.put("s3Key", s3Key);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
