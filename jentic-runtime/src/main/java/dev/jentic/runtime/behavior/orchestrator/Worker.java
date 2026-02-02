package dev.jentic.runtime.behavior.orchestrator;

import java.util.Set;

/**
 * Worker interface for orchestrator pattern.
 * Workers execute specialized subtasks assigned by orchestrator.
 * 
 * @since 0.7.0
 */
public interface Worker {
    
    /**
     * Worker unique name.
     */
    String getName();
    
    /**
     * Capabilities/skills this worker can handle.
     */
    Set<String> getCapabilities();
    
    /**
     * Execute a subtask.
     * 
     * @param task the subtask to execute
     * @return execution result
     */
    SubTaskResult execute(SubTask task);
}
