package dev.jentic.core;

/**
 * Enumeration of possible agent states
 */
public enum AgentStatus {
    /**
     * Agent is running and healthy
     */
    RUNNING,
    
    /**
     * Agent is stopped
     */
    STOPPED,
    
    /**
     * Agent is starting up
     */
    STARTING,
    
    /**
     * Agent is shutting down
     */
    STOPPING,
    
    /**
     * Agent encountered an error
     */
    ERROR,
    
    /**
     * Agent status is unknown
     */
    UNKNOWN
}