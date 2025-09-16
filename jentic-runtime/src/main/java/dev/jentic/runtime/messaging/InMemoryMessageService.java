package dev.jentic.runtime.messaging;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.core.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * In-memory implementation of MessageService.
 * Uses virtual threads for message delivery.
 */
public class InMemoryMessageService implements MessageService {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryMessageService.class);
    
    private final Map<String, List<MessageHandler>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, PredicateSubscription> predicateSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    @Override
    public CompletableFuture<Void> send(Message message) {
        return CompletableFuture.runAsync(() -> {
            log.debug("Sending message: {} to topic: {}", message.id(), message.topic());
            
            // Handle reply messages (with correlation ID)
            if (message.correlationId() != null) {
                CompletableFuture<Message> pendingRequest = pendingRequests.remove(message.correlationId());
                if (pendingRequest != null) {
                    pendingRequest.complete(message);
                    return;
                }
            }
            
            // Deliver to topic subscribers
            if (message.topic() != null) {
                deliverToTopicSubscribers(message);
            }
            
            // Deliver to predicate subscribers
            deliverToPredicateSubscribers(message);
            
        }, VIRTUAL_EXECUTOR);
    }
    
    @Override
    public String subscribe(String topic, MessageHandler handler) {
        String subscriptionId = UUID.randomUUID().toString();
        
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                          .add(handler);
        
        log.debug("Subscribed to topic: {} with subscription ID: {}", topic, subscriptionId);
        return subscriptionId;
    }
    
    @Override
    public String subscribe(Predicate<Message> filter, MessageHandler handler) {
        String subscriptionId = UUID.randomUUID().toString();
        
        predicateSubscriptions.put(subscriptionId, new PredicateSubscription(filter, handler));
        
        log.debug("Subscribed with predicate filter, subscription ID: {}", subscriptionId);
        return subscriptionId;
    }
    
    @Override
    public void unsubscribe(String subscriptionId) {
        // For topic subscriptions, we'd need to track subscription IDs to handlers
        // For simplicity in MVP, we'll remove from predicate subscriptions
        predicateSubscriptions.remove(subscriptionId);
        
        log.debug("Unsubscribed: {}", subscriptionId);
    }
    
    @Override
    public CompletableFuture<Message> sendAndWait(Message message, long timeout) {
        CompletableFuture<Message> replyFuture = new CompletableFuture<>();
        
        // Store the pending request
        pendingRequests.put(message.id(), replyFuture);
        
        // Send the message
        send(message);
        
        // Set up timeout
        CompletableFuture.delayedExecutor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                if (pendingRequests.remove(message.id()) != null) {
                    replyFuture.completeExceptionally(
                        new TimeoutException("No reply received within " + timeout + "ms"));
                }
            });
        
        return replyFuture;
    }
    
    private void deliverToTopicSubscribers(Message message) {
        List<MessageHandler> handlers = topicSubscriptions.get(message.topic());
        if (handlers != null && !handlers.isEmpty()) {
            for (MessageHandler handler : handlers) {
                // Use virtual thread for each handler
                Thread.startVirtualThread(() -> {
                    try {
                        handler.handle(message).join();
                    } catch (Exception e) {
                        log.error("Error handling message: {} by handler: {}", message.id(), handler, e);
                    }
                });
            }
        }
    }
    
    private void deliverToPredicateSubscribers(Message message) {
        for (PredicateSubscription subscription : predicateSubscriptions.values()) {
            if (subscription.filter().test(message)) {
                // Use virtual thread for each handler
                Thread.startVirtualThread(() -> {
                    try {
                        subscription.handler().handle(message).join();
                    } catch (Exception e) {
                        log.error("Error handling message: {} by predicate handler", message.id(), e);
                    }
                });
            }
        }
    }
    
    private record PredicateSubscription(Predicate<Message> filter, MessageHandler handler) {}
}