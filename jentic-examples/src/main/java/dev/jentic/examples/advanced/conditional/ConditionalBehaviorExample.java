package dev.jentic.examples.advanced.conditional;

import dev.jentic.core.Message;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.condition.Condition;
import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.advanced.ConditionalBehavior;
import dev.jentic.runtime.condition.SystemCondition;
import dev.jentic.runtime.condition.TimeCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Example demonstrating ConditionalBehavior usage.
 * Shows how to execute behaviors only when specific conditions are met.
 */
public class ConditionalBehaviorExample {
    
    private static final Logger log = LoggerFactory.getLogger(ConditionalBehaviorExample.class);
    
    public static void main(String[] args) throws InterruptedException {
        log.info("=== Conditional Behavior Example ===");
        
        // Create runtime
        var runtime = JenticRuntime.builder().build();
        
        // Register conditional agents
        runtime.registerAgent(new ResourceAwareAgent());
        runtime.registerAgent(new BusinessHoursAgent());
        runtime.registerAgent(new AdaptiveAgent());
        
        // Start runtime
        runtime.start().join();
        
        log.info("Conditional behaviors running. Watch for condition-based execution...");
        log.info("ResourceAwareAgent: Only runs when system load is low");
        log.info("BusinessHoursAgent: Only runs during business hours (9 AM - 5 PM)");
        log.info("AdaptiveAgent: Adjusts behavior based on system conditions");
        
        // Let it run for 30 seconds
        Thread.sleep(30_000);
        
        // Print statistics
        printStatistics(runtime);
        
        // Stop runtime
        log.info("Stopping runtime...");
        runtime.stop().join();
        
        log.info("=== Example completed ===");
    }
    
    private static void printStatistics(JenticRuntime runtime) {
        log.info("\n=== Execution Statistics ===");
        
        runtime.getAgent("resource-aware-agent").ifPresent(agent -> {
            if (agent instanceof ResourceAwareAgent raAgent) {
                raAgent.printStatistics();
            }
        });
        
        runtime.getAgent("business-hours-agent").ifPresent(agent -> {
            if (agent instanceof BusinessHoursAgent bhAgent) {
                bhAgent.printStatistics();
            }
        });
    }
}

/**
 * Agent that performs resource-intensive tasks only when system load is low.
 */
@JenticAgent("resource-aware-agent")
class ResourceAwareAgent extends BaseAgent {

    private ConditionalBehavior heavyTaskBehavior;

    public ResourceAwareAgent() {
        super("resource-aware-agent", "Resource Aware Agent");
    }

    @Override
    protected void onStart() {
        log.info("Starting Resource Aware Agent");

        // Condition: Execute only when CPU < 50% AND Memory < 70%
        Condition lowLoad = SystemCondition.cpuBelow(50.0)
                .and(SystemCondition.memoryBelow(70.0));

        // Heavy task behavior
        heavyTaskBehavior = ConditionalBehavior.cyclic(
                lowLoad,
                Duration.ofSeconds(3),
                this::performHeavyTask
        );
        heavyTaskBehavior.setAgent(this);

        addBehavior(heavyTaskBehavior);

        log.info("Heavy task will execute only when system load is low");
    }

    private void performHeavyTask() {
        log.info("🔨 Executing heavy computational task (system load is acceptable)");

        // Simulate heavy computation
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Send result
        Message result = Message.builder()
                .topic("heavy.task.completed")
                .senderId(getAgentId())
                .content("Task completed at low system load")
                .build();

        messageService.send(result);
    }

    public void printStatistics() {
        log.info("Resource Aware Agent Statistics:");
        log.info("  - Successful executions: {}", heavyTaskBehavior.getSuccessfulExecutions());
        log.info("  - Skipped executions: {}", heavyTaskBehavior.getSkippedExecutions());
        log.info("  - Satisfaction rate: {:.1f}%", heavyTaskBehavior.getSatisfactionRate() * 100);
    }
}

/**
 * Agent that sends notifications only during business hours.
 */
@JenticAgent("business-hours-agent")
class BusinessHoursAgent extends BaseAgent {

    private ConditionalBehavior notificationBehavior;

    public BusinessHoursAgent() {
        super("business-hours-agent", "Business Hours Agent");
    }

    @Override
    protected void onStart() {
        log.info("Starting Business Hours Agent");

        // Condition: Business hours (9 AM - 5 PM) on weekdays
        Condition businessTime = TimeCondition.businessHours()
                .and(TimeCondition.weekday());

        // Notification behavior
        notificationBehavior = ConditionalBehavior.cyclic(
                businessTime,
                Duration.ofSeconds(5),
                this::sendNotification
        );
        notificationBehavior.setAgent(this);

        addBehavior(notificationBehavior);

        log.info("Notifications will be sent only during business hours (9 AM - 5 PM, weekdays)");
    }

    private void sendNotification() {
        log.info("📧 Sending business notification (currently business hours)");

        Message notification = Message.builder()
                .topic("notification.sent")
                .senderId(getAgentId())
                .content("Business notification sent during working hours")
                .build();

        messageService.send(notification);
    }

    public void printStatistics() {
        log.info("Business Hours Agent Statistics:");
        log.info("  - Notifications sent: {}", notificationBehavior.getSuccessfulExecutions());
        log.info("  - Skipped (outside hours): {}", notificationBehavior.getSkippedExecutions());
        log.info("  - Satisfaction rate: {:.1f}%", notificationBehavior.getSatisfactionRate() * 100);
    }
}

/**
 * Agent that adapts its behavior based on system conditions.
 */
@JenticAgent("adaptive-agent")
class AdaptiveAgent extends BaseAgent {

    public AdaptiveAgent() {
        super("adaptive-agent", "Adaptive Agent");
    }

    @Override
    protected void onStart() {
        log.info("Starting Adaptive Agent");

        // High priority task - runs when system is healthy
        Condition systemHealthy = SystemCondition.systemHealthy();
        ConditionalBehavior highPriorityTask = ConditionalBehavior.cyclic(
                systemHealthy,
                Duration.ofSeconds(2),
                () -> log.info("⚡ High priority task (system healthy)")
        );
        highPriorityTask.setAgent(this);
        addBehavior(highPriorityTask);

        // Low priority task - runs only when system is VERY idle
        Condition veryIdle = SystemCondition.cpuBelow(30.0)
                .and(SystemCondition.memoryBelow(50.0));
        ConditionalBehavior lowPriorityTask = ConditionalBehavior.cyclic(
                veryIdle,
                Duration.ofSeconds(4),
                () -> log.info("🐌 Low priority background task (system very idle)")
        );
        lowPriorityTask.setAgent(this);
        addBehavior(lowPriorityTask);

        // Emergency task - runs when system is under load (inverted condition)
        Condition underLoad = SystemCondition.systemUnderLoad();
        ConditionalBehavior emergencyTask = ConditionalBehavior.cyclic(
                underLoad,
                Duration.ofSeconds(6),
                () -> log.warn("🚨 Emergency monitoring (system under load!)")
        );
        emergencyTask.setAgent(this);
        addBehavior(emergencyTask);

        log.info("Adaptive behaviors configured based on system load");
    }
}