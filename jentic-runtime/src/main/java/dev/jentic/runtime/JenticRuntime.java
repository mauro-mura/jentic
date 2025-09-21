package dev.jentic.runtime;

import dev.jentic.core.*;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.directory.LocalAgentDirectory;
import dev.jentic.runtime.discovery.AgentFactory;
import dev.jentic.runtime.discovery.AgentScanner;
import dev.jentic.runtime.discovery.AnnotationProcessor;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import dev.jentic.runtime.scheduler.SimpleBehaviorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main runtime for Jentic framework with automatic agent discovery.
 * Manages agent lifecycle, service discovery, and annotation processing.
 */
public class JenticRuntime {

    private static final Logger log = LoggerFactory.getLogger(JenticRuntime.class);

    private final MessageService messageService;
    private final AgentDirectory agentDirectory;
    private final BehaviorScheduler behaviorScheduler;

    // Discovery components
    private final AgentScanner agentScanner;
    private final AgentFactory agentFactory;
    private final AnnotationProcessor annotationProcessor;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Set<String> scanPackages = new HashSet<>();
    private final Map<Class<?>, Object> serviceInstances = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private JenticRuntime(Builder builder) {
        // Initialize services
        this.messageService = builder.messageService != null ?
                builder.messageService : new InMemoryMessageService();
        this.agentDirectory = builder.agentDirectory != null ?
                builder.agentDirectory : new LocalAgentDirectory();
        this.behaviorScheduler = builder.behaviorScheduler != null ?
                builder.behaviorScheduler : new SimpleBehaviorScheduler();

        // Initialize discovery components
        this.agentScanner = new AgentScanner();
        this.agentFactory = new AgentFactory(messageService, agentDirectory, behaviorScheduler);
        this.annotationProcessor = new AnnotationProcessor(messageService);

        // Configuration
        this.scanPackages.addAll(builder.scanPackages);
        this.serviceInstances.putAll(builder.serviceInstances);

        // Register additional services with factory
        for (Map.Entry<Class<?>, Object> entry : serviceInstances.entrySet()) {
            registerServiceUnchecked(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Start the runtime and all registered agents
     */
    public CompletableFuture<Void> start() {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Starting Jentic Runtime...");

        return CompletableFuture.runAsync(() -> {
            try {
                // Start core services
                behaviorScheduler.start().join();

                // Discover and create agents
                if (!scanPackages.isEmpty()) {
                    discoverAndCreateAgents();
                }

                // Process annotations for all agents
                processAgentAnnotations();

                // Start all agents
                startAllAgents();

                running = true;
                log.info("Jentic Runtime started successfully with {} agents", agents.size());

                // Log agent summary
                logAgentSummary();

            } catch (Exception e) {
                log.error("Failed to start Jentic Runtime", e);
                throw new RuntimeException("Failed to start runtime", e);
            }
        });
    }

    /**
     * Stop the runtime and all agents
     */
    public CompletableFuture<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Stopping Jentic Runtime...");

        return CompletableFuture.runAsync(() -> {
            try {
                // Stop all agents
                List<CompletableFuture<Void>> stopFutures = new ArrayList<>();
                for (Agent agent : agents.values()) {
                    stopFutures.add(agent.stop());
                }

                // Wait for all agents to stop
                CompletableFuture.allOf(stopFutures.toArray(new CompletableFuture[0])).join();

                // Stop core services
                behaviorScheduler.stop().join();

                running = false;
                log.info("Jentic Runtime stopped successfully");

            } catch (Exception e) {
                log.error("Error stopping Jentic Runtime", e);
            }
        });
    }

    /**
     * Get an agent by ID
     */
    public Optional<Agent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Get all registered agents
     */
    public Collection<Agent> getAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    /**
     * Register a new agent instance
     */
    public void registerAgent(Agent agent) {
        agents.put(agent.getAgentId(), agent);

        // Configure agent services
        if (agent instanceof BaseAgent baseAgent) {
            baseAgent.setMessageService(messageService);
            baseAgent.setAgentDirectory(agentDirectory);
            baseAgent.setBehaviorScheduler(behaviorScheduler);
        }

        log.info("Registered agent: {} ({})", agent.getAgentName(), agent.getAgentId());
    }

    /**
     * Create agent from class using annotation discovery
     */
    public <T extends Agent> T createAgent(Class<T> agentClass) {
        try {
            T agent = agentFactory.createAgent(agentClass);
            registerAgent(agent);

            // Process annotations if runtime is already started
            if (running) {
                annotationProcessor.processAnnotations(agent);
            }

            return agent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create agent: " + agentClass.getName(), e);
        }
    }

    /**
     * Check if runtime is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get runtime statistics
     */
    public RuntimeStats getStats() {
        long runningAgents = agents.values().stream().mapToLong(agent -> agent.isRunning() ? 1 : 0).sum();

        return new RuntimeStats(
                agents.size(),
                (int) runningAgents,
                scanPackages.size(),
                serviceInstances.size()
        );
    }

    /**
     * Helper method to register services with type erasure
     */
    @SuppressWarnings("unchecked")
    private <T> void registerServiceUnchecked(Class<?> serviceClass, Object instance) {
        agentFactory.addService((Class<T>) serviceClass, (T) instance);
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Discover agents from configured packages and create instances
     */
    private void discoverAndCreateAgents() {
        log.info("Starting agent discovery in {} packages", scanPackages.size());

        // Scan for agent classes
        String[] packageArray = scanPackages.toArray(new String[0]);
        Set<Class<? extends Agent>> agentClasses = agentScanner.scanForAgents(packageArray);

        if (agentClasses.isEmpty()) {
            log.info("No agent classes found in scanned packages");
            return;
        }

        // Create agent instances
        Map<String, Agent> discoveredAgents = agentFactory.createAgents(agentClasses);

        // Register discovered agents
        for (Agent agent : discoveredAgents.values()) {
            registerAgent(agent);
        }

        log.info("Agent discovery completed. Created {} agents from {} classes",
                discoveredAgents.size(), agentClasses.size());
    }

    /**
     * Process annotations for all registered agents
     */
    private void processAgentAnnotations() {
        log.info("Processing annotations for {} agents", agents.size());

        for (Agent agent : agents.values()) {
            try {
                annotationProcessor.processAnnotations(agent);
            } catch (Exception e) {
                log.error("Failed to process annotations for agent: {}", agent.getAgentId(), e);
            }
        }

        log.info("Annotation processing completed");
    }

    /**
     * Start all registered agents
     */
    private void startAllAgents() {
        List<CompletableFuture<Void>> startFutures = new ArrayList<>();

        for (Agent agent : agents.values()) {
            // Check if agent should auto-start
            if (shouldAutoStart(agent)) {
                startFutures.add(agent.start());
            }
        }

        // Wait for all agents to start
        CompletableFuture.allOf(startFutures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Check if agent should auto-start based on annotation
     */
    private boolean shouldAutoStart(Agent agent) {
        JenticAgent annotation = agent.getClass().getAnnotation(JenticAgent.class);
        return annotation == null || annotation.autoStart();
    }

    /**
     * Log summary of discovered and registered agents
     */
    private void logAgentSummary() {
        if (agents.isEmpty()) {
            log.info("No agents registered");
            return;
        }

        log.info("Agent Summary:");
        agents.values().forEach(agent -> {
            JenticAgent annotation = agent.getClass().getAnnotation(JenticAgent.class);
            String type = annotation != null ? annotation.type() : "unknown";
            String[] capabilities = annotation != null ? annotation.capabilities() : new String[0];

            log.info("  - {} ({}) - Type: {}, Capabilities: [{}], Running: {}",
                    agent.getAgentName(),
                    agent.getAgentId(),
                    type.isEmpty() ? agent.getClass().getSimpleName() : type,
                    String.join(", ", capabilities),
                    agent.isRunning());
        });
    }

    // ========== BUILDER ==========

    /**
     * Create a new runtime builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageService messageService;
        private AgentDirectory agentDirectory;
        private BehaviorScheduler behaviorScheduler;
        private final Set<String> scanPackages = new HashSet<>();
        private final Map<Class<?>, Object> serviceInstances = new HashMap<>();

        public Builder messageService(MessageService messageService) {
            this.messageService = messageService;
            return this;
        }

        public Builder agentDirectory(AgentDirectory agentDirectory) {
            this.agentDirectory = agentDirectory;
            return this;
        }

        public Builder behaviorScheduler(BehaviorScheduler behaviorScheduler) {
            this.behaviorScheduler = behaviorScheduler;
            return this;
        }

        public Builder scanPackage(String packageName) {
            if (packageName != null && !packageName.trim().isEmpty()) {
                this.scanPackages.add(packageName.trim());
            }
            return this;
        }

        public Builder scanPackages(String... packageNames) {
            for (String packageName : packageNames) {
                scanPackage(packageName);
            }
            return this;
        }

        public Builder scanPackages(Collection<String> packageNames) {
            for (String packageName : packageNames) {
                scanPackage(packageName);
            }
            return this;
        }

        public <T> Builder service(Class<T> serviceClass, T instance) {
            if (serviceClass != null && instance != null) {
                this.serviceInstances.put(serviceClass, instance);
            }
            return this;
        }

        public JenticRuntime build() {
            return new JenticRuntime(this);
        }
    }

    // ========== RUNTIME STATS ==========

    /**
     * Runtime statistics record
     */
    public record RuntimeStats(
            int totalAgents,
            int runningAgents,
            int scannedPackages,
            int registeredServices
    ) {
        @Override
        public String toString() {
            return String.format("RuntimeStats[agents=%d/%d, packages=%d, services=%d]",
                    runningAgents, totalAgents, scannedPackages, registeredServices);
        }
    }
}