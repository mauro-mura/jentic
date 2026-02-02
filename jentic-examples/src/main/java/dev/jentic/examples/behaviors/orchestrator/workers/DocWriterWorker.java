package dev.jentic.examples.behaviors.orchestrator.workers;

import dev.jentic.runtime.behavior.orchestrator.SubTask;
import dev.jentic.runtime.behavior.orchestrator.SubTaskResult;
import dev.jentic.runtime.behavior.orchestrator.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Worker specialized in documentation writing.
 * 
 * @since 0.7.0
 */
public class DocWriterWorker implements Worker {
    
    private static final Logger log = LoggerFactory.getLogger(DocWriterWorker.class);
    
    @Override
    public String getName() {
        return "documenter";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of("javadoc", "readme", "api-docs", "user-guides");
    }
    
    @Override
    public SubTaskResult execute(SubTask task) {
        log.info("Writing documentation: {}", task.task());
        
        String docs = String.format("""
            Documentation Created:
            - JavaDoc complete for all public APIs
            - README updated with examples
            - User guide section added
            Task: %s
            """, task.task());
        
        return new SubTaskResult(getName(), task.task(), docs);
    }
}
