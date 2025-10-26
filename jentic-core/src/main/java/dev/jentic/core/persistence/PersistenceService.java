package dev.jentic.core.persistence;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for agent state persistence.
 * Implementations can use files, databases, or other storage mechanisms.
 */
public interface PersistenceService {
    
    /**
     * Save agent state asynchronously
     * 
     * @param agentId the agent identifier
     * @param state the state to persist
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> saveState(String agentId, AgentState state);
    
    /**
     * Load agent state asynchronously
     * 
     * @param agentId the agent identifier
     * @return CompletableFuture containing the agent state if found
     */
    CompletableFuture<Optional<AgentState>> loadState(String agentId);
    
    /**
     * Delete agent state
     * 
     * @param agentId the agent identifier
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> deleteState(String agentId);
    
    /**
     * Check if state exists for an agent
     * 
     * @param agentId the agent identifier
     * @return CompletableFuture with true if state exists
     */
    CompletableFuture<Boolean> existsState(String agentId);
    
    /**
     * Create a snapshot of agent state
     * 
     * @param agentId the agent identifier
     * @param snapshotId optional snapshot identifier
     * @return CompletableFuture containing the snapshot identifier
     */
    CompletableFuture<String> createSnapshot(String agentId, String snapshotId);
    
    /**
     * Restore agent state from a snapshot
     * 
     * @param agentId the agent identifier
     * @param snapshotId the snapshot identifier
     * @return CompletableFuture containing the restored state
     */
    CompletableFuture<Optional<AgentState>> restoreSnapshot(String agentId, String snapshotId);
    
    /**
     * List available snapshots for an agent
     * 
     * @param agentId the agent identifier
     * @return CompletableFuture containing list of snapshot identifiers
     */
    CompletableFuture<java.util.List<String>> listSnapshots(String agentId);
}