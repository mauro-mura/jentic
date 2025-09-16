package dev.jentic.core;

import java.util.concurrent.CompletableFuture;

/**
 * Core agent interface defining the lifecycle and basic operations of an agent.
 * All agents in Jentic must implement this interface.
 */
public interface Agent {
    
    /**
     * Unique identifier for this agent
     * @return the agent ID
     */
    String getAgentId();
    
    /**
     * Human-readable name for this agent
     * @return the agent name
     */
    String getAgentName();
    
    /**
     * Start the agent and all its behaviors
     * @return CompletableFuture that completes when agent is fully started
     */
    CompletableFuture<Void> start();
    
    /**
     * Stop the agent and all its behaviors
     * @return CompletableFuture that completes when agent is fully stopped
     */
    CompletableFuture<Void> stop();
    
    /**
     * Check if the agent is currently running
     * @return true if the agent is active
     */
    boolean isRunning();
    
    /**
     * Add a behavior to this agent
     * @param behavior the behavior to add
     */
    void addBehavior(Behavior behavior);
    
    /**
     * Remove a behavior from this agent
     * @param behaviorId the ID of the behavior to remove
     */
    void removeBehavior(String behaviorId);
    
    /**
     * Get the message service for this agent
     * @return the message service
     */
    MessageService getMessageService();
}