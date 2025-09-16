package dev.jentic.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Immutable descriptor containing agent metadata for discovery.
 */
public record AgentDescriptor(
    @JsonProperty("agentId") String agentId,
    @JsonProperty("agentName") String agentName,
    @JsonProperty("agentType") String agentType,
    @JsonProperty("status") AgentStatus status,
    @JsonProperty("capabilities") Set<String> capabilities,
    @JsonProperty("metadata") Map<String, String> metadata,
    @JsonProperty("registeredAt") Instant registeredAt,
    @JsonProperty("lastSeen") Instant lastSeen
) {
    
    @JsonCreator
    public AgentDescriptor(
        @JsonProperty("agentId") String agentId,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("status") AgentStatus status,
        @JsonProperty("capabilities") Set<String> capabilities,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("registeredAt") Instant registeredAt,
        @JsonProperty("lastSeen") Instant lastSeen
    ) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentType = agentType;
        this.status = status != null ? status : AgentStatus.UNKNOWN;
        this.capabilities = capabilities != null ? Set.copyOf(capabilities) : Set.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.registeredAt = registeredAt != null ? registeredAt : Instant.now();
        this.lastSeen = lastSeen != null ? lastSeen : Instant.now();
    }
    
    /**
     * Create a builder for constructing agent descriptors
     * @param agentId the agent ID
     * @return new agent descriptor builder
     */
    public static AgentDescriptorBuilder builder(String agentId) {
        return new AgentDescriptorBuilder(agentId);
    }
    
    public static class AgentDescriptorBuilder {
        private final String agentId;
        private String agentName;
        private String agentType;
        private AgentStatus status = AgentStatus.UNKNOWN;
        private Set<String> capabilities = Set.of();
        private Map<String, String> metadata = Map.of();
        private Instant registeredAt;
        private Instant lastSeen;
        
        private AgentDescriptorBuilder(String agentId) {
            this.agentId = agentId;
        }
        
        public AgentDescriptorBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }
        
        public AgentDescriptorBuilder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }
        
        public AgentDescriptorBuilder status(AgentStatus status) {
            this.status = status;
            return this;
        }
        
        public AgentDescriptorBuilder capabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }
        
        public AgentDescriptorBuilder capability(String capability) {
            if (this.capabilities.isEmpty()) {
                this.capabilities = Set.of(capability);
            } else {
                var newCapabilities = new java.util.HashSet<>(this.capabilities);
                newCapabilities.add(capability);
                this.capabilities = Set.copyOf(newCapabilities);
            }
            return this;
        }
        
        public AgentDescriptorBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public AgentDescriptorBuilder metadata(String key, String value) {
            if (this.metadata.isEmpty()) {
                this.metadata = Map.of(key, value);
            } else {
                var newMetadata = new java.util.HashMap<>(this.metadata);
                newMetadata.put(key, value);
                this.metadata = Map.copyOf(newMetadata);
            }
            return this;
        }
        
        public AgentDescriptorBuilder registeredAt(Instant registeredAt) {
            this.registeredAt = registeredAt;
            return this;
        }
        
        public AgentDescriptorBuilder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }
        
        public AgentDescriptor build() {
            return new AgentDescriptor(agentId, agentName, agentType, status, 
                                     capabilities, metadata, registeredAt, lastSeen);
        }
    }
}