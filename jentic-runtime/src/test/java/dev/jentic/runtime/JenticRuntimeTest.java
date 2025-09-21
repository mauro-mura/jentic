package dev.jentic.runtime;

import dev.jentic.core.Agent;
import dev.jentic.runtime.agent.BaseAgent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Collection;

/**
 * Unit tests for JenticRuntime
 */
class JenticRuntimeTest {
    
    @Test
    void shouldCreateRuntimeWithDefaults() {
        // When
        JenticRuntime runtime = JenticRuntime.builder().build();
        
        // Then
        assertThat(runtime).isNotNull();
        assertThat(runtime.isRunning()).isFalse();
        assertThat(runtime.getAgents()).isEmpty();
    }
    
    @Test
    void shouldRegisterAgents() {
        // Given
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent1 = new TestAgent("agent-1", "Agent 1");
        TestAgent agent2 = new TestAgent("agent-2", "Agent 2");
        
        // When
        runtime.registerAgent(agent1);
        runtime.registerAgent(agent2);
        
        // Then
        Collection<Agent> agents = runtime.getAgents();
        assertThat(agents).hasSize(2)
                         .containsExactlyInAnyOrder(agent1, agent2);
    }
    
    @Test
    void shouldFindAgentById() {
        // Given
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("test-agent", "Test Agent");
        runtime.registerAgent(agent);
        
        // When
        var found = runtime.getAgent("test-agent");
        var notFound = runtime.getAgent("non-existent");
        
        // Then
        assertThat(found).isPresent().contains(agent);
        assertThat(notFound).isEmpty();
    }
    
    @Test
    void shouldStartAndStopRuntime() {
        // Given
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("test-agent", "Test Agent");
        runtime.registerAgent(agent);
        
        // When
        runtime.start().join();
        
        // Then
        assertThat(runtime.isRunning()).isTrue();
        assertThat(agent.isRunning()).isTrue();
        
        // When
        runtime.stop().join();
        
        // Then
        assertThat(runtime.isRunning()).isFalse();
        assertThat(agent.isRunning()).isFalse();
    }
    
    @Test
    void shouldGetRuntimeStats() {
        // Given
        JenticRuntime runtime = JenticRuntime.builder()
            .scanPackage("com.example")
            .build();
        
        TestAgent agent1 = new TestAgent("agent-1", "Agent 1");
        TestAgent agent2 = new TestAgent("agent-2", "Agent 2");
        
        runtime.registerAgent(agent1);
        runtime.registerAgent(agent2);
        
        runtime.start().join();
        
        // When
        JenticRuntime.RuntimeStats stats = runtime.getStats();
        
        // Then
        assertThat(stats.totalAgents()).isEqualTo(2);
        assertThat(stats.runningAgents()).isEqualTo(2);
        assertThat(stats.scannedPackages()).isEqualTo(1);
        assertThat(stats.registeredServices()).isEqualTo(0);
    }
    
    @Test
    void shouldCreateAgentFromClass() {
        // Given
        JenticRuntime runtime = JenticRuntime.builder().build();
        
        // When
        TestAgent agent = runtime.createAgent(TestAgent.class);
        
        // Then
        assertThat(agent).isNotNull();
        assertThat(runtime.getAgents()).contains(agent);
        assertThat(agent.getMessageService()).isNotNull();
    }
    
    // Test agent for runtime testing
    static class TestAgent extends BaseAgent {
        TestAgent(String agentId, String agentName) {
            super(agentId, agentName);
        }
        
        // No-arg constructor for factory creation
        public TestAgent() {
            super();
        }
    }
}