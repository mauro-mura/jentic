package dev.jentic.examples.behaviors.orchestrator.workers;

import dev.jentic.runtime.behavior.orchestrator.SubTask;
import dev.jentic.runtime.behavior.orchestrator.SubTaskResult;
import dev.jentic.runtime.behavior.orchestrator.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Worker specialized in bug fixing.
 * 
 * @since 0.7.0
 */
public class BugFixerWorker implements Worker {
    
    private static final Logger log = LoggerFactory.getLogger(BugFixerWorker.class);
    
    @Override
    public String getName() {
        return "bugfixer";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of("bug-fixing", "debugging", "root-cause-analysis");
    }
    
    @Override
    public SubTaskResult execute(SubTask task) {
        log.info("Fixing bugs: {}", task.task());
        
        String fix = String.format("""
            Bug Fix Report:
            - Root cause identified
            - 3 bugs fixed
            - Regression tests added
            Task: %s
            """, task.task());
        
        return new SubTaskResult(getName(), task.task(), fix);
    }
}
