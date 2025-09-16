package dev.jentic.core;

/**
 * Enumeration of supported behavior types
 */
public enum BehaviorType {
    /**
     * Execute once and stop
     */
    ONE_SHOT,
    
    /**
     * Execute repeatedly at fixed intervals
     */
    CYCLIC,
    
    /**
     * Wake up at specific times or conditions
     */
    WAKER,
    
    /**
     * Event-driven behavior (responds to messages/events)
     */
    EVENT_DRIVEN,
    
    /**
     * Custom behavior type
     */
    CUSTOM
}