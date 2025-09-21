package dev.jentic.runtime.agent;

import dev.jentic.core.*;
import dev.jentic.runtime.behavior.OneShotBehavior;
import dev.jentic.runtime.directory.LocalAgentDirectory;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import dev.jentic.runtime.scheduler.SimpleBehaviorScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for BaseAgent
 */
class BaseAgentTest {
    
    private MessageService messageService;
    private AgentDirectory agentDirectory;
    private BehaviorScheduler behaviorScheduler;
    private TestAgent agent;
    
    @BeforeEach
    void setUp() {
        messageService = new InMemoryMessageService();
        agentDirectory = new LocalAgentDirectory();
        behaviorScheduler = new SimpleBehaviorScheduler();
        behaviorScheduler.start().join();
        
        agent = new TestAgent("test-agent", "Test Agent");
        agent.setMessageService(messageService);
        agent.setAgentDirectory(agentDirectory);
        agent.setBehaviorScheduler(behaviorScheduler);
    }
    
    @Test
    void shouldHaveCorrectIdentity() {
        assertThat(agent.getAgentId()).isEqualTo("test-agent");
        assertThat(agent.getAgentName()).isEqualTo("Test Agent");
        assertThat(agent.isRunning()).isFalse();
    }
    
    @Test
    void shouldStartAndStop() {
        // Initially stopped
        assertThat(agent.isRunning()).isFalse();
        
        // Start agent
        agent.start().join();
        assertThat(agent.isRunning()).isTrue();
        assertThat(agent.startCalled).isTrue();
        
        // Stop agent
        agent.stop().join();
        assertThat(agent.isRunning()).isFalse();
        assertThat(agent.stopCalled).isTrue();
    }
    
    @Test
    void shouldNotStartTwice() {
        // Given
        agent.start().join();
        assertThat(agent.isRunning()).isTrue();
        
        // When
        CompletableFuture<Void> secondStart = agent.start();
        
        // Then
        assertThat(secondStart).isCompletedWithValue(null);
        assertThat(agent.isRunning()).isTrue();
    }
    
    @Test
    void shouldNotStopTwice() {
        // Given
        agent.start().join();
        agent.stop().join();
        assertThat(agent.isRunning()).isFalse();
        
        // When
        CompletableFuture<Void> secondStop = agent.stop();
        
        // Then
        assertThat(secondStop).isCompletedWithValue(null);
        assertThat(agent.isRunning()).isFalse();
    }
    
    @Test
    void shouldAddAndRemoveBehaviors() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        
        OneShotBehavior behavior = OneShotBehavior.from("test-behavior", () -> {
            executed.set(true);
            latch.countDown();
        });
        
        agent.start().join();
        
        // When
        agent.addBehavior(behavior);
        
        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executed.get()).isTrue();
        
        // When
        agent.removeBehavior(behavior.getBehaviorId());
        
        // Behavior should be stopped (though OneShotBehavior stops itself anyway)
        assertThat(behavior.isActive()).isFalse();
    }
    
    @Test
    void shouldRegisterWithDirectory() {
        // When
        agent.start().join();
        
        // Then
        var descriptor = agentDirectory.findById("test-agent").join();
        assertThat(descriptor).isPresent();
        assertThat(descriptor.get().agentId()).isEqualTo("test-agent");
        assertThat(descriptor.get().agentName()).isEqualTo("Test Agent");
        assertThat(descriptor.get().status()).isEqualTo(AgentStatus.RUNNING);
    }
    
    @Test
    void shouldUnregisterFromDirectory() {
        // Given
        agent.start().join();
        assertThat(agentDirectory.findById("test-agent").join()).isPresent();
        
        // When
        agent.stop().join();
        
        // Then
        assertThat(agentDirectory.findById("test-agent").join()).isEmpty();
    }
    
    @Test
    void shouldGetMessageService() {
        assertThat(agent.getMessageService()).isEqualTo(messageService);
    }
    
    @Test
    void shouldInitializeServicesWhenNoneSet() {
        // Given
        TestAgent agentWithoutServices = new TestAgent("test-2", "Test Agent 2");
        // Note: no services set
        
        // When
        agentWithoutServices.start().join();
        
        // Then
        assertThat(agentWithoutServices.getMessageService()).isNotNull();
        assertThat(agentWithoutServices.getMessageService()).isInstanceOf(InMemoryMessageService.class);
    }
    
    // Test agent implementation
    static class TestAgent extends BaseAgent {
        boolean startCalled = false;
        boolean stopCalled = false;
        
        TestAgent(String agentId, String agentName) {
            super(agentId, agentName);
        }
        
        @Override
        protected void onStart() {
            startCalled = true;
        }
        
        @Override
        protected void onStop() {
            stopCalled = true;
        }
    }
}