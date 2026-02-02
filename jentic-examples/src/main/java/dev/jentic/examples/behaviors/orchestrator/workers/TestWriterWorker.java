package dev.jentic.examples.behaviors.orchestrator.workers;

import dev.jentic.runtime.behavior.orchestrator.SubTask;
import dev.jentic.runtime.behavior.orchestrator.SubTaskResult;
import dev.jentic.runtime.behavior.orchestrator.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Worker specialized in writing unit tests.
 * 
 * @since 0.7.0
 */
public class TestWriterWorker implements Worker {
    
    private static final Logger log = LoggerFactory.getLogger(TestWriterWorker.class);
    
    @Override
    public String getName() {
        return "tester";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of("unit-tests", "integration-tests", "coverage-analysis");
    }
    
    @Override
    public SubTaskResult execute(SubTask task) {
        log.info("Writing tests: {}", task.task());
        
        String tests = String.format("""
            Generated Tests:
            - 5 unit tests created
            - Coverage: 92%%
            - All tests passing
            Task: %s
            """, task.task());
        
        return new SubTaskResult(getName(), task.task(), tests);
    }
}
