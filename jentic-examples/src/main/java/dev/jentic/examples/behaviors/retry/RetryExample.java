package dev.jentic.examples.behaviors.retry;

import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.advanced.RetryBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive example demonstrating RetryBehavior usage in real-world scenarios.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>External API calls with network failures</li>
 *   <li>Database operations with transient errors</li>
 *   <li>File operations with temporary locks</li>
 *   <li>Message processing with retries</li>
 * </ol>
 *
 * <p>This example demonstrates:
 * <ul>
 *   <li>Different backoff strategies</li>
 *   <li>Exception filtering</li>
 *   <li>Success/failure callbacks</li>
 *   <li>Metrics tracking</li>
 *   <li>Real-world retry patterns</li>
 * </ul>
 */
public class RetryExample {

    private static final Logger log = LoggerFactory.getLogger(RetryExample.class);

    public static void main(String[] args) throws Exception {
        log.info("=".repeat(80));
        log.info("JENTIC RETRY BEHAVIOR - COMPREHENSIVE EXAMPLE");
        log.info("Demonstrating retry patterns for real-world scenarios");
        log.info("=".repeat(80) + "\n");

        // Create and start runtime
        JenticRuntime runtime = JenticRuntime.builder()
                .scanPackage("dev.jentic.examples.behaviors.retry")
                .build();

        log.info("🚀 Starting Jentic Runtime...\n");
        runtime.start().get(10, TimeUnit.SECONDS);

        Thread.sleep(2000);

        // =====================================================================
        // SCENARIO 1: API Client with Retry
        // =====================================================================

        log.info("\n" + "=".repeat(80));
        log.info("SCENARIO 1: External API calls with exponential backoff");
        log.info("=".repeat(80) + "\n");

        ApiClientAgent apiAgent = (ApiClientAgent) runtime.getAgent("api-client").orElseThrow();
        apiAgent.demonstrateApiRetry();

        Thread.sleep(3000);

        // =====================================================================
        // SCENARIO 2: Database Operations with Retry
        // =====================================================================

        log.info("\n\n" + "=".repeat(80));
        log.info("SCENARIO 2: Database operations with linear backoff");
        log.info("=".repeat(80) + "\n");

        DatabaseAgent dbAgent = (DatabaseAgent) runtime.getAgent("database-agent").orElseThrow();
        dbAgent.demonstrateDatabaseRetry();

        Thread.sleep(3000);

        // =====================================================================
        // SCENARIO 3: File Operations with Retry
        // =====================================================================

        log.info("\n\n" + "=".repeat(80));
        log.info("SCENARIO 3: File operations with jittered retry");
        log.info("=".repeat(80) + "\n");

        FileProcessorAgent fileAgent = (FileProcessorAgent) runtime.getAgent("file-processor").orElseThrow();
        fileAgent.demonstrateFileRetry();

        Thread.sleep(3000);

        // =====================================================================
        // SCENARIO 4: Message Processing with Selective Retry
        // =====================================================================

        log.info("\n\n" + "=".repeat(80));
        log.info("SCENARIO 4: Message processing with selective retry");
        log.info("=".repeat(80) + "\n");

        MessageProcessorAgent msgAgent = (MessageProcessorAgent) runtime.getAgent("message-processor").orElseThrow();
        msgAgent.demonstrateMessageRetry();

        Thread.sleep(3000);

        // =====================================================================
        // Display Metrics Summary
        // =====================================================================

        log.info("\n\n" + "=".repeat(80));
        log.info("METRICS SUMMARY");
        log.info("=".repeat(80));

        apiAgent.printMetrics();
        dbAgent.printMetrics();
        fileAgent.printMetrics();
        msgAgent.printMetrics();

        // =====================================================================
        // Shutdown
        // =====================================================================

        log.info("\n\n" + "=".repeat(80));
        log.info("Shutting down Jentic Runtime...");
        log.info("=".repeat(80) + "\n");

        runtime.stop().get(10, TimeUnit.SECONDS);

        log.info("✅ RetryExample completed successfully\n");
    }
}

// =============================================================================
// SCENARIO 1: API Client Agent
// =============================================================================

/**
 * Agent that calls external APIs with retry logic
 */
@JenticAgent(
        value = "api-client",
        type = "ApiClient",
        capabilities = {"http", "retry", "monitoring"}
)
class ApiClientAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(ApiClientAgent.class);

    private final SimulatedExternalApi externalApi = new SimulatedExternalApi();
    private RetryBehavior<String> apiRetryBehavior;

    public ApiClientAgent() {
        super("api-client", "API Client Agent");
    }

    @Override
    protected void onStart() {
        log.info("🌐 API Client Agent started");

        // Create retry behavior for API calls with exponential backoff
        apiRetryBehavior = new RetryBehavior<>(
                "api-call-retry",
                5,  // Max 5 retries
                RetryBehavior.BackoffStrategy.EXPONENTIAL,
                Duration.ofMillis(100),  // Start with 100ms
                Duration.ofSeconds(5),   // Cap at 5 seconds
                Duration.ofSeconds(2)    // Timeout per attempt
        ) {
            @Override
            protected String attemptAction() throws Exception {
                return externalApi.callApi();
            }

            @Override
            protected boolean shouldRetry(Exception e) {
                // Retry on network errors, not on client errors (4xx)
                if (e instanceof ApiException apiEx) {
                    return apiEx.isRetryable();
                }
                return true;
            }
        };

        // Configure callbacks
        apiRetryBehavior
                .onSuccess(response ->
                        log.info("   ✅ API call succeeded: {}", response))
                .onFailure(e ->
                        log.error("   ❌ API call failed after all retries: {}", e.getMessage()))
                .onRetry(retryNum ->
                        log.info("   🔄 Retrying API call (attempt {})", retryNum));

        addBehavior(apiRetryBehavior);
    }

    public void demonstrateApiRetry() {
        log.info("📡 Calling external API (will fail 3 times before succeeding)...");
        externalApi.setFailuresBeforeSuccess(3);

        try {
            apiRetryBehavior.execute().get();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    public void printMetrics() {
        log.info("\n📊 API Client Metrics:");
        log.info("   {}", apiRetryBehavior.getMetricsSummary());
    }
}

// =============================================================================
// SCENARIO 2: Database Agent
// =============================================================================

/**
 * Agent that performs database operations with retry
 */
@JenticAgent(
        value = "database-agent",
        type = "Database",
        capabilities = {"database", "retry", "transaction"}
)
class DatabaseAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAgent.class);

    private final SimulatedDatabase database = new SimulatedDatabase();
    private RetryBehavior<Integer> dbRetryBehavior;

    public DatabaseAgent() {
        super("database-agent", "Database Agent");
    }

    @Override
    protected void onStart() {
        log.info("💾 Database Agent started");

        // Create retry behavior with linear backoff
        dbRetryBehavior = new RetryBehavior<>(
                "db-operation-retry",
                3,  // Max 3 retries
                RetryBehavior.BackoffStrategy.LINEAR,
                Duration.ofMillis(200)  // 200ms, 400ms, 600ms
        ) {
            @Override
            protected Integer attemptAction() throws Exception {
                return database.executeQuery();
            }

            @Override
            protected boolean shouldRetry(Exception e) {
                // Retry on deadlock and timeout, not on constraint violations
                if (e instanceof DatabaseException dbEx) {
                    return dbEx.isTransient();
                }
                return false;
            }
        };

        dbRetryBehavior
                .onSuccess(rowCount ->
                        log.info("   ✅ Database query succeeded: {} rows affected", rowCount))
                .onFailure(e ->
                        log.error("   ❌ Database query failed: {}", e.getMessage()))
                .onRetry(retryNum ->
                        log.warn("   🔄 Retrying database query (attempt {})", retryNum));

        addBehavior(dbRetryBehavior);
    }

    public void demonstrateDatabaseRetry() {
        log.info("💾 Executing database query (will encounter deadlock twice)...");
        database.setDeadlocksBeforeSuccess(2);

        try {
            dbRetryBehavior.execute().get();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    public void printMetrics() {
        log.info("\n📊 Database Agent Metrics:");
        log.info("   {}", dbRetryBehavior.getMetricsSummary());
    }
}

// =============================================================================
// SCENARIO 3: File Processor Agent
// =============================================================================

/**
 * Agent that processes files with retry on lock contention
 */
@JenticAgent(
        value = "file-processor",
        type = "FileProcessor",
        capabilities = {"file-io", "retry"}
)
class FileProcessorAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(FileProcessorAgent.class);

    private final SimulatedFileSystem fileSystem = new SimulatedFileSystem();
    private RetryBehavior<Boolean> fileRetryBehavior;

    public FileProcessorAgent() {
        super("file-processor", "File Processor Agent");
    }

    @Override
    protected void onStart() {
        log.info("📁 File Processor Agent started");

        // Create retry behavior with jittered backoff (avoid thundering herd)
        fileRetryBehavior = new RetryBehavior<>(
                "file-operation-retry",
                4,  // Max 4 retries
                RetryBehavior.BackoffStrategy.JITTER,
                Duration.ofMillis(100)
        ) {
            @Override
            protected Boolean attemptAction() throws Exception {
                return fileSystem.processFile();
            }

            @Override
            protected boolean shouldRetry(Exception e) {
                // Retry on file locks, not on file not found
                return e instanceof IOException &&
                        e.getMessage().contains("locked");
            }
        };

        fileRetryBehavior
                .onSuccess(success ->
                        log.info("   ✅ File processed successfully"))
                .onFailure(e ->
                        log.error("   ❌ File processing failed: {}", e.getMessage()))
                .onRetry(retryNum ->
                        log.info("   🔄 Retrying file operation (attempt {})", retryNum));

        addBehavior(fileRetryBehavior);
    }

    public void demonstrateFileRetry() {
        log.info("📁 Processing file (will be locked 3 times)...");
        fileSystem.setLocksBeforeSuccess(3);

        try {
            fileRetryBehavior.execute().get();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    public void printMetrics() {
        log.info("\n📊 File Processor Metrics:");
        log.info("   {}", fileRetryBehavior.getMetricsSummary());
    }
}

// =============================================================================
// SCENARIO 4: Message Processor Agent
// =============================================================================

/**
 * Agent that processes messages with selective retry
 */
@JenticAgent(
        value = "message-processor",
        type = "MessageProcessor",
        capabilities = {"messaging", "retry", "validation"}
)
class MessageProcessorAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessorAgent.class);

    private final SimulatedMessageQueue messageQueue = new SimulatedMessageQueue();
    private RetryBehavior<String> messageRetryBehavior;

    public MessageProcessorAgent() {
        super("message-processor", "Message Processor Agent");
    }

    @Override
    protected void onStart() {
        log.info("📨 Message Processor Agent started");

        // Create retry with fixed delay and selective retry
        messageRetryBehavior = new RetryBehavior<>(
                "message-processing-retry",
                3,  // Max 3 retries
                RetryBehavior.BackoffStrategy.FIXED,
                Duration.ofMillis(150)
        ) {
            @Override
            protected String attemptAction() throws Exception {
                return messageQueue.processNextMessage();
            }
        };

        // Only retry on transient processing errors
        messageRetryBehavior.withRetryCondition(e -> {
            if (e instanceof MessageException msgEx) {
                return msgEx.isTransient();
            }
            return false;
        });

        messageRetryBehavior
                .onSuccess(messageId ->
                        log.info("   ✅ Message processed: {}", messageId))
                .onFailure(e ->
                        log.error("   ❌ Message processing failed: {}", e.getMessage()))
                .onRetry(retryNum ->
                        log.warn("   🔄 Retrying message processing (attempt {})", retryNum));

        addBehavior(messageRetryBehavior);
    }

    public void demonstrateMessageRetry() {
        log.info("📨 Processing message (will have transient errors)...");
        messageQueue.setTransientErrorsBeforeSuccess(2);

        try {
            messageRetryBehavior.execute().get();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    public void printMetrics() {
        log.info("\n📊 Message Processor Metrics:");
        log.info("   {}", messageRetryBehavior.getMetricsSummary());
    }
}

// =============================================================================
// SIMULATED EXTERNAL SYSTEMS
// =============================================================================

/**
 * Simulated external API
 */
class SimulatedExternalApi {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private int failuresBeforeSuccess = 0;

    public void setFailuresBeforeSuccess(int failures) {
        this.failuresBeforeSuccess = failures;
        this.callCount.set(0);
    }

    public String callApi() throws ApiException {
        int call = callCount.incrementAndGet();

        if (call <= failuresBeforeSuccess) {
            // Simulate network error
            throw new ApiException("Network timeout", 503, true);
        }

        return "API Response: Success (call #" + call + ")";
    }
}

class ApiException extends Exception {
    private final int statusCode;
    private final boolean retryable;

    public ApiException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

/**
 * Simulated database
 */
class SimulatedDatabase {
    private final AtomicInteger queryCount = new AtomicInteger(0);
    private int deadlocksBeforeSuccess = 0;

    public void setDeadlocksBeforeSuccess(int deadlocks) {
        this.deadlocksBeforeSuccess = deadlocks;
        this.queryCount.set(0);
    }

    public int executeQuery() throws DatabaseException {
        int query = queryCount.incrementAndGet();

        if (query <= deadlocksBeforeSuccess) {
            throw new DatabaseException("Deadlock detected", true);
        }

        return ThreadLocalRandom.current().nextInt(1, 100);
    }
}

class DatabaseException extends Exception {
    private final boolean transient_;

    public DatabaseException(String message, boolean transient_) {
        super(message);
        this.transient_ = transient_;
    }

    public boolean isTransient() {
        return transient_;
    }
}

/**
 * Simulated file system
 */
class SimulatedFileSystem {
    private final AtomicInteger operationCount = new AtomicInteger(0);
    private int locksBeforeSuccess = 0;

    public void setLocksBeforeSuccess(int locks) {
        this.locksBeforeSuccess = locks;
        this.operationCount.set(0);
    }

    public boolean processFile() throws IOException {
        int operation = operationCount.incrementAndGet();

        if (operation <= locksBeforeSuccess) {
            throw new IOException("File is locked by another process");
        }

        return true;
    }
}

/**
 * Simulated message queue
 */
class SimulatedMessageQueue {
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private int transientErrorsBeforeSuccess = 0;

    public void setTransientErrorsBeforeSuccess(int errors) {
        this.transientErrorsBeforeSuccess = errors;
        this.messageCount.set(0);
    }

    public String processNextMessage() throws MessageException {
        int msg = messageCount.incrementAndGet();

        if (msg <= transientErrorsBeforeSuccess) {
            throw new MessageException("Temporary processing error", true);
        }

        return "MSG-" + msg;
    }
}

class MessageException extends Exception {
    private final boolean transient_;

    public MessageException(String message, boolean transient_) {
        super(message);
        this.transient_ = transient_;
    }

    public boolean isTransient() {
        return transient_;
    }
}