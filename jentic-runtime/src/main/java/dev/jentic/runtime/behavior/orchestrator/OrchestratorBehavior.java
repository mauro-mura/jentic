package dev.jentic.runtime.behavior.orchestrator;

import dev.jentic.core.BehaviorType;
import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import dev.jentic.runtime.behavior.BaseBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrator behavior with LLM-driven task decomposition.
 * Implements Anthropic's Orchestrator-Workers pattern.
 * 
 * @since 0.7.0
 */
public class OrchestratorBehavior extends BaseBehavior {
    
    private static final Logger log = LoggerFactory.getLogger(OrchestratorBehavior.class);
    
    private final LLMProvider planner;
    private final WorkerPool workers;
    private final String planningPromptTemplate;
    private final String synthesisPromptTemplate;
    private Consumer<TaskResult> onComplete;
    private Task currentTask;
    
    private OrchestratorBehavior(Builder builder) {
        super(builder.id, BehaviorType.ONE_SHOT, null);
        this.planner = builder.planner;
        this.workers = builder.workers;
        this.planningPromptTemplate = builder.planningPromptTemplate;
        this.synthesisPromptTemplate = builder.synthesisPromptTemplate;
        this.onComplete = builder.onComplete;
    }
    
    @Override
    protected void action() {
        if (currentTask == null) {
            log.warn("No task set for orchestrator");
            return;
        }
        
        try {
            // Step 1: LLM decomposes task
            log.info("Planning task decomposition: {}", currentTask.description());
            List<SubTask> plan = planWithLLM(currentTask);
            log.info("Generated {} subtasks", plan.size());
            
            // Step 2: Execute in parallel
            log.info("Executing subtasks in parallel...");
            List<CompletableFuture<SubTaskResult>> futures = plan.stream()
                .map(workers::execute)
                .toList();
            
            List<SubTaskResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
            
            long successCount = results.stream().filter(SubTaskResult::success).count();
            log.info("Completed {}/{} subtasks successfully", successCount, results.size());
            
            // Step 3: LLM synthesizes results
            log.info("Synthesizing final result...");
            TaskResult finalResult = synthesizeWithLLM(currentTask, results);
            
            if (onComplete != null) {
                onComplete.accept(finalResult);
            }
            
            log.info("Orchestration complete for task: {}", currentTask.id());
            
        } catch (Exception e) {
            log.error("Orchestration failed", e);
            throw new OrchestratorException("Task orchestration failed", e);
        }
    }
    
    /**
     * Set task to orchestrate.
     */
    public void setTask(Task task) {
        this.currentTask = task;
    }
    
    private List<SubTask> planWithLLM(Task task) {
        String prompt = String.format(planningPromptTemplate,
            task.description(),
            workers.describeCapabilities()
        );
        
        LLMResponse response = planner.chat(
            LLMRequest.builder(planner.getProviderName())
                .systemMessage("You are a task planning assistant. Break tasks into subtasks.")
                .userMessage(prompt)
                .build()
        ).join();
        
        return parseSubTasks(response.content());
    }
    
    private TaskResult synthesizeWithLLM(Task task, List<SubTaskResult> results) {
        String prompt = String.format(synthesisPromptTemplate,
            task.description(),
            formatResults(results)
        );
        
        LLMResponse response = planner.chat(
            LLMRequest.builder(planner.getProviderName())
                .systemMessage("You are a synthesis assistant. Combine results into coherent answer.")
                .userMessage(prompt)
                .build()
        ).join();
        
        return new TaskResult(task.id(), response.content(), results, java.time.Instant.now());
    }
    
    private List<SubTask> parseSubTasks(String llmOutput) {
        List<SubTask> subtasks = new ArrayList<>();
        
        // Simple line-based parsing: "worker_name: task description"
        Pattern pattern = Pattern.compile("^\\s*-?\\s*(\\w+)\\s*:\\s*(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(llmOutput);
        
        int priority = 0;
        while (matcher.find()) {
            String workerName = matcher.group(1);
            String taskDesc = matcher.group(2).trim();
            subtasks.add(new SubTask(workerName, taskDesc, priority++));
        }
        
        if (subtasks.isEmpty()) {
            log.warn("No subtasks parsed from LLM output, using fallback");
            // Fallback: create single subtask for first available worker
            String firstWorker = workers.getWorkerNames().iterator().next();
            subtasks.add(new SubTask(firstWorker, llmOutput));
        }
        
        return subtasks;
    }
    
    private String formatResults(List<SubTaskResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SubTaskResult r : results) {
            sb.append(String.format("- %s: %s\n  Result: %s\n",
                r.workerName(),
                r.task(),
                r.success() ? r.result() : "FAILED: " + r.error()
            ));
        }
        return sb.toString();
    }
    
    public static Builder builder(LLMProvider planner, WorkerPool workers) {
        return new Builder(planner, workers);
    }
    
    public static class Builder {
        private final LLMProvider planner;
        private final WorkerPool workers;
        private String id = "orchestrator";
        private String planningPromptTemplate = DEFAULT_PLANNING_PROMPT;
        private String synthesisPromptTemplate = DEFAULT_SYNTHESIS_PROMPT;
        private Consumer<TaskResult> onComplete;
        
        private static final String DEFAULT_PLANNING_PROMPT = """
            Break down this task into subtasks:
            
            Task: %s
            
            Available workers and their capabilities:
            %s
            
            Respond with subtasks in format:
            - worker_name: task description
            - worker_name: task description
            """;
        
        private static final String DEFAULT_SYNTHESIS_PROMPT = """
            Original task: %s
            
            Subtask results:
            %s
            
            Synthesize these results into a final coherent answer.
            """;
        
        public Builder(LLMProvider planner, WorkerPool workers) {
            this.planner = planner;
            this.workers = workers;
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder planningPrompt(String template) {
            this.planningPromptTemplate = template;
            return this;
        }
        
        public Builder synthesisPrompt(String template) {
            this.synthesisPromptTemplate = template;
            return this;
        }
        
        public Builder onComplete(Consumer<TaskResult> callback) {
            this.onComplete = callback;
            return this;
        }
        
        public OrchestratorBehavior build() {
            return new OrchestratorBehavior(this);
        }
    }
}
