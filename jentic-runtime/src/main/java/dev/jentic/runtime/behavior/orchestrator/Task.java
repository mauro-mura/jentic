package dev.jentic.runtime.behavior.orchestrator;

import java.util.Map;

/**
 * Main task to be decomposed by orchestrator.
 * 
 * @param id unique task identifier
 * @param description task description
 * @param context additional context/parameters
 * @since 0.7.0
 */
public record Task(
    String id,
    String description,
    Map<String, Object> context
) {
    public Task(String id, String description) {
        this(id, description, Map.of());
    }
}
