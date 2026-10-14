package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * cr-java-0065: HTTP session state migrated to Amazon ElastiCache for Redis
 * via Spring Session. All HttpSession.setAttribute / getAttribute calls are
 * now backed by a distributed Redis store, enabling stateless application
 * instances and horizontal scaling without server affinity.
 *
 * cr-java-0067: Unbounded in-memory bookingCache (HashMap) replaced with
 * Amazon ElastiCache for Redis via Spring Data RedisTemplate. Each cache
 * entry is stored with a configurable TTL (default: 60 minutes) to prevent
 * indefinite memory growth, stale data, and cross-instance inconsistencies.
 *
 * Required runtime environment variables (injected via ECS task definition,
 * EKS secret, or Elastic Beanstalk .ebextensions):
 *   REDIS_HOST          – ElastiCache primary endpoint  (default: localhost)
 *   REDIS_PORT          – ElastiCache port              (default: 6379)
 *   BOOKING_CACHE_TTL   – Cache entry TTL in minutes    (default: 60)
 *
 * Spring Session automatically intercepts all HttpSession operations and
 * delegates them to the configured Redis store, so no changes to the
 * session.setAttribute / session.getAttribute call-sites are required.
 */
@EnableRedisHttpSession
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * cr-java-0067: RedisTemplate replaces the static in-memory HashMap cache.
     * Entries are stored in Amazon ElastiCache for Redis with a TTL, ensuring
     * controlled expiration, consistent data across all application instances,
     * and centralised cache management.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * cr-java-0067: TTL for booking cache entries, resolved from the environment
     * variable BOOKING_CACHE_TTL (minutes). Defaults to 60 minutes.
     * Configure via AWS Systems Manager Parameter Store:
     *   Parameter name : /resortslite/booking/cache-ttl-minutes
     *   Parameter type : String
     *   Example value  : 60
     */
    @Value("${app.booking.cache.ttl-minutes:${BOOKING_CACHE_TTL:60}}")
    private long bookingCacheTtlMinutes;

    private static final String BOOKING_CACHE_KEY_PREFIX = "booking:";

    /**
     * cr-java-0071: Inventory service URL externalised from hard-coded value.
     * Value is resolved from the environment variable INVENTORY_SERVICE_URL,
     * which should be backed by AWS Systems Manager Parameter Store
     * (e.g. /resortslite/inventory/service-url) and injected at runtime via
     * ECS task definition, EKS secret, or AWS AppConfig.
     */
    @Value("${app.inventory.service.url:${INVENTORY_SERVICE_URL:http://inventory-service.internal:8081/rooms/available}}")
    private String inventoryServiceUrl;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cr-java-0065: Session attributes are now stored in Amazon ElastiCache for Redis
        // via Spring Session. The HttpSession interface is unchanged; Spring Session
        // transparently serialises these attributes to the distributed Redis store,
        // eliminating server affinity and enabling safe horizontal scaling.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // cr-java-0067: Store booking in Amazon ElastiCache for Redis with TTL.
        // Replaces the unbounded static HashMap (bookingCache) that caused indefinite
        // memory growth and stale data across instances. Each entry expires automatically
        // after bookingCacheTtlMinutes, preventing memory leaks and ensuring freshness.
        String cacheKey = BOOKING_CACHE_KEY_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // cr-java-0065: Session attribute retrieved from Amazon ElastiCache for Redis
        // via Spring Session — any application instance can serve this request because
        // session data is centralised in the distributed Redis store.
        String lastGuest = (String) session.getAttribute("guestName");

        // cr-java-0067: Retrieve booking from Amazon ElastiCache for Redis.
        // Falls back to bookingService if the entry has expired or is not cached.
        String cacheKey = BOOKING_CACHE_KEY_PREFIX + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", cachedBooking != null ? cachedBooking : bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
       
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; 

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
