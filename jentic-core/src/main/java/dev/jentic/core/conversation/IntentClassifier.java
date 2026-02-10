package dev.jentic.core.conversation;

import java.util.concurrent.CompletableFuture;

/**
 * Classifies user messages into intents.
 * 
 * @since 0.7.0
 */
public interface IntentClassifier {
    
    /**
     * Classifies a user message into intent.
     * 
     * @param userMessage The message to classify
     * @param conversationId The conversation ID for context retrieval
     * @return Future containing the classified intent
     */
    CompletableFuture<Intent> classify(String userMessage, String conversationId);
}
