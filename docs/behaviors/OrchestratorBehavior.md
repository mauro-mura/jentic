# OrchestratorBehavior

Dynamic task orchestration with LLM-driven decomposition.

## Pattern
Implements [Anthropic's Orchestrator-Workers Pattern](https://www.anthropic.com/research/building-effective-agents).

## Quick Start

```java
// Create worker pool
WorkerPool workers = new WorkerPool();
workers.registerWorkers(
    new CodeAnalyzerWorker(),
    new TestWriterWorker(),
    new BugFixerWorker()
);

// Create orchestrator
OrchestratorBehavior orchestrator = OrchestratorBehavior.builder(llm, workers)
    .onComplete(result -> {
        System.out.println("Result: " + result.result());
    })
    .build();

// Execute task
Task task = new Task("refactor-001", 
    "Refactor UserService: analyze, fix bugs, add tests");
orchestrator.setTask(task);
orchestrator.execute();
```

## How It Works

1. **Planning**: LLM decomposes task into subtasks for available workers
2. **Execution**: Workers execute subtasks in parallel (virtual threads)
3. **Synthesis**: LLM combines results into final answer

## Worker Interface

```java
public interface Worker {
    String getName();
    Set<String> getCapabilities();
    SubTaskResult execute(SubTask task);
}
```

## Custom Prompts

```java
OrchestratorBehavior.builder(llm, workers)
    .planningPrompt("""
        Break down: %s
        Workers: %s
        Format: worker: task
        """)
    .synthesisPrompt("""
        Task: %s
        Results: %s
        Combine into answer.
        """)
    .build();
```

## Example: CodeOrchestratorAgent

See `jentic-examples/orchestrator/CodeOrchestratorAgent.java`.

## Configuration

```yaml
jentic:
  behaviors:
    orchestrator:
      max-workers: 5
      timeout-seconds: 300
```

## API Reference

- `OrchestratorBehavior`: Main behavior class
- `WorkerPool`: Worker management
- `Worker`: Worker interface
- `Task`, `SubTask`: Task definitions
- `TaskResult`, `SubTaskResult`: Results
