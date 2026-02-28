package dev.jentic.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Message service interface for agent communication in the Jentic framework.
 *
 * <p>The {@code MessageService} is the communication backbone that enables agents
 * to exchange messages using various patterns: publish-subscribe, point-to-point,
 * and request-response. It provides a unified API for all messaging needs while
 * remaining implementation-agnostic.
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Message Delivery</strong> - Route messages to subscribers</li>
 *   <li><strong>Subscription Management</strong> - Register/unregister handlers</li>
 *   <li><strong>Pattern Support</strong> - Pub-sub, point-to-point, request-response</li>
 *   <li><strong>Filtering</strong> - Topic-based, predicate-based routing</li>
 * </ul>
 *
 * <p><strong>Implementation Strategies:</strong>
 * <table border="1">
 *   <caption>Message service implementation strategies</caption>
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Characteristics</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>In-Memory</td>
 *     <td>Single-JVM, fast, non-persistent</td>
 *     <td>Development, testing, single-process</td>
 *   </tr>
 *   <tr>
 *     <td>JMS/ActiveMQ</td>
 *     <td>Enterprise messaging, persistent</td>
 *     <td>Multi-process, reliability required</td>
 *   </tr>
 *   <tr>
 *     <td>Kafka</td>
 *     <td>High-throughput, distributed, replay</td>
 *     <td>Event streaming, big data, microservices</td>
 *   </tr>
 *   <tr>
 *     <td>Redis Pub/Sub</td>
 *     <td>Fast, distributed, simple</td>
 *     <td>Real-time notifications, caching layer</td>
 *   </tr>
 * </table>
 *
 * <p><strong>Messaging Patterns:</strong>
 *
 * <p><strong>1. Publish-Subscribe (Topic-Based):</strong>
 * One-to-many communication via topics.
 * <pre>{@code
 * // Subscribe to topic
 * String subId = messageService.subscribe("orders.created", message -> {
 *     OrderData order = message.getContent(OrderData.class);
 *     return processNewOrder(order);
 * });
 *
 * // Publish to topic (received by all subscribers)
 * Message announcement = Message.builder()
 *     .topic("orders.created")
 *     .senderId("order-service")
 *     .content(orderData)
 *     .build();
 *
 * messageService.send(announcement);
 *
 * // Unsubscribe
 * messageService.unsubscribe(subId);
 * }</pre>
 *
 * <p><strong>2. Point-to-Point (Direct Messaging):</strong>
 * One-to-one communication via receiver IDs.
 * <pre>{@code
 * // Subscribe to direct messages
 * String subId = messageService.subscribeToReceiver("payment-service", message -> {
 *     PaymentRequest req = message.getContent(PaymentRequest.class);
 *     return processPayment(req);
 * });
 *
 * // Send direct message (received by specific agent only)
 * Message direct = Message.builder()
 *     .senderId("order-service")
 *     .receiverId("payment-service")
 *     .content(paymentRequest)
 *     .build();
 *
 * messageService.send(direct);
 * }</pre>
 *
 * <p><strong>3. Request-Response (Correlation-Based):</strong>
 * Synchronous-style communication with timeouts.
 * <pre>{@code
 * // Requester: send and wait for response
 * Message request = Message.builder()
 *     .senderId("client")
 *     .receiverId("server")
 *     .content("query data")
 *     .build();
 *
 * CompletableFuture<Message> responseFuture =
 *     messageService.sendAndWait(request, 5000);  // 5 second timeout
 *
 * responseFuture.thenAccept(response -> {
 *     Data result = response.getContent(Data.class);
 *     processResult(result);
 * }).exceptionally(ex -> {
 *     log.error("Request failed or timed out", ex);
 *     return null;
 * });
 *
 * // Responder: handle request and send reply
 * messageService.subscribeToReceiver("server", request -> {
 *     Data result = processQuery(request.content());
 *
 *     // Reply with correlation ID automatically set
 *     Message response = request.reply(result)
 *         .senderId("server")
 *         .build();
 *
 *     return messageService.send(response);
 * });
 * }</pre>
 *
 * <p><strong>4. Predicate-Based Filtering:</strong>
 * Subscribe with custom filtering logic.
 * <pre>{@code
 * // Subscribe to high-priority messages only
 * String subId = messageService.subscribe(
 *     message -> "HIGH".equals(message.headers().get("priority")),
 *     this::handleHighPriority
 * );
 *
 * // Complex filter: orders from specific region
 * messageService.subscribe(
 *     message -> message.topic().startsWith("orders.") &&
 *                "US-WEST".equals(message.headers().get("region")),
 *     this::handleRegionalOrder
 * );
 * }</pre>
 *
 * <p><strong>Concurrency:</strong>
 * All methods return {@code CompletableFuture} to support asynchronous
 * operations. Implementations should:
 * <ul>
 *   <li>Not block the calling thread</li>
 *   <li>Handle concurrent sends and subscribes safely</li>
 *   <li>Process messages concurrently when possible</li>
 *   <li>Use virtual threads or appropriate thread pools</li>
 * </ul>
 *
 * <p><strong>Error Handling:</strong>
 * <ul>
 *   <li>Send failures complete the returned future exceptionally</li>
 *   <li>Handler exceptions are caught and logged</li>
 *   <li>One handler's failure doesn't affect others</li>
 *   <li>Timeout on sendAndWait completes exceptionally</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Use topic-based routing for better scalability than predicates</li>
 *   <li>Avoid heavy processing in handlers; delegate to async workers</li>
 *   <li>Consider message rate and throughput requirements</li>
 *   <li>Monitor subscription count and memory usage</li>
 * </ul>
 *
 * <p><strong>Best Practices:</strong>
 * <ul>
 *   <li>Always set sender ID for traceability</li>
 *   <li>Use meaningful topic names (hierarchical: "orders.created.us-west")</li>
 *   <li>Unsubscribe when handlers are no longer needed</li>
 *   <li>Handle timeouts appropriately in request-response</li>
 *   <li>Use headers for cross-cutting concerns (tracing, priority)</li>
 * </ul>
 *
 * @since 0.1.0
 * @see Message
 * @see MessageHandler
 */
public interface MessageService {

    /**
     * Sends a message asynchronously.
     *
     * <p>This method delivers a message to all subscribers matching the message's
     * routing criteria (topic and/or receiver ID). The message is delivered
     * asynchronously, and the returned future completes when delivery is initiated
     * (not when all handlers complete).
     *
     * <p><strong>Routing Behavior:</strong>
     * <ul>
     *   <li>If {@code topic} is set: delivered to all topic subscribers</li>
     *   <li>If {@code receiverId} is set: delivered to receiver subscribers</li>
     *   <li>If both are set: delivered to both topic and receiver subscribers</li>
     *   <li>Also delivered to predicate subscribers if they match</li>
     * </ul>
     *
     * <p><strong>Delivery Guarantees:</strong>
     * Guarantees depend on the implementation:
     * <ul>
     *   <li><strong>In-Memory</strong>: Best-effort, non-persistent</li>
     *   <li><strong>JMS/ActiveMQ</strong>: At-least-once, persistent</li>
     *   <li><strong>Kafka</strong>: At-least-once, replayable</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong>
     * This method is thread-safe and can be called concurrently from multiple
     * threads.
     *
     * <p><strong>Performance:</strong>
     * This method should be fast and non-blocking. Heavy message serialization
     * or delivery should happen asynchronously.
     *
     * <p><strong>Examples:</strong>
     *
     * <p>Simple send:
     * <pre>{@code
     * Message message = Message.builder()
     *     .topic("events")
     *     .content(eventData)
     *     .build();
     *
     * messageService.send(message);
     * }</pre>
     *
     * <p>Send with completion handling:
     * <pre>{@code
     * messageService.send(message)
     *     .thenRun(() -> log.info("Message sent"))
     *     .exceptionally(ex -> {
     *         log.error("Send failed", ex);
     *         return null;
     *     });
     * }</pre>
     *
     * <p>Synchronous send (wait for completion):
     * <pre>{@code
     * try {
     *     messageService.send(message).join();
     *     log.info("Message delivered");
     * } catch (CompletionException e) {
     *     log.error("Delivery failed", e.getCause());
     * }
     * }</pre>
     *
     * @param message the message to send, must not be null
     * @return a {@code CompletableFuture} that completes when send is initiated,
     *         or completes exceptionally if send fails
     * @throws NullPointerException if message is null
     * @see #sendAndWait(Message, long)
     */
    CompletableFuture<Void> send(Message message);

    /**
     * Subscribes to direct messages for a specific receiver.
     *
     * <p>This method enables point-to-point communication. The handler will
     * receive all messages where {@link Message#receiverId()} matches the
     * specified receiver ID.
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Direct agent-to-agent communication</li>
     *   <li>Request-response patterns</li>
     *   <li>Targeted notifications</li>
     * </ul>
     *
     * <p><strong>Multiple Subscriptions:</strong>
     * Multiple handlers can subscribe to the same receiver ID. All handlers
     * will receive each message (broadcast to all handlers for that receiver).
     *
     * <p><strong>Handler Execution:</strong>
     * Handlers are executed asynchronously, potentially concurrently for
     * different messages.
     *
     * <p><strong>Subscription Lifecycle:</strong>
     * The subscription remains active until explicitly unsubscribed via
     * {@link #unsubscribe(String)} using the returned subscription ID.
     *
     * <p>Example:
     * <pre>{@code
     * // Agent subscribes to its own ID
     * String subId = messageService.subscribeToReceiver(
     *     agentId,
     *     message -> {
     *         log.info("Received direct message from {}",
     *                  message.senderId());
     *         handleDirectMessage(message);
     *         return CompletableFuture.completedFuture(null);
     *     }
     * );
     *
     * // Later, cleanup
     * messageService.unsubscribe(subId);
     * }</pre>
     *
     * @param receiverId the receiver ID to subscribe to, must not be null or empty
     * @param handler the message handler, must not be null
     * @return a unique subscription ID for later unsubscription
     * @throws IllegalArgumentException if receiverId is null or empty
     * @throws NullPointerException if handler is null
     * @see #send(Message)
     * @see #unsubscribe(String)
     */
    String subscribeToReceiver(String receiverId, MessageHandler handler);

    /**
     * Subscribes to messages on a specific topic.
     *
     * <p>This method enables publish-subscribe communication. The handler will
     * receive all messages where {@link Message#topic()} equals the specified
     * topic string.
     *
     * <p><strong>Topic Matching:</strong>
     * Topic matching is exact string equality. For pattern matching (wildcards,
     * hierarchies), use {@link #subscribe(Predicate, MessageHandler)} with a
     * custom predicate.
     *
     * <p><strong>Multiple Subscribers:</strong>
     * Multiple handlers can subscribe to the same topic. Each message published
     * to the topic will be delivered to all subscribers.
     *
     * <p><strong>Topic Naming Conventions:</strong>
     * <ul>
     *   <li>Use hierarchical names: "orders.created", "system.alerts.critical"</li>
     *   <li>Be consistent with naming patterns</li>
     *   <li>Document topic semantics</li>
     * </ul>
     *
     * <p>Examples:
     *
     * <p>Simple topic subscription:
     * <pre>{@code
     * String subId = messageService.subscribe(
     *     "orders.created",
     *     message -> {
     *         OrderData order = message.getContent(OrderData.class);
     *         return processNewOrder(order);
     *     }
     * );
     * }</pre>
     *
     * <p>Multiple handlers for same topic:
     * <pre>{@code
     * // Handler 1: Log all orders
     * messageService.subscribe("orders.created", message -> {
     *     log.info("New order: {}", message.id());
     *     return CompletableFuture.completedFuture(null);
     * });
     *
     * // Handler 2: Update inventory
     * messageService.subscribe("orders.created", message -> {
     *     return updateInventory(message);
     * });
     *
     * // Handler 3: Send notification
     * messageService.subscribe("orders.created", message -> {
     *     return sendNotification(message);
     * });
     * }</pre>
     *
     * @param topic the topic to subscribe to, must not be null
     * @param handler the message handler, must not be null
     * @return a unique subscription ID for later unsubscription
     * @throws NullPointerException if topic or handler is null
     * @see #send(Message)
     * @see #unsubscribe(String)
     */
    String subscribe(String topic, MessageHandler handler);

    /**
     * Subscribes to messages matching a custom predicate.
     *
     * <p>This method provides maximum flexibility for message filtering. The
     * handler will receive all messages for which the predicate returns {@code true}.
     *
     * <p><strong>Predicate Evaluation:</strong>
     * <ul>
     *   <li>Evaluated for every message sent</li>
     *   <li>Should be fast to avoid performance impact</li>
     *   <li>Should be pure (no side effects)</li>
     *   <li>Thread-safe (may be called concurrently)</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Complex filtering logic (multiple conditions)</li>
     *   <li>Header-based routing</li>
     *   <li>Content inspection</li>
     *   <li>Pattern matching on topics</li>
     * </ul>
     *
     * <p><strong>Performance Warning:</strong>
     * Predicates are evaluated for every message. Prefer topic-based subscriptions
     * when possible for better performance. Reserve predicates for cases where
     * topic routing isn't sufficient.
     *
     * <p>Examples:
     *
     * <p>Header-based filtering:
     * <pre>{@code
     * messageService.subscribe(
     *     message -> "HIGH".equals(message.headers().get("priority")),
     *     this::handleHighPriorityMessage
     * );
     * }</pre>
     *
     * <p>Topic pattern matching:
     * <pre>{@code
     * // Subscribe to all "orders.*" topics
     * messageService.subscribe(
     *     message -> message.topic() != null &&
     *                message.topic().startsWith("orders."),
     *     this::handleOrderMessage
     * );
     * }</pre>
     *
     * <p>Complex criteria:
     * <pre>{@code
     * messageService.subscribe(
     *     message -> {
     *         String region = message.headers().get("region");
     *         String priority = message.headers().get("priority");
     *         return "US-WEST".equals(region) &&
     *                ("HIGH".equals(priority) || "CRITICAL".equals(priority));
     *     },
     *     this::handleUrgentWestCoastMessage
     * );
     * }</pre>
     *
     * @param filter the predicate to filter messages, must not be null
     * @param handler the message handler, must not be null
     * @return a unique subscription ID for later unsubscription
     * @throws NullPointerException if filter or handler is null
     * @see #subscribe(String, MessageHandler)
     * @see #unsubscribe(String)
     */
    String subscribe(Predicate<Message> filter, MessageHandler handler);

    /**
     * Unsubscribes from a topic or filter using the subscription ID.
     *
     * <p>This method removes a subscription created by any of the subscribe
     * methods. After unsubscribing, the associated handler will no longer
     * receive messages.
     *
     * <p><strong>Idempotency:</strong>
     * Unsubscribing with an invalid or already-unsubscribed ID should be safe
     * and not throw exceptions (may log a warning).
     *
     * <p><strong>Immediate Effect:</strong>
     * The unsubscription takes effect immediately. No new messages will be
     * delivered to the handler after this call, though messages currently
     * being processed may complete.
     *
     * <p><strong>Thread Safety:</strong>
     * This method is thread-safe and can be called while messages are being
     * delivered to the handler.
     *
     * <p>Examples:
     *
     * <p>Basic unsubscribe:
     * <pre>{@code
     * String subId = messageService.subscribe("events", handler);
     * // ... later
     * messageService.unsubscribe(subId);
     * }</pre>
     *
     * <p>Cleanup pattern:
     * <pre>{@code
     * public class MyAgent {
     *     private List<String> subscriptions = new ArrayList<>();
     *
     *     public void setup() {
     *         subscriptions.add(messageService.subscribe("topic1", handler1));
     *         subscriptions.add(messageService.subscribe("topic2", handler2));
     *     }
     *
     *     public void cleanup() {
     *         subscriptions.forEach(messageService::unsubscribe);
     *         subscriptions.clear();
     *     }
     * }
     * }</pre>
     *
     * <p>Try-with-resources style (custom wrapper):
     * <pre>{@code
     * try (Subscription sub = messageService.autoSubscribe("topic", handler)) {
     *     // Use subscription
     * } // Automatically unsubscribes
     * }</pre>
     *
     * @param subscriptionId the subscription ID returned from a subscribe method
     * @see #subscribe(String, MessageHandler)
     * @see #subscribe(Predicate, MessageHandler)
     * @see #subscribeToReceiver(String, MessageHandler)
     */
    void unsubscribe(String subscriptionId);

    /**
     * Sends a message and waits for a correlated reply with a timeout.
     *
     * <p>This method implements the request-response pattern. It:
     * <ol>
     *   <li>Sends the message</li>
     *   <li>Registers a temporary handler for the reply</li>
     *   <li>Waits for a message with matching correlation ID</li>
     *   <li>Returns the reply or times out</li>
     * </ol>
     *
     * <p><strong>Correlation Mechanism:</strong>
     * The reply must have its {@code correlationId} set to the request's {@code id}.
     * Use {@link Message#reply(Object)} to ensure correct correlation.
     *
     * <p><strong>Timeout Behavior:</strong>
     * <ul>
     *   <li>If reply arrives before timeout: future completes with reply</li>
     *   <li>If timeout occurs: future completes exceptionally with TimeoutException</li>
     *   <li>Timeout starts from when this method is called, not when message is sent</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong>
     * This method is thread-safe. Multiple concurrent request-response operations
     * can be in flight simultaneously.
     *
     * <p><strong>Resource Management:</strong>
     * The temporary handler is automatically cleaned up after reply or timeout.
     * No manual unsubscription is needed.
     *
     * <p>Examples:
     *
     * <p>Basic request-response:
     * <pre>{@code
     * // Send request and wait for response
     * Message request = Message.builder()
     *     .senderId("client")
     *     .receiverId("server")
     *     .content("query data")
     *     .build();
     *
     * CompletableFuture<Message> response =
     *     messageService.sendAndWait(request, 5000);
     *
     * response.thenAccept(reply -> {
     *     Data result = reply.getContent(Data.class);
     *     processResult(result);
     * });
     * }</pre>
     *
     * <p>With timeout handling:
     * <pre>{@code
     * messageService.sendAndWait(request, 5000)
     *     .thenAccept(reply -> {
     *         log.info("Received reply: {}", reply.content());
     *     })
     *     .exceptionally(ex -> {
     *         if (ex.getCause() instanceof TimeoutException) {
     *             log.warn("Request timed out after 5 seconds");
     *             handleTimeout();
     *         } else {
     *             log.error("Request failed", ex);
     *         }
     *         return null;
     *     });
     * }</pre>
     *
     * <p>Synchronous style:
     * <pre>{@code
     * try {
     *     Message reply = messageService.sendAndWait(request, 5000).join();
     *     Data result = reply.getContent(Data.class);
     *     return result;
     * } catch (CompletionException e) {
     *     if (e.getCause() instanceof TimeoutException) {
     *         throw new RequestTimeoutException("Server did not respond");
     *     }
     *     throw new RequestFailedException("Request failed", e);
     * }
     * }</pre>
     *
     * <p>Server side (responder):
     * <pre>{@code
     * messageService.subscribeToReceiver("server", request -> {
     *     // Process request
     *     Data result = processQuery(request.content());
     *
     *     // Send reply with correlation
     *     Message reply = request.reply(result)
     *         .senderId("server")
     *         .build();
     *
     *     return messageService.send(reply);
     * });
     * }</pre>
     *
     * @param message the request message, must not be null
     * @param timeout timeout in milliseconds, must be positive
     * @return a {@code CompletableFuture} that completes with the reply message,
     *         or completes exceptionally with TimeoutException if timeout occurs
     * @throws NullPointerException if message is null
     * @throws IllegalArgumentException if timeout is not positive
     * @see Message#reply(Object)
     * @see #send(Message)
     */
    CompletableFuture<Message> sendAndWait(Message message, long timeout);
}