package dev.jentic.runtime.persistence;

import dev.jentic.core.AgentStatus;
import dev.jentic.core.persistence.AgentState;
import dev.jentic.core.persistence.PersistenceStrategy;
import dev.jentic.core.persistence.Stateful;
import dev.jentic.core.annotations.JenticPersistenceConfig;
import dev.jentic.runtime.agent.BaseAgent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PersistenceManagerTest {
    
    @TempDir
    Path tempDir;
    
    private FilePersistenceService persistenceService;
    private PersistenceManager persistenceManager;
    
    @BeforeEach
    void setUp() throws Exception {
        persistenceService = new FilePersistenceService(tempDir);
        persistenceManager = new PersistenceManager(persistenceService);
        persistenceManager.start().get();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        persistenceManager.stop().get();
    }
    
    @Test
    @DisplayName("Should register agent with manual persistence")
    void testRegisterManualAgent() {
        // Given
        TestAgent agent = new TestAgent("test-agent", PersistenceStrategy.MANUAL);
        
        // When
        persistenceManager.registerAgent(agent);
        
        // Then
        assertThat(persistenceManager.getStats())
            .containsEntry("registeredAgents", 1);
    }
    
    @Test
    @DisplayName("Should save and load agent manually")
    void testManualSaveLoad() throws Exception {
        // Given
        TestAgent agent = new TestAgent("test-agent", PersistenceStrategy.MANUAL);
        agent.counter.set(42);
        
        // When
        persistenceManager.saveAgent(agent).get();
        
        TestAgent newAgent = new TestAgent("test-agent", PersistenceStrategy.MANUAL);
        boolean loaded = persistenceManager.loadAgent(newAgent).get();
        
        // Then
        assertThat(loaded).isTrue();
        assertThat(newAgent.counter.get()).isEqualTo(42);
    }
    
    @Test
    @DisplayName("Should handle periodic persistence")
    void testPeriodicPersistence() throws Exception {
        // Given
        PeriodicTestAgent agent = new PeriodicTestAgent("test-agent");
        agent.setRunning(true);
        agent.counter.set(10);
        
        // When
        persistenceManager.registerAgent(agent);
        Thread.sleep(500); // Wait for ~5 saves
        agent.counter.set(20);
        Thread.sleep(200); // Wait for another save
        
        // Then
        TestAgent newAgent = new TestAgent("test-agent", PersistenceStrategy.MANUAL);
        persistenceManager.loadAgent(newAgent).get();
        assertThat(newAgent.counter.get()).isEqualTo(20);
    }
    
    @Test
    @DisplayName("Should create and restore snapshots")
    void testSnapshotManagement() throws Exception {
        // Given
        TestAgent agent = new TestAgent("test-agent", PersistenceStrategy.MANUAL);
        agent.counter.set(100);
        persistenceManager.registerAgent(agent);
        persistenceManager.saveAgent(agent).get();
        
        // Create snapshot
        String snapshotId = persistenceManager.createSnapshot("test-agent").get();

        // Verify snapshot was created
        assertThat(snapshotId).isNotNull();
        assertThat(snapshotId).isNotEmpty();

        // Modify state
        agent.counter.set(200);
        persistenceManager.saveAgent(agent).get();

        // Verify current state is 200
        assertThat(agent.counter.get()).isEqualTo(200);

        // When
        boolean restored = persistenceManager.restoreSnapshot("test-agent", snapshotId).get();
        
        // Then
        assertThat(restored)
                .withFailMessage("Failed to restore snapshot. SnapshotId: %s", snapshotId)
                .isTrue();
        assertThat(agent.counter.get())
                .withFailMessage("Expected counter to be restored to 100, but was %d", agent.counter.get())
                .isEqualTo(100); // Restored to snapshot value
    }
    
    @Test
    @DisplayName("Should get persistence statistics")
    void testGetStats() throws Exception {
        // Given
        PeriodicTestAgent agent1 = new PeriodicTestAgent("agent-1");
        PeriodicTestAgent agent2 = new PeriodicTestAgent("agent-2");
        
        // When
        persistenceManager.registerAgent(agent1);
        persistenceManager.registerAgent(agent2);
        
        var stats = persistenceManager.getStats();
        
        // Then
        assertThat(stats).containsEntry("running", true);
        assertThat(stats).containsKey("registeredAgents");
    }
    
    // Test helper classes
    
    @JenticPersistenceConfig(strategy = PersistenceStrategy.MANUAL)
    static class TestAgent extends BaseAgent implements Stateful {
        final AtomicInteger counter = new AtomicInteger(0);
        private boolean running = false;
        
        TestAgent(String agentId, PersistenceStrategy strategy) {
            this(agentId, strategy, "60s");
        }
        
        TestAgent(String agentId, PersistenceStrategy strategy, String interval) {
            super(agentId, agentId);
            // Dynamically set persistence config via annotation would require
            // runtime annotation manipulation, so we'll use a simpler approach
        }
        
        void setRunning(boolean running) {
            this.running = running;
        }
        
        @Override
        public boolean isRunning() {
            return running;
        }
        
        @Override
        public AgentState captureState() {
            return AgentState.builder(getAgentId())
                .agentName(getAgentName())
                .agentType("TestAgent")
                .status(isRunning() ? AgentStatus.RUNNING : AgentStatus.STOPPED)
                .data("counter", counter.get())
                .build();
        }
        
        @Override
        public void restoreState(AgentState state) {
            Integer counterValue = (Integer) state.data().get("counter");
            if (counterValue != null) {
                counter.set(counterValue);
            }
        }
    }

    @JenticPersistenceConfig(strategy = PersistenceStrategy.PERIODIC, interval = "100ms")
    static class PeriodicTestAgent extends BaseAgent implements Stateful {
        final AtomicInteger counter = new AtomicInteger(0);
        private boolean running = false;

        PeriodicTestAgent(String agentId) {
            super(agentId, agentId);
        }

        void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public AgentState captureState() {
            return AgentState.builder(getAgentId())
                    .agentName(getAgentName())
                    .agentType("PeriodicTestAgent")
                    .status(isRunning() ? AgentStatus.RUNNING : AgentStatus.STOPPED)
                    .data("counter", counter.get())
                    .build();
        }

        @Override
        public void restoreState(AgentState state) {
            Integer counterValue = (Integer) state.data().get("counter");
            if (counterValue != null) {
                counter.set(counterValue);
            }
        }
    }
}