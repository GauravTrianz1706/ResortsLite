-- ResortsLite Database Schema

-- Bookings table
CREATE TABLE IF NOT EXISTS bookings (
    id VARCHAR(255) PRIMARY KEY,
    guest VARCHAR(255) NOT NULL,
    room VARCHAR(255) NOT NULL,
    checkin VARCHAR(255) NOT NULL,
    checkout VARCHAR(255) NOT NULL
);

-- Reviews table
CREATE TABLE IF NOT EXISTS reviews (
    id VARCHAR(255) PRIMARY KEY,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    reviewer_name VARCHAR(255) DEFAULT 'Anonymous',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
