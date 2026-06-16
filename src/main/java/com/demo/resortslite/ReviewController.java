package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.cache.ttl.minutes:30}")
    private long cacheTtlMinutes;

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitReview(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) String reviewerName) {

        // Validate rating
        if (rating == null || rating < 1 || rating > 5) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rating is required and must be between 1 and 5");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        // Submit review
        Map<String, Object> review = reviewService.submitReview(rating, comment, reviewerName);

        // Invalidate cache
        redisTemplate.delete("reviews:all");
        redisTemplate.delete("reviews:stats");

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("reviewId", review.get("reviewId"));
        response.put("rating", review.get("rating"));
        response.put("comment", review.get("comment"));
        response.put("reviewerName", review.get("reviewerName"));
        response.put("timestamp", review.get("timestamp"));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllReviews() {
        // Check cache first
        String cacheKey = "reviews:all";
        Object cachedReviews = redisTemplate.opsForValue().get(cacheKey);

        if (cachedReviews != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reviews = (List<Map<String, Object>>) cachedReviews;
            return ResponseEntity.ok(reviews);
        }

        // Get from database
        List<Map<String, Object>> reviews = reviewService.getAllReviews();

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, reviews, cacheTtlMinutes, TimeUnit.MINUTES);

        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getReviewStats() {
        // Check cache first
        String cacheKey = "reviews:stats";
        Object cachedStats = redisTemplate.opsForValue().get(cacheKey);

        if (cachedStats != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) cachedStats;
            return ResponseEntity.ok(stats);
        }

        // Get from database
        Map<String, Object> stats = reviewService.getReviewStats();

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, stats, cacheTtlMinutes, TimeUnit.MINUTES);

        return ResponseEntity.ok(stats);
    }
}
