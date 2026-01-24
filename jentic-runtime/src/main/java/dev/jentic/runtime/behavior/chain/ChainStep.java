package dev.jentic.runtime.behavior.chain;

/**
 * Represents a single step in a ChainBehavior sequence.
 * 
 * <p>Each step has:
 * <ul>
 *   <li>A unique name for identification</li>
 *   <li>A prompt template supporting ${variable} substitution</li>
 *   <li>An optional gate for output validation</li>
 *   <li>An optional gate action override</li>
 * </ul>
 * 
 * @param name unique step identifier
 * @param prompt template with ${variable} placeholders
 * @param gate validation gate (null = no validation)
 * @param gateAction action override on gate failure (null = use default)
 * 
 * @since 0.7.0
 */
public record ChainStep(
    String name,
    String prompt,
    Gate gate,
    GateAction gateAction
) {
    public ChainStep {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Step name cannot be null or blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Step prompt cannot be null or blank");
        }
    }
}
