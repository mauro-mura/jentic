package dev.jentic.core;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduler interface for managing behavior execution.
 * Implementations can use different scheduling strategies.
 */
public interface BehaviorScheduler {
    
    /**
     * Schedule a behavior for execution
     * @param behavior the behavior to schedule
     * @return CompletableFuture that completes when behavior is scheduled
     */
    CompletableFuture<Void> schedule(Behavior behavior);
    
    /**
     * Cancel a scheduled behavior
     * @param behaviorId the ID of the behavior to cancel
     * @return true if behavior was found and canceled
     */
    boolean cancel(String behaviorId);
    
    /**
     * Check if scheduler is running
     * @return true if scheduler is active
     */
    boolean isRunning();
    
    /**
     * Start the scheduler
     * @return CompletableFuture that completes when scheduler is started
     */
    CompletableFuture<Void> start();
    
    /**
     * Stop the scheduler and all scheduled behaviors
     * @return CompletableFuture that completes when scheduler is stopped
     */
    CompletableFuture<Void> stop();
}