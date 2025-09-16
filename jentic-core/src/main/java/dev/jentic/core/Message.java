package dev.jentic.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable message object for agent communication.
 * Uses builder pattern for construction.
 */
public record Message(
    @JsonProperty("id") String id,
    @JsonProperty("topic") String topic,
    @JsonProperty("senderId") String senderId,
    @JsonProperty("receiverId") String receiverId,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("content") Object content,
    @JsonProperty("headers") Map<String, String> headers,
    @JsonProperty("timestamp") Instant timestamp
) {
    
    @JsonCreator
    public Message(
        @JsonProperty("id") String id,
        @JsonProperty("topic") String topic,
        @JsonProperty("senderId") String senderId,
        @JsonProperty("receiverId") String receiverId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("content") Object content,
        @JsonProperty("headers") Map<String, String> headers,
        @JsonProperty("timestamp") Instant timestamp
    ) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.topic = topic;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.correlationId = correlationId;
        this.content = content;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    /**
     * Get content as specific type
     * @param type the target type
     * @return the content cast to the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getContent(Class<T> type) {
        return (T) content;
    }
    
    /**
     * Create a builder for constructing messages
     * @return new message builder
     */
    public static MessageBuilder builder() {
        return new MessageBuilder();
    }
    
    /**
     * Create a reply message with correlation ID set
     * @param content the reply content
     * @return new message builder with correlation ID set
     */
    public MessageBuilder reply(Object content) {
        return builder()
            .correlationId(this.id)
            .receiverId(this.senderId)
            .content(content);
    }
    
    public static class MessageBuilder {
        private String id;
        private String topic;
        private String senderId;
        private String receiverId;
        private String correlationId;
        private Object content;
        private Map<String, String> headers = Map.of();
        private Instant timestamp;
        
        public MessageBuilder id(String id) {
            this.id = id;
            return this;
        }
        
        public MessageBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }
        
        public MessageBuilder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }
        
        public MessageBuilder receiverId(String receiverId) {
            this.receiverId = receiverId;
            return this;
        }
        
        public MessageBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public MessageBuilder content(Object content) {
            this.content = content;
            return this;
        }
        
        public MessageBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }
        
        public MessageBuilder header(String key, String value) {
            if (this.headers.isEmpty()) {
                this.headers = Map.of(key, value);
            } else {
                var newHeaders = new java.util.HashMap<>(this.headers);
                newHeaders.put(key, value);
                this.headers = Map.copyOf(newHeaders);
            }
            return this;
        }
        
        public MessageBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Message build() {
            return new Message(id, topic, senderId, receiverId, correlationId, 
                             content, headers, timestamp);
        }
    }
}