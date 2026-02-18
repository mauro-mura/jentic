package dev.jentic.runtime;

import dev.jentic.core.Agent;
import dev.jentic.core.JenticConfiguration;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.directory.LocalAgentDirectory;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import dev.jentic.runtime.scheduler.SimpleBehaviorScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class JenticRuntimeTest {

    // Most tests create their own runtime inline to keep them self-contained.
    // Tests that need start()/stop() use a shared instance managed by @AfterEach.
    private JenticRuntime runtimeUnderTest;

    @AfterEach
    void stopRuntime() {
        if (runtimeUnderTest != null && runtimeUnderTest.isRunning()) {
            runtimeUnderTest.stop().join();
        }
    }

    // ========== CREATION ==========

    @Test
    void shouldCreateRuntimeWithDefaults() {
        JenticRuntime runtime = JenticRuntime.builder().build();

        assertThat(runtime).isNotNull();
        assertThat(runtime.isRunning()).isFalse();
        assertThat(runtime.getAgents()).isEmpty();
    }

    // ========== REGISTER / FIND ==========

    @Test
    void shouldRegisterAgents() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent1 = new TestAgent("agent-1", "Agent 1");
        TestAgent agent2 = new TestAgent("agent-2", "Agent 2");

        runtime.registerAgent(agent1);
        runtime.registerAgent(agent2);

        Collection<Agent> agents = runtime.getAgents();
        assertThat(agents).hasSize(2)
                          .containsExactlyInAnyOrder(agent1, agent2);
    }

    @Test
    void shouldFindAgentById() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("test-agent", "Test Agent");
        runtime.registerAgent(agent);

        var found    = runtime.getAgent("test-agent");
        var notFound = runtime.getAgent("non-existent");

        assertThat(found).isPresent().contains(agent);
        assertThat(notFound).isEmpty();
    }

    @Test
    void registerAgent_shouldThrowForNullAgent() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        assertThatThrownBy(() -> runtime.registerAgent(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Agent cannot be null");
    }

    @Test
    void registerAgent_shouldGenerateRandomIdWhenAgentIdIsNullOrBlank() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        // BaseAgent no-arg constructor leaves agentId unset
        NoIdAgent agent = new NoIdAgent();

        assertThatCode(() -> runtime.registerAgent(agent)).doesNotThrowAnyException();
        assertThat(runtime.getAgents()).hasSize(1);
    }

    // ========== START / STOP ==========

    @Test
    void shouldStartAndStopRuntime() {
        runtimeUnderTest = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("test-agent", "Test Agent");
        runtimeUnderTest.registerAgent(agent);

        runtimeUnderTest.start().join();
        assertThat(runtimeUnderTest.isRunning()).isTrue();
        assertThat(agent.isRunning()).isTrue();

        runtimeUnderTest.stop().join();
        assertThat(runtimeUnderTest.isRunning()).isFalse();
        assertThat(agent.isRunning()).isFalse();
    }

    @Test
    void start_shouldBeIdempotentWhenAlreadyRunning() {
        runtimeUnderTest = JenticRuntime.builder().build();
        runtimeUnderTest.start().join();

        // Second start must return immediately without error or state change
        assertThatCode(() -> runtimeUnderTest.start().join()).doesNotThrowAnyException();
        assertThat(runtimeUnderTest.isRunning()).isTrue();
    }

    @Test
    void stop_shouldBeIdempotentWhenNotRunning() {
        runtimeUnderTest = JenticRuntime.builder().build();
        assertThat(runtimeUnderTest.isRunning()).isFalse();

        assertThatCode(() -> runtimeUnderTest.stop().join()).doesNotThrowAnyException();
        assertThat(runtimeUnderTest.isRunning()).isFalse();
    }

    @Test
    void stop_shouldBeIdempotentAfterAlreadyStopped() {
        runtimeUnderTest = JenticRuntime.builder().build();
        runtimeUnderTest.start().join();
        runtimeUnderTest.stop().join();

        assertThatCode(() -> runtimeUnderTest.stop().join()).doesNotThrowAnyException();
        assertThat(runtimeUnderTest.isRunning()).isFalse();
    }

    // ========== UNREGISTER ==========

    @Test
    void unregisterAgent_shouldRemoveAgentFromRuntime() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("agent-1", "Agent 1");
        runtime.registerAgent(agent);

        runtime.unregisterAgent("agent-1").join();

        assertThat(runtime.getAgents()).isEmpty();
        assertThat(runtime.getAgent("agent-1")).isEmpty();
    }

    @Test
    void unregisterAgent_shouldStopRunningAgentBeforeRemoving() {
        runtimeUnderTest = JenticRuntime.builder().build();
        TestAgent agent = new TestAgent("agent-1", "Agent 1");
        runtimeUnderTest.registerAgent(agent);
        runtimeUnderTest.start().join();
        assertThat(agent.isRunning()).isTrue();

        runtimeUnderTest.unregisterAgent("agent-1").join();

        assertThat(agent.isRunning()).isFalse();
        assertThat(runtimeUnderTest.getAgent("agent-1")).isEmpty();
    }

    @Test
    void unregisterAgent_shouldHandleNonExistentAgentGracefully() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        assertThatCode(() -> runtime.unregisterAgent("does-not-exist").join())
            .doesNotThrowAnyException();
    }

    // ========== createAgent ==========

    @Test
    void shouldCreateAgentFromClass() {
        JenticRuntime runtime = JenticRuntime.builder().build();

        TestAgent agent = runtime.createAgent(TestAgent.class);

        assertThat(agent).isNotNull();
        assertThat(runtime.getAgents()).contains(agent);
        assertThat(agent.getMessageService()).isNotNull();
    }

    @Test
    void createAgent_shouldProcessAnnotationsIfRuntimeAlreadyRunning() {
        runtimeUnderTest = JenticRuntime.builder().build();
        runtimeUnderTest.start().join();

        // Exercises the `if (running)` branch inside createAgent
        TestAgent agent = runtimeUnderTest.createAgent(TestAgent.class);

        assertThat(agent).isNotNull();
        assertThat(runtimeUnderTest.getAgents()).contains(agent);
    }

    // ========== GETTERS ==========

    @Test
    void shouldExposeCorrectServices() {
        JenticRuntime runtime = JenticRuntime.builder().build();

        assertThat(runtime.getAgentDirectory()).isNotNull();
        assertThat(runtime.getMessageService()).isNotNull();
        assertThat(runtime.getBehaviorScheduler()).isNotNull();
        assertThat(runtime.getLifecycleManager()).isNotNull();
        assertThat(runtime.getConfiguration()).isNotNull();
    }

    // ========== STATS ==========

    @Test
    void shouldGetRuntimeStats() {
        runtimeUnderTest = JenticRuntime.builder()
            .scanPackage("com.example")
            .build();
        runtimeUnderTest.registerAgent(new TestAgent("agent-1", "Agent 1"));
        runtimeUnderTest.registerAgent(new TestAgent("agent-2", "Agent 2"));
        runtimeUnderTest.start().join();

        JenticRuntime.RuntimeStats stats = runtimeUnderTest.getStats();

        assertThat(stats.totalAgents()).isEqualTo(2);
        assertThat(stats.runningAgents()).isEqualTo(2);
        assertThat(stats.scannedPackages()).isEqualTo(1);
        assertThat(stats.registeredServices()).isEqualTo(0);
    }

    @Test
    void getStats_shouldReflectNoRunningAgentsBeforeStart() {
        JenticRuntime runtime = JenticRuntime.builder().build();
        runtime.registerAgent(new TestAgent("a1", "Agent 1"));

        JenticRuntime.RuntimeStats stats = runtime.getStats();

        assertThat(stats.totalAgents()).isEqualTo(1);
        assertThat(stats.runningAgents()).isEqualTo(0);
    }

    @Test
    void getStats_shouldReturnZerosWhenEmpty() {
        JenticRuntime runtime = JenticRuntime.builder().build();

        JenticRuntime.RuntimeStats stats = runtime.getStats();

        assertThat(stats.totalAgents()).isEqualTo(0);
        assertThat(stats.runningAgents()).isEqualTo(0);
    }

    // ========== RuntimeStats record ==========

    @Test
    void runtimeStats_toString_shouldContainKeyValues() {
        JenticRuntime.RuntimeStats stats = new JenticRuntime.RuntimeStats(5, 3, 2, 1);
        String str = stats.toString();
        assertThat(str).contains("5").contains("3").contains("RuntimeStats");
    }

    @Test
    void runtimeStats_accessors_shouldReturnCorrectValues() {
        JenticRuntime.RuntimeStats stats = new JenticRuntime.RuntimeStats(10, 7, 3, 4);
        assertThat(stats.totalAgents()).isEqualTo(10);
        assertThat(stats.runningAgents()).isEqualTo(7);
        assertThat(stats.scannedPackages()).isEqualTo(3);
        assertThat(stats.registeredServices()).isEqualTo(4);
    }

    // ========== BUILDER OPTIONS ==========

    @Test
    void builder_shouldAcceptCustomMessageService() {
        InMemoryMessageService customMs = new InMemoryMessageService();
        JenticRuntime r = JenticRuntime.builder().messageService(customMs).build();
        assertThat(r.getMessageService()).isSameAs(customMs);
    }

    @Test
    void builder_shouldAcceptCustomAgentDirectory() {
        LocalAgentDirectory dir = new LocalAgentDirectory();
        JenticRuntime r = JenticRuntime.builder().agentDirectory(dir).build();
        assertThat(r.getAgentDirectory()).isSameAs(dir);
    }

    @Test
    void builder_shouldAcceptCustomBehaviorScheduler() {
        SimpleBehaviorScheduler scheduler = new SimpleBehaviorScheduler();
        JenticRuntime r = JenticRuntime.builder().behaviorScheduler(scheduler).build();
        assertThat(r.getBehaviorScheduler()).isSameAs(scheduler);
    }

    @Test
    void builder_shouldAcceptScanPackagesVarargs() {
        JenticRuntime r = JenticRuntime.builder()
            .scanPackages("com.example.a", "com.example.b")
            .build();
        assertThat(r.getStats().scannedPackages()).isEqualTo(2);
    }

    @Test
    void builder_shouldAcceptScanPackagesCollection() {
        JenticRuntime r = JenticRuntime.builder()
            .scanPackages(List.of("com.example.a", "com.example.b", "com.example.c"))
            .build();
        assertThat(r.getStats().scannedPackages()).isEqualTo(3);
    }

    @Test
    void builder_scanPackage_shouldIgnoreNullAndBlankPackages() {
        JenticRuntime r = JenticRuntime.builder()
            .scanPackage(null)
            .scanPackage("   ")
            .build();
        assertThat(r.getStats().scannedPackages()).isEqualTo(0);
    }

    @Test
    void builder_shouldRegisterServices() {
        SampleService svc = new SampleService();
        JenticRuntime r = JenticRuntime.builder()
            .service(SampleService.class, svc)
            .build();
        assertThat(r.getStats().registeredServices()).isEqualTo(1);
    }

    @Test
    void builder_service_shouldIgnoreNullValues() {
        JenticRuntime r = JenticRuntime.builder()
            .service(null, null)
            .build();
        assertThat(r.getStats().registeredServices()).isEqualTo(0);
    }

    @Test
    void builder_shouldAcceptLlmMemoryManagerFactory() {
        JenticRuntime r = JenticRuntime.builder()
            .llmMemoryManagerFactory(agentId -> null)
            .build();
        assertThat(r).isNotNull();
    }

    @Test
    void builder_withConfiguration_shouldUseProvidedConfig() {
        JenticConfiguration config = JenticConfiguration.defaults();
        JenticRuntime r = JenticRuntime.builder().withConfiguration(config).build();
        assertThat(r.getConfiguration()).isEqualTo(config);
    }

    @Test
    void builder_withDefaultConfig_shouldNotThrow() {
        assertThatCode(() -> JenticRuntime.builder().withDefaultConfig().build())
            .doesNotThrowAnyException();
    }

    // ========== HELPERS ==========

    static class TestAgent extends BaseAgent {
        TestAgent(String agentId, String agentName) {
            super(agentId, agentName);
        }

        public TestAgent() {
            super();
        }
    }

    /** BaseAgent with no ID set — runtime must generate a random one. */
    static class NoIdAgent extends BaseAgent {
        NoIdAgent() {
            super();
        }
    }

    static class SampleService {}
}