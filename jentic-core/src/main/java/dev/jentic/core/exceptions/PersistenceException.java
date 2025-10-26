package dev.jentic.core.exceptions;

/**
 * Exception thrown during persistence operations
 */
public class PersistenceException extends RuntimeException {
    
    private final String agentId;
    private final PersistenceOperation operation;
    
    public PersistenceException(String agentId, PersistenceOperation operation, String message) {
        super(message);
        this.agentId = agentId;
        this.operation = operation;
    }
    
    public PersistenceException(String agentId, PersistenceOperation operation, 
                               String message, Throwable cause) {
        super(message, cause);
        this.agentId = agentId;
        this.operation = operation;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public PersistenceOperation getOperation() {
        return operation;
    }
    
    public enum PersistenceOperation {
        SAVE, LOAD, DELETE, SNAPSHOT, RESTORE
    }
}