package dev.jentic.core.ratelimit;

import java.time.Instant;

/**
 * Statistics for rate limiter performance
 */
public record RateLimiterStats(
    long totalRequests,
    long allowedRequests,
    long rejectedRequests,
    double rejectionRate,
    Instant lastReset,
    int currentPermits
) {
    public static RateLimiterStats create(long allowed, long rejected, int permits) {
        long total = allowed + rejected;
        double rate = total > 0 ? (double) rejected / total * 100 : 0.0;
        return new RateLimiterStats(total, allowed, rejected, rate, Instant.now(), permits);
    }
}