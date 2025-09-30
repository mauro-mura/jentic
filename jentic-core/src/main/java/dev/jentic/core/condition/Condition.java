package dev.jentic.core.condition;

import dev.jentic.core.Agent;

/**
 * Interface for defining conditions that control behavior execution.
 * Conditions can check system state, agent state, time, or any other criteria.
 */
@FunctionalInterface
public interface Condition {
    
    /**
     * Evaluate the condition in the context of an agent
     * 
     * @param agent the agent executing the behavior
     * @return true if condition is satisfied, false otherwise
     */
    boolean evaluate(Agent agent);
    
    /**
     * Combine conditions with AND logic
     */
    default Condition and(Condition other) {
        return agent -> this.evaluate(agent) && other.evaluate(agent);
    }
    
    /**
     * Combine conditions with OR logic
     */
    default Condition or(Condition other) {
        return agent -> this.evaluate(agent) || other.evaluate(agent);
    }
    
    /**
     * Negate condition
     */
    default Condition negate() {
        return agent -> !this.evaluate(agent);
    }
    
    /**
     * Always true condition
     */
    static Condition always() {
        return agent -> true;
    }
    
    /**
     * Never true condition
     */
    static Condition never() {
        return agent -> false;
    }
}