package dev.jentic.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Set;
import java.util.Map;

/**
 * Unit tests for AgentDescriptor record and builder
 */
class AgentDescriptorTest {
    
    @Test
    void shouldCreateDescriptorWithBuilder() {
        // Given
        String agentId = "test-agent";
        String agentName = "Test Agent";
        String agentType = "test-type";
        
        // When
        AgentDescriptor descriptor = AgentDescriptor.builder(agentId)
            .agentName(agentName)
            .agentType(agentType)
            .status(AgentStatus.RUNNING)
            .build();
        
        // Then
        assertThat(descriptor.agentId()).isEqualTo(agentId);
        assertThat(descriptor.agentName()).isEqualTo(agentName);
        assertThat(descriptor.agentType()).isEqualTo(agentType);
        assertThat(descriptor.status()).isEqualTo(AgentStatus.RUNNING);
        assertThat(descriptor.capabilities()).isEmpty();
        assertThat(descriptor.metadata()).isEmpty();
        assertThat(descriptor.registeredAt()).isNotNull();
        assertThat(descriptor.lastSeen()).isNotNull();
    }
    
    @Test
    void shouldAddCapabilities() {
        // When
        AgentDescriptor descriptor = AgentDescriptor.builder("agent-1")
            .capability("cap1")
            .capability("cap2")
            .capabilities(Set.of("cap3", "cap4"))
            .build();
        
        // Then
        assertThat(descriptor.capabilities())
            .containsExactlyInAnyOrder("cap1", "cap2", "cap3", "cap4");
    }
    
    @Test
    void shouldAddMetadata() {
        // When
        AgentDescriptor descriptor = AgentDescriptor.builder("agent-1")
            .metadata("key1", "value1")
            .metadata("key2", "value2")
            .metadata(Map.of("key3", "value3", "key4", "value4"))
            .build();
        
        // Then
        assertThat(descriptor.metadata())
            .containsEntry("key1", "value1")
            .containsEntry("key2", "value2")
            .containsEntry("key3", "value3")
            .containsEntry("key4", "value4");
    }
    
    @Test
    void shouldHaveDefaultValues() {
        // When
        AgentDescriptor descriptor = AgentDescriptor.builder("agent-1").build();
        
        // Then
        assertThat(descriptor.status()).isEqualTo(AgentStatus.UNKNOWN);
        assertThat(descriptor.capabilities()).isEmpty();
        assertThat(descriptor.metadata()).isEmpty();
        assertThat(descriptor.registeredAt()).isNotNull();
        assertThat(descriptor.lastSeen()).isNotNull();
    }
    
    @Test
    void shouldHaveImmutableCollections() {
        // Given
        AgentDescriptor descriptor = AgentDescriptor.builder("agent-1")
            .capability("test")
            .metadata("key", "value")
            .build();
        
        // When/Then
        assertThatThrownBy(() -> descriptor.capabilities().add("new-cap"))
            .isInstanceOf(UnsupportedOperationException.class);
            
        assertThatThrownBy(() -> descriptor.metadata().put("new-key", "new-value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}