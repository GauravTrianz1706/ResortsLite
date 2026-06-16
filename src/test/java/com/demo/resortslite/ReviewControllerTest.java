package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    public void setup() {
        // Clean up before each test
        jdbcTemplate.execute("DELETE FROM reviews");
        redisTemplate.delete("reviews:all");
        redisTemplate.delete("reviews:stats");
    }

    @Test
    public void testSubmitReview() throws Exception {
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "5")
                .param("comment", "Excellent service!")
                .param("reviewerName", "John Doe"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Excellent service!"))
                .andExpect(jsonPath("$.reviewerName").value("John Doe"))
                .andExpect(jsonPath("$.reviewId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    public void testSubmitReviewAnonymous() throws Exception {
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "4")
                .param("comment", "Good experience"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.reviewerName").value("Anonymous"));
    }

    @Test
    public void testSubmitReviewInvalidRating() throws Exception {
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "6")
                .param("comment", "Test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Rating is required and must be between 1 and 5"));
    }

    @Test
    public void testSubmitReviewMissingRating() throws Exception {
        mockMvc.perform(post("/api/reviews/submit")
                .param("comment", "Test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Rating is required and must be between 1 and 5"));
    }

    @Test
    public void testGetAllReviews() throws Exception {
        // Submit a review first
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "5")
                .param("comment", "Great!"));

        // Get all reviews
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].reviewId").exists())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Great!"));
    }

    @Test
    public void testGetAllReviewsEmpty() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    public void testGetReviewStats() throws Exception {
        // Submit reviews
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "5")
                .param("comment", "Excellent"));
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "4")
                .param("comment", "Good"));

        // Get stats
        mockMvc.perform(get("/api/reviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.averageRating").exists())
                .andExpect(jsonPath("$.ratingBreakdown").exists());
    }

    @Test
    public void testGetReviewStatsEmpty() throws Exception {
        mockMvc.perform(get("/api/reviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.averageRating").value(0.0));
    }

    @Test
    public void testCacheInvalidation() throws Exception {
        // Submit a review
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "5")
                .param("comment", "First review"));

        // Get reviews (will cache)
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].comment").value("First review"));

        // Submit another review (should invalidate cache)
        mockMvc.perform(post("/api/reviews/submit")
                .param("rating", "4")
                .param("comment", "Second review"));

        // Get reviews again (should see both)
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].comment").value("Second review"))
                .andExpect(jsonPath("$[1].comment").value("First review"));
    }
}
