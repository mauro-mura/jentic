package dev.jentic.core;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Query object for searching agents in the directory.
 * Supports filtering by various criteria.
 */
public record AgentQuery(
    String agentType,
    Set<String> requiredCapabilities,
    AgentStatus status,
    Predicate<AgentDescriptor> customFilter
) {
    
    /**
     * Create a query builder
     * @return new query builder
     */
    public static AgentQueryBuilder builder() {
        return new AgentQueryBuilder();
    }
    
    /**
     * Create a simple query by agent type
     * @param agentType the agent type to search for
     * @return new agent query
     */
    public static AgentQuery byType(String agentType) {
        return new AgentQuery(agentType, null, null, null);
    }
    
    /**
     * Create a simple query by status
     * @param status the agent status to search for
     * @return new agent query
     */
    public static AgentQuery byStatus(AgentStatus status) {
        return new AgentQuery(null, null, status, null);
    }
    
    /**
     * Create a query for agents with specific capabilities
     * @param capabilities the required capabilities
     * @return new agent query
     */
    public static AgentQuery withCapabilities(Set<String> capabilities) {
        return new AgentQuery(null, capabilities, null, null);
    }
    
    public static class AgentQueryBuilder {
        private String agentType;
        private Set<String> requiredCapabilities;
        private AgentStatus status;
        private Predicate<AgentDescriptor> customFilter;
        
        public AgentQueryBuilder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }
        
        public AgentQueryBuilder requiredCapabilities(Set<String> capabilities) {
            this.requiredCapabilities = capabilities;
            return this;
        }
        
        public AgentQueryBuilder requiredCapability(String capability) {
            if (this.requiredCapabilities == null) {
                this.requiredCapabilities = Set.of(capability);
            } else {
                var newCapabilities = new java.util.HashSet<>(this.requiredCapabilities);
                newCapabilities.add(capability);
                this.requiredCapabilities = Set.copyOf(newCapabilities);
            }
            return this;
        }
        
        public AgentQueryBuilder status(AgentStatus status) {
            this.status = status;
            return this;
        }
        
        public AgentQueryBuilder customFilter(Predicate<AgentDescriptor> filter) {
            this.customFilter = filter;
            return this;
        }
        
        public AgentQuery build() {
            return new AgentQuery(agentType, requiredCapabilities, status, customFilter);
        }
    }
}