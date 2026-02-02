package dev.jentic.runtime.behavior.orchestrator;

/**
 * Exception thrown during orchestration failures.
 * 
 * @since 0.7.0
 */
public class OrchestratorException extends RuntimeException {
    
    public OrchestratorException(String message) {
        super(message);
    }
    
    public OrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
