package dev.jentic.examples.behaviors.orchestrator;

import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import dev.jentic.core.llm.StreamingChunk;
import dev.jentic.examples.behaviors.orchestrator.workers.*;
import dev.jentic.runtime.behavior.orchestrator.OrchestratorBehavior;
import dev.jentic.runtime.behavior.orchestrator.Task;
import dev.jentic.runtime.behavior.orchestrator.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Standalone executable example demonstrating OrchestratorBehavior.
 * Can run with or without real LLM provider.
 * 
 * Usage:
 * <pre>{@code
 * // Without API key (mock mode)
 * mvn exec:java -Dexec.mainClass="dev.jentic.examples.orchestrator.OrchestratorExample"
 * 
 * // With Anthropic API key
 * export ANTHROPIC_API_KEY=your_key_here
 * mvn exec:java -Dexec.mainClass="dev.jentic.examples.orchestrator.OrchestratorExample"
 * }</pre>
 * 
 * @since 0.7.0
 */
public class OrchestratorExample {
    
    private static final Logger log = LoggerFactory.getLogger(OrchestratorExample.class);
    
    public static void main(String[] args) {
        log.info("=== Orchestrator Behavior Example ===");
        
        // Create LLM provider (mock or real)
        LLMProvider llm = createLLMProvider();
        
        // Create worker pool
        WorkerPool workers = createWorkerPool();
        
        // Create orchestrator
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(llm, workers)
            .id("demo-orchestrator")
            .onComplete(result -> {
                log.info("\n=== TASK COMPLETED ===");
                log.info("Task ID: {}", result.taskId());
                log.info("Final Result:\n{}", result.result());
                log.info("Subtasks executed: {}", result.subResults().size());
                
                result.subResults().forEach(sr -> {
                    log.info("  - {}: {}", sr.workerName(), 
                        sr.success() ? "SUCCESS" : "FAILED");
                });
            })
            .build();
        
        // Create and execute task
        Task task = new Task(
            "refactor-001",
            "Refactor UserService class: analyze code quality, fix bugs, add tests, update documentation"
        );
        
        log.info("\n=== STARTING ORCHESTRATION ===");
        log.info("Task: {}", task.description());
        
        orchestrator.setTask(task);
        
        try {
            orchestrator.execute().join();
            log.info("\n=== ORCHESTRATION SUCCESSFUL ===");
        } catch (Exception e) {
            log.error("\n=== ORCHESTRATION FAILED ===", e);
        } finally {
            workers.close();
        }
    }
    
    /**
     * Create LLM provider - real Anthropic if API key available, mock otherwise.
     */
    private static LLMProvider createLLMProvider() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("Using Anthropic LLM provider");
            // Commented out - requires anthropic adapter dependency
            /*
            return LLMProviderFactory.anthropic()
                .apiKey(apiKey)
                .modelName("claude-sonnet-4-5-20250929")
                .build();
            */
            log.warn("Anthropic provider code commented out - uncomment in real project");
            return createMockLLMProvider();
        } else {
            log.info("No API key found - using mock LLM provider");
            return createMockLLMProvider();
        }
    }
    
    /**
     * Create mock LLM provider for demonstration.
     */
    private static LLMProvider createMockLLMProvider() {
        return new LLMProvider() {
            private int callCount = 0;
            
            @Override
            public String getProviderName() {
                return "mock-llm";
            }
            
            @Override
            public CompletableFuture<LLMResponse> chat(LLMRequest request) {
                callCount++;
                
                String content;
                if (callCount == 1) {
                    // Planning response
                    content = """
                        I'll break down this refactoring task into specialized subtasks:
                        
                        - analyzer: Analyze UserService code quality and complexity
                        - bugfixer: Identify and fix bugs in UserService
                        - tester: Write comprehensive unit tests
                        - documenter: Update JavaDoc and documentation
                        """;
                } else {
                    // Synthesis response
                    content = """
                        Refactoring Complete!
                        
                        Code Analysis:
                        - Complexity: Medium
                        - Code smells: 2 detected (fixed)
                        - Best practices compliance: 85%
                        
                        Bugs Fixed:
                        - 3 bugs identified and corrected
                        - Root cause analysis completed
                        - Regression tests added
                        
                        Testing:
                        - 5 unit tests created
                        - Code coverage: 92%
                        - All tests passing
                        
                        Documentation:
                        - JavaDoc complete for all public APIs
                        - README updated with examples
                        - User guide section added
                        
                        The UserService class is now production-ready!
                        """;
                }
                
                LLMResponse response = LLMResponse.builder("mock-" + callCount, "mock-model")
                    .content(content)
                    .build();
                
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public CompletableFuture<Void> chatStream(LLMRequest request, Consumer<StreamingChunk> chunkHandler) {
                return null;
            }

            @Override
            public CompletableFuture<List<String>> getAvailableModels() {
                return null;
            }
        };
    }
    
    /**
     * Create worker pool with specialized workers.
     */
    private static WorkerPool createWorkerPool() {
        WorkerPool pool = new WorkerPool();
        
        pool.registerWorkers(
            new CodeAnalyzerWorker(),
            new TestWriterWorker(),
            new BugFixerWorker(),
            new DocWriterWorker()
        );
        
        log.info("Registered {} workers", pool.getWorkerNames().size());
        log.info("Worker capabilities:\n{}", pool.describeCapabilities());
        
        return pool;
    }
}
