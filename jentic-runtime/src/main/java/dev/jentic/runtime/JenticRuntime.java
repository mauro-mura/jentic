package dev.jentic.runtime;

import dev.jentic.core.*;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.*;
import dev.jentic.runtime.directory.LocalAgentDirectory;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import dev.jentic.runtime.scheduler.SimpleBehaviorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main runtime for Jentic framework.
 * Manages agent lifecycle, service discovery, and configuration.
 */
public class JenticRuntime {
    
    private static final Logger log = LoggerFactory.getLogger(JenticRuntime.class);
    
    private final MessageService messageService;
    private final AgentDirectory agentDirectory;
    private final BehaviorScheduler behaviorScheduler;
    
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Set<String> scanPackages = new HashSet<>();
    private final Map<Class<?>, Object> serviceInstances = new ConcurrentHashMap<>();
    
    private volatile boolean running = false;
    
    private JenticRuntime(Builder builder) {
        this.messageService = builder.messageService != null ? 
            builder.messageService : new InMemoryMessageService();
        this.agentDirectory = builder.agentDirectory != null ? 
            builder.agentDirectory : new LocalAgentDirectory();
        this.behaviorScheduler = builder.behaviorScheduler != null ? 
            builder.behaviorScheduler : new SimpleBehaviorScheduler();
        
        this.scanPackages.addAll(builder.scanPackages);
        this.serviceInstances.putAll(builder.serviceInstances);
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
                
                // Discover and register agents
                discoverAgents();
                
                // Start all agents
                List<CompletableFuture<Void>> startFutures = new ArrayList<>();
                for (Agent agent : agents.values()) {
                    startFutures.add(agent.start());
                }
                
                // Wait for all agents to start
                CompletableFuture.allOf(startFutures.toArray(new CompletableFuture[0])).join();
                
                running = true;
                log.info("Jentic Runtime started successfully with {} agents", agents.size());
                
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
        processAnnotations(agent);
        // Configure agent services
        if (agent instanceof BaseAgent baseAgent) {
            baseAgent.setMessageService(messageService);
            baseAgent.setAgentDirectory(agentDirectory);
            baseAgent.setBehaviorScheduler(behaviorScheduler);
        }
        
        log.info("Registered agent: {} ({})", agent.getAgentName(), agent.getAgentId());
    }
    
    /**
     * Create agent from class
     */
    public <T extends Agent> T createAgent(Class<T> agentClass) {
        try {
            T agent = instantiateAgent(agentClass);

            registerAgent(agent);
            return agent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create agent: " + agentClass.getName(), e);
        }
    }
    
    private void discoverAgents() {
        for (String packageName : scanPackages) {
            log.debug("Scanning package for agents: {}", packageName);
            
            // For MVP, we'll need a simple class scanner
            // In a real implementation, you'd use libraries like Reflections
            Set<Class<?>> agentClasses = findAnnotatedClasses(packageName, JenticAgent.class);
            
            for (Class<?> agentClass : agentClasses) {
                if (Agent.class.isAssignableFrom(agentClass)) {
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends Agent> typedClass = (Class<? extends Agent>) agentClass;
                        createAgent(typedClass);
                    } catch (Exception e) {
                        log.error("Failed to create agent from class: {}", agentClass.getName(), e);
                    }
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Agent> T instantiateAgent(Class<T> agentClass) throws Exception {
        // Try to find a constructor that we can satisfy with available services
        Constructor<?>[] constructors = agentClass.getDeclaredConstructors();
        
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            boolean canSatisfy = true;
            
            for (int i = 0; i < paramTypes.length; i++) {
                Object service = serviceInstances.get(paramTypes[i]);
                if (service != null) {
                    args[i] = service;
                } else if (paramTypes[i] == MessageService.class) {
                    args[i] = messageService;
                } else if (paramTypes[i] == AgentDirectory.class) {
                    args[i] = agentDirectory;
                } else if (paramTypes[i] == BehaviorScheduler.class) {
                    args[i] = behaviorScheduler;
                } else {
                    canSatisfy = false;
                    break;
                }
            }
            
            if (canSatisfy) {
                constructor.setAccessible(true);
                return (T) constructor.newInstance(args);
            }
        }
        
        // Fallback to no-args constructor
        return agentClass.getDeclaredConstructor().newInstance();
    }
    
    private void processAnnotations(Agent agent) {
        Class<?> agentClass = agent.getClass();
        
        // Process @JenticBehavior annotations
        for (Method method : agentClass.getDeclaredMethods()) {
            JenticBehavior behaviorAnnotation = method.getAnnotation(JenticBehavior.class);
            if (behaviorAnnotation != null && behaviorAnnotation.autoStart()) {
                createBehaviorFromAnnotation(agent, method, behaviorAnnotation);
            }
            
            JenticMessageHandler handlerAnnotation = method.getAnnotation(JenticMessageHandler.class);
            if (handlerAnnotation != null && handlerAnnotation.autoSubscribe()) {
                createMessageHandlerFromAnnotation(agent, method, handlerAnnotation);
            }
        }
    }
    
    private void createBehaviorFromAnnotation(Agent agent, Method method, JenticBehavior annotation) {
        try {
            method.setAccessible(true);
            
            Behavior behavior = switch (annotation.type()) {
                case ONE_SHOT -> OneShotBehavior.from(method.getName(), () -> {
                    try {
                        method.invoke(agent);
                    } catch (Exception e) {
                        throw new RuntimeException("Error executing behavior method", e);
                    }
                });
                
                case CYCLIC -> {
                    Duration interval = parseDuration(annotation.interval());
                    yield CyclicBehavior.from(method.getName(), interval, () -> {
                        try {
                            method.invoke(agent);
                        } catch (Exception e) {
                            throw new RuntimeException("Error executing behavior method", e);
                        }
                    });
                }
                
                case EVENT_DRIVEN -> new EventDrivenBehavior(method.getName(), "default") {
                    @Override
                    protected void handleMessage(Message message) {
                        try {
                            method.invoke(agent, message);
                        } catch (Exception e) {
                            throw new RuntimeException("Error executing event-driven behavior", e);
                        }
                    }
                };
                
                default -> throw new UnsupportedOperationException("Behavior type not supported: " + annotation.type());
            };
            
            if (behavior instanceof BaseBehavior baseBehavior) {
                baseBehavior.setAgent(agent);
            }
            
            agent.addBehavior(behavior);
            log.debug("Added behavior: {} to agent: {}", behavior.getBehaviorId(), agent.getAgentId());
            
        } catch (Exception e) {
            log.error("Failed to create behavior from annotation for method: {}", method.getName(), e);
        }
    }
    
    private void createMessageHandlerFromAnnotation(Agent agent, Method method, JenticMessageHandler annotation) {
        try {
            method.setAccessible(true);
            String topic = annotation.value();
            
            MessageHandler handler = MessageHandler.sync(message -> {
                try {
                    method.invoke(agent, message);
                } catch (Exception e) {
                    throw new RuntimeException("Error executing message handler", e);
                }
            });
            
            messageService.subscribe(topic, handler);
            log.debug("Subscribed agent {} to topic: {}", agent.getAgentId(), topic);
            
        } catch (Exception e) {
            log.error("Failed to create message handler from annotation for method: {}", method.getName(), e);
        }
    }
    
    private Duration parseDuration(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) {
            return Duration.ofSeconds(1); // Default
        }
        
        // Simple parser for common formats: "30s", "1m", "5min"
        durationString = durationString.trim().toLowerCase();
        
        if (durationString.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(durationString.substring(0, durationString.length() - 2)));
        } else if (durationString.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(durationString.substring(0, durationString.length() - 1)));
        } else if (durationString.endsWith("m") || durationString.endsWith("min")) {
            String number = durationString.endsWith("min") ? 
                durationString.substring(0, durationString.length() - 3) :
                durationString.substring(0, durationString.length() - 1);
            return Duration.ofMinutes(Long.parseLong(number));
        } else if (durationString.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(durationString.substring(0, durationString.length() - 1)));
        }
        
        // Fallback: try to parse as seconds
        try {
            return Duration.ofSeconds(Long.parseLong(durationString));
        } catch (NumberFormatException e) {
            log.warn("Could not parse duration: {}, using 1 second default", durationString);
            return Duration.ofSeconds(1);
        }
    }
    
    // Simplified class scanning (for MVP)
    // In production, use libraries like Reflections or Spring's ClassPathScanningCandidateComponentProvider
    private Set<Class<?>> findAnnotatedClasses(String packageName, Class<? extends java.lang.annotation.Annotation> annotation) {
        // For MVP, return empty set - agents will be registered manually
        // TODO: Implement proper classpath scanning
        return Collections.emptySet();
    }
    
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
            this.scanPackages.add(packageName);
            return this;
        }
        
        public Builder scanPackages(String... packageNames) {
            Collections.addAll(this.scanPackages, packageNames);
            return this;
        }
        
        public <T> Builder service(Class<T> serviceClass, T instance) {
            this.serviceInstances.put(serviceClass, instance);
            return this;
        }
        
        public JenticRuntime build() {
            return new JenticRuntime(this);
        }
    }
}