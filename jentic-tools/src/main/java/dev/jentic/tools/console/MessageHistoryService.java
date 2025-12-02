package dev.jentic.tools.console;

import dev.jentic.core.Message;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Thread-safe service for storing and querying recent message history.
 *
 * @deprecated Use {@link dev.jentic.runtime.messaging.MessageHistoryService} instead.
 *             This class will be removed in version 0.5.0.
 * @since 0.4.0
 */
@Deprecated(since = "0.4.0", forRemoval = true)
public class MessageHistoryService {

    private final dev.jentic.runtime.messaging.MessageHistoryService delegate;

    public static final int DEFAULT_MAX_SIZE =
            dev.jentic.runtime.messaging.MessageHistoryService.DEFAULT_MAX_SIZE;
    public static final int MIN_SIZE =
            dev.jentic.runtime.messaging.MessageHistoryService.MIN_SIZE;
    public static final int MAX_SIZE =
            dev.jentic.runtime.messaging.MessageHistoryService.MAX_SIZE;

    /**
     * Stored message wrapper - delegates to runtime implementation.
     */
    public record StoredMessage(
            String id,
            String topic,
            String senderId,
            String receiverId,
            String correlationId,
            Object payload,
            Map<String, String> headers,
            Instant timestamp,
            Instant storedAt
    ) {
        public static StoredMessage from(Message message) {
            return new StoredMessage(
                    message.id(),
                    message.topic(),
                    message.senderId(),
                    message.receiverId(),
                    message.correlationId(),
                    message.content(),
                    message.headers(),
                    message.timestamp(),
                    Instant.now()
            );
        }

        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("id", id);
            map.put("topic", topic);
            map.put("senderId", senderId);
            map.put("receiverId", receiverId);
            map.put("correlationId", correlationId);
            map.put("payload", payload);
            map.put("headers", headers);
            map.put("timestamp", timestamp != null ? timestamp.toString() : null);
            map.put("storedAt", storedAt != null ? storedAt.toString() : null);
            return map;
        }

        // Convert from runtime StoredMessage
        static StoredMessage fromRuntime(
                dev.jentic.runtime.messaging.MessageHistoryService.StoredMessage msg) {
            return new StoredMessage(
                    msg.id(), msg.topic(), msg.senderId(), msg.receiverId(),
                    msg.correlationId(), msg.payload(), msg.headers(),
                    msg.timestamp(), msg.storedAt()
            );
        }
    }

    public MessageHistoryService() {
        this.delegate = new dev.jentic.runtime.messaging.MessageHistoryService();
    }

    public MessageHistoryService(int maxSize) {
        this.delegate = new dev.jentic.runtime.messaging.MessageHistoryService(maxSize);
    }

    public void store(Message message) {
        delegate.store(message);
    }

    public List<StoredMessage> getRecent(int limit) {
        return delegate.getRecent(limit).stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public List<StoredMessage> getByTopic(String topic) {
        return delegate.getByTopic(topic).stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public List<StoredMessage> getByTopicPattern(String topicPattern) {
        return delegate.getByTopicPattern(topicPattern).stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public List<StoredMessage> getBySender(String senderId) {
        return delegate.getBySender(senderId).stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public List<StoredMessage> getByTimeRange(Instant from, Instant to) {
        return delegate.getByTimeRange(from, to).stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public List<StoredMessage> getByFilter(Predicate<StoredMessage> predicate) {
        // Note: predicate operates on tools.StoredMessage, need to adapt
        return delegate.getByFilter(msg -> predicate.test(StoredMessage.fromRuntime(msg)))
                .stream()
                .map(StoredMessage::fromRuntime)
                .toList();
    }

    public Optional<StoredMessage> getById(String messageId) {
        return delegate.getById(messageId).map(StoredMessage::fromRuntime);
    }

    public void clear() {
        delegate.clear();
    }

    public int size() {
        return delegate.size();
    }

    public int getMaxSize() {
        return delegate.getMaxSize();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * Get the underlying runtime service.
     * Use this for direct access to the new API.
     */
    public dev.jentic.runtime.messaging.MessageHistoryService getRuntimeService() {
        return delegate;
    }
}