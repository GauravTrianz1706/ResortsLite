package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Submit a new customer review
     */
    public Map<String, Object> submitReview(Integer rating, String comment, String reviewerName) {
        // Generate unique review ID
        String reviewId = "RV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Set default reviewer name if not provided
        if (reviewerName == null || reviewerName.trim().isEmpty()) {
            reviewerName = "Anonymous";
        }

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO reviews (id, rating, comment, reviewer_name, created_at) VALUES (?, ?, ?, ?, ?)";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(sql, reviewId, rating, comment, reviewerName, timestamp);

        // Return review data
        Map<String, Object> review = new HashMap<>();
        review.put("reviewId", reviewId);
        review.put("rating", rating);
        review.put("comment", comment);
        review.put("reviewerName", reviewerName);
        review.put("timestamp", timestamp.toInstant().toString());

        return review;
    }

    /**
     * Get all reviews ordered by created_at DESC
     */
    public List<Map<String, Object>> getAllReviews() {
        try {
            String sql = "SELECT id, rating, comment, reviewer_name, created_at FROM reviews ORDER BY created_at DESC";
            List<Map<String, Object>> reviews = jdbcTemplate.queryForList(sql);

            // Convert timestamps to ISO-8601 format
            for (Map<String, Object> review : reviews) {
                Object createdAt = review.get("created_at");
                if (createdAt instanceof Timestamp) {
                    review.put("timestamp", ((Timestamp) createdAt).toInstant().toString());
                    review.remove("created_at");
                }
                // Rename keys for consistency
                review.put("reviewId", review.remove("id"));
                review.put("reviewerName", review.remove("reviewer_name"));
            }

            return reviews;
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    /**
     * Get review statistics
     */
    public Map<String, Object> getReviewStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Get total count and average rating
            String countSql = "SELECT COUNT(*) as total FROM reviews";
            Integer totalReviews = jdbcTemplate.queryForObject(countSql, Integer.class);

            if (totalReviews == null) {
                totalReviews = 0;
            }

            Double averageRating = 0.0;
            if (totalReviews > 0) {
                String avgSql = "SELECT COALESCE(AVG(rating), 0.0) as average FROM reviews";
                averageRating = jdbcTemplate.queryForObject(avgSql, Double.class);
            }

            stats.put("totalReviews", totalReviews);
            stats.put("averageRating", averageRating);

            // Get breakdown by rating
            Map<Integer, Integer> ratingBreakdown = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                ratingBreakdown.put(i, 0);
            }

            if (totalReviews > 0) {
                String breakdownSql = "SELECT rating, COUNT(*) as count FROM reviews GROUP BY rating";
                List<Map<String, Object>> breakdownResults = jdbcTemplate.queryForList(breakdownSql);

                for (Map<String, Object> result : breakdownResults) {
                    Integer rating = (Integer) result.get("rating");
                    Integer count = ((Number) result.get("count")).intValue();
                    ratingBreakdown.put(rating, count);
                }
            }

            stats.put("ratingBreakdown", ratingBreakdown);

        } catch (Exception e) {
            stats.put("totalReviews", 0);
            stats.put("averageRating", 0.0);
            Map<Integer, Integer> emptyBreakdown = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                emptyBreakdown.put(i, 0);
            }
            stats.put("ratingBreakdown", emptyBreakdown);
        }

        return stats;
    }
}
