package dev.jentic.runtime.behavior.orchestrator;

import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrchestratorBehavior.
 * 
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorBehaviorTest {
    
    @Mock
    private LLMProvider mockLLM;
    
    private WorkerPool workers;
    private TestWorker testWorker;
    
    @BeforeEach
    void setup() {
        workers = new WorkerPool();
        testWorker = new TestWorker();
        workers.registerWorker(testWorker);
        
        when(mockLLM.getProviderName()).thenReturn("test-provider");
    }
    
    @AfterEach
    void cleanup() {
        workers.close();
    }
    
    @Test
    @DisplayName("Should decompose task and execute workers")
    void shouldDecomposeAndExecute() {
        // Mock LLM responses
        when(mockLLM.chat(any(LLMRequest.class)))
            // Planning response
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("- test_worker: analyze code\n- test_worker: write tests")
                        .build()
            ))
            // Synthesis response
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("All tasks completed successfully")
                        .build()
            ));
        
        AtomicReference<TaskResult> resultRef = new AtomicReference<>();
        
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(mockLLM, workers)
            .onComplete(resultRef::set)
            .build();
        
        Task task = new Task("task-1", "Refactor UserService");
        orchestrator.setTask(task);
        orchestrator.execute().join();
        
        // Verify LLM called for planning and synthesis
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
        
        // Verify result
        TaskResult result = resultRef.get();
        assertThat(result).isNotNull();
        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.result()).contains("completed successfully");
        assertThat(result.subResults()).hasSize(2);
    }
    
    @Test
    @DisplayName("Should handle worker execution failure")
    void shouldHandleWorkerFailure() {
        Worker failingWorker = new Worker() {
            @Override
            public String getName() { return "failing_worker"; }
            
            @Override
            public Set<String> getCapabilities() { return Set.of("fail"); }
            
            @Override
            public SubTaskResult execute(SubTask task) {
                throw new RuntimeException("Worker failed");
            }
        };
        
        workers.registerWorker(failingWorker);
        
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("- failing_worker: do something")
                        .build()
            ))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("Task partially completed")
                        .build()
            ));
        
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(mockLLM, workers)
            .build();
        
        Task task = new Task("task-2", "Test failure handling");
        orchestrator.setTask(task);
        orchestrator.execute().join();
        
        // Should complete despite worker failure
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
    }
    
    @Test
    @DisplayName("Should parse subtasks from LLM output")
    void shouldParseSubTasks() {
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("""
                        - test_worker: first task
                        - test_worker: second task
                        - test_worker: third task
                        """)
                        .build()
            ))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "test-model")
                        .content("Synthesis")
                        .build()
            ));
        
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(mockLLM, workers)
            .build();
        
        Task task = new Task("task-3", "Multi-subtask test");
        orchestrator.setTask(task);
        orchestrator.execute().join();
        
        // Worker should be called 3 times
        assertThat(testWorker.getExecutionCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should use custom prompts")
    void shouldUseCustomPrompts() {
        String customPlanningPrompt = "Custom planning: %s with workers: %s";
        String customSynthesisPrompt = "Custom synthesis: %s results: %s";
        
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "model-test")
                        .content("- test_worker: task")
                        .build()
            ))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "model-test")
                        .content("Done")
                        .build()
            ));
        
        OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(mockLLM, workers)
            .planningPrompt(customPlanningPrompt)
            .synthesisPrompt(customSynthesisPrompt)
            .build();
        
        Task task = new Task("task-4", "Custom prompt test");
        orchestrator.setTask(task);
        orchestrator.execute().join();
        
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
    }
    
    // Test helper worker
    private static class TestWorker implements Worker {
        private int executionCount = 0;
        
        @Override
        public String getName() {
            return "test_worker";
        }
        
        @Override
        public Set<String> getCapabilities() {
            return Set.of("testing", "analysis");
        }
        
        @Override
        public SubTaskResult execute(SubTask task) {
            executionCount++;
            return new SubTaskResult(getName(), task.task(), "Result " + executionCount);
        }
        
        public int getExecutionCount() {
            return executionCount;
        }
    }
}
