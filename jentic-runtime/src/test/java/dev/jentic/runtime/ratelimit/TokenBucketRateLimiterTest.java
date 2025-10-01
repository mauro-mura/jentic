package dev.jentic.runtime.ratelimit;

import dev.jentic.core.ratelimit.RateLimit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenBucketRateLimiterTest {
    
    @Test
    void shouldAllowRequestsUpToLimit() {
        // Given
        RateLimit limit = RateLimit.perSecond(5);
        var rateLimiter = new TokenBucketRateLimiter(limit);
        
        // When/Then - should allow 5 requests
        assertThat(rateLimiter.tryAcquire()).isTrue();
        assertThat(rateLimiter.tryAcquire()).isTrue();
        assertThat(rateLimiter.tryAcquire()).isTrue();
        assertThat(rateLimiter.tryAcquire()).isTrue();
        assertThat(rateLimiter.tryAcquire()).isTrue();
        
        // 6th request should be rejected
        assertThat(rateLimiter.tryAcquire()).isFalse();
    }
    
    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {
        // Given
        RateLimit limit = RateLimit.perSecond(2);
        var rateLimiter = new TokenBucketRateLimiter(limit);
        
        // Exhaust tokens
        rateLimiter.tryAcquire();
        rateLimiter.tryAcquire();
        assertThat(rateLimiter.tryAcquire()).isFalse();
        
        // Wait for refill
        Thread.sleep(600); // Should refill 1 token
        
        // Should allow one more request
        assertThat(rateLimiter.tryAcquire()).isTrue();
    }
}