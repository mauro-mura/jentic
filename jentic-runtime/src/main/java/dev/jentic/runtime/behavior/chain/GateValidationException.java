package dev.jentic.runtime.behavior.chain;

/**
 * Exception thrown when a Gate validation fails.
 * 
 * @since 0.7.0
 */
public class GateValidationException extends RuntimeException {
    
    public GateValidationException(String message) {
        super(message);
    }
    
    public GateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
