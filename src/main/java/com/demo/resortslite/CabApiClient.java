package com.demo.resortslite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Client interface and placeholder implementation for third-party cab booking API.
 * In production, this would integrate with an actual transportation service provider.
 */
@Component
public class CabApiClient {

    private static final Logger logger = LoggerFactory.getLogger(CabApiClient.class);

    /**
     * Retrieves available cab options from third-party API based on trip parameters.
     * Returns mock data for demonstration - in production would call external REST API.
     *
     * @param pickupLocation Starting point for the trip
     * @param pickupTime ISO-8601 formatted timestamp
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @return List of available cab options with type, capacity, price, and API reference
     */
    public List<Map<String, Object>> getAvailableCabs(String pickupLocation, String pickupTime,
                                                        int passengerCount, String tripType) {
        try {
            logger.info("Fetching available cabs: location={}, time={}, passengers={}, tripType={}",
                       pickupLocation, pickupTime, passengerCount, tripType);

            // Placeholder implementation - returns mock cab options
            // In production, this would use RestTemplate or WebClient to call external API
            List<Map<String, Object>> cabOptions = new ArrayList<>();

            // Option 1: Sedan
            if (passengerCount <= 4) {
                Map<String, Object> sedan = new HashMap<>();
                sedan.put("cabType", "SEDAN");
                sedan.put("capacity", 4);
                sedan.put("estimatedPrice", 45.00);
                sedan.put("apiReferenceId", "CAB-API-" + UUID.randomUUID().toString().substring(0, 8));
                cabOptions.add(sedan);
            }

            // Option 2: SUV
            if (passengerCount <= 6) {
                Map<String, Object> suv = new HashMap<>();
                suv.put("cabType", "SUV");
                suv.put("capacity", 6);
                suv.put("estimatedPrice", 65.00);
                suv.put("apiReferenceId", "CAB-API-" + UUID.randomUUID().toString().substring(0, 8));
                cabOptions.add(suv);
            }

            // Option 3: Van (for larger groups)
            if (passengerCount > 4) {
                Map<String, Object> van = new HashMap<>();
                van.put("cabType", "VAN");
                van.put("capacity", 8);
                van.put("estimatedPrice", 85.00);
                van.put("apiReferenceId", "CAB-API-" + UUID.randomUUID().toString().substring(0, 8));
                cabOptions.add(van);
            }

            logger.info("Retrieved {} cab options", cabOptions.size());
            return cabOptions;

        } catch (Exception e) {
            logger.error("Error fetching available cabs from third-party API: {}", e.getMessage(), e);
            // Return empty list on failure to allow booking to proceed without cab option
            return new ArrayList<>();
        }
    }

    /**
     * Confirms a cab booking with the third-party API.
     * Returns an API reference ID to track the booking with the external provider.
     *
     * @param cabType Type of cab (SEDAN, SUV, VAN)
     * @param pickupTime ISO-8601 formatted timestamp
     * @param pickupLocation Starting point
     * @param dropoffLocation Destination
     * @param passengerCount Number of passengers
     * @param tripType AIRPORT_TO_HOTEL or HOTEL_TO_AIRPORT
     * @return API reference ID from third-party service
     */
    public String confirmCabBooking(String cabType, String pickupTime, String pickupLocation,
                                     String dropoffLocation, int passengerCount, String tripType) {
        try {
            logger.info("Confirming cab booking: type={}, time={}, from={}, to={}, passengers={}, tripType={}",
                       cabType, pickupTime, pickupLocation, dropoffLocation, passengerCount, tripType);

            // Placeholder implementation - returns mock API reference ID
            // In production, this would POST to external API and return their confirmation ID
            String apiReferenceId = "API-REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

            logger.info("Cab booking confirmed with API reference: {}", apiReferenceId);
            return apiReferenceId;

        } catch (Exception e) {
            logger.error("Error confirming cab booking with third-party API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to confirm cab booking with third-party provider. Please try again later.", e);
        }
    }

    /**
     * Updates an existing cab booking with the third-party API.
     * Synchronizes local changes with the external provider.
     *
     * @param apiReferenceId Original API reference from confirmCabBooking
     * @param cabType Updated cab type (if changed)
     * @param pickupTime Updated pickup time (if changed)
     * @param passengerCount Updated passenger count (if changed)
     * @return Updated confirmation message from API
     */
    public String updateCabBooking(String apiReferenceId, String cabType, String pickupTime,
                                    int passengerCount) {
        try {
            logger.info("Updating cab booking: apiRef={}, type={}, time={}, passengers={}",
                       apiReferenceId, cabType, pickupTime, passengerCount);

            // Placeholder implementation - returns mock success message
            // In production, this would PUT/PATCH to external API
            String confirmation = "Booking updated successfully. API Reference: " + apiReferenceId;

            logger.info("Cab booking updated: {}", confirmation);
            return confirmation;

        } catch (Exception e) {
            logger.error("Error updating cab booking with third-party API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update cab booking with third-party provider. Please try again later.", e);
        }
    }
}
