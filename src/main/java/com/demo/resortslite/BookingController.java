package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

// cz-java-0069: HttpSession is now backed by Spring Session + Amazon ElastiCache (Redis)
// via @EnableRedisHttpSession in SessionConfig. The javax.servlet.http.HttpSession interface
// is preserved — Spring Session transparently replaces the in-memory store with Redis,
// enabling container-safe, horizontally-scalable session management on EKS.
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Value("${REPORT_BASE_PATH:/var/reports}")
    private String reportBasePath;

    // cz-java-0070: Replaced local in-process HashMap cache with Amazon ElastiCache (Redis)
    // via RedisTemplate. This ensures the booking cache is shared across all EKS pod replicas
    // and survives container restarts, enabling safe horizontal scaling.
    // Connection details are injected via environment variables REDIS_HOST and REDIS_PORT
    // (set as Kubernetes ConfigMap/Secret entries), with IRSA-secured access on EKS.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String BOOKING_CACHE_PREFIX = "bookingCache:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            // cz-java-0069 (line 31-32): HttpSession is now Redis-backed via Spring Session
            // (SessionConfig @EnableRedisHttpSession + ElastiCache env vars REDIS_HOST/REDIS_PORT).
            // Sessions survive container restarts and are shared across all EKS pod replicas.
            // session.setAttribute calls below write to ElastiCache (Redis), not local JVM memory.
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cz-java-0069 (Line 31): Externalized to Amazon ElastiCache (Redis) via Spring Session
        session.setAttribute("lastBooking", booking);
        // cz-java-0069 (Line 32): Externalized to Amazon ElastiCache (Redis) via Spring Session
        session.setAttribute("guestName", guestName);

        // cz-java-0070: Store booking in Amazon ElastiCache (Redis) instead of local HashMap
        redisTemplate.opsForValue().set(BOOKING_CACHE_PREFIX + booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            // cz-java-0069: HttpSession is now Redis-backed via Spring Session
            // (SessionConfig @EnableRedisHttpSession + ElastiCache env vars).
            // Session data is retrieved from ElastiCache, not local JVM memory.
            HttpSession session) {

       
        String lastGuest = (String) session.getAttribute("guestName"); 

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
       
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available"; 

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
       
        String reportPath = reportBasePath + "/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
