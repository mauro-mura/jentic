package dev.jentic.examples.behaviors.orchestrator;

import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import dev.jentic.core.llm.StreamingChunk;
import dev.jentic.runtime.behavior.orchestrator.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Minimal standalone example showing OrchestratorBehavior usage.
 * No agent framework required - just the behavior itself.
 * 
 * Run: mvn exec:java -Dexec.mainClass="dev.jentic.examples.orchestrator.SimpleOrchestratorExample"
 * 
 * @since 0.7.0
 */
public class SimpleOrchestratorExample {
    
    public static void main(String[] args) {
        System.out.println("=== Simple Orchestrator Example ===\n");
        
        // 1. Create workers
        WorkerPool workers = new WorkerPool();
        workers.registerWorkers(
            new SimpleWorker("researcher", Set.of("web-search", "fact-checking")),
            new SimpleWorker("writer", Set.of("content-creation", "editing")),
            new SimpleWorker("reviewer", Set.of("quality-check", "proofreading"))
        );
        
        // 2. Create mock LLM
        LLMProvider mockLLM = new SimpleMockLLM();
        
        // 3. Build orchestrator
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(mockLLM, workers)
            .onComplete(result -> {
                System.out.println("\n✅ COMPLETED:");
                System.out.println(result.result());
            })
            .build();
        
        // 4. Set task and execute
        orchestrator.setTask(new Task(
            "article-001",
            "Write a blog post about AI trends in 2025"
        ));
        
        System.out.println("🚀 Orchestrating task...\n");
        orchestrator.execute().join();
        
        workers.close();
        System.out.println("\n✨ Done!");
    }
    
    /**
     * Simple worker implementation.
     */
    static class SimpleWorker implements Worker {
        private final String name;
        private final Set<String> capabilities;
        
        SimpleWorker(String name, Set<String> capabilities) {
            this.name = name;
            this.capabilities = capabilities;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public Set<String> getCapabilities() {
            return capabilities;
        }
        
        @Override
        public SubTaskResult execute(SubTask task) {
            System.out.println("  📋 " + name + ": " + task.task());
            
            // Simulate work
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String result = String.format("[%s completed: %s]", name, task.task());
            return new SubTaskResult(name, task.task(), result);
        }
    }
    
    /**
     * Simple mock LLM.
     */
    static class SimpleMockLLM implements LLMProvider {
        private boolean isPlanning = true;
        
        @Override
        public String getProviderName() {
            return "simple-mock";
        }
        
        @Override
        public CompletableFuture<LLMResponse> chat(LLMRequest request) {
            String content;
            
            if (isPlanning) {
                content = """
                    - researcher: Research latest AI trends
                    - writer: Write engaging article based on research
                    - reviewer: Review and polish the article
                    """;
                isPlanning = false;
            } else {
                content = "Blog post completed! Research shows exciting AI developments.";
            }
            
            LLMResponse response = LLMResponse.builder("id", "model")
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
    }
}
