package dev.jentic.runtime;

import dev.jentic.core.*;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.config.ConfigurationLoader;
import dev.jentic.core.exceptions.ConfigurationException;
import dev.jentic.core.memory.MemoryStore;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.directory.LocalAgentDirectory;
import dev.jentic.runtime.discovery.AgentFactory;
import dev.jentic.runtime.discovery.AgentScanner;
import dev.jentic.runtime.discovery.AnnotationProcessor;
import dev.jentic.runtime.lifecycle.LifecycleListener;
import dev.jentic.runtime.lifecycle.LifecycleManager;
import dev.jentic.runtime.memory.InMemoryStore;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import dev.jentic.runtime.scheduler.SimpleBehaviorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main runtime for Jentic framework with automatic agent discovery.
 * Manages agent lifecycle, service discovery, and annotation processing.
 */
public class JenticRuntime {

    private static final Logger log = LoggerFactory.getLogger(JenticRuntime.class);

    private final JenticConfiguration configuration;
    private final MessageService messageService;
    private final AgentDirectory agentDirectory;
    private final BehaviorScheduler behaviorScheduler;
    private final MemoryStore memoryStore;

    // Discovery components
    private final AgentScanner agentScanner;
    private final AgentFactory agentFactory;
    private final AnnotationProcessor annotationProcessor;
    private final LifecycleManager lifecycleManager;

    // Configuration
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Set<String> scanPackages = new HashSet<>();
    private final Map<Class<?>, Object> serviceInstances = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    private JenticRuntime(Builder builder) {
        // Configuration (from file or builder)
        this.configuration = builder.configuration;

        logConfigurationInfo();

        // Initialize services
        this.messageService = builder.messageService != null ?
                builder.messageService : new InMemoryMessageService();
        this.agentDirectory = builder.agentDirectory != null ?
                builder.agentDirectory : new LocalAgentDirectory();
        this.behaviorScheduler = builder.behaviorScheduler != null ?
                builder.behaviorScheduler : new SimpleBehaviorScheduler();
        this.memoryStore = builder.memoryStore != null ?
        		builder.memoryStore : new InMemoryStore();

        // Initialize discovery components
        this.agentScanner = new AgentScanner();
        this.agentFactory = new AgentFactory(messageService, agentDirectory, behaviorScheduler, memoryStore);
        this.annotationProcessor = new AnnotationProcessor(messageService);
        this.lifecycleManager = new LifecycleManager();

        // Add default lifecycle listener
        this.lifecycleManager.addLifecycleListener(LifecycleListener.logging());

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
                    if (agent.isRunning()) {
                        CompletableFuture<Void> stopFuture = lifecycleManager
                                .stopAgent(agent, DEFAULT_SHUTDOWN_TIMEOUT)
                                .thenCompose(v -> {
                                    // Unregister from directory after successful stop
                                    log.debug("Unregistering agent {} from directory", agent.getAgentId());
                                    return agentDirectory.unregister(agent.getAgentId());
                                })
                                .exceptionally(throwable -> {
                                    log.error("Failed to stop agent: {} - {}",
                                            agent.getAgentId(), throwable.getMessage());
                                    return null;
                                });
                        stopFutures.add(stopFuture);
                    }
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
        Objects.requireNonNull(agent, "Agent cannot be null");
        String agentId = agent.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = java.util.UUID.randomUUID().toString();
            log.warn("Agent ID not set. Generated random ID: {}", agentId);
        }
        agents.put(agentId, agent);

        // Configure agent services
        if (agent instanceof BaseAgent baseAgent) {
            baseAgent.setMessageService(messageService);
            baseAgent.setAgentDirectory(agentDirectory);
            baseAgent.setBehaviorScheduler(behaviorScheduler);
            if (memoryStore != null) {
            	baseAgent.setMemoryStore(memoryStore);
            }
        }

        // Create descriptor and register in directory
        AgentDescriptor descriptor = agentFactory.createDescriptor(
                agent.getClass(),
                agent
        );

        agentDirectory.register(descriptor)
                .exceptionally(throwable -> {
                    log.error("Failed to register agent {} in directory: {}",
                            agent.getAgentId(), throwable.getMessage());
                    return null;
                });

        log.info("Registered agent: {} ({}) in runtime and directory",
                agent.getAgentName(), agent.getAgentId());
    }

    /**
     * Create agent from class using annotation discovery
     */
    public <T extends Agent> T createAgent(Class<T> agentClass) {
        try {
            T agent = agentFactory.createAgent(agentClass);
            Objects.requireNonNull(agent, "Factory returned null agent for class: " + agentClass.getName());

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
     * Get the agent directory
     */
    public AgentDirectory getAgentDirectory() {
        return agentDirectory;
    }

    /**
     * Get the message service
     */
    public MessageService getMessageService() {
        return messageService;
    }

    /**
     * Get the behavior scheduler
     */
    public BehaviorScheduler getBehaviorScheduler() {
        return behaviorScheduler;
    }

    /**
     * Check if runtime is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the lifecycle manager
     */
    public LifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    /**
     * Get the current configuration
     */
    public JenticConfiguration getConfiguration() {
        return configuration;
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
     * Log configuration information
     */
    private void logConfigurationInfo() {
        log.debug("Runtime Configuration:");
        log.debug("  Name: {}", configuration.runtime().name());
        log.debug("  Environment: {}", configuration.runtime().environment());

        if (!configuration.runtime().properties().isEmpty()) {
            log.debug("  Properties:");
            configuration.runtime().properties().forEach((key, value) ->
                    log.debug("    {}: {}", key, value)
            );
        }

        log.debug("Agent Configuration:");
        log.debug("  Auto Discovery: {}", configuration.agents().autoDiscovery());
        log.debug("  Scan Packages: {}", configuration.agents().scanPackages());

        if (!configuration.agents().properties().isEmpty()) {
            log.debug("  Properties:");
            configuration.agents().properties().forEach((key, value) ->
                    log.debug("    {}: {}", key, value)
            );
        }
    }

    /**
     * Discover agents from configured packages and create instances
     */
    private void discoverAndCreateAgents() {
        List<String> packages = configuration.agents().getAllScanPackages();
        log.info("Starting agent discovery in {} packages", packages.size());

        // Scan for agent classes
        String[] packageArray = packages.toArray(new String[0]);
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
                // Use LifecycleManager for proper state tracking and timeout handling
                CompletableFuture<Void> startFuture = lifecycleManager
                        .startAgent(agent, DEFAULT_STARTUP_TIMEOUT)
                        .thenCompose(v -> {
                            // Update agent status in directory after successful start
                            log.debug("Updating agent {} status to RUNNING in directory",
                                    agent.getAgentId());
                            return agentDirectory.updateStatus(agent.getAgentId(), AgentStatus.RUNNING);
                        })
                        .exceptionally(throwable -> {
                            log.error("Failed to start agent: {} - {}",
                                    agent.getAgentId(), throwable.getMessage());
                            // Update status to ERROR in directory
                            agentDirectory.updateStatus(agent.getAgentId(), AgentStatus.ERROR)
                                    .exceptionally(ex -> {
                                        log.warn("Could not update error status for agent {}",
                                                agent.getAgentId());
                                        return null;
                                    });
                            // Don't fail entire startup for one agent
                            return null;
                        });
                startFutures.add(startFuture);
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

    /**
     * Unregister an agent from runtime and directory
     */
    public CompletableFuture<Void> unregisterAgent(String agentId) {
        Agent agent = agents.remove(agentId);

        if (agent == null) {
            log.warn("Attempted to unregister non-existent agent: {}", agentId);
            return CompletableFuture.completedFuture(null);
        }

        log.info("Unregistering agent: {} ({})", agent.getAgentName(), agentId);

        // Stop if running
        CompletableFuture<Void> stopFuture = agent.isRunning()
                ? agent.stop()
                : CompletableFuture.completedFuture(null);

        // Then unregister from directory
        return stopFuture
                .thenCompose(v -> agentDirectory.unregister(agentId))
                .exceptionally(throwable -> {
                    log.error("Error unregistering agent {}: {}",
                            agentId, throwable.getMessage());
                    return null;
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
        private JenticConfiguration configuration;
        private MessageService messageService;
        private AgentDirectory agentDirectory;
        private BehaviorScheduler behaviorScheduler;
        private MemoryStore memoryStore;
        private final Set<String> scanPackages = new HashSet<>();
        private final Map<Class<?>, Object> serviceInstances = new HashMap<>();

        /**
         * Load configuration from YAML/JSON file
         *
         * @param configPath path to configuration file
         * @return this builder
         * @throws ConfigurationException if loading fails
         */
        public Builder fromConfig(String configPath) throws ConfigurationException {
            ConfigurationLoader loader = new ConfigurationLoader();
            this.configuration = loader.loadFromFile(configPath);
            loader.validate(this.configuration);
            log.info("Loaded configuration from file: {}", configPath);
            return this;
        }

        /**
         * Load configuration from classpath resource
         *
         * @param resourcePath classpath resource path
         * @return this builder
         * @throws ConfigurationException if loading fails
         */
        public Builder fromClasspathConfig(String resourcePath) throws ConfigurationException {
            ConfigurationLoader loader = new ConfigurationLoader();
            this.configuration = loader.loadFromClasspath(resourcePath);
            loader.validate(this.configuration);
            log.info("Loaded configuration from classpath: {}", resourcePath);
            return this;
        }

        /**
         * Use provided configuration object
         *
         * @param config the configuration to use
         * @return this builder
         */
        public Builder withConfiguration(JenticConfiguration config) {
            this.configuration = config;
            log.info("Using provided configuration: {}", config.runtime().name());
            return this;
        }

        /**
         * Load default configuration (jentic.yml from filesystem or classpath)
         *
         * @return this builder
         */
        public Builder withDefaultConfig() {
            ConfigurationLoader loader = new ConfigurationLoader();
            this.configuration = loader.loadDefault();
            try {
                loader.validate(this.configuration);
            } catch (ConfigurationException e) {
                log.warn("Default configuration validation failed: {}", e.getMessage());
            }
            log.info("Loaded default configuration");
            return this;
        }

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
        
        public Builder memoryStore(MemoryStore memoryStore) {
           this.memoryStore = memoryStore;
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
            // Use default config if none provided
            if (this.configuration == null) {
                log.debug("No configuration provided, using defaults");
                this.configuration = JenticConfiguration.defaults();
            }

            // Merge builder scan packages with configuration
            if (!scanPackages.isEmpty()) {
                log.debug("Merging {} builder scan packages with configuration", scanPackages.size());

                List<String> allPackages = new ArrayList<>(configuration.agents().getAllScanPackages());
                allPackages.addAll(scanPackages);

                JenticConfiguration.AgentsConfig updatedAgentsConfig =
                        new JenticConfiguration.AgentsConfig(
                                configuration.agents().autoDiscovery(),
                                configuration.agents().basePackage(),
                                configuration.agents().scanPaths(),
                                allPackages,  // scanPackages
                                configuration.agents().properties()
                        );

                this.configuration = new JenticConfiguration(
                        configuration.runtime(),
                        updatedAgentsConfig,
                        configuration.messaging(),
                        configuration.directory(),
                        configuration.scheduler()
                );
            }
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