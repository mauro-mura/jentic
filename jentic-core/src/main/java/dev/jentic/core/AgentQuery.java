package dev.jentic.core;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Query object for searching agents in the {@link AgentDirectory}.
 *
 * <p>This immutable record encapsulates multiple search criteria that can be
 * combined to find agents matching specific requirements. Queries support
 * filtering by:
 * <ul>
 *   <li><strong>Agent Type</strong> - Logical grouping (e.g., "processor", "monitor")</li>
 *   <li><strong>Capabilities</strong> - Required skills or features</li>
 *   <li><strong>Status</strong> - Current operational state (RUNNING, IDLE, etc.)</li>
 *   <li><strong>Custom Filter</strong> - Arbitrary predicate for complex queries</li>
 * </ul>
 *
 * <p><strong>Query Execution Model:</strong>
 * Queries are typically evaluated in an optimized order:
 * <ol>
 *   <li>Type filtering (usually indexed) - O(log n)</li>
 *   <li>Capability filtering (usually indexed) - O(log n)</li>
 *   <li>Status filtering (in-memory) - O(n)</li>
 *   <li>Custom predicate (in-memory) - O(n)</li>
 * </ol>
 *
 * <p><strong>Null Semantics:</strong> All fields are nullable. A {@code null}
 * value means "don't filter by this criterion":
 * <ul>
 *   <li>{@code agentType = null} → match any type</li>
 *   <li>{@code requiredCapabilities = null} → no capability requirements</li>
 *   <li>{@code status = null} → match any status</li>
 *   <li>{@code customFilter = null} → no custom filtering</li>
 * </ul>
 *
 * <p><strong>Combining Criteria:</strong> When multiple criteria are specified,
 * they are combined with AND logic. An agent must satisfy ALL non-null criteria
 * to match the query.
 *
 * <p><strong>Immutability:</strong> {@code AgentQuery} is immutable. Use the
 * builder pattern to construct queries with multiple criteria.
 *
 * <p><strong>Example Usage:</strong>
 * <pre>{@code
 * // Simple queries using factory methods
 * AgentQuery byType = AgentQuery.byType("order-processor");
 * AgentQuery byStatus = AgentQuery.byStatus(AgentStatus.RUNNING);
 * AgentQuery byCapabilities = AgentQuery.withCapabilities(
 *     Set.of("payment-processing", "fraud-detection")
 * );
 *
 * // Complex query using builder
 * AgentQuery complex = AgentQuery.builder()
 *     .agentType("processor")
 *     .requiredCapability("payment-processing")
 *     .requiredCapability("fraud-detection")
 *     .status(AgentStatus.RUNNING)
 *     .customFilter(desc -> {
 *         String priority = desc.metadata().get("priority");
 *         return priority != null && Integer.parseInt(priority) > 7;
 *     })
 *     .build();
 *
 * // Execute query
 * directory.findAgents(complex)
 *     .thenAccept(agents -> {
 *         log.info("Found {} high-priority payment processors", agents.size());
 *         agents.forEach(this::assignTask);
 *     });
 * }</pre>
 *
 * <p><strong>Performance Tips:</strong>
 * <ul>
 *   <li>Use indexed fields (type, capabilities) for filtering when possible</li>
 *   <li>Avoid complex custom predicates on large directories</li>
 *   <li>Cache frequently used queries</li>
 *   <li>Consider pagination for queries returning many results</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This record is immutable and thread-safe.
 * The same query instance can be safely used from multiple threads concurrently.
 *
 * @param agentType optional agent type to filter by (e.g., "processor", "monitor").
 *        When null, agents of all types match.
 * @param requiredCapabilities optional set of capabilities that agents must have.
 *        When null or empty, no capability of filtering is applied. When non-empty,
 *        agents must have ALL specified capabilities to match.
 * @param status optional agent status to filter by (RUNNING, IDLE, etc.).
 *        When null, agents in any status match.
 * @param customFilter optional predicate for complex filtering logic.
 *        When null, no custom filtering is applied. When non-null, only agents
 *        for which the predicate returns {@code true} will match.
 *
 * @since 0.1.0
 * @see AgentDirectory#findAgents(AgentQuery)
 * @see AgentDescriptor
 * @see AgentStatus
 */
public record AgentQuery(
        String agentType,
        Set<String> requiredCapabilities,
        AgentStatus status,
        Predicate<AgentDescriptor> customFilter
) {

    /**
     * Creates a new query builder for constructing complex queries.
     *
     * <p>The builder pattern is recommended when you need to combine multiple
     * search criteria. It provides a fluent API that's more readable than
     * constructor calls with many parameters.
     *
     * <p>Example:
     * <pre>{@code
     * AgentQuery query = AgentQuery.builder()
     *     .agentType("payment-processor")
     *     .requiredCapability("stripe-api")
     *     .requiredCapability("paypal-api")
     *     .status(AgentStatus.RUNNING)
     *     .build();
     * }</pre>
     *
     * @return a new {@link AgentQueryBuilder} instance
     */
    public static AgentQueryBuilder builder() {
        return new AgentQueryBuilder();
    }

    /**
     * Creates a simple query that filters by agent type only.
     *
     * <p>This is a convenience factory method for the common case of finding
     * all agents of a specific type, regardless of their capabilities or status.
     *
     * <p>Example:
     * <pre>{@code
     * // Find all order processors
     * AgentQuery query = AgentQuery.byType("order-processor");
     * List<AgentDescriptor> processors = directory.findAgents(query).join();
     * }</pre>
     *
     * @param agentType the agent type to search for, must not be null
     * @return a new query matching only the specified agent type
     * @throws NullPointerException if agentType is null
     */
    public static AgentQuery byType(String agentType) {
        return new AgentQuery(agentType, null, null, null);
    }

    /**
     * Creates a simple query that filters by agent status only.
     *
     * <p>This is useful for operational queries, such as finding all running
     * agents, identifying idle agents for task assignment, or locating agents
     * in error states for debugging.
     *
     * <p>Example:
     * <pre>{@code
     * // Find all running agents for health dashboard
     * AgentQuery query = AgentQuery.byStatus(AgentStatus.RUNNING);
     * List<AgentDescriptor> running = directory.findAgents(query).join();
     *
     * // Find agents in error state for alerting
     * AgentQuery errorQuery = AgentQuery.byStatus(AgentStatus.ERROR);
     * directory.findAgents(errorQuery)
     *     .thenAccept(agents -> agents.forEach(this::sendAlert));
     * }</pre>
     *
     * @param status the agent status to search for, must not be null
     * @return a new query matching only the specified status
     * @throws NullPointerException if status is null
     */
    public static AgentQuery byStatus(AgentStatus status) {
        return new AgentQuery(null, null, status, null);
    }

    /**
     * Creates a query that filters by required capabilities.
     *
     * <p>An agent matches this query only if it has ALL of the specified
     * capabilities. This is useful for finding agents that can perform a
     * specific set of tasks.
     *
     * <p>Example:
     * <pre>{@code
     * // Find agents that can process both Stripe and PayPal payments
     * AgentQuery query = AgentQuery.withCapabilities(
     *     Set.of("stripe-payment", "paypal-payment")
     * );
     *
     * // Find a monitoring agent with specific metrics capabilities
     * AgentQuery monitorQuery = AgentQuery.withCapabilities(
     *     Set.of("cpu-monitoring", "memory-monitoring", "disk-monitoring")
     * );
     * }</pre>
     *
     * @param capabilities the set of required capabilities, must not be null or empty
     * @return a new query matching agents with all specified capabilities
     * @throws NullPointerException if capabilities is null
     * @throws IllegalArgumentException if capabilities is empty
     */
    public static AgentQuery withCapabilities(Set<String> capabilities) {
        return new AgentQuery(null, capabilities, null, null);
    }

    /**
     * Fluent builder for constructing {@link AgentQuery} instances with
     * multiple search criteria.
     *
     * <p>The builder accumulates query parameters and creates an immutable
     * {@code AgentQuery} when {@link #build()} is called. This pattern is
     * more readable and maintainable than constructor calls when combining
     * multiple criteria.
     *
     * <p>All builder methods return {@code this} to enable method chaining.
     *
     * <p><strong>Thread Safety:</strong> This builder is NOT thread-safe.
     * Each thread should use its own builder instance.
     */
    public static class AgentQueryBuilder {
        private String agentType;
        private Set<String> requiredCapabilities;
        private AgentStatus status;
        private Predicate<AgentDescriptor> customFilter;

        /**
         * Sets the agent type filter.
         *
         * @param agentType the type to filter by
         * @return this builder for method chaining
         */
        public AgentQueryBuilder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        /**
         * Sets all required capabilities at once, replacing any previously
         * set capabilities.
         *
         * @param capabilities the set of capabilities to require
         * @return this builder for method chaining
         */
        public AgentQueryBuilder requiredCapabilities(Set<String> capabilities) {
            this.requiredCapabilities = capabilities;
            return this;
        }

        /**
         * Adds a single required capability to the query.
         *
         * <p>This method can be called multiple times to add multiple
         * capabilities. The resulting query will match only agents that
         * have ALL added capabilities.
         *
         * @param capability the capability to require
         * @return this builder for method chaining
         */
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

        /**
         * Sets the status filter.
         *
         * @param status the status to filter by
         * @return this builder for method chaining
         */
        public AgentQueryBuilder status(AgentStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets a custom filter predicate for complex query logic.
         *
         * <p>The predicate is evaluated for each agent after all other
         * filters have been applied. Only agents for which the predicate
         * returns {@code true} will be included in the results.
         *
         * <p><strong>Performance Warning:</strong> Custom predicates execute
         * in memory and can be slow for large result sets. Use indexed
         * filters (type, capabilities, status) whenever possible.
         *
         * @param filter the predicate to apply
         * @return this builder for method chaining
         */
        public AgentQueryBuilder customFilter(Predicate<AgentDescriptor> filter) {
            this.customFilter = filter;
            return this;
        }

        /**
         * Builds an immutable {@link AgentQuery} with the accumulated criteria.
         *
         * <p>This method can be called multiple times to create multiple
         * queries with the same criteria.
         *
         * @return a new immutable query instance
         */
        public AgentQuery build() {
            return new AgentQuery(agentType, requiredCapabilities, status, customFilter);
        }
    }
}