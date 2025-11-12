package dev.jentic.runtime.discovery;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.jentic.core.Agent;
import dev.jentic.core.AgentDescriptor;
import dev.jentic.core.AgentDirectory;
import dev.jentic.core.AgentStatus;
import dev.jentic.core.BehaviorScheduler;
import dev.jentic.core.MessageService;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.exceptions.AgentException;
import dev.jentic.runtime.agent.BaseAgent;

/**
 * Factory for creating agent instances from annotated classes.
 * Handles dependency injection and configuration.
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final MessageService messageService;
    private final AgentDirectory agentDirectory;
    private final BehaviorScheduler behaviorScheduler;
    private final Map<Class<?>, Object> availableServices;

    public AgentFactory(MessageService messageService,
                        AgentDirectory agentDirectory,
                        BehaviorScheduler behaviorScheduler) {
        this.messageService = messageService;
        this.agentDirectory = agentDirectory;
        this.behaviorScheduler = behaviorScheduler;
        this.availableServices = new HashMap<>();

        // Register core services
        this.availableServices.put(MessageService.class, messageService);
        this.availableServices.put(AgentDirectory.class, agentDirectory);
        this.availableServices.put(BehaviorScheduler.class, behaviorScheduler);
    }

    /**
     * Add a service instance for dependency injection
     */
    public <T> void addService(Class<T> serviceClass, T instance) {
        availableServices.put(serviceClass, instance);
        log.debug("Registered service: {} -> {}", serviceClass.getSimpleName(), instance.getClass().getSimpleName());
    }

    /**
     * Create agent instances from the given classes
     */
    public Map<String, Agent> createAgents(Set<Class<? extends Agent>> agentClasses) {
        Map<String, Agent> agents = new HashMap<>();

        for (Class<? extends Agent> agentClass : agentClasses) {
            try {
                Agent agent = createAgent(agentClass);

                // Extract agent ID from annotation or use class name
                String agentId = extractAgentId(agentClass, agent);
                agents.put(agentId, agent);

                log.info("Created agent: {} from class: {}", agentId, agentClass.getSimpleName());

            } catch (Exception e) {
                log.error("Failed to create agent from class: {}", agentClass.getName(), e);
            }
        }

        return agents;
    }

    /**
     * Create a single agent instance
     */
    public <T extends Agent> T createAgent(Class<T> agentClass) throws AgentException {
        try {
            log.debug("Creating agent from class: {}", agentClass.getName());

            // Try constructor-based dependency injection
            T agent = tryConstructorInjection(agentClass);

            // Configure agent services if it's a BaseAgent
            if (agent instanceof BaseAgent baseAgent) {
                configureBaseAgent(baseAgent);
            }

            return agent;

        } catch (Exception e) {
            throw new AgentException("unknown", "Failed to create agent from class: " + agentClass.getName(), e);
        }
    }

    /**
     * Try to instantiate using constructor dependency injection
     */
    private <T extends Agent> T tryConstructorInjection(Class<T> agentClass) throws Exception {
        Constructor<?>[] constructors = agentClass.getDeclaredConstructors();

        // Sort constructors by parameter count (try most specific first)
        java.util.Arrays.sort(constructors, (a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));

        for (Constructor<?> constructor : constructors) {
            Object[] args = resolveConstructorArguments(constructor);
            if (args != null) {
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                T instance = (T) constructor.newInstance(args);

                log.debug("Successfully instantiated {} using constructor with {} parameters",
                        agentClass.getSimpleName(), constructor.getParameterCount());
                return instance;
            }
        }

        throw new IllegalArgumentException("No suitable constructor found for agent class: " + agentClass.getName());
    }

    /**
     * Resolve constructor arguments from available services
     */
    private Object[] resolveConstructorArguments(Constructor<?> constructor) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Object service = availableServices.get(parameterTypes[i]);

            if (service != null) {
                args[i] = service;
            } else if (parameterTypes[i] == String.class) {
                // For String parameters, we might need agent ID - handle this case
                args[i] = null; // Will be resolved later if needed
            } else {
                // Cannot satisfy this constructor
                log.trace("Cannot resolve parameter {} of type {} for constructor",
                        i, parameterTypes[i].getSimpleName());
                return null;
            }
        }

        return args;
    }

    /**
     * Configure BaseAgent with required services
     */
    private void configureBaseAgent(BaseAgent baseAgent) {
        baseAgent.setMessageService(messageService);
        baseAgent.setAgentDirectory(agentDirectory);
        baseAgent.setBehaviorScheduler(behaviorScheduler);

        log.debug("Configured BaseAgent: {}", baseAgent.getAgentId());
    }

    /**
     * Extract agent ID from annotation or agent instance
     */
    private String extractAgentId(Class<? extends Agent> agentClass, Agent agent) {
        JenticAgent annotation = agentClass.getAnnotation(JenticAgent.class);

        if (annotation != null && !annotation.value().trim().isEmpty()) {
            return annotation.value().trim();
        }

        // Fallback to agent's own ID
        return agent.getAgentId();
    }

    /**
     * Extract agent metadata from annotation
     */
    public AgentDescriptor createDescriptor(Class<? extends Agent> agentClass, Agent agent) {
        JenticAgent annotation = agentClass.getAnnotation(JenticAgent.class);

        if (annotation == null) {
            // Minimal descriptor for non-annotated agents
            return AgentDescriptor.builder(agent.getAgentId())
                    .agentName(agent.getAgentName())
                    .agentType("unknown")
                    .status(AgentStatus.STOPPED)
                    .build();
        }

        // Create descriptor from annotation
        AgentDescriptor.AgentDescriptorBuilder builder = AgentDescriptor.builder(agent.getAgentId())
                .agentName(agent.getAgentName())
                .agentType(annotation.type().isEmpty() ? agentClass.getSimpleName() : annotation.type())
                .status(AgentStatus.STOPPED);

        // Add capabilities
        for (String capability : annotation.capabilities()) {
            if (!capability.trim().isEmpty()) {
                builder.capability(capability.trim());
            }
        }

        // Add metadata
        builder.metadata("class", agentClass.getName())
                .metadata("autoStart", String.valueOf(annotation.autoStart()));

        return builder.build();
    }
}