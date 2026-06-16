package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpSession;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CabBookingController.
 * Tests REST endpoints for cab booking operations.
 */
public class CabBookingControllerTest {

    @Mock
    private CabService cabService;

    @Mock
    private BookingService bookingService;

    @Mock
    private HttpSession session;

    @InjectMocks
    private CabBookingController cabBookingController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCheckCabAvailability_Success() {
        // Arrange
        List<Map<String, Object>> mockCabs = new ArrayList<>();
        Map<String, Object> sedan = new HashMap<>();
        sedan.put("cabType", "SEDAN");
        sedan.put("capacity", 4);
        sedan.put("estimatedPrice", 45.00);
        mockCabs.add(sedan);

        when(cabService.checkAvailability(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn(mockCabs);

        // Act
        ResponseEntity<?> response = cabBookingController.checkCabAvailability(
            "BK-12345", "2026-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("BK-12345", body.get("bookingId"));
        List<Map<String, Object>> cabs = (List<Map<String, Object>>) body.get("availableCabs");
        assertEquals(1, cabs.size());
    }

    @Test
    public void testCheckCabAvailability_InvalidPassengerCount() {
        // Act
        ResponseEntity<?> response = cabBookingController.checkCabAvailability(
            "BK-12345", "2026-12-01T10:00:00Z", 0, "AIRPORT_TO_HOTEL");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("Passenger count"));
    }

    @Test
    public void testCheckCabAvailability_InvalidTripType() {
        // Act
        ResponseEntity<?> response = cabBookingController.checkCabAvailability(
            "BK-12345", "2026-12-01T10:00:00Z", 2, "INVALID_TYPE");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("Trip type"));
    }

    @Test
    public void testCreateCabBooking_Success() {
        // Arrange
        Map<String, Object> hotelBooking = new HashMap<>();
        hotelBooking.put("bookingId", "BK-12345");
        hotelBooking.put("guestName", "John Doe");

        Map<String, Object> cabBooking = new HashMap<>();
        cabBooking.put("id", "CAB-12345");
        cabBooking.put("bookingId", "BK-12345");
        cabBooking.put("cabType", "SEDAN");
        cabBooking.put("status", "CONFIRMED");

        when(bookingService.getBookingById("BK-12345")).thenReturn(hotelBooking);
        when(cabService.createCabBooking(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyString(), anyDouble())).thenReturn(cabBooking);

        // Act
        ResponseEntity<?> response = cabBookingController.createCabBooking(
            "BK-12345", "SEDAN", "2026-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("confirmed", body.get("status"));
        assertNotNull(body.get("cabBooking"));
    }

    @Test
    public void testCreateCabBooking_HotelBookingNotFound() {
        // Arrange
        Map<String, Object> errorBooking = new HashMap<>();
        errorBooking.put("error", "Booking not found");

        when(bookingService.getBookingById("BK-NONEXISTENT")).thenReturn(errorBooking);

        // Act
        ResponseEntity<?> response = cabBookingController.createCabBooking(
            "BK-NONEXISTENT", "SEDAN", "2026-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("not found"));
    }

    @Test
    public void testCreateCabBooking_ValidationError() {
        // Arrange
        Map<String, Object> hotelBooking = new HashMap<>();
        hotelBooking.put("bookingId", "BK-12345");

        when(bookingService.getBookingById("BK-12345")).thenReturn(hotelBooking);
        when(cabService.createCabBooking(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyInt(), anyString(), anyDouble()))
            .thenThrow(new IllegalArgumentException("Invalid pickup time"));

        // Act
        ResponseEntity<?> response = cabBookingController.createCabBooking(
            "BK-12345", "SEDAN", "2020-12-01T10:00:00Z", 2, "AIRPORT_TO_HOTEL");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("Invalid pickup time"));
    }

    @Test
    public void testGetCabBooking_Success() {
        // Arrange
        Map<String, Object> cabBooking = new HashMap<>();
        cabBooking.put("id", "CAB-12345");
        cabBooking.put("booking_id", "BK-12345");
        cabBooking.put("cab_type", "SEDAN");
        cabBooking.put("status", "CONFIRMED");

        when(cabService.getCabBookingByBookingId("BK-12345")).thenReturn(cabBooking);

        // Act
        ResponseEntity<?> response = cabBookingController.getCabBooking("BK-12345");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("CAB-12345", body.get("id"));
        assertEquals("SEDAN", body.get("cab_type"));
    }

    @Test
    public void testGetCabBooking_NotFound() {
        // Arrange
        when(cabService.getCabBookingByBookingId("BK-NONEXISTENT")).thenReturn(new HashMap<>());

        // Act
        ResponseEntity<?> response = cabBookingController.getCabBooking("BK-NONEXISTENT");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("message").contains("No cab booking found"));
    }

    @Test
    public void testUpdateCabBooking_Success() {
        // Arrange
        Map<String, Object> hotelBooking = new HashMap<>();
        hotelBooking.put("bookingId", "BK-12345");
        hotelBooking.put("guest", "John Doe");

        Map<String, Object> updatedCabBooking = new HashMap<>();
        updatedCabBooking.put("id", "CAB-12345");
        updatedCabBooking.put("booking_id", "BK-12345");
        updatedCabBooking.put("cab_type", "SUV");
        updatedCabBooking.put("status", "MODIFIED");

        when(session.getAttribute("guestName")).thenReturn("John Doe");
        when(bookingService.getBookingById("BK-12345")).thenReturn(hotelBooking);
        when(cabService.updateCabBooking(eq("CAB-12345"), eq("BK-12345"), eq("SUV"),
            isNull(), isNull(), anyDouble())).thenReturn(updatedCabBooking);

        // Act
        ResponseEntity<?> response = cabBookingController.updateCabBooking(
            "BK-12345", "CAB-12345", "SUV", null, null, session);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("updated", body.get("status"));
        Map<String, Object> cab = (Map<String, Object>) body.get("cabBooking");
        assertEquals("SUV", cab.get("cab_type"));
    }

    @Test
    public void testUpdateCabBooking_NoSession() {
        // Arrange
        when(session.getAttribute("guestName")).thenReturn(null);

        // Act
        ResponseEntity<?> response = cabBookingController.updateCabBooking(
            "BK-12345", "CAB-12345", "SUV", null, null, session);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("No active session"));
    }

    @Test
    public void testUpdateCabBooking_WrongGuest() {
        // Arrange
        Map<String, Object> hotelBooking = new HashMap<>();
        hotelBooking.put("bookingId", "BK-12345");
        hotelBooking.put("guest", "Jane Doe");

        when(session.getAttribute("guestName")).thenReturn("John Doe");
        when(bookingService.getBookingById("BK-12345")).thenReturn(hotelBooking);

        // Act
        ResponseEntity<?> response = cabBookingController.updateCabBooking(
            "BK-12345", "CAB-12345", "SUV", null, null, session);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("permission"));
    }

    @Test
    public void testUpdateCabBooking_ValidationError() {
        // Arrange
        Map<String, Object> hotelBooking = new HashMap<>();
        hotelBooking.put("bookingId", "BK-12345");
        hotelBooking.put("guest", "John Doe");

        when(session.getAttribute("guestName")).thenReturn("John Doe");
        when(bookingService.getBookingById("BK-12345")).thenReturn(hotelBooking);
        when(cabService.updateCabBooking(anyString(), anyString(), anyString(),
            anyString(), any(), anyDouble()))
            .thenThrow(new IllegalArgumentException("Cab booking not found"));

        // Act
        ResponseEntity<?> response = cabBookingController.updateCabBooking(
            "BK-12345", "CAB-NONEXISTENT", "SUV", null, null, session);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertTrue(body.get("error").contains("Cab booking not found"));
    }
}
