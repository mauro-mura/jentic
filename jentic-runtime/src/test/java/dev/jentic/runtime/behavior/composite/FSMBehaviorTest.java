package dev.jentic.runtime.behavior.composite;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FSM Behavior Tests")
class FSMBehaviorTest {

    private FSMBehavior fsmBehavior;
    private List<String> stateHistory;
    private AtomicInteger counter;

    @BeforeEach
    void setUp() {
        fsmBehavior = new FSMBehavior("test-fsm", "IDLE");
        stateHistory = new ArrayList<>();
        counter = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should start in initial state")
    void shouldStartInInitialState() {
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("IDLE");
        assertThat(fsmBehavior.isInState("IDLE")).isTrue();
    }

    @Test
    @DisplayName("Should transition between states based on conditions")
    void shouldTransitionBetweenStates() throws Exception {
        // Given
        fsmBehavior.addState("IDLE", createTestBehavior("idle-action"));
        fsmBehavior.addState("ACTIVE", createTestBehavior("active-action"));
        fsmBehavior.addState("DONE", createTestBehavior("done-action"));

        fsmBehavior.addTransition("IDLE", "ACTIVE", fsm -> counter.get() >= 1);
        fsmBehavior.addTransition("ACTIVE", "DONE", fsm -> counter.get() >= 2);

        // When - execute FSM multiple times
        counter.set(0);
        fsmBehavior.execute().get(1, TimeUnit.SECONDS);
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("IDLE");

        counter.set(1);
        fsmBehavior.execute().get(1, TimeUnit.SECONDS);
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("ACTIVE");

        counter.set(2);
        fsmBehavior.execute().get(1, TimeUnit.SECONDS);
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("Should execute state behavior on execute")
    void shouldExecuteStateBehavior() throws Exception {
        // Given - use global fsmBehavior which has "IDLE" as initial state
        List<String> executed = new ArrayList<>();
        fsmBehavior.addState("IDLE", createRecordingBehavior("idle", executed));

        // When
        fsmBehavior.execute().get(1, TimeUnit.SECONDS);

        // Then
        assertThat(executed).contains("idle");
    }

    @Test
    @DisplayName("Should execute transition action when transitioning")
    void shouldExecuteTransitionAction() throws Exception {
        // Given - Create new FSM with proper initial state
        List<String> executed = new ArrayList<>();
        FSMBehavior testFSM = new FSMBehavior("test-fsm", "START");
        testFSM.addState("START", createRecordingBehavior("start", executed));
        testFSM.addState("END", createRecordingBehavior("end", executed));

        Behavior transitionAction = createRecordingBehavior("transition", executed);
        testFSM.addTransition("START", "END", fsm -> true, "go-to-end", transitionAction);

        // When
        testFSM.execute().get(1, TimeUnit.SECONDS);

        // Then
        assertThat(executed).containsExactly("start", "transition");
        assertThat(testFSM.getCurrentState()).isEqualTo("END");
    }

    @Test
    @DisplayName("Should stay in current state if no transition matches")
    void shouldStayInStateIfNoTransition() throws Exception {
        // Given
        fsmBehavior.addState("IDLE", createTestBehavior("idle"));
        fsmBehavior.addState("ACTIVE", createTestBehavior("active"));
        fsmBehavior.addTransition("IDLE", "ACTIVE", fsm -> false); // Condition never met

        // When
        fsmBehavior.execute().get(1, TimeUnit.SECONDS);

        // Then
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("Should reset to initial state")
    void shouldResetToInitialState() throws Exception {
        // Given
        fsmBehavior.addState("IDLE", createTestBehavior("idle"));
        fsmBehavior.addState("ACTIVE", createTestBehavior("active"));
        fsmBehavior.addTransition("IDLE", "ACTIVE", fsm -> true);

        fsmBehavior.execute().get(1, TimeUnit.SECONDS);
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("ACTIVE");

        // When
        fsmBehavior.reset();

        // Then
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("Should support forced transition")
    void shouldSupportForcedTransition() {
        // Given
        fsmBehavior.addState("A", createTestBehavior("a"));
        fsmBehavior.addState("B", createTestBehavior("b"));
        fsmBehavior.addState("C", createTestBehavior("c"));

        // When
        fsmBehavior.transitionTo("C");

        // Then
        assertThat(fsmBehavior.getCurrentState()).isEqualTo("C");
    }

    @Test
    @DisplayName("Should return FSM type")
    void shouldReturnFSMType() {
        assertThat(fsmBehavior.getType()).isEqualTo(BehaviorType.FSM);
    }

    @Test
    @DisplayName("Should build FSM using builder")
    void shouldBuildFSMUsingBuilder() {
        // Given
        FSMBehavior fsm = FSMBehavior.builder("traffic-light", "RED")
                .state("RED", createTestBehavior("stop"))
                .state("YELLOW", createTestBehavior("prepare"))
                .state("GREEN", createTestBehavior("go"))
                .transition("RED", "GREEN", f -> counter.get() > 5)
                .transition("GREEN", "YELLOW", f -> counter.get() > 10)
                .transition("YELLOW", "RED", f -> counter.get() > 15)
                .build();

        // Then
        assertThat(fsm.getCurrentState()).isEqualTo("RED");
        assertThat(fsm.getStateNames()).containsExactlyInAnyOrder("RED", "YELLOW", "GREEN");
    }

    @Test
    @DisplayName("Should fail to build FSM without initial state")
    void shouldFailToBuildWithoutInitialState() {
        // Given
        FSMBehavior.Builder builder = FSMBehavior.builder("invalid-fsm", "UNKNOWN")
                .state("A", createTestBehavior("a"))
                .state("B", createTestBehavior("b"));

        // When / Then
        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Initial state 'UNKNOWN' not defined");
    }

    @Test
    @DisplayName("Should handle multiple transitions from same state")
    void shouldHandleMultipleTransitions() throws Exception {
        // Given - Create FSM with correct initial state
        FSMBehavior testFSM = new FSMBehavior("test-fsm", "START");
        testFSM.addState("START", createTestBehavior("start"));
        testFSM.addState("PATH_A", createTestBehavior("path-a"));
        testFSM.addState("PATH_B", createTestBehavior("path-b"));

        // First matching transition should be taken
        testFSM.addTransition("START", "PATH_A", fsm -> counter.get() < 5);
        testFSM.addTransition("START", "PATH_B", fsm -> counter.get() >= 5);

        // When - Test first transition
        counter.set(3);
        testFSM.execute().get(1, TimeUnit.SECONDS);
        assertThat(testFSM.getCurrentState()).isEqualTo("PATH_A");

        // Reset and test second transition
        testFSM.reset(); // Goes back to "START" (initial state)
        counter.set(7);
        testFSM.execute().get(1, TimeUnit.SECONDS);
        assertThat(testFSM.getCurrentState()).isEqualTo("PATH_B");
    }

    @Test
    @DisplayName("Should get all state names")
    void shouldGetAllStateNames() {
        // Given - use global fsmBehavior and include IDLE
        fsmBehavior.addState("IDLE", createTestBehavior("idle"));
        fsmBehavior.addState("A", createTestBehavior("a"));
        fsmBehavior.addState("B", createTestBehavior("b"));
        fsmBehavior.addState("C", createTestBehavior("c"));

        // Then
        assertThat(fsmBehavior.getStateNames()).containsExactlyInAnyOrder("IDLE", "A", "B", "C");
    }

    // Helper methods

    private Behavior createTestBehavior(String name) {
        return new TestBehavior(name, 10);
    }

    private Behavior createRecordingBehavior(String name, List<String> recordList) {
        return new RecordingBehavior(name, recordList);
    }

    private class TestBehavior implements Behavior {
        private final String name;
        private final long delayMs;
        private boolean active = true;

        TestBehavior(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public String getBehaviorId() {
            return name;
        }

        @Override
        public dev.jentic.core.Agent getAgent() {
            return null;
        }

        @Override
        public CompletableFuture<Void> execute() {
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                    stateHistory.add(name);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void stop() {
            active = false;
        }

        @Override
        public BehaviorType getType() {
            return BehaviorType.ONE_SHOT;
        }

        @Override
        public Duration getInterval() {
            return null;
        }
    }

    private class RecordingBehavior implements Behavior {
        private final String name;
        private final List<String> recordList;
        private boolean active = true;

        RecordingBehavior(String name, List<String> recordList) {
            this.name = name;
            this.recordList = recordList;
        }

        @Override
        public String getBehaviorId() {
            return name;
        }

        @Override
        public dev.jentic.core.Agent getAgent() {
            return null;
        }

        @Override
        public CompletableFuture<Void> execute() {
            return CompletableFuture.runAsync(() -> {
                recordList.add(name);
            });
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void stop() {
            active = false;
        }

        @Override
        public BehaviorType getType() {
            return BehaviorType.ONE_SHOT;
        }

        @Override
        public Duration getInterval() {
            return null;
        }
    }
}