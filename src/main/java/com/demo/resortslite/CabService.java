package com.demo.resortslite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service class for managing cab bookings linked to hotel reservations.
 * Handles business logic for checking availability, creating, retrieving, and updating cab bookings.
 */
@Service
public class CabService {

    private static final Logger logger = LoggerFactory.getLogger(CabService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CabApiClient cabApiClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Checks available cabs via third-party API.
     * Returns empty list if API is unavailable to allow booking to proceed.
     *
     * @param pickupLocation Starting point for the trip
     * @param pickupTime ISO-8601 formatted timestamp
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @return List of available cab options or empty list if unavailable
     */
    public List<Map<String, Object>> checkAvailability(String pickupLocation, String pickupTime,
                                                         int passengerCount, String tripType) {
        try {
            List<Map<String, Object>> cabs = cabApiClient.getAvailableCabs(
                pickupLocation, pickupTime, passengerCount, tripType);

            if (cabs == null || cabs.isEmpty()) {
                logger.warn("No cabs available from third-party API");
                return new ArrayList<>();
            }

            return cabs;
        } catch (Exception e) {
            logger.error("Failed to check cab availability: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Creates a new cab booking linked to a hotel reservation.
     * Validates inputs, calls third-party API, and stores in database.
     *
     * @param bookingId Hotel booking ID (foreign key)
     * @param cabType Type of cab (SEDAN, SUV, VAN)
     * @param pickupTime ISO-8601 formatted timestamp
     * @param pickupLocation Starting point
     * @param dropoffLocation Destination
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @param price Cab booking price
     * @return Map containing cab booking details
     */
    public Map<String, Object> createCabBooking(String bookingId, String cabType, String pickupTime,
                                                  String pickupLocation, String dropoffLocation,
                                                  int passengerCount, String tripType, double price) {
        // Validation
        validateCabBookingInputs(cabType, pickupTime, passengerCount, tripType);

        // Confirm with third-party API
        String apiReferenceId = cabApiClient.confirmCabBooking(
            cabType, pickupTime, pickupLocation, dropoffLocation, passengerCount, tripType);

        // Generate cab booking ID
        String cabId = "CAB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Insert into database using parameterized query
        String sql = "INSERT INTO cab_bookings (id, booking_id, cab_type, pickup_time, " +
                     "pickup_location, dropoff_location, passenger_count, price, api_reference_id, " +
                     "status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        jdbcTemplate.update(sql, cabId, bookingId, cabType, pickupTime, pickupLocation,
                           dropoffLocation, passengerCount, price, apiReferenceId, "CONFIRMED");

        // Build response map
        Map<String, Object> cabBooking = new HashMap<>();
        cabBooking.put("id", cabId);
        cabBooking.put("bookingId", bookingId);
        cabBooking.put("cabType", cabType);
        cabBooking.put("pickupTime", pickupTime);
        cabBooking.put("pickupLocation", pickupLocation);
        cabBooking.put("dropoffLocation", dropoffLocation);
        cabBooking.put("passengerCount", passengerCount);
        cabBooking.put("price", price);
        cabBooking.put("apiReferenceId", apiReferenceId);
        cabBooking.put("status", "CONFIRMED");

        // Cache in Redis with 1 hour TTL
        String cacheKey = "cab_booking:" + cabId;
        redisTemplate.opsForValue().set(cacheKey, cabBooking, 1, TimeUnit.HOURS);

        logger.info("Created cab booking: {} for hotel booking: {}", cabId, bookingId);
        return cabBooking;
    }

    /**
     * Retrieves cab booking details for a hotel reservation.
     * Returns empty map if no cab booking exists.
     *
     * @param bookingId Hotel booking ID
     * @return Map containing cab booking details or empty map
     */
    public Map<String, Object> getCabBookingByBookingId(String bookingId) {
        String sql = "SELECT * FROM cab_bookings WHERE booking_id = ?";

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, bookingId);
            logger.info("Retrieved cab booking for hotel booking: {}", bookingId);
            return result;
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No cab booking found for hotel booking: {}", bookingId);
            return new HashMap<>();
        } catch (Exception e) {
            logger.error("Error retrieving cab booking for hotel booking {}: {}", bookingId, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Updates an existing cab booking and synchronizes with third-party API.
     * Validates ownership and updates both local database and external API.
     *
     * @param cabId Cab booking ID
     * @param bookingId Hotel booking ID (for validation)
     * @param cabType Updated cab type (optional)
     * @param pickupTime Updated pickup time (optional)
     * @param passengerCount Updated passenger count (optional)
     * @param price Updated price
     * @return Map containing updated cab booking details
     */
    public Map<String, Object> updateCabBooking(String cabId, String bookingId, String cabType,
                                                 String pickupTime, Integer passengerCount, double price) {
        // Validate that cab booking exists and belongs to the hotel booking
        Map<String, Object> existing = getCabBookingById(cabId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Cab booking not found: " + cabId);
        }

        String existingBookingId = (String) existing.get("booking_id");
        if (!existingBookingId.equals(bookingId)) {
            throw new IllegalArgumentException("Cab booking does not belong to the specified hotel booking");
        }

        // Use existing values if new values not provided
        String updatedCabType = (cabType != null && !cabType.isEmpty()) ? cabType : (String) existing.get("cab_type");
        String updatedPickupTime = (pickupTime != null && !pickupTime.isEmpty()) ? pickupTime : (String) existing.get("pickup_time");
        int updatedPassengerCount = (passengerCount != null) ? passengerCount : (Integer) existing.get("passenger_count");

        // Validate updated inputs
        if (pickupTime != null && !pickupTime.isEmpty()) {
            validatePickupTime(pickupTime);
        }
        if (passengerCount != null) {
            validatePassengerCount(passengerCount);
        }

        // Update with third-party API
        String apiReferenceId = (String) existing.get("api_reference_id");
        cabApiClient.updateCabBooking(apiReferenceId, updatedCabType, updatedPickupTime, updatedPassengerCount);

        // Update database using parameterized query
        String sql = "UPDATE cab_bookings SET cab_type = ?, pickup_time = ?, passenger_count = ?, " +
                     "price = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND booking_id = ?";

        jdbcTemplate.update(sql, updatedCabType, updatedPickupTime, updatedPassengerCount,
                           price, "MODIFIED", cabId, bookingId);

        // Retrieve and return updated booking
        Map<String, Object> updated = getCabBookingById(cabId);

        // Update Redis cache
        String cacheKey = "cab_booking:" + cabId;
        redisTemplate.opsForValue().set(cacheKey, updated, 1, TimeUnit.HOURS);

        logger.info("Updated cab booking: {}", cabId);
        return updated;
    }

    /**
     * Retrieves cab booking by cab ID.
     *
     * @param cabId Cab booking ID
     * @return Map containing cab booking details or empty map
     */
    private Map<String, Object> getCabBookingById(String cabId) {
        String sql = "SELECT * FROM cab_bookings WHERE id = ?";

        try {
            return jdbcTemplate.queryForMap(sql, cabId);
        } catch (EmptyResultDataAccessException e) {
            return new HashMap<>();
        }
    }

    /**
     * Validates all required inputs for cab booking creation.
     *
     * @param cabType Type of cab
     * @param pickupTime ISO-8601 timestamp
     * @param passengerCount Number of passengers
     * @param tripType Trip direction
     */
    private void validateCabBookingInputs(String cabType, String pickupTime, int passengerCount, String tripType) {
        // Validate cab type
        if (cabType == null || cabType.isEmpty()) {
            throw new IllegalArgumentException("Cab type cannot be null or empty");
        }

        // Validate pickup time
        validatePickupTime(pickupTime);

        // Validate passenger count
        validatePassengerCount(passengerCount);

        // Validate trip type
        if (tripType == null || (!tripType.equals("AIRPORT_TO_HOTEL") && !tripType.equals("HOTEL_TO_AIRPORT"))) {
            throw new IllegalArgumentException("Trip type must be 'AIRPORT_TO_HOTEL' or 'HOTEL_TO_AIRPORT'");
        }
    }

    /**
     * Validates pickup time is in ISO-8601 format and in the future.
     *
     * @param pickupTime ISO-8601 formatted timestamp
     */
    private void validatePickupTime(String pickupTime) {
        try {
            Instant pickup = Instant.parse(pickupTime);
            Instant now = Instant.now();

            if (!pickup.isAfter(now)) {
                throw new IllegalArgumentException("Pickup time must be in the future");
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Pickup time must be in ISO-8601 format", e);
        }
    }

    /**
     * Validates passenger count is positive.
     *
     * @param passengerCount Number of passengers
     */
    private void validatePassengerCount(int passengerCount) {
        if (passengerCount <= 0) {
            throw new IllegalArgumentException("Passenger count must be a positive integer");
        }
    }
}
