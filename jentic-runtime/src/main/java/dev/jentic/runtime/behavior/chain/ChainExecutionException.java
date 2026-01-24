package dev.jentic.runtime.behavior.chain;

/**
 * Exception thrown when ChainBehavior execution fails.
 * 
 * @since 0.7.0
 */
public class ChainExecutionException extends RuntimeException {
    
    public ChainExecutionException(String message) {
        super(message);
    }
    
    public ChainExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
