package dev.jentic.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Set;

/**
 * Unit tests for AgentQuery record and builder
 */
class AgentQueryTest {
    
    @Test
    void shouldCreateQueryByType() {
        // When
        AgentQuery query = AgentQuery.byType("test-type");
        
        // Then
        assertThat(query.agentType()).isEqualTo("test-type");
        assertThat(query.requiredCapabilities()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.customFilter()).isNull();
    }
    
    @Test
    void shouldCreateQueryByStatus() {
        // When
        AgentQuery query = AgentQuery.byStatus(AgentStatus.RUNNING);
        
        // Then
        assertThat(query.status()).isEqualTo(AgentStatus.RUNNING);
        assertThat(query.agentType()).isNull();
        assertThat(query.requiredCapabilities()).isNull();
        assertThat(query.customFilter()).isNull();
    }
    
    @Test
    void shouldCreateQueryWithCapabilities() {
        // Given
        Set<String> capabilities = Set.of("cap1", "cap2");
        
        // When
        AgentQuery query = AgentQuery.withCapabilities(capabilities);
        
        // Then
        assertThat(query.requiredCapabilities()).isEqualTo(capabilities);
        assertThat(query.agentType()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.customFilter()).isNull();
    }
    
    @Test
    void shouldCreateComplexQueryWithBuilder() {
        // Given
        Set<String> capabilities = Set.of("monitoring", "alerts");
        
        // When
        AgentQuery query = AgentQuery.builder()
            .agentType("monitor")
            .status(AgentStatus.RUNNING)
            .requiredCapabilities(capabilities)
            .customFilter(descriptor -> descriptor.agentName().contains("test"))
            .build();
        
        // Then
        assertThat(query.agentType()).isEqualTo("monitor");
        assertThat(query.status()).isEqualTo(AgentStatus.RUNNING);
        assertThat(query.requiredCapabilities()).isEqualTo(capabilities);
        assertThat(query.customFilter()).isNotNull();
    }
    
    @Test
    void shouldAddCapabilitiesIndividually() {
        // When
        AgentQuery query = AgentQuery.builder()
            .requiredCapability("cap1")
            .requiredCapability("cap2")
            .build();
        
        // Then
        assertThat(query.requiredCapabilities())
            .containsExactlyInAnyOrder("cap1", "cap2");
    }
    
    @Test
    void shouldApplyCustomFilter() {
        // Given
        AgentDescriptor descriptor1 = AgentDescriptor.builder("agent-1")
            .agentName("test-agent")
            .build();
            
        AgentDescriptor descriptor2 = AgentDescriptor.builder("agent-2")
            .agentName("production-agent")
            .build();
        
        AgentQuery query = AgentQuery.builder()
            .customFilter(desc -> desc.agentName().contains("test"))
            .build();
        
        // When/Then
        assertThat(query.customFilter().test(descriptor1)).isTrue();
        assertThat(query.customFilter().test(descriptor2)).isFalse();
    }
}