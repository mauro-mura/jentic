package dev.jentic.runtime.discovery;

import dev.jentic.core.*;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor for handling annotations on agent classes.
 * Creates behaviors and message handlers from annotations.
 */
public class AnnotationProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(AnnotationProcessor.class);
    
    private final MessageService messageService;
    
    public AnnotationProcessor(MessageService messageService) {
        this.messageService = messageService;
    }
    
    /**
     * Process all annotations on an agent
     */
    public void processAnnotations(Agent agent) {
        Class<?> agentClass = agent.getClass();
        
        log.debug("Processing annotations for agent: {} ({})", agent.getAgentName(), agentClass.getName());
        
        // Process behavior annotations
        processBehaviorAnnotations(agent, agentClass);
        
        // Process message handler annotations
        processMessageHandlerAnnotations(agent, agentClass);
        
        log.debug("Annotation processing completed for agent: {}", agent.getAgentName());
    }
    
    /**
     * Process @JenticBehavior annotations
     */
    private void processBehaviorAnnotations(Agent agent, Class<?> agentClass) {
        Method[] methods = agentClass.getDeclaredMethods();
        
        for (Method method : methods) {
            JenticBehavior behaviorAnnotation = method.getAnnotation(JenticBehavior.class);
            
            if (behaviorAnnotation != null) {
                try {
                    createBehaviorFromAnnotation(agent, method, behaviorAnnotation);
                } catch (Exception e) {
                    log.error("Failed to create behavior from annotation for method: {}.{}", 
                             agentClass.getName(), method.getName(), e);
                }
            }
        }
    }
    
    /**
     * Process @JenticMessageHandler annotations
     */
    private void processMessageHandlerAnnotations(Agent agent, Class<?> agentClass) {
        Method[] methods = agentClass.getDeclaredMethods();
        
        for (Method method : methods) {
            JenticMessageHandler handlerAnnotation = method.getAnnotation(JenticMessageHandler.class);
            
            if (handlerAnnotation != null && handlerAnnotation.autoSubscribe()) {
                try {
                    createMessageHandlerFromAnnotation(agent, method, handlerAnnotation);
                } catch (Exception e) {
                    log.error("Failed to create message handler from annotation for method: {}.{}", 
                             agentClass.getName(), method.getName(), e);
                }
            }
        }
    }
    
    /**
     * Create behavior from @JenticBehavior annotation
     */
    private void createBehaviorFromAnnotation(Agent agent, Method method, JenticBehavior annotation) {
        if (!annotation.autoStart()) {
            log.debug("Skipping auto-start for behavior method: {}", method.getName());
            return;
        }
        
        // Validate method signature
        if (!isValidBehaviorMethod(method)) {
            log.warn("Invalid behavior method signature: {}. Method should be public, non-static, and have no parameters", 
                    method.getName());
            return;
        }
        
        method.setAccessible(true);
        
        Behavior behavior = switch (annotation.type()) {
            case ONE_SHOT -> createOneShotBehavior(agent, method, annotation);
            case CYCLIC -> createCyclicBehavior(agent, method, annotation);
            case WAKER -> createWakerBehavior(agent, method, annotation);
            case EVENT_DRIVEN -> createEventDrivenBehavior(agent, method, annotation);
            case CUSTOM -> createCustomBehavior(agent, method, annotation);
        };
        
        if (behavior != null) {
            if (behavior instanceof BaseBehavior baseBehavior) {
                baseBehavior.setAgent(agent);
            }
            
            agent.addBehavior(behavior);
            
            log.info("Added {} behavior '{}' to agent '{}'", 
                    annotation.type().name().toLowerCase(), 
                    method.getName(), 
                    agent.getAgentName());
        }
    }
    
    /**
     * Create message handler from @JenticMessageHandler annotation
     */
    private void createMessageHandlerFromAnnotation(Agent agent, Method method, JenticMessageHandler annotation) {
        // Validate method signature
        if (!isValidMessageHandlerMethod(method)) {
            log.warn("Invalid message handler method signature: {}. Method should be public, non-static, and take a Message parameter", 
                    method.getName());
            return;
        }
        
        method.setAccessible(true);
        String topic = annotation.value();
        
        MessageHandler handler = MessageHandler.sync(message -> {
            try {
                method.invoke(agent, message);
            } catch (Exception e) {
                throw new RuntimeException("Error executing message handler: " + method.getName(), e);
            }
        });
        
        String subscriptionId = messageService.subscribe(topic, handler);
        
        log.info("Subscribed agent '{}' to topic '{}' (method: {}, subscription: {})", 
                agent.getAgentName(), topic, method.getName(), subscriptionId);
    }
    
    private Behavior createOneShotBehavior(Agent agent, Method method, JenticBehavior annotation) {
        String behaviorId = generateBehaviorId(agent, method);
        
        return new OneShotBehavior(behaviorId) {
            @Override
            protected void action() {
                invokeMethod(agent, method);
            }
        };
    }
    
    private Behavior createCyclicBehavior(Agent agent, Method method, JenticBehavior annotation) {
        String behaviorId = generateBehaviorId(agent, method);
        Duration interval = parseDuration(annotation.interval());
        
        return new CyclicBehavior(behaviorId, interval) {
            @Override
            protected void action() {
                invokeMethod(agent, method);
            }
        };
    }
    
    private Behavior createWakerBehavior(Agent agent, Method method, JenticBehavior annotation) {
        String behaviorId = generateBehaviorId(agent, method);
        Duration initialDelay = parseDuration(annotation.initialDelay());
        
        // For now, treat waker as delayed one-shot
        return WakerBehavior.wakeAfter(initialDelay, () -> invokeMethod(agent, method));
    }
    
    private Behavior createEventDrivenBehavior(Agent agent, Method method, JenticBehavior annotation) {
        // Event-driven behaviors typically need a topic - for now, use method name as topic
        String topic = method.getName().toLowerCase();
        String behaviorId = generateBehaviorId(agent, method);
        
        return new EventDrivenBehavior(behaviorId, topic) {
            @Override
            protected void handleMessage(Message message) {
                invokeMethod(agent, method);
            }
        };
    }
    
    private Behavior createCustomBehavior(Agent agent, Method method, JenticBehavior annotation) {
        // Custom behaviors use the interval as their execution pattern
        String behaviorId = generateBehaviorId(agent, method);
        Duration interval = parseDuration(annotation.interval());
        
        return new CyclicBehavior(behaviorId, interval) {
            @Override
            protected void action() {
                invokeMethod(agent, method);
            }
        };
    }
    
    private void invokeMethod(Agent agent, Method method) {
        try {
            method.invoke(agent);
        } catch (Exception e) {
            log.error("Error invoking behavior method: {}.{}", 
                     agent.getClass().getName(), method.getName(), e);
        }
    }
    
    private String generateBehaviorId(Agent agent, Method method) {
        return agent.getAgentId() + "." + method.getName();
    }
    
    private Duration parseDuration(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) {
            return Duration.ofSeconds(1); // Default interval
        }
        
        try {
            durationString = durationString.trim().toLowerCase();
            
            if (durationString.endsWith("ms")) {
                long value = Long.parseLong(durationString.substring(0, durationString.length() - 2));
                return Duration.ofMillis(value);
            } else if (durationString.endsWith("s")) {
                long value = Long.parseLong(durationString.substring(0, durationString.length() - 1));
                return Duration.ofSeconds(value);
            } else if (durationString.endsWith("m")) {
                long value = Long.parseLong(durationString.substring(0, durationString.length() - 1));
                return Duration.ofMinutes(value);
            } else if (durationString.endsWith("min")) {
                long value = Long.parseLong(durationString.substring(0, durationString.length() - 3));
                return Duration.ofMinutes(value);
            } else if (durationString.endsWith("h")) {
                long value = Long.parseLong(durationString.substring(0, durationString.length() - 1));
                return Duration.ofHours(value);
            } else {
                // Try to parse as seconds
                long value = Long.parseLong(durationString);
                return Duration.ofSeconds(value);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid duration format: '{}', using 1 second default", durationString);
            return Duration.ofSeconds(1);
        }
    }
    
    private boolean isValidBehaviorMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) &&
               !Modifier.isStatic(method.getModifiers()) &&
               method.getParameterCount() == 0 &&
               (method.getReturnType() == void.class || method.getReturnType() == Void.class);
    }
    
    private boolean isValidMessageHandlerMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) &&
               !Modifier.isStatic(method.getModifiers()) &&
               method.getParameterCount() == 1 &&
               method.getParameterTypes()[0] == Message.class &&
               (method.getReturnType() == void.class || method.getReturnType() == Void.class);
    }
}