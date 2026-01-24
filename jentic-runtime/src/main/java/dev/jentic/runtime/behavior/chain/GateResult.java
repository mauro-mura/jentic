package dev.jentic.runtime.behavior.chain;

/**
 * Result of a Gate validation.
 * 
 * @param passed whether validation passed
 * @param message failure message (null if passed)
 * 
 * @since 0.7.0
 */
public record GateResult(boolean passed, String message) {
    
    /**
     * Creates a passing result.
     * 
     * @return passing result
     */
    public static GateResult pass() {
        return new GateResult(true, null);
    }
    
    /**
     * Creates a failing result with message.
     * 
     * @param message failure reason
     * @return failing result
     */
    public static GateResult fail(String message) {
        return new GateResult(false, message);
    }
}
