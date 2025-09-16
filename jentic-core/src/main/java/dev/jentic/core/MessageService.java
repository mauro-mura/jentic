package dev.jentic.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Message service interface for agent communication.
 * Implementations can be in-memory, JMS-based, Kafka-based, etc.
 */
public interface MessageService {
    
    /**
     * Send a message asynchronously
     * @param message the message to send
     * @return CompletableFuture that completes when message is sent
     */
    CompletableFuture<Void> send(Message message);
    
    /**
     * Subscribe to messages on a topic
     * @param topic the topic to subscribe to
     * @param handler the message handler
     * @return subscription ID for later unsubscription
     */
    String subscribe(String topic, MessageHandler handler);
    
    /**
     * Subscribe to messages matching a predicate
     * @param filter the message filter predicate
     * @param handler the message handler
     * @return subscription ID for later unsubscription
     */
    String subscribe(Predicate<Message> filter, MessageHandler handler);
    
    /**
     * Unsubscribe from a topic or filter
     * @param subscriptionId the subscription ID returned from subscribe
     */
    void unsubscribe(String subscriptionId);
    
    /**
     * Send a message and wait for a reply with correlation ID
     * @param message the request message
     * @param timeout timeout in milliseconds
     * @return CompletableFuture with the reply message
     */
    CompletableFuture<Message> sendAndWait(Message message, long timeout);
}