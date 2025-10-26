package dev.jentic.core.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jentic.core.AgentStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record representing persisted agent state.
 * Contains all information needed to restore an agent.
 */
public record AgentState(
    @JsonProperty("agentId") String agentId,
    @JsonProperty("agentName") String agentName,
    @JsonProperty("agentType") String agentType,
    @JsonProperty("status") AgentStatus status,
    @JsonProperty("data") Map<String, Object> data,
    @JsonProperty("metadata") Map<String, String> metadata,
    @JsonProperty("version") long version,
    @JsonProperty("savedAt") Instant savedAt
) {
    
    @JsonCreator
    public AgentState(
        @JsonProperty("agentId") String agentId,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("status") AgentStatus status,
        @JsonProperty("data") Map<String, Object> data,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("version") long version,
        @JsonProperty("savedAt") Instant savedAt
    ) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentType = agentType != null ? agentType : "unknown";
        this.status = status != null ? status : AgentStatus.UNKNOWN;
        this.data = data != null ? Map.copyOf(data) : Map.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.version = version;
        this.savedAt = savedAt != null ? savedAt : Instant.now();
    }
    
    /**
     * Create a builder for agent state
     */
    public static AgentStateBuilder builder(String agentId) {
        return new AgentStateBuilder(agentId);
    }
    
    /**
     * Get a data value by key
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * Get a metadata value by key
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    public static class AgentStateBuilder {
        private final String agentId;
        private String agentName;
        private String agentType;
        private AgentStatus status;
        private Map<String, Object> data = Map.of();
        private Map<String, String> metadata = Map.of();
        private long version = 1;
        private Instant savedAt;
        
        private AgentStateBuilder(String agentId) {
            this.agentId = agentId;
        }
        
        public AgentStateBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }
        
        public AgentStateBuilder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }
        
        public AgentStateBuilder status(AgentStatus status) {
            this.status = status;
            return this;
        }
        
        public AgentStateBuilder data(Map<String, Object> data) {
            if (data != null) {
                var allData = new java.util.HashMap<>(this.data);
                allData.putAll(data);
                this.data = Map.copyOf(allData);
            }
            return this;
        }
        
        public AgentStateBuilder data(String key, Object value) {
            if (key != null && value != null) {
                var newData = new java.util.HashMap<>(this.data);
                newData.put(key, value);
                this.data = Map.copyOf(newData);
            }
            return this;
        }
        
        public AgentStateBuilder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                var allMetadata = new java.util.HashMap<>(this.metadata);
                allMetadata.putAll(metadata);
                this.metadata = Map.copyOf(allMetadata);
            }
            return this;
        }
        
        public AgentStateBuilder metadata(String key, String value) {
            if (key != null && value != null) {
                var newMetadata = new java.util.HashMap<>(this.metadata);
                newMetadata.put(key, value);
                this.metadata = Map.copyOf(newMetadata);
            }
            return this;
        }
        
        public AgentStateBuilder version(long version) {
            this.version = version;
            return this;
        }
        
        public AgentStateBuilder savedAt(Instant savedAt) {
            this.savedAt = savedAt;
            return this;
        }
        
        public AgentState build() {
            return new AgentState(agentId, agentName, agentType, status, 
                                 data, metadata, version, savedAt);
        }
    }
}