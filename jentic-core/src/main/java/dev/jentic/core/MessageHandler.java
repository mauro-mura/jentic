package dev.jentic.core;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for handling received messages.
 * Can be implemented as lambda or method reference.
 */
@FunctionalInterface
public interface MessageHandler {
    
    /**
     * Handle a received message
     * @param message the received message
     * @return CompletableFuture that completes when handling is done
     */
    CompletableFuture<Void> handle(Message message);
    
    /**
     * Default implementation for synchronous handlers
     * @param syncHandler synchronous message handler
     * @return async message handler
     */
    static MessageHandler sync(SyncMessageHandler syncHandler) {
        return message -> {
            try {
                syncHandler.handle(message);
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }
    
    @FunctionalInterface
    interface SyncMessageHandler {
        void handle(Message message) throws Exception;
    }
}