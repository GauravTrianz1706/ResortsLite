package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CabService.
 * Tests business logic for cab booking operations.
 */
public class CabServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CabApiClient cabApiClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CabService cabService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testCheckAvailability_Success() {
        // Arrange
        List<Map<String, Object>> mockCabs = new ArrayList<>();
        Map<String, Object> sedan = new HashMap<>();
        sedan.put("cabType", "SEDAN");
        sedan.put("capacity", 4);
        sedan.put("estimatedPrice", 45.00);
        mockCabs.add(sedan);

        when(cabApiClient.getAvailableCabs(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn(mockCabs);

        // Act
        List<Map<String, Object>> result = cabService.checkAvailability(
            "Airport", "2026-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SEDAN", result.get(0).get("cabType"));
    }

    @Test
    public void testCheckAvailability_ApiFailure_ReturnsEmptyList() {
        // Arrange
        when(cabApiClient.getAvailableCabs(anyString(), anyString(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("API unavailable"));

        // Act
        List<Map<String, Object>> result = cabService.checkAvailability(
            "Airport", "2026-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCreateCabBooking_Success() {
        // Arrange
        String apiRefId = "API-REF-12345";
        when(cabApiClient.confirmCabBooking(anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyString())).thenReturn(apiRefId);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        // Act
        Map<String, Object> result = cabService.createCabBooking(
            "BK-12345", "SEDAN", "2026-12-01T10:00:00Z", "Airport", "Hotel", 2, "AIRPORT_TO_HOTEL", 45.00);

        // Assert
        assertNotNull(result);
        assertEquals("BK-12345", result.get("bookingId"));
        assertEquals("SEDAN", result.get("cabType"));
        assertEquals("CONFIRMED", result.get("status"));
        assertEquals(apiRefId, result.get("apiReferenceId"));
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(valueOperations, times(1)).set(anyString(), any(), anyLong(), any());
    }

    @Test
    public void testCreateCabBooking_InvalidPassengerCount_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cabService.createCabBooking(
                "BK-12345", "SEDAN", "2026-12-01T10:00:00Z", "Airport", "Hotel",
                0, "AIRPORT_TO_HOTEL", 45.00);
        });
    }

    @Test
    public void testCreateCabBooking_InvalidTripType_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cabService.createCabBooking(
                "BK-12345", "SEDAN", "2026-12-01T10:00:00Z", "Airport", "Hotel",
                2, "INVALID_TYPE", 45.00);
        });
    }

    @Test
    public void testCreateCabBooking_PastPickupTime_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cabService.createCabBooking(
                "BK-12345", "SEDAN", "2020-12-01T10:00:00Z", "Airport", "Hotel",
                2, "AIRPORT_TO_HOTEL", 45.00);
        });
    }

    @Test
    public void testGetCabBookingByBookingId_Success() {
        // Arrange
        Map<String, Object> mockCabBooking = new HashMap<>();
        mockCabBooking.put("id", "CAB-12345");
        mockCabBooking.put("booking_id", "BK-12345");
        mockCabBooking.put("cab_type", "SEDAN");
        mockCabBooking.put("status", "CONFIRMED");

        when(jdbcTemplate.queryForMap(anyString(), anyString())).thenReturn(mockCabBooking);

        // Act
        Map<String, Object> result = cabService.getCabBookingByBookingId("BK-12345");

        // Assert
        assertNotNull(result);
        assertEquals("CAB-12345", result.get("id"));
        assertEquals("BK-12345", result.get("booking_id"));
    }

    @Test
    public void testGetCabBookingByBookingId_NotFound_ReturnsEmptyMap() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString(), anyString()))
            .thenThrow(new EmptyResultDataAccessException(1));

        // Act
        Map<String, Object> result = cabService.getCabBookingByBookingId("BK-NONEXISTENT");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateCabBooking_Success() {
        // Arrange
        Map<String, Object> existingBooking = new HashMap<>();
        existingBooking.put("id", "CAB-12345");
        existingBooking.put("booking_id", "BK-12345");
        existingBooking.put("cab_type", "SEDAN");
        existingBooking.put("pickup_time", "2026-12-01T10:00:00Z");
        existingBooking.put("passenger_count", 2);
        existingBooking.put("api_reference_id", "API-REF-12345");

        Map<String, Object> updatedBooking = new HashMap<>(existingBooking);
        updatedBooking.put("cab_type", "SUV");
        updatedBooking.put("status", "MODIFIED");

        when(jdbcTemplate.queryForMap(eq("SELECT * FROM cab_bookings WHERE id = ?"), eq("CAB-12345")))
            .thenReturn(existingBooking)
            .thenReturn(updatedBooking);
        when(cabApiClient.updateCabBooking(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn("Updated successfully");
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        Map<String, Object> result = cabService.updateCabBooking(
            "CAB-12345", "BK-12345", "SUV", null, null, 65.00);

        // Assert
        assertNotNull(result);
        assertEquals("SUV", result.get("cab_type"));
        verify(cabApiClient, times(1)).updateCabBooking(anyString(), anyString(), anyString(), anyInt());
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testUpdateCabBooking_WrongBookingId_ThrowsException() {
        // Arrange
        Map<String, Object> existingBooking = new HashMap<>();
        existingBooking.put("id", "CAB-12345");
        existingBooking.put("booking_id", "BK-99999");
        existingBooking.put("cab_type", "SEDAN");

        when(jdbcTemplate.queryForMap(eq("SELECT * FROM cab_bookings WHERE id = ?"), eq("CAB-12345")))
            .thenReturn(existingBooking);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cabService.updateCabBooking("CAB-12345", "BK-12345", "SUV", null, null, 65.00);
        });
    }

    @Test
    public void testUpdateCabBooking_NotFound_ThrowsException() {
        // Arrange
        when(jdbcTemplate.queryForMap(eq("SELECT * FROM cab_bookings WHERE id = ?"), anyString()))
            .thenThrow(new EmptyResultDataAccessException(1));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cabService.updateCabBooking("CAB-NONEXISTENT", "BK-12345", "SUV", null, null, 65.00);
        });
    }
}
