package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import dev.jentic.core.composite.CompletionStrategy;
import dev.jentic.core.composite.CompositeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final AtomicInteger completedCount;

    public ParallelBehavior(String behaviorId) {
        this(behaviorId, CompletionStrategy.ALL);
    }

    public ParallelBehavior(String behaviorId, CompletionStrategy strategy) {
        this(behaviorId, strategy, 0);
    }

    public ParallelBehavior(String behaviorId, CompletionStrategy strategy, int requiredCompletions) {
        super(behaviorId);
        this.strategy = strategy;
        this.requiredCompletions = requiredCompletions;
        this.completedCount = new AtomicInteger(0);
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
            CompletableFuture<Void> future = behavior.execute()
                    .whenComplete((result, throwable) -> {
                        completedCount.incrementAndGet();
                        if (throwable != null) {
                            log.warn("Child behavior '{}' failed in parallel execution: {}",
                                    behavior.getBehaviorId(), throwable.getMessage());
                        }
                    })
                    .exceptionally(throwable -> null); // Convert exception to null to not fail allOf
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.debug("Parallel behavior '{}' completed ALL children ({} completed)",
                            behaviorId, completedCount.get());
                });
    }

    /**
     * Complete when ANY behavior completes (but let all continue)
     */
    private CompletableFuture<Void> executeAny() {
        CompletableFuture<Void> result = new CompletableFuture<>();

        for (Behavior behavior : childBehaviors) {
            behavior.execute()
                    .thenRun(() -> {
                        if (completedCount.incrementAndGet() == 1) {
                            log.debug("Parallel behavior '{}' completed (first child done)", behaviorId);
                            result.complete(null);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.warn("Child behavior '{}' failed: {}",
                                behavior.getBehaviorId(), throwable.getMessage());
                        return null;
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
            CompletableFuture<Void> future = behavior.execute()
                    .exceptionally(throwable -> {
                        log.warn("Child behavior '{}' failed in race: {}",
                                behavior.getBehaviorId(), throwable.getMessage());
                        return null;
                    });
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

        for (Behavior behavior : childBehaviors) {
            behavior.execute()
                    .thenRun(() -> {
                        int completed = completedCount.incrementAndGet();
                        if (completed >= requiredCompletions && !result.isDone()) {
                            log.debug("Parallel behavior '{}' completed ({}/{} children done)",
                                    behaviorId, completed, childBehaviors.size());
                            result.complete(null);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.warn("Child behavior '{}' failed in N_OF_M: {}",
                                behavior.getBehaviorId(), throwable.getMessage());
                        return null;
                    });
        }

        return result;
    }

    public CompletionStrategy getStrategy() {
        return strategy;
    }

    public int getCompletedCount() {
        return completedCount.get();
    }
}