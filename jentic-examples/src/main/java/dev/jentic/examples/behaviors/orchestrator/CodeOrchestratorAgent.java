package dev.jentic.examples.behaviors.orchestrator;

import dev.jentic.core.llm.LLMProvider;
import dev.jentic.examples.behaviors.orchestrator.workers.*;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.orchestrator.OrchestratorBehavior;
import dev.jentic.runtime.behavior.orchestrator.Task;
import dev.jentic.runtime.behavior.orchestrator.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example orchestrator agent for code-related tasks.
 * Uses LLM to decompose complex code tasks and coordinate specialized workers.
 * 
 * Usage example:
 * <pre>{@code
 * var llm = LLMProviderFactory.anthropic()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .modelName("claude-sonnet-4-5-20250929")
 *     .build();
 * 
 * var agent = new CodeOrchestratorAgent(llm);
 * agent.orchestrateTask(new Task(
 *     "refactor-001",
 *     "Refactor the UserService class: analyze code quality, fix bugs, add tests, update docs"
 * ));
 * }</pre>
 * 
 * @since 0.9.0
 */
public class CodeOrchestratorAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(CodeOrchestratorAgent.class);
    
    private final LLMProvider llm;
    private final WorkerPool workers;
    private final OrchestratorBehavior orchestrator;
    
    public CodeOrchestratorAgent(LLMProvider llm) {
        super("code-orchestrator", "Code Orchestrator");
        this.llm = llm;
        this.workers = createWorkerPool();
        this.orchestrator = createOrchestrator();
    }
    
    private WorkerPool createWorkerPool() {
        WorkerPool pool = new WorkerPool();
        pool.registerWorkers(
            new CodeAnalyzerWorker(),
            new TestWriterWorker(),
            new BugFixerWorker(),
            new DocWriterWorker()
        );
        return pool;
    }
    
    private OrchestratorBehavior createOrchestrator() {
        return OrchestratorBehavior.builder(llm, workers)
            .id("code-orchestrator-behavior")
            .onComplete(result -> {
                log.info("Task {} completed:", result.taskId());
                log.info("Final result: {}", result.result());
                log.info("Processed {} subtasks", result.subResults().size());
            })
            .build();
    }
    
    @Override
    protected void onStart() {
        addBehavior(orchestrator);
    }
    
    /**
     * Orchestrate a code-related task.
     */
    public void orchestrateTask(Task task) {
        log.info("Starting orchestration for task: {}", task.description());
        orchestrator.setTask(task);
        // Orchestrator behavior will execute on next cycle
    }
    
    @Override
    protected void onStop() {
        workers.close();
    }
}
