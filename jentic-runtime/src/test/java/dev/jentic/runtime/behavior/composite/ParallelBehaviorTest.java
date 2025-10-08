package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import dev.jentic.core.composite.CompletionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Parallel Behavior Tests")
class ParallelBehaviorTest {
    
    private ParallelBehavior parallelBehavior;
    private ConcurrentHashMap<String, Long> executionTimestamps;
    private AtomicInteger completionCount;
    
    @BeforeEach
    void setUp() {
        parallelBehavior = new ParallelBehavior("test-parallel");
        executionTimestamps = new ConcurrentHashMap<>();
        completionCount = new AtomicInteger(0);
    }
    
    @Test
    @DisplayName("Should execute behaviors in parallel with ALL strategy")
    void shouldExecuteInParallelAll() throws Exception {
        // Given
        parallelBehavior = new ParallelBehavior("test-parallel", CompletionStrategy.ALL);
        parallelBehavior.addChildBehavior(createTestBehavior("task1", 100));
        parallelBehavior.addChildBehavior(createTestBehavior("task2", 100));
        parallelBehavior.addChildBehavior(createTestBehavior("task3", 100));
        
        // When
        long start = System.currentTimeMillis();
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(2, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;
        
        // Then
        assertThat(executionTimestamps).hasSize(3);
        // Be more lenient with timing - parallel execution should be faster than sequential
        // Sequential would be ~300ms, parallel should be ~100ms, allow up to 250ms for CI environments
        assertThat(duration).isLessThan(250); 
        assertThat(parallelBehavior.getCompletedCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should complete when ANY behavior completes")
    void shouldCompleteWithAnyStrategy() throws Exception {
        // Given
        parallelBehavior = new ParallelBehavior("test-parallel", CompletionStrategy.ANY);
        parallelBehavior.addChildBehavior(createTestBehavior("fast", 50));
        parallelBehavior.addChildBehavior(createTestBehavior("slow", 500));
        
        // When
        long start = System.currentTimeMillis();
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(1, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;
        
        // Then - should complete after fast task (~50ms), allow margin for slow systems
        assertThat(duration).isLessThan(300);
        assertThat(parallelBehavior.getCompletedCount()).isGreaterThanOrEqualTo(1);
    }
    
    @Test
    @DisplayName("Should race and complete with FIRST strategy")
    void shouldRaceWithFirstStrategy() throws Exception {
        // Given
        parallelBehavior = new ParallelBehavior("test-parallel", CompletionStrategy.FIRST);
        parallelBehavior.addChildBehavior(createTestBehavior("fast", 30));
        parallelBehavior.addChildBehavior(createTestBehavior("slow", 300));
        
        // When
        long start = System.currentTimeMillis();
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(1, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;
        
        // Then - should complete after fast task, be lenient for CI
        assertThat(duration).isLessThan(200);
    }
    
    @Test
    @DisplayName("Should complete when N behaviors complete with N_OF_M strategy")
    void shouldCompleteWithNofMStrategy() throws Exception {
        // Given - require 2 out of 4 to complete
        parallelBehavior = new ParallelBehavior("test-parallel", CompletionStrategy.N_OF_M, 2);
        parallelBehavior.addChildBehavior(createTestBehavior("task1", 50));
        parallelBehavior.addChildBehavior(createTestBehavior("task2", 50));
        parallelBehavior.addChildBehavior(createTestBehavior("task3", 200));
        parallelBehavior.addChildBehavior(createTestBehavior("task4", 200));
        
        // When
        long start = System.currentTimeMillis();
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(1, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;
        
        // Then - should complete after 2 fast tasks, be lenient
        assertThat(duration).isLessThan(250);
        assertThat(parallelBehavior.getCompletedCount()).isGreaterThanOrEqualTo(2);
    }
    
    @Test
    @DisplayName("Should handle child behavior failures")
    void shouldHandleChildFailures() throws Exception {
        // Given
        parallelBehavior.addChildBehavior(createTestBehavior("ok1", 50));
        parallelBehavior.addChildBehavior(createFailingBehavior("fail"));
        parallelBehavior.addChildBehavior(createTestBehavior("ok2", 50));
        
        // When
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(1, TimeUnit.SECONDS);
        
        // Then - should complete despite failure
        assertThat(executionTimestamps).containsKeys("ok1", "ok2");
        assertThat(executionTimestamps).doesNotContainKey("fail");
    }
    
    @Test
    @DisplayName("Should return PARALLEL type")
    void shouldReturnParallelType() {
        assertThat(parallelBehavior.getType()).isEqualTo(BehaviorType.PARALLEL);
    }
    
    @Test
    @DisplayName("Should track completion count")
    void shouldTrackCompletionCount() throws Exception {
        // Given
        parallelBehavior.addChildBehavior(createTestBehavior("task1", 50));
        parallelBehavior.addChildBehavior(createTestBehavior("task2", 50));
        parallelBehavior.addChildBehavior(createTestBehavior("task3", 50));
        
        // When
        parallelBehavior.execute().get(1, TimeUnit.SECONDS);
        
        // Then
        assertThat(parallelBehavior.getCompletedCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should handle empty child behaviors")
    void shouldHandleEmptyChildren() throws Exception {
        // When
        CompletableFuture<Void> future = parallelBehavior.execute();
        future.get(1, TimeUnit.SECONDS);
        
        // Then
        assertThat(parallelBehavior.getCompletedCount()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Should stop all child behaviors when stopped")
    void shouldStopChildBehaviors() {
        // Given
        TestBehavior child1 = new TestBehavior("child1", 100);
        TestBehavior child2 = new TestBehavior("child2", 100);
        parallelBehavior.addChildBehavior(child1);
        parallelBehavior.addChildBehavior(child2);
        
        // When
        parallelBehavior.stop();
        
        // Then
        assertThat(parallelBehavior.isActive()).isFalse();
        assertThat(child1.isActive()).isFalse();
        assertThat(child2.isActive()).isFalse();
    }
    
    // Helper methods
    
    private Behavior createTestBehavior(String name, long delayMs) {
        return new TestBehavior(name, delayMs);
    }
    
    private Behavior createFailingBehavior(String name) {
        return new TestBehavior(name, 0, true);
    }
    
    private class TestBehavior implements Behavior {
        private final String name;
        private final long delayMs;
        private final boolean shouldFail;
        private boolean active = true;
        
        TestBehavior(String name, long delayMs) {
            this(name, delayMs, false);
        }
        
        TestBehavior(String name, long delayMs, boolean shouldFail) {
            this.name = name;
            this.delayMs = delayMs;
            this.shouldFail = shouldFail;
        }
        
        @Override
        public String getBehaviorId() {
            return name;
        }
        
        @Override
        public dev.jentic.core.Agent getAgent() {
            return null;
        }
        
        @Override
        public CompletableFuture<Void> execute() {
            if (shouldFail) {
                return CompletableFuture.failedFuture(
                    new RuntimeException("Simulated failure in " + name)
                );
            }
            
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                    executionTimestamps.put(name, System.currentTimeMillis());
                    completionCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        @Override
        public boolean isActive() {
            return active;
        }
        
        @Override
        public void stop() {
            active = false;
        }
        
        @Override
        public BehaviorType getType() {
            return BehaviorType.ONE_SHOT;
        }
        
        @Override
        public Duration getInterval() {
            return null;
        }
    }
}