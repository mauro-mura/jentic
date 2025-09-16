package dev.jentic.runtime.behavior;

import dev.jentic.core.Agent;
import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of Behavior interface.
 * Provides common functionality for all behaviors.
 */
public abstract class BaseBehavior implements Behavior {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    private final String behaviorId;
    private final BehaviorType type;
    private final Duration interval;
    private final AtomicBoolean active = new AtomicBoolean(true);
    
    private Agent agent;
    
    protected BaseBehavior(BehaviorType type) {
        this(UUID.randomUUID().toString(), type, null);
    }
    
    protected BaseBehavior(BehaviorType type, Duration interval) {
        this(UUID.randomUUID().toString(), type, interval);
    }
    
    protected BaseBehavior(String behaviorId, BehaviorType type, Duration interval) {
        this.behaviorId = behaviorId;
        this.type = type;
        this.interval = interval;
    }
    
    @Override
    public String getBehaviorId() {
        return behaviorId;
    }
    
    @Override
    public Agent getAgent() {
        return agent;
    }
    
    @Override
    public BehaviorType getType() {
        return type;
    }
    
    @Override
    public Duration getInterval() {
        return interval;
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public void stop() {
        if (active.compareAndSet(true, false)) {
            log.debug("Behavior stopped: {}", behaviorId);
            onStop();
        }
    }
    
    @Override
    public CompletableFuture<Void> execute() {
        if (!active.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                log.trace("Executing behavior: {}", behaviorId);
                action();
                
                // For one-shot behaviors, automatically stop after execution
                if (type == BehaviorType.ONE_SHOT) {
                    stop();
                }
            } catch (Exception e) {
                log.error("Error in behavior execution: {}", behaviorId, e);
                onError(e);
            }
        });
    }
    
    /**
     * Set the owning agent for this behavior
     */
    public void setAgent(Agent agent) {
        this.agent = agent;
    }
    
    /**
     * The main action to be performed by this behavior.
     * Must be implemented by subclasses.
     */
    protected abstract void action();
    
    /**
     * Called when the behavior is stopped.
     * Override for cleanup logic.
     */
    protected void onStop() {
        // Override in subclasses if needed
    }
    
    /**
     * Called when an error occurs during execution.
     * Override for custom error handling.
     */
    protected void onError(Exception error) {
        // Default: just log the error
        log.error("Behavior error: {}", behaviorId, error);
    }
}