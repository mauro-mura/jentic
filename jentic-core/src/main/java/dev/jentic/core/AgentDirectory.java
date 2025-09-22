package dev.jentic.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Directory service for agent discovery and registration.
 * Implementations can be local, database-based, or distributed.
 */
public interface AgentDirectory {
    
    /**
     * Register an agent in the directory
     * @param descriptor the agent descriptor
     * @return CompletableFuture that completes when the agent is registered
     */
    CompletableFuture<Void> register(AgentDescriptor descriptor);
    
    /**
     * Unregister an agent from the directory
     * @param agentId the ID of the agent to unregister
     * @return CompletableFuture that completes when the agent is unregistered
     */
    CompletableFuture<Void> unregister(String agentId);
    
    /**
     * Find an agent by ID
     * @param agentId the agent ID to search for
     * @return Optional containing the agent descriptor if found
     */
    CompletableFuture<Optional<AgentDescriptor>> findById(String agentId);
    
    /**
     * Find agents matching a query
     * @param query the search query
     * @return List of matching agent descriptors
     */
    CompletableFuture<List<AgentDescriptor>> findAgents(AgentQuery query);
    
    /**
     * List all registered agents
     * @return List of all agent descriptors
     */
    CompletableFuture<List<AgentDescriptor>> listAll();
    
    /**
     * Update agent status (health check)
     * @param agentId the agent ID
     * @param status the new status
     * @return CompletableFuture that completes when status is updated
     */
    CompletableFuture<Void> updateStatus(String agentId, AgentStatus status);
}