-- Database schema for cab bookings feature
-- This table stores cab booking records linked to hotel reservations

CREATE TABLE IF NOT EXISTS cab_bookings (
    id VARCHAR(50) PRIMARY KEY,
    booking_id VARCHAR(50) NOT NULL,
    cab_type VARCHAR(50) NOT NULL,
    pickup_time TIMESTAMP NOT NULL,
    pickup_location VARCHAR(255) NOT NULL,
    dropoff_location VARCHAR(255) NOT NULL,
    passenger_count INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    api_reference_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

-- Index on booking_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_cab_bookings_booking_id ON cab_bookings(booking_id);

-- Index on status for reporting queries
CREATE INDEX IF NOT EXISTS idx_cab_bookings_status ON cab_bookings(status);
