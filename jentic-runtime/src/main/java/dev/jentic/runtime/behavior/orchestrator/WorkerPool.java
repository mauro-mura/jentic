package dev.jentic.runtime.behavior.orchestrator;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Pool of workers for parallel subtask execution.
 * Uses virtual threads for high concurrency.
 * 
 * @since 0.7.0
 */
public class WorkerPool implements AutoCloseable {
    
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    
    public WorkerPool() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * Register a worker.
     */
    public void registerWorker(Worker worker) {
        workers.put(worker.getName(), worker);
    }
    
    /**
     * Register multiple workers.
     */
    public void registerWorkers(Worker... workers) {
        for (Worker w : workers) {
            registerWorker(w);
        }
    }
    
    /**
     * Execute subtask asynchronously.
     */
    public CompletableFuture<SubTaskResult> execute(SubTask task) {
        return CompletableFuture.supplyAsync(() -> {
            Worker worker = workers.get(task.workerName());
            if (worker == null) {
                return SubTaskResult.failure(
                    task.workerName(), 
                    task.task(),
                    "Unknown worker: " + task.workerName()
                );
            }
            
            try {
                return worker.execute(task);
            } catch (Exception e) {
                return SubTaskResult.failure(
                    task.workerName(),
                    task.task(),
                    "Execution failed: " + e.getMessage()
                );
            }
        }, executor);
    }
    
    /**
     * Get all registered worker names.
     */
    public Set<String> getWorkerNames() {
        return Set.copyOf(workers.keySet());
    }
    
    /**
     * Describe all worker capabilities for LLM planning.
     */
    public String describeCapabilities() {
        return workers.values().stream()
            .map(w -> String.format("- %s: %s", 
                w.getName(), 
                String.join(", ", w.getCapabilities())))
            .collect(Collectors.joining("\n"));
    }
    
    @Override
    public void close() {
        executor.shutdown();
    }
}
