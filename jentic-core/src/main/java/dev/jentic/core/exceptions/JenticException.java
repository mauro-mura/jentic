package dev.jentic.core.exceptions;

/**
 * Base exception for all Jentic framework exceptions
 */
public class JenticException extends Exception {
    
    public JenticException(String message) {
        super(message);
    }
    
    public JenticException(String message, Throwable cause) {
        super(message, cause);
    }
}