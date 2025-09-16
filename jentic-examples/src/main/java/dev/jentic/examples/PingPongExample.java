package dev.jentic.examples;

import dev.jentic.runtime.JenticRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating two agents communicating via messages.
 * PingAgent sends periodic ping messages, PongAgent responds with pong messages.
 */
public class PingPongExample {
    
    private static final Logger log = LoggerFactory.getLogger(PingPongExample.class);
    
    public static void main(String[] args) throws InterruptedException {
        log.info("=== Jentic Ping-Pong Example ===");
        
        // Create runtime
        JenticRuntime runtime = JenticRuntime.builder()
            .scanPackage("dev.jentic.examples")
            .build();
        
        // Manually register agents (since classpath scanning is not implemented yet)
        PingAgent pingAgent = new PingAgent();
        PongAgent pongAgent = new PongAgent();
        
        runtime.registerAgent(pingAgent);
        runtime.registerAgent(pongAgent);
        
        // Start runtime
        runtime.start().join();
        
        log.info("Runtime started with {} agents", runtime.getAgents().size());
        runtime.getAgents().forEach(agent -> 
            log.info("  - {} ({})", agent.getAgentName(), agent.getAgentId()));
        
        // Let it run for 20 seconds
        Thread.sleep(20_000);
        
        // Stop runtime
        log.info("Stopping runtime...");
        runtime.stop().join();
        
        log.info("=== Example completed ===");
    }
}