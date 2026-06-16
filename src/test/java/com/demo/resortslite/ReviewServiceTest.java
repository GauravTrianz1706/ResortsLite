package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ReviewServiceTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        // Clean up reviews table before each test
        jdbcTemplate.execute("DELETE FROM reviews");
    }

    @Test
    public void testSubmitReview() {
        Map<String, Object> review = reviewService.submitReview(5, "Excellent service!", "John Doe");

        assertNotNull(review);
        assertTrue(review.get("reviewId").toString().startsWith("RV-"));
        assertEquals(5, review.get("rating"));
        assertEquals("Excellent service!", review.get("comment"));
        assertEquals("John Doe", review.get("reviewerName"));
        assertNotNull(review.get("timestamp"));
    }

    @Test
    public void testSubmitReviewWithAnonymous() {
        Map<String, Object> review = reviewService.submitReview(4, "Good experience", null);

        assertNotNull(review);
        assertEquals("Anonymous", review.get("reviewerName"));
    }

    @Test
    public void testSubmitReviewWithEmptyName() {
        Map<String, Object> review = reviewService.submitReview(3, "Average", "");

        assertNotNull(review);
        assertEquals("Anonymous", review.get("reviewerName"));
    }

    @Test
    public void testGetAllReviews() {
        reviewService.submitReview(5, "Great!", "Alice");
        reviewService.submitReview(4, "Good", "Bob");

        List<Map<String, Object>> reviews = reviewService.getAllReviews();

        assertNotNull(reviews);
        assertEquals(2, reviews.size());
    }

    @Test
    public void testGetAllReviewsEmpty() {
        List<Map<String, Object>> reviews = reviewService.getAllReviews();

        assertNotNull(reviews);
        assertEquals(0, reviews.size());
    }

    @Test
    public void testGetReviewStats() {
        reviewService.submitReview(5, "Excellent", "User1");
        reviewService.submitReview(4, "Good", "User2");
        reviewService.submitReview(5, "Great", "User3");

        Map<String, Object> stats = reviewService.getReviewStats();

        assertNotNull(stats);
        assertEquals(3, stats.get("totalReviews"));
        assertTrue((Double) stats.get("averageRating") > 4.5);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> breakdown = (Map<Integer, Integer>) stats.get("ratingBreakdown");
        assertEquals(2, breakdown.get(5));
        assertEquals(1, breakdown.get(4));
    }

    @Test
    public void testGetReviewStatsEmpty() {
        Map<String, Object> stats = reviewService.getReviewStats();

        assertNotNull(stats);
        assertEquals(0, stats.get("totalReviews"));
        assertEquals(0.0, stats.get("averageRating"));
    }
}
