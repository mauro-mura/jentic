package dev.jentic.core.composite;

import dev.jentic.core.Agent;
import dev.jentic.core.Behavior;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for behaviors that coordinate other behaviors
 */
public abstract class CompositeBehavior implements Behavior {
    
    protected final String behaviorId;
    protected final List<Behavior> childBehaviors;
    protected Agent agent;
    protected volatile boolean active;
    
    protected CompositeBehavior(String behaviorId) {
        this.behaviorId = behaviorId;
        this.childBehaviors = new ArrayList<>();
        this.active = true;
    }
    
    @Override
    public String getBehaviorId() {
        return behaviorId;
    }
    
    @Override
    public Agent getAgent() {
        return agent;
    }
    
    public void setAgent(Agent agent) {
        this.agent = agent;
        // Propagate agent to child behaviors
        for (Behavior child : childBehaviors) {
            if (child instanceof CompositeBehavior composite) {
                composite.setAgent(agent);
            }
        }
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void stop() {
        active = false;
        // Stop all child behaviors
        for (Behavior child : childBehaviors) {
            child.stop();
        }
    }
    
    @Override
    public Duration getInterval() {
        return null; // Composite behaviors don't have intervals
    }
    
    /**
     * Add a child behavior to this composite
     */
    public void addChildBehavior(Behavior behavior) {
        childBehaviors.add(behavior);
        if (behavior instanceof CompositeBehavior composite) {
            composite.setAgent(agent);
        }
    }
    
    /**
     * Get all child behaviors (immutable)
     */
    public List<Behavior> getChildBehaviors() {
        return Collections.unmodifiableList(childBehaviors);
    }
}