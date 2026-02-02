package dev.jentic.runtime.behavior.orchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WorkerPool.
 * 
 * @since 0.7.0
 */
class WorkerPoolTest {
    
    private WorkerPool pool;
    
    @BeforeEach
    void setup() {
        pool = new WorkerPool();
    }
    
    @AfterEach
    void cleanup() {
        pool.close();
    }
    
    @Test
    @DisplayName("Should register and retrieve workers")
    void shouldRegisterWorkers() {
        TestWorker worker = new TestWorker("worker1");
        pool.registerWorker(worker);
        
        assertThat(pool.getWorkerNames()).contains("worker1");
    }
    
    @Test
    @DisplayName("Should register multiple workers at once")
    void shouldRegisterMultipleWorkers() {
        pool.registerWorkers(
            new TestWorker("w1"),
            new TestWorker("w2"),
            new TestWorker("w3")
        );
        
        assertThat(pool.getWorkerNames())
            .hasSize(3)
            .containsExactlyInAnyOrder("w1", "w2", "w3");
    }
    
    @Test
    @DisplayName("Should execute subtask asynchronously")
    void shouldExecuteAsync() {
        pool.registerWorker(new TestWorker("async-worker"));
        
        SubTask task = new SubTask("async-worker", "test task");
        CompletableFuture<SubTaskResult> future = pool.execute(task);
        
        SubTaskResult result = future.join();
        
        assertThat(result.success()).isTrue();
        assertThat(result.workerName()).isEqualTo("async-worker");
        assertThat(result.result()).contains("executed");
    }
    
    @Test
    @DisplayName("Should handle unknown worker")
    void shouldHandleUnknownWorker() {
        SubTask task = new SubTask("unknown-worker", "test");
        CompletableFuture<SubTaskResult> future = pool.execute(task);
        
        SubTaskResult result = future.join();
        
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Unknown worker");
    }
    
    @Test
    @DisplayName("Should handle worker execution errors")
    void shouldHandleWorkerErrors() {
        Worker failingWorker = new Worker() {
            @Override
            public String getName() { return "failing"; }
            
            @Override
            public Set<String> getCapabilities() { return Set.of(); }
            
            @Override
            public SubTaskResult execute(SubTask task) {
                throw new RuntimeException("Worker error");
            }
        };
        
        pool.registerWorker(failingWorker);
        
        SubTask task = new SubTask("failing", "test");
        SubTaskResult result = pool.execute(task).join();
        
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Execution failed");
    }
    
    @Test
    @DisplayName("Should describe worker capabilities")
    void shouldDescribeCapabilities() {
        Worker w1 = new TestWorker("worker1", Set.of("skill1", "skill2"));
        Worker w2 = new TestWorker("worker2", Set.of("skill3"));
        
        pool.registerWorkers(w1, w2);
        
        String capabilities = pool.describeCapabilities();
        
        assertThat(capabilities)
            .contains("worker1")
            .contains("skill1")
            .contains("skill2")
            .contains("worker2")
            .contains("skill3");
    }
    
    @Test
    @DisplayName("Should execute tasks in parallel")
    void shouldExecuteInParallel() {
        pool.registerWorkers(
            new SlowWorker("w1", 100),
            new SlowWorker("w2", 100),
            new SlowWorker("w3", 100)
        );
        
        long start = System.currentTimeMillis();
        
        var futures = java.util.stream.Stream.of(
            pool.execute(new SubTask("w1", "task1")),
            pool.execute(new SubTask("w2", "task2")),
            pool.execute(new SubTask("w3", "task3"))
        ).toList();
        
        futures.forEach(CompletableFuture::join);
        
        long duration = System.currentTimeMillis() - start;
        
        // Should complete in ~100ms (parallel) not 300ms (sequential)
        assertThat(duration).isLessThan(250);
    }
    
    // Helper workers
    private static class TestWorker implements Worker {
        private final String name;
        private final Set<String> capabilities;
        
        TestWorker(String name) {
            this(name, Set.of("test"));
        }
        
        TestWorker(String name, Set<String> capabilities) {
            this.name = name;
            this.capabilities = capabilities;
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public Set<String> getCapabilities() { return capabilities; }
        
        @Override
        public SubTaskResult execute(SubTask task) {
            return new SubTaskResult(name, task.task(), "Task executed");
        }
    }
    
    private static class SlowWorker implements Worker {
        private final String name;
        private final int delayMs;
        
        SlowWorker(String name, int delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public Set<String> getCapabilities() { return Set.of(); }
        
        @Override
        public SubTaskResult execute(SubTask task) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SubTaskResult(name, task.task(), "Done");
        }
    }
}
