package dev.jentic.examples.behaviors.orchestrator.workers;

import dev.jentic.runtime.behavior.orchestrator.SubTask;
import dev.jentic.runtime.behavior.orchestrator.SubTaskResult;
import dev.jentic.runtime.behavior.orchestrator.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Worker specialized in code analysis.
 * 
 * @since 0.7.0
 */
public class CodeAnalyzerWorker implements Worker {
    
    private static final Logger log = LoggerFactory.getLogger(CodeAnalyzerWorker.class);
    
    @Override
    public String getName() {
        return "analyzer";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of("code-review", "complexity-analysis", "smell-detection");
    }
    
    @Override
    public SubTaskResult execute(SubTask task) {
        log.info("Analyzing code: {}", task.task());
        
        // Simulate analysis
        String analysis = String.format("""
            Code Analysis Results:
            - Complexity: Medium
            - Code smells: 2 detected
            - Best practices: 85%% compliance
            Task: %s
            """, task.task());
        
        return new SubTaskResult(getName(), task.task(), analysis);
    }
}
