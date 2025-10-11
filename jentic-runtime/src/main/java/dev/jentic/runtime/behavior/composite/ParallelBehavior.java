package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import dev.jentic.core.composite.CompletionStrategy;
import dev.jentic.core.composite.CompositeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes multiple child behaviors in parallel using virtual threads.
 * Supports different completion strategies (ALL, ANY, FIRST, N_OF_M).
 */
public class ParallelBehavior extends CompositeBehavior {

    private static final Logger log = LoggerFactory.getLogger(ParallelBehavior.class);

    private final CompletionStrategy strategy;
    private final int requiredCompletions; // For N_OF_M strategy
    private final AtomicInteger completedCount;  // Successful completions only
    private final AtomicInteger finishedCount;   // All finished (success + failure)
    private Duration childTimeout; // Timeout for each child behavior

    public ParallelBehavior(String behaviorId) {
        this(behaviorId, CompletionStrategy.ALL);
    }

    public ParallelBehavior(String behaviorId, CompletionStrategy strategy) {
        this(behaviorId, strategy, 0);
    }

    public ParallelBehavior(String behaviorId, CompletionStrategy strategy, int requiredCompletions) {
        this(behaviorId, strategy, requiredCompletions, null);
    }

    public ParallelBehavior(String behaviorId, CompletionStrategy strategy,
                            int requiredCompletions, Duration childTimeout) {
        super(behaviorId);
        this.strategy = strategy;
        this.requiredCompletions = requiredCompletions;
        this.completedCount = new AtomicInteger(0);
        this.finishedCount = new AtomicInteger(0);
        this.childTimeout = childTimeout;
    }

    @Override
    public BehaviorType getType() {
        return BehaviorType.PARALLEL;
    }

    @Override
    public CompletableFuture<Void> execute() {
        if (!active || childBehaviors.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        log.debug("Parallel behavior '{}' executing {} child behaviors with strategy: {}",
                behaviorId, childBehaviors.size(), strategy);

        completedCount.set(0);
        finishedCount.set(0);

        return switch (strategy) {
            case ALL -> executeAll();
            case ANY -> executeAny();
            case FIRST -> executeFirst();
            case N_OF_M -> executeNofM();
        };
    }

    /**
     * Wait for ALL behaviors to complete
     */
    private CompletableFuture<Void> executeAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Behavior behavior : childBehaviors) {
            CompletableFuture<Void> future = executeWithTimeout(behavior);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.debug("Parallel behavior '{}' completed ALL children ({} successful, {} finished)",
                            behaviorId, completedCount.get(), finishedCount.get());
                });
    }

    /**
     * Complete when ANY behavior completes (but let all continue)
     */
    private CompletableFuture<Void> executeAny() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicInteger localCompleted = new AtomicInteger(0);

        for (Behavior behavior : childBehaviors) {
            executeWithTimeout(behavior)
                    .thenRun(() -> {
                        if (localCompleted.incrementAndGet() == 1) {
                            log.debug("Parallel behavior '{}' completed (first child done)", behaviorId);
                            result.complete(null);
                        }
                    });
        }

        return result;
    }

    /**
     * Race: complete when FIRST behavior completes (cancel others)
     */
    private CompletableFuture<Void> executeFirst() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Behavior behavior : childBehaviors) {
            CompletableFuture<Void> future = executeWithTimeout(behavior);
            futures.add(future);
        }

        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.debug("Parallel behavior '{}' completed (first child won race)", behaviorId);
                    // Stop remaining behaviors
                    for (Behavior behavior : childBehaviors) {
                        behavior.stop();
                    }
                });
    }

    /**
     * Complete when N behaviors complete
     */
    private CompletableFuture<Void> executeNofM() {
        if (requiredCompletions <= 0 || requiredCompletions > childBehaviors.size()) {
            log.warn("Invalid N_OF_M configuration: required={}, total={}",
                    requiredCompletions, childBehaviors.size());
            return executeAll();
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicInteger localCompleted = new AtomicInteger(0);

        for (Behavior behavior : childBehaviors) {
            executeWithTimeout(behavior)
                    .thenRun(() -> {
                        int completed = localCompleted.incrementAndGet();
                        if (completed >= requiredCompletions && !result.isDone()) {
                            log.debug("Parallel behavior '{}' completed ({}/{} children done)",
                                    behaviorId, completed, childBehaviors.size());
                            result.complete(null);
                        }
                    });
        }

        return result;
    }

    /**
     * Execute a behavior with optional timeout
     */
    private CompletableFuture<Void> executeWithTimeout(Behavior behavior) {
        CompletableFuture<Void> future = behavior.execute();

        if (childTimeout != null) {
            future = future.orTimeout(childTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // Use handle() to track both success and failure
        return future.handle((result, throwable) -> {
            // Always increment finished count
            finishedCount.incrementAndGet();

            if (throwable != null) {
                // Failed or timed out - don't increment completedCount
                if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Parallel behavior '{}' child '{}' timed out after {}",
                            behaviorId, behavior.getBehaviorId(), childTimeout);
                } else {
                    log.warn("Child behavior '{}' failed in parallel execution: {}",
                            behavior.getBehaviorId(), throwable.getMessage());
                }
            } else {
                // Success - increment completedCount
                completedCount.incrementAndGet();
            }

            return null;
        });
    }

    /**
     * Set timeout for each child behavior
     */
    public void setChildTimeout(Duration timeout) {
        this.childTimeout = timeout;
        log.debug("Parallel behavior '{}' child timeout set to: {}", behaviorId, timeout);
    }

    /**
     * Get configured child timeout
     */
    public Duration getChildTimeout() {
        return childTimeout;
    }

    public CompletionStrategy getStrategy() {
        return strategy;
    }

    /**
     * Get the number of successfully completed children
     */
    public int getCompletedCount() {
        return completedCount.get();
    }

    /**
     * Get the total number of finished children (success + failure)
     */
    public int getFinishedCount() {
        return finishedCount.get();
    }
}