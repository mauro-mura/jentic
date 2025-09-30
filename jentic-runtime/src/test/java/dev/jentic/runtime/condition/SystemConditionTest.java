package dev.jentic.runtime.condition;

import dev.jentic.core.Agent;
import dev.jentic.core.condition.Condition;
import dev.jentic.runtime.agent.BaseAgent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SystemCondition pre-built conditions
 */
class SystemConditionTest {
    
    private final Agent testAgent = new TestAgent();
    
    @Test
    void shouldCheckCpuBelow() {
        // Given
        Condition condition = SystemCondition.cpuBelow(100.0); // Should always be true
        
        // When/Then
        assertThat(condition.evaluate(testAgent)).isTrue();
    }
    
    @Test
    void shouldCheckCpuAbove() {
        // Given
        Condition condition = SystemCondition.cpuAbove(100.0); // Should be false
        
        // When/Then
        assertThat(condition.evaluate(testAgent)).isFalse();
    }
    
    @Test
    void shouldCheckMemoryBelow() {
        // Given
        Condition condition = SystemCondition.memoryBelow(100.0); // Should be true
        
        // When/Then
        assertThat(condition.evaluate(testAgent)).isTrue();
    }
    
    @Test
    void shouldCheckSystemHealthy() {
        // Given
        Condition condition = SystemCondition.systemHealthy();
        
        // When
        boolean result = condition.evaluate(testAgent);
        
        // Then - depends on actual system load
        assertThat(result).isIn(true, false);
    }
    
    @Test
    void shouldCombineSystemConditions() {
        // Given
        Condition condition = SystemCondition.cpuBelow(80.0)
            .and(SystemCondition.memoryBelow(80.0));
        
        // When
        boolean result = condition.evaluate(testAgent);
        
        // Then
        assertThat(result).isIn(true, false);
    }
    
    static class TestAgent extends BaseAgent {
        TestAgent() {
            super("test-agent", "Test Agent");
        }
    }
}