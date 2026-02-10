package dev.jentic.core.conversation;

import java.util.Map;

/**
 * Represents classified user intent.
 * 
 * @param name Intent name (e.g., "simple_query", "multilingual")
 * @param requiredCapability The capability needed to handle this intent
 * @param parameters Additional intent parameters extracted from a user message
 * @since 0.7.0
 */
public record Intent(
    String name,
    String requiredCapability,
    Map<String, Object> parameters
) {
    
    public Intent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Intent name cannot be null or blank");
        }
        if (requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("Required capability cannot be null or blank");
        }
        if (parameters == null) {
            parameters = Map.of();
        }
    }
    
    /**
     * Creates a simple intent without parameters.
     */
    public static Intent simple(String name, String capability) {
        return new Intent(name, capability, Map.of());
    }
    
    /**
     * Creates an intent with parameters.
     */
    public static Intent withParams(String name, String capability, Map<String, Object> params) {
        return new Intent(name, capability, params);
    }
}
