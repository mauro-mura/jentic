package dev.jentic.runtime.behavior.orchestrator;

import java.time.Instant;
import java.util.List;

/**
 * Final orchestrated task result.
 * 
 * @param taskId original task ID
 * @param result synthesized result
 * @param subResults all subtask results
 * @param completedAt completion timestamp
 * @since 0.7.0
 */
public record TaskResult(
    String taskId,
    String result,
    List<SubTaskResult> subResults,
    Instant completedAt
) {
    public TaskResult(String taskId, String result) {
        this(taskId, result, List.of(), Instant.now());
    }
}
