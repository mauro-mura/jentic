package dev.jentic.runtime.agent;

import dev.jentic.core.*;
import dev.jentic.core.exceptions.AgentException;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of Agent interface.
 * Provides common functionality for all agents.
 */
public abstract class BaseAgent implements Agent {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    private final String agentId;
    private final String agentName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Behavior> behaviors = new ConcurrentHashMap<>();
    
    protected MessageService messageService;
    protected BehaviorScheduler behaviorScheduler;
    protected AgentDirectory agentDirectory;
    
    /**
     * Create agent with auto-generated ID
     */
    protected BaseAgent() {
        this(UUID.randomUUID().toString());
    }
    
    /**
     * Create agent with specific ID
     */
    protected BaseAgent(String agentId) {
        this(agentId, agentId);
    }
    
    /**
     * Create agent with ID and name
     */
    protected BaseAgent(String agentId, String agentName) {
        this.agentId = agentId;
        this.agentName = agentName;
    }
    
    @Override
    public String getAgentId() {
        return agentId;
    }
    
    @Override
    public String getAgentName() {
        return agentName;
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public CompletableFuture<Void> start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting agent: {} ({})", agentName, agentId);
            
            return CompletableFuture.runAsync(() -> {
                try {
                    // Initialize services if not already set
                    initializeServices();
                    
                    // Register with directory
                    registerWithDirectory();
                    
                    // Start behaviors
                    startBehaviors();
                    
                    // Call lifecycle hook
                    onStart();
                    
                    log.info("Agent started successfully: {} ({})", agentName, agentId);
                    
                } catch (Exception e) {
                    running.set(false);
                    throw new RuntimeException("Failed to start agent: " + agentId, e);
                }
            });
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping agent: {} ({})", agentName, agentId);
            
            return CompletableFuture.runAsync(() -> {
                try {
                    // Call lifecycle hook
                    onStop();
                    
                    // Stop behaviors
                    stopBehaviors();
                    
                    // Unregister from directory
                    unregisterFromDirectory();
                    
                    log.info("Agent stopped successfully: {} ({})", agentName, agentId);
                    
                } catch (Exception e) {
                    log.error("Error stopping agent: " + agentId, e);
                }
            });
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void addBehavior(Behavior behavior) {
        behaviors.put(behavior.getBehaviorId(), behavior);
        if (running.get() && behaviorScheduler != null) {
            behaviorScheduler.schedule(behavior);
        }
    }
    
    @Override
    public void removeBehavior(String behaviorId) {
        Behavior behavior = behaviors.remove(behaviorId);
        if (behavior != null) {
            behavior.stop();
            if (behaviorScheduler != null) {
                behaviorScheduler.cancel(behaviorId);
            }
        }
    }
    
    @Override
    public MessageService getMessageService() {
        return messageService;
    }
    
    /**
     * Set the message service for this agent
     */
    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }
    
    /**
     * Set the behavior scheduler for this agent
     */
    public void setBehaviorScheduler(BehaviorScheduler behaviorScheduler) {
        this.behaviorScheduler = behaviorScheduler;
    }
    
    /**
     * Set the agent directory for this agent
     */
    public void setAgentDirectory(AgentDirectory agentDirectory) {
        this.agentDirectory = agentDirectory;
    }
    
    /**
     * Initialize services with defaults if not set
     */
    protected void initializeServices() {
        if (messageService == null) {
            messageService = new InMemoryMessageService();
        }
    }
    
    /**
     * Lifecycle hook called when agent starts
     */
    protected void onStart() {
        // Override in subclasses
    }
    
    /**
     * Lifecycle hook called when agent stops
     */
    protected void onStop() {
        // Override in subclasses
    }
    
    private void startBehaviors() {
        if (behaviorScheduler != null) {
            behaviors.values().forEach(behaviorScheduler::schedule);
        }
    }
    
    private void stopBehaviors() {
        behaviors.values().forEach(Behavior::stop);
    }
    
    private void registerWithDirectory() {
        if (agentDirectory != null) {
            var descriptor = AgentDescriptor.builder(agentId)
                .agentName(agentName)
                .agentType(getClass().getSimpleName())
                .status(AgentStatus.RUNNING)
                .build();
                
            agentDirectory.register(descriptor);
        }
    }
    
    private void unregisterFromDirectory() {
        if (agentDirectory != null) {
            agentDirectory.unregister(agentId);
        }
    }
}