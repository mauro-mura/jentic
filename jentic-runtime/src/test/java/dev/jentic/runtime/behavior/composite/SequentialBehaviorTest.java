package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Sequential Behavior Tests")
class SequentialBehaviorTest {

    private SequentialBehavior sequentialBehavior;
    private List<String> executionOrder;
    private AtomicInteger executionCount;

    @BeforeEach
    void setUp() {
        sequentialBehavior = new SequentialBehavior("test-sequential");
        executionOrder = new ArrayList<>();
        executionCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should execute behaviors in sequence")
    void shouldExecuteInSequence() throws Exception {
        // Given
        sequentialBehavior.addChildBehavior(createTestBehavior("step1", 50));
        sequentialBehavior.addChildBehavior(createTestBehavior("step2", 50));
        sequentialBehavior.addChildBehavior(createTestBehavior("step3", 50));

        // When
        CompletableFuture<Void> future = sequentialBehavior.execute();
        future.get(2, TimeUnit.SECONDS);

        // Then
        assertThat(executionOrder).containsExactly("step1", "step2", "step3");
        assertThat(sequentialBehavior.isActive()).isFalse();
        assertThat(sequentialBehavior.getCurrentStep()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should repeat sequence when repeatSequence is true")
    void shouldRepeatSequence() throws Exception {
        // Given
        sequentialBehavior = new SequentialBehavior("test-sequential", true);
        sequentialBehavior.addChildBehavior(createTestBehavior("step1", 20));
        sequentialBehavior.addChildBehavior(createTestBehavior("step2", 20));

        // When - execute multiple times (repeating behavior executes one step at a time)
        CompletableFuture<Void> f1 = sequentialBehavior.execute();
        f1.get(1, TimeUnit.SECONDS); // step1, index: 0→1

        CompletableFuture<Void> f2 = sequentialBehavior.execute();
        f2.get(1, TimeUnit.SECONDS); // step2, index: 1→2→0 (reset)

        CompletableFuture<Void> f3 = sequentialBehavior.execute();
        f3.get(1, TimeUnit.SECONDS); // step1 again, index: 0→1

        CompletableFuture<Void> f4 = sequentialBehavior.execute();
        f4.get(1, TimeUnit.SECONDS); // step2 again, index: 1→2→0 (reset)

        // Wait a bit for async completion of the handle block
        Thread.sleep(50);

        // Then - should have executed twice through the sequence
        assertThat(executionOrder).containsExactly("step1", "step2", "step1", "step2");
        assertThat(sequentialBehavior.isActive()).isTrue(); // Still active (can repeat)
        assertThat(sequentialBehavior.getCurrentStep()).isEqualTo(0); // Reset to start after completing cycle
    }

    @Test
    @DisplayName("Should skip failed step and continue")
    void shouldSkipFailedStep() throws Exception {
        // Given
        sequentialBehavior.addChildBehavior(createTestBehavior("step1", 20));
        sequentialBehavior.addChildBehavior(createFailingBehavior("step2"));
        sequentialBehavior.addChildBehavior(createTestBehavior("step3", 20));

        // When
        CompletableFuture<Void> future = sequentialBehavior.execute();

        // Wait for async completion - failure doesn't stop the chain
        Thread.sleep(200);

        // Then - step3 should execute despite step2 failure
        assertThat(executionOrder).contains("step1", "step3");
        assertThat(executionOrder).doesNotContain("step2");
    }

    @Test
    @DisplayName("Should timeout on slow step")
    void shouldTimeoutOnSlowStep() throws Exception {
        // Given
        Duration timeout = Duration.ofMillis(100);
        sequentialBehavior = new SequentialBehavior("test-sequential", false, timeout);

        sequentialBehavior.addChildBehavior(createTestBehavior("step1", 20));
        sequentialBehavior.addChildBehavior(createTestBehavior("step2", 500)); // Will timeout
        sequentialBehavior.addChildBehavior(createTestBehavior("step3", 20));

        // When
        CompletableFuture<Void> future = sequentialBehavior.execute();

        // Wait longer for the sequence to complete all steps despite timeout
        Thread.sleep(400);

        // Then - step1 should complete, step2 times out, step3 should execute
        assertThat(executionOrder).contains("step1", "step3");
        assertThat(executionOrder).doesNotContain("step2");
    }

    @Test
    @DisplayName("Should reset sequence to beginning")
    void shouldResetSequence() throws Exception {
        // Given
        sequentialBehavior.addChildBehavior(createTestBehavior("step1", 20));
        sequentialBehavior.addChildBehavior(createTestBehavior("step2", 20));

        // When
        sequentialBehavior.execute().get(1, TimeUnit.SECONDS);
        assertThat(sequentialBehavior.getCurrentStep()).isEqualTo(2);

        sequentialBehavior.reset();

        // Then
        assertThat(sequentialBehavior.getCurrentStep()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return SEQUENTIAL type")
    void shouldReturnSequentialType() {
        assertThat(sequentialBehavior.getType()).isEqualTo(BehaviorType.SEQUENTIAL);
    }

    @Test
    @DisplayName("Should handle empty child behaviors")
    void shouldHandleEmptyChildren() throws Exception {
        // When
        CompletableFuture<Void> future = sequentialBehavior.execute();
        future.get(1, TimeUnit.SECONDS);

        // Then
        assertThat(executionOrder).isEmpty();
        assertThat(sequentialBehavior.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should stop all child behaviors when stopped")
    void shouldStopChildBehaviors() {
        // Given
        TestBehavior child1 = new TestBehavior("child1", 100);
        TestBehavior child2 = new TestBehavior("child2", 100);
        sequentialBehavior.addChildBehavior(child1);
        sequentialBehavior.addChildBehavior(child2);

        // When
        sequentialBehavior.stop();

        // Then
        assertThat(sequentialBehavior.isActive()).isFalse();
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
            executionCount.incrementAndGet();

            if (shouldFail) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Simulated failure in " + name)
                );
            }

            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                    executionOrder.add(name);
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