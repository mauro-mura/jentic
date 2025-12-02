package dev.jentic.tools.console;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.core.MessageService;
import dev.jentic.runtime.messaging.MessageHistoryService;
import dev.jentic.core.console.ConsoleEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * MessageService decorator that stores all sent messages in a history service.
 *
 * <p>This wrapper intercepts all send() calls and stores the messages
 * before delegating to the underlying MessageService.
 *
 * @since 0.4.0
 */
public class StoringMessageService implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(StoringMessageService.class);

    private final MessageService delegate;
    private final MessageHistoryService messageHistory;
    private volatile ConsoleEventListener eventListener;

    /**
     * Creates a storing message service.
     *
     * @param delegate the underlying message service
     * @param messageHistory the history service to store messages
     */
    public StoringMessageService(MessageService delegate, MessageHistoryService messageHistory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.messageHistory = Objects.requireNonNull(messageHistory, "messageHistory cannot be null");
        this.eventListener = eventListener;
        log.info("StoringMessageService initialized (eventListener={})",
                eventListener != null ? "yes" : "no");
    }

    /**
     * Sets the event listener for WebSocket notifications.
     * Can be called after construction to wire up the WebSocketHandler.
     */
    public void setEventListener(ConsoleEventListener eventListener) {
        this.eventListener = eventListener;
        log.debug("Event listener set: {}", eventListener != null ? eventListener.getClass().getSimpleName() : "null");
    }

    @Override
    public CompletableFuture<Void> send(Message message) {
        // Store message before sending
        if (message != null) {
            messageHistory.store(message);

            // Notify WebSocket listener
            if (eventListener != null) {
                try {
                    eventListener.onMessageSent(
                            message.id(),
                            message.topic(),
                            message.senderId()
                    );
                } catch (Exception e) {
                    log.warn("Failed to notify event listener: {}", e.getMessage());
                }
            }

            log.trace("Processed message: id={}, topic={}", message.id(), message.topic());
        }
        return delegate.send(message);
    }

    @Override
    public String subscribe(String topic, MessageHandler handler) {
        return delegate.subscribe(topic, handler);
    }

    @Override
    public String subscribe(Predicate<Message> filter, MessageHandler handler) {
        return delegate.subscribe(filter, handler);
    }

    @Override
    public String subscribeToReceiver(String receiverId, MessageHandler handler) {
        return delegate.subscribeToReceiver(receiverId, handler);
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        delegate.unsubscribe(subscriptionId);
    }

    @Override
    public CompletableFuture<Message> sendAndWait(Message message, long timeout) {
        // Store message before sending
        if (message != null) {
            messageHistory.store(message);
        }
        return delegate.sendAndWait(message, timeout);
    }

    /**
     * Gets the underlying message service.
     */
    public MessageService getDelegate() {
        return delegate;
    }

    /**
     * Gets the message history service.
     */
    public MessageHistoryService getMessageHistory() {
        return messageHistory;
    }
}