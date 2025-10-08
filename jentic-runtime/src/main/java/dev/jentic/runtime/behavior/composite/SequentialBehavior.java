package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import dev.jentic.core.composite.CompositeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Executes child behaviors sequentially, one after another.
 * Each behavior waits for the previous one to complete.
 */
public class SequentialBehavior extends CompositeBehavior {

    private static final Logger log = LoggerFactory.getLogger(SequentialBehavior.class);

    private int currentIndex = 0;
    private boolean repeatSequence;
    private Duration stepTimeout;

    public SequentialBehavior(String behaviorId) {
        this(behaviorId, false);
    }

    public SequentialBehavior(String behaviorId, boolean repeatSequence) {
        this(behaviorId, repeatSequence, null);
    }

    public SequentialBehavior(String behaviorId, boolean repeatSequence, Duration stepTimeout) {
        super(behaviorId);
        this.repeatSequence = repeatSequence;
        this.stepTimeout = stepTimeout;
    }

    @Override
    public BehaviorType getType() {
        return BehaviorType.SEQUENTIAL;
    }

    @Override
    public CompletableFuture<Void> execute() {
        if (!active || childBehaviors.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // For repeating sequences, execute only the current step
        // The scheduler will call execute() again for the next cycle
        if (repeatSequence) {
            return executeSingleStep();
        } else {
            // For non-repeating, execute all steps in one go
            return executeCurrentBehavior();
        }
    }

    /**
     * Execute only the current step (for repeating sequences)
     */
    private CompletableFuture<Void> executeSingleStep() {
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if we need to reset before executing
        if (currentIndex >= childBehaviors.size()) {
            currentIndex = 0; // Reset for next cycle
            log.trace("Sequential behavior '{}' restarting sequence (pre-check)", behaviorId);
        }

        Behavior currentBehavior = childBehaviors.get(currentIndex);
        int stepNumber = currentIndex + 1; // For logging

        log.trace("Sequential behavior '{}' executing step {}/{}: {} (currentIndex before: {})",
                behaviorId, stepNumber, childBehaviors.size(),
                currentBehavior.getBehaviorId(), currentIndex);

        CompletableFuture<Void> executionFuture = currentBehavior.execute();

        // Apply timeout if configured
        if (stepTimeout != null) {
            executionFuture = executionFuture.orTimeout(stepTimeout.toMillis(),
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                            log.warn("Sequential behavior '{}' step {} timed out after {}",
                                    behaviorId, stepNumber, stepTimeout);
                        }
                        throw new java.util.concurrent.CompletionException(throwable);
                    });
        }

        return executionFuture
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error in sequential behavior '{}' at step {}: {}",
                                behaviorId, stepNumber, throwable.getMessage());
                    }
                    // Always increment, even on error
                    int oldIndex = currentIndex;
                    currentIndex++;
                    log.trace("Sequential behavior '{}' step completed, index: {} → {}",
                            behaviorId, oldIndex, currentIndex);

                    // Reset if we've completed a full cycle
                    if (currentIndex >= childBehaviors.size()) {
                        currentIndex = 0;
                        log.trace("Sequential behavior '{}' completed cycle, reset to start (index now: {})",
                                behaviorId, currentIndex);
                    }
                })
                .thenApply(v -> null); // Convert to Void
    }

    /**
     * Execute current behavior and continue recursively (for non-repeating sequences)
     */
    private CompletableFuture<Void> executeCurrentBehavior() {
        // Check if we've reached the end
        if (currentIndex >= childBehaviors.size()) {
            active = false; // Sequential behavior is done
            log.debug("Sequential behavior '{}' completed all steps", behaviorId);
            return CompletableFuture.completedFuture(null);
        }

        // If not active, stop
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }

        Behavior currentBehavior = childBehaviors.get(currentIndex);
        int stepNumber = currentIndex + 1; // For logging

        log.trace("Sequential behavior '{}' executing step {}/{}: {}",
                behaviorId, stepNumber, childBehaviors.size(),
                currentBehavior.getBehaviorId());

        CompletableFuture<Void> executionFuture = currentBehavior.execute();

        // Apply timeout if configured
        if (stepTimeout != null) {
            executionFuture = executionFuture.orTimeout(stepTimeout.toMillis(),
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                            log.warn("Sequential behavior '{}' step {} timed out after {}",
                                    behaviorId, stepNumber, stepTimeout);
                        }
                        throw new java.util.concurrent.CompletionException(throwable);
                    });
        }

        return executionFuture
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error in sequential behavior '{}' at step {}: {}",
                                behaviorId, stepNumber, throwable.getMessage());
                    }
                    // Always increment and continue, even on error
                    currentIndex++;
                    return null;
                })
                .thenCompose(v -> executeCurrentBehavior()); // Continue to next step
    }

    /**
     * Reset the sequence to start from the beginning
     */
    public void reset() {
        currentIndex = 0;
        log.debug("Sequential behavior '{}' reset to beginning", behaviorId);
    }

    /**
     * Get current step index
     */
    public int getCurrentStep() {
        return currentIndex;
    }

    /**
     * Get total number of steps
     */
    public int getTotalSteps() {
        return childBehaviors.size();
    }

    /**
     * Set timeout for each step
     */
    public void setStepTimeout(Duration timeout) {
        this.stepTimeout = timeout;
        log.debug("Sequential behavior '{}' step timeout set to: {}", behaviorId, timeout);
    }

    /**
     * Get configured step timeout
     */
    public Duration getStepTimeout() {
        return stepTimeout;
    }
}