package dev.jentic.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for agent behaviors.
 * Behaviors define what an agent does over time.
 */
public interface Behavior {

    /**
     * Unique identifier for this behavior
     * @return the behavior ID
     */
    String getBehaviorId();

    /**
     * The agent that owns this behavior
     * @return the owner agent
     */
    Agent getAgent();

    /**
     * Execute this behavior at once
     * @return CompletableFuture that completes when execution is done
     */
    CompletableFuture<Void> execute();

    /**
     * Check if this behavior should continue running
     * @return true if the behavior is still active
     */
    boolean isActive();

    /**
     * Stop this behavior
     */
    void stop();

    /**
     * Get the type of this behavior
     * @return the behavior type
     */
    BehaviorType getType();

    /**
     * Get the execution interval for cyclic behaviors
     * @return the interval duration, or null for non-cyclic behaviors
     */
    Duration getInterval();
}