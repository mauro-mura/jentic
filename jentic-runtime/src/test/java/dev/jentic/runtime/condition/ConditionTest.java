package dev.jentic.runtime.condition;

import dev.jentic.core.Agent;
import dev.jentic.core.condition.Condition;
import dev.jentic.runtime.agent.BaseAgent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Condition interface and combinators
 */
class ConditionTest {
    
    private final Agent testAgent = new TestAgent();
    
    @Test
    void shouldAlwaysReturnTrue() {
        // Given
        Condition condition = Condition.always();
        
        // When/Then
        assertThat(condition.evaluate(testAgent)).isTrue();
    }
    
    @Test
    void shouldNeverReturnTrue() {
        // Given
        Condition condition = Condition.never();
        
        // When/Then
        assertThat(condition.evaluate(testAgent)).isFalse();
    }
    
    @Test
    void shouldCombineWithAnd() {
        // Given
        Condition trueCondition = agent -> true;
        Condition falseCondition = agent -> false;
        
        // When
        Condition andBoth = trueCondition.and(trueCondition);
        Condition andMixed = trueCondition.and(falseCondition);
        
        // Then
        assertThat(andBoth.evaluate(testAgent)).isTrue();
        assertThat(andMixed.evaluate(testAgent)).isFalse();
    }
    
    @Test
    void shouldCombineWithOr() {
        // Given
        Condition trueCondition = agent -> true;
        Condition falseCondition = agent -> false;
        
        // When
        Condition orMixed = trueCondition.or(falseCondition);
        Condition orBoth = falseCondition.or(falseCondition);
        
        // Then
        assertThat(orMixed.evaluate(testAgent)).isTrue();
        assertThat(orBoth.evaluate(testAgent)).isFalse();
    }
    
    @Test
    void shouldNegateCondition() {
        // Given
        Condition trueCondition = agent -> true;
        
        // When
        Condition negated = trueCondition.negate();
        
        // Then
        assertThat(negated.evaluate(testAgent)).isFalse();
    }
    
    @Test
    void shouldChainComplexConditions() {
        // Given
        Condition cond1 = agent -> true;
        Condition cond2 = agent -> false;
        Condition cond3 = agent -> true;
        
        // When
        Condition complex = cond1.and(cond2.or(cond3));
        
        // Then
        assertThat(complex.evaluate(testAgent)).isTrue();
    }
    
    static class TestAgent extends BaseAgent {
        TestAgent() {
            super("test-agent", "Test Agent");
        }
    }
}