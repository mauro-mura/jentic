package dev.jentic.runtime.behavior.chain;

/**
 * Action to take when a Gate validation fails.
 * 
 * @since 0.7.0
 */
public enum GateAction {
    
    /**
     * Abort the entire chain on gate failure.
     * Throws GateValidationException immediately.
     */
    ABORT,
    
    /**
     * Retry the current step on gate failure.
     * Respects maxRetryAttempts configuration.
     * If retries exhausted, throws GateValidationException.
     */
    RETRY,
    
    /**
     * Continue to next step despite gate failure.
     * Logs a warning but does not throw exception.
     * Use with caution.
     */
    CONTINUE
}
