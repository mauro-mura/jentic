package dev.jentic.runtime.behavior.orchestrator;

/**
 * Individual subtask execution result.
 * 
 * @param workerName worker that executed
 * @param task original subtask
 * @param result execution result
 * @param success execution status
 * @param error error message if failed
 * @since 0.7.0
 */
public record SubTaskResult(
    String workerName,
    String task,
    String result,
    boolean success,
    String error
) {
    public SubTaskResult(String workerName, String task, String result) {
        this(workerName, task, result, true, null);
    }
    
    public static SubTaskResult failure(String workerName, String task, String error) {
        return new SubTaskResult(workerName, task, null, false, error);
    }
}
