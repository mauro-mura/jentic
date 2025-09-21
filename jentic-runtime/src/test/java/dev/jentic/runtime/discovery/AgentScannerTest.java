package dev.jentic.runtime.discovery;

import dev.jentic.core.Agent;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.runtime.agent.BaseAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Set;

/**
 * Unit tests for AgentScanner
 */
class AgentScannerTest {
    
    private AgentScanner scanner;
    
    @BeforeEach
    void setUp() {
        scanner = new AgentScanner();
    }
    
    @Test
    void shouldScanCurrentPackage() {
        // When
        Set<Class<? extends Agent>> agents = scanner.scanForAgents("dev.jentic.runtime.discovery");
        
        // Then
        assertThat(agents).isNotEmpty();
        
        // Check that we found test agents
        boolean foundTestAgent = agents.stream()
            .anyMatch(clazz -> clazz.getSimpleName().equals("TestValidAgent"));
        assertThat(foundTestAgent).isTrue();
    }
    
    @Test
    void shouldIgnoreInvalidClasses() {
        // When
        Set<Class<? extends Agent>> agents = scanner.scanForAgents("dev.jentic.runtime.discovery");
        
        // Then
        // Should not include classes without @JenticAgent annotation
        boolean foundInvalid = agents.stream()
            .anyMatch(clazz -> clazz.getSimpleName().equals("TestInvalidAgent"));
        assertThat(foundInvalid).isFalse();
        
        // Should not include abstract classes
        boolean foundAbstract = agents.stream()
            .anyMatch(clazz -> clazz.getSimpleName().equals("TestAbstractAgent"));
        assertThat(foundAbstract).isFalse();
        
        // Should not include interfaces
        boolean foundInterface = agents.stream()
            .anyMatch(clazz -> clazz.getSimpleName().equals("TestAgentInterface"));
        assertThat(foundInterface).isFalse();
    }
    
    @Test
    void shouldHandleEmptyPackages() {
        // When
        Set<Class<? extends Agent>> agents = scanner.scanForAgents("non.existent.package");
        
        // Then
        assertThat(agents).isEmpty();
    }
    
    @Test
    void shouldHandleNullAndEmptyPackageNames() {
        // When
        Set<Class<? extends Agent>> agents = scanner.scanForAgents(null, "", "  ");
        
        // Then
        assertThat(agents).isEmpty();
    }
    
    @Test
    void shouldScanMultiplePackages() {
        // When
        Set<Class<? extends Agent>> agents = scanner.scanForAgents(
            "dev.jentic.runtime.discovery", 
            "dev.jentic.examples"
        );
        
        // Then
        assertThat(agents).isNotEmpty();
        
        // Should include agents from both packages
        boolean foundDiscoveryAgent = agents.stream()
            .anyMatch(clazz -> clazz.getSimpleName().equals("TestValidAgent"));
        assertThat(foundDiscoveryAgent).isTrue();
    }
}

// Test classes for AgentScannerTest

@JenticAgent("test-valid")
class TestValidAgent extends BaseAgent {
    public TestValidAgent() {
        super("test-valid", "Test Valid Agent");
    }
}

// This class should be ignored - no annotation
class TestInvalidAgent extends BaseAgent {
    public TestInvalidAgent() {
        super("test-invalid", "Test Invalid Agent");
    }
}

// This interface should be ignored
@JenticAgent("test-interface")
interface TestAgentInterface extends Agent {
}