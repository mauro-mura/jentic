package dev.jentic.tools.console;

import dev.jentic.core.console.ConsoleEventListener;
import dev.jentic.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Console event listener that stores messages in a history service.
 *
 * <p>This listener acts as a decorator, forwarding events to a delegate
 * while also storing message-related events for later retrieval.
 *
 * @since 0.4.0
 */
public class MessageStoringEventListener implements ConsoleEventListener {

    private static final Logger log = LoggerFactory.getLogger(MessageStoringEventListener.class);

    private final ConsoleEventListener delegate;
    private final MessageHistoryService messageHistory;

    /**
     * Creates a listener that only stores messages, without delegation.
     *
     * @param messageHistory the history service to store messages
     */
    public MessageStoringEventListener(MessageHistoryService messageHistory) {
        this(ConsoleEventListener.noOp(), messageHistory);
    }

    /**
     * Creates a listener that delegates events and stores messages.
     *
     * @param delegate the listener to delegate events to
     * @param messageHistory the history service to store messages
     */
    public MessageStoringEventListener(ConsoleEventListener delegate, 
                                       MessageHistoryService messageHistory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.messageHistory = Objects.requireNonNull(messageHistory, "messageHistory cannot be null");
    }

    @Override
    public void onAgentStarted(String agentId, String agentName) {
        delegate.onAgentStarted(agentId, agentName);
    }

    @Override
    public void onAgentStopped(String agentId, String agentName) {
        delegate.onAgentStopped(agentId, agentName);
    }

    @Override
    public void onMessageSent(String messageId, String topic, String senderId) {
        delegate.onMessageSent(messageId, topic, senderId);
        log.trace("Message sent event: id={}, topic={}, sender={}", messageId, topic, senderId);
    }

    @Override
    public void onError(String source, String message) {
        delegate.onError(source, message);
    }

    /**
     * Stores a full message in history.
     *
     * @param message the message to store
     */
    public void storeMessage(Message message) {
        if (message != null) {
            messageHistory.store(message);
            log.debug("Stored message: id={}, topic={}", message.id(), message.topic());
        }
    }

    /**
     * Gets the underlying message history service.
     *
     * @return the message history service
     */
    public MessageHistoryService getMessageHistory() {
        return messageHistory;
    }

    /**
     * Gets the delegate listener.
     *
     * @return the delegate
     */
    public ConsoleEventListener getDelegate() {
        return delegate;
    }
}