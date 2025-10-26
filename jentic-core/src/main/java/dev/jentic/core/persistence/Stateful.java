package dev.jentic.core.persistence;

/**
 * Interface for agents that support state persistence.
 * Agents implementing this interface can save and restore their state.
 */
public interface Stateful {
    
    /**
     * Capture current agent state for persistence
     * 
     * @return the current agent state
     */
    AgentState captureState();
    
    /**
     * Restore agent state from persisted data
     * 
     * @param state the state to restore
     */
    void restoreState(AgentState state);
    
    /**
     * Get the version of the current state
     * Used for optimistic locking and conflict detection
     * 
     * @return the state version
     */
    default long getStateVersion() {
        return 1L;
    }
}