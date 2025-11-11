package dev.jentic.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Directory service for agent discovery, registration, and lookup within
 * the Jentic multi-agent framework.
 *
 * <p>The {@code AgentDirectory} provides a centralized registry where agents
 * can advertise their presence, capabilities, and current status. This enables
 * dynamic agent discovery and facilitates coordination in distributed or
 * multi-agent systems.
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Registration</strong> - Agents register themselves on startup</li>
 *   <li><strong>Discovery</strong> - Agents find other agents by ID, type, or capabilities</li>
 *   <li><strong>Health Monitoring</strong> - Track agent status and availability</li>
 *   <li><strong>Deregistration</strong> - Agents unregister on shutdown</li>
 * </ul>
 *
 * <p><strong>Implementation Strategies:</strong>
 * <table border="1">
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Use Case</th>
 *     <th>Implementation</th>
 *   </tr>
 *   <tr>
 *     <td>In-Memory</td>
 *     <td>Single-process applications, testing</td>
 *     <td>Fast, non-persistent, ConcurrentHashMap-based</td>
 *   </tr>
 *   <tr>
 *     <td>Database</td>
 *     <td>Multi-process, persistent directory</td>
 *     <td>SQL/NoSQL backed directory</td>
 *   </tr>
 *   <tr>
 *     <td>Distributed</td>
 *     <td>Microservices, cloud deployments</td>
 *     <td>Redis, Consul, etcd, ZooKeeper</td>
 *   </tr>
 * </table>
 *
 * <p><strong>Concurrency:</strong> All methods return {@code CompletableFuture}
 * to support asynchronous operations. This is crucial for distributed
 * implementations where directory operations involve network I/O.
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe and
 * handle concurrent registration/deregistration correctly.
 *
 * <p><strong>Example Usage:</strong>
 * <pre>{@code
 * // Create directory
 * AgentDirectory directory = new LocalAgentDirectory();
 *
 * // Register an agent
 * AgentDescriptor descriptor = AgentDescriptor.builder("order-processor")
 *     .agentName("Order Processor")
 *     .agentType("processor")
 *     .capability("order-processing")
 *     .capability("payment-verification")
 *     .status(AgentStatus.RUNNING)
 *     .build();
 *
 * directory.register(descriptor).join();
 *
 * // Find agents by type
 * List<AgentDescriptor> processors = directory
 *     .findAgents(AgentQuery.byType("processor"))
 *     .join();
 *
 * // Find agents with specific capability
 * List<AgentDescriptor> paymentAgents = directory
 *     .findAgents(AgentQuery.withCapabilities(Set.of("payment-verification")))
 *     .join();
 *
 * // Update agent status (health check)
 * directory.updateStatus("order-processor", AgentStatus.RUNNING)
 *     .thenRun(() -> log.debug("Status updated"));
 *
 * // Cleanup on shutdown
 * directory.unregister("order-processor").join();
 * }</pre>
 *
 * <p><strong>Health Monitoring Pattern:</strong>
 * <pre>{@code
 * // Periodic health check
 * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
 * scheduler.scheduleAtFixedRate(() -> {
 *     directory.updateStatus(agentId, AgentStatus.RUNNING);
 * }, 0, 30, TimeUnit.SECONDS);
 *
 * // On error, update status
 * try {
 *     performOperation();
 * } catch (Exception e) {
 *     directory.updateStatus(agentId, AgentStatus.ERROR);
 * }
 * }</pre>
 *
 * <p><strong>Integration with Runtime:</strong> The directory is typically
 * managed by {@code JenticRuntime}, which automatically handles agent
 * registration/deregistration during the agent lifecycle.
 *
 * @since 0.1.0
 * @see AgentDescriptor
 * @see AgentQuery
 * @see AgentStatus
 */
public interface AgentDirectory {

    /**
     * Registers an agent in the directory, making it discoverable by other agents.
     *
     * <p>Registration typically occurs when an agent starts up. The descriptor
     * contains all metadata needed for discovery: ID, name, type, capabilities,
     * status, and custom metadata.
     *
     * <p><strong>Idempotency:</strong> Registering an agent that is already
     * registered should either:
     * <ul>
     *   <li>Update the existing entry with new information (recommended)</li>
     *   <li>Succeed as a no-op if the descriptor is identical</li>
     *   <li>Fail with an exception (least flexible)</li>
     * </ul>
     *
     * <p><strong>Atomicity:</strong> Registration should be atomic - either the
     * agent is fully registered or not at all. Partial registrations should be
     * rolled back on failure.
     *
     * <p><strong>Distributed Considerations:</strong> In distributed directories,
     * ensure proper handling of:
     * <ul>
     *   <li>Network partitions (CAP theorem tradeoffs)</li>
     *   <li>Stale registrations (TTL, lease mechanisms)</li>
     *   <li>Split-brain scenarios (conflict resolution)</li>
     * </ul>
     *
     * @param descriptor the agent descriptor containing registration metadata, must not be null
     * @return a {@code CompletableFuture} that completes when registration is successful,
     *         or completes exceptionally if registration fails
     * @throws NullPointerException if descriptor is null
     * @see #unregister(String)
     * @see AgentDescriptor
     */
    CompletableFuture<Void> register(AgentDescriptor descriptor);

    /**
     * Unregisters an agent from the directory, removing it from discovery.
     *
     * <p>Unregistration typically occurs during agent shutdown. After unregistration,
     * the agent will no longer appear in directory queries.
     *
     * <p><strong>Idempotency:</strong> Unregistering an agent that is not registered
     * should succeed as a no-op rather than failing with an exception.
     *
     * <p><strong>Cleanup:</strong> Implementations should clean up:
     * <ul>
     *   <li>Directory entries</li>
     *   <li>Index entries (by type, capability, etc.)</li>
     *   <li>Associated resources (locks, leases, etc.)</li>
     * </ul>
     *
     * <p><strong>Graceful Degradation:</strong> If unregistration fails (e.g., due
     * to network issues in distributed systems), consider:
     * <ul>
     *   <li>Implementing TTL/lease mechanisms for automatic cleanup</li>
     *   <li>Background cleanup processes for stale entries</li>
     *   <li>Retry logic with exponential backoff</li>
     * </ul>
     *
     * @param agentId the unique identifier of the agent to unregister, must not be null
     * @return a {@code CompletableFuture} that completes when unregistration is successful,
     *         or completes exceptionally if unregistration fails
     * @throws NullPointerException if agentId is null
     * @see #register(AgentDescriptor)
     */
    CompletableFuture<Void> unregister(String agentId);

    /**
     * Finds an agent by its unique identifier.
     *
     * <p>This is the fastest lookup method as it uses the primary key (agent ID).
     * Use this when you know the exact agent ID, such as when sending directed
     * messages or checking a specific agent's status.
     *
     * <p><strong>Consistency:</strong> In distributed directories, this method
     * may return stale data depending on the consistency model:
     * <ul>
     *   <li><strong>Strong consistency</strong> - Always returns latest data (slower)</li>
     *   <li><strong>Eventual consistency</strong> - May return stale data (faster)</li>
     * </ul>
     *
     * <p><strong>Not Found:</strong> If no agent exists with the given ID, the
     * returned future completes with an empty {@code Optional}.
     *
     * @param agentId the unique identifier of the agent to find, must not be null
     * @return a {@code CompletableFuture} containing an {@code Optional} with the
     *         agent descriptor if found, or empty if not found. Never returns null.
     * @throws NullPointerException if agentId is null
     * @see #findAgents(AgentQuery)
     */
    CompletableFuture<Optional<AgentDescriptor>> findById(String agentId);

    /**
     * Finds all agents matching the specified query criteria.
     *
     * <p>This method supports complex queries combining multiple criteria:
     * <ul>
     *   <li>Agent type (e.g., "processor", "monitor")</li>
     *   <li>Required capabilities (e.g., "payment-processing")</li>
     *   <li>Status (e.g., RUNNING, IDLE)</li>
     *   <li>Custom predicates for complex filtering</li>
     * </ul>
     *
     * <p><strong>Query Execution:</strong> Queries are evaluated in this order:
     * <ol>
     *   <li>Type filtering (index-based, fast)</li>
     *   <li>Capability filtering (index-based, fast)</li>
     *   <li>Status filtering (in-memory, fast)</li>
     *   <li>Custom predicate (in-memory, potentially slow)</li>
     * </ol>
     *
     * <p><strong>Performance:</strong> For large directories, consider:
     * <ul>
     *   <li>Indexing frequently queried fields (type, capabilities)</li>
     *   <li>Caching query results for repeated queries</li>
     *   <li>Pagination for queries returning many results</li>
     * </ul>
     *
     * <p><strong>Empty Results:</strong> If no agents match the query, returns
     * an empty list (not null).
     *
     * <p>Examples:
     * <pre>{@code
     * // Find all processors
     * directory.findAgents(AgentQuery.byType("processor"))
     *     .thenAccept(agents -> agents.forEach(this::notifyProcessor));
     *
     * // Find running agents with payment capability
     * directory.findAgents(
     *     AgentQuery.builder()
     *         .status(AgentStatus.RUNNING)
     *         .requiredCapability("payment-processing")
     *         .build()
     * ).thenAccept(this::distributePaymentTasks);
     *
     * // Complex custom query
     * directory.findAgents(
     *     AgentQuery.builder()
     *         .customFilter(desc ->
     *             desc.agentType().startsWith("processor-") &&
     *             desc.metadata().containsKey("priority") &&
     *             Integer.parseInt(desc.metadata().get("priority")) > 5
     *         )
     *         .build()
     * );
     * }</pre>
     *
     * @param query the search query specifying filter criteria, must not be null
     * @return a {@code CompletableFuture} containing a list of matching agent descriptors,
     *         possibly empty but never null
     * @throws NullPointerException if query is null
     * @see AgentQuery
     * @see #listAll()
     */
    CompletableFuture<List<AgentDescriptor>> findAgents(AgentQuery query);

    /**
     * Lists all registered agents in the directory.
     *
     * <p>This method returns every agent regardless of type, status, or capabilities.
     * Use with caution in large directories as it may return many results.
     *
     * <p><strong>Performance Warning:</strong> In production systems with many agents,
     * prefer filtered queries using {@link #findAgents(AgentQuery)} to reduce
     * data transfer and memory usage.
     *
     * <p><strong>Ordering:</strong> The order of agents in the returned list is
     * implementation-dependent. Do not rely on any specific ordering unless
     * documented by the implementation.
     *
     * <p><strong>Snapshot Semantics:</strong> The returned list represents a
     * snapshot at query time. Agents may be registered or unregistered while
     * the list is being used, making it potentially stale immediately.
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Administrative dashboards showing all agents</li>
     *   <li>Debugging and monitoring tools</li>
     *   <li>Bulk operations on all agents (use with care)</li>
     *   <li>System health checks</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * // Get system overview
     * directory.listAll().thenAccept(agents -> {
     *     long running = agents.stream()
     *         .filter(a -> a.status() == AgentStatus.RUNNING)
     *         .count();
     *     log.info("System has {} running agents out of {} total",
     *              running, agents.size());
     * });
     * }</pre>
     *
     * @return a {@code CompletableFuture} containing a list of all registered agents,
     *         possibly empty but never null
     * @see #findAgents(AgentQuery)
     */
    CompletableFuture<List<AgentDescriptor>> listAll();

    /**
     * Updates the status of a registered agent, typically for health monitoring.
     *
     * <p>This method provides a lightweight way to update only the agent's status
     * without re-registering the entire descriptor. This is commonly used for:
     * <ul>
     *   <li><strong>Heartbeats</strong> - Periodic updates confirming agent is alive</li>
     *   <li><strong>State transitions</strong> - Notifying status changes (RUNNING → IDLE)</li>
     *   <li><strong>Error reporting</strong> - Signaling when an agent encounters problems</li>
     * </ul>
     *
     * <p><strong>Health Monitoring Pattern:</strong>
     * <pre>{@code
     * // Send heartbeat every 30 seconds
     * scheduler.scheduleAtFixedRate(() -> {
     *     try {
     *         directory.updateStatus(agentId, AgentStatus.RUNNING)
     *             .exceptionally(ex -> {
     *                 log.warn("Failed to update status", ex);
     *                 return null;
     *             });
     *     } catch (Exception e) {
     *         log.error("Heartbeat failed", e);
     *     }
     * }, 0, 30, TimeUnit.SECONDS);
     * }</pre>
     *
     * <p><strong>Timestamp:</strong> Implementations should update the agent's
     * {@code lastSeen} timestamp to reflect when the status update was received.
     *
     * <p><strong>Not Found:</strong> If the agent ID doesn't exist in the directory,
     * implementations should either:
     * <ul>
     *   <li>Fail with an exception (strict mode)</li>
     *   <li>Succeed as a no-op with a warning log (lenient mode)</li>
     * </ul>
     *
     * <p><strong>Distributed Considerations:</strong> In distributed directories:
     * <ul>
     *   <li>Status updates may be lost during network partitions</li>
     *   <li>Consider implementing TTL-based expiry</li>
     *   <li>Use monotonic timestamps to prevent out-of-order updates</li>
     * </ul>
     *
     * @param agentId the unique identifier of the agent whose status to update, must not be null
     * @param status the new status to set, must not be null
     * @return a {@code CompletableFuture} that completes when the status is updated,
     *         or completes exceptionally if the update fails
     * @throws NullPointerException if agentId or status is null
     * @see AgentStatus
     * @see AgentDescriptor#lastSeen()
     */
    CompletableFuture<Void> updateStatus(String agentId, AgentStatus status);
}