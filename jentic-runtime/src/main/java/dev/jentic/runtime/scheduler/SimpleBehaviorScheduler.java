package dev.jentic.runtime.scheduler;

import dev.jentic.core.Behavior;
import dev.jentic.core.BehaviorScheduler;
import dev.jentic.core.BehaviorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple implementation of BehaviorScheduler using ScheduledExecutorService.
 * Uses virtual threads for behavior execution.
 */
public class SimpleBehaviorScheduler implements BehaviorScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleBehaviorScheduler.class);
    
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledBehaviors = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public SimpleBehaviorScheduler() {
        this(4); // Default thread pool size
    }
    
    public SimpleBehaviorScheduler(int threadPoolSize) {
        this.scheduler = new ScheduledThreadPoolExecutor(threadPoolSize);
    }
    
    @Override
    public CompletableFuture<Void> schedule(Behavior behavior) {
        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                log.warn("Scheduler not running, cannot schedule behavior: {}", behavior.getBehaviorId());
                return;
            }
            
            switch (behavior.getType()) {
                case ONE_SHOT -> scheduleOneShot(behavior);
                case CYCLIC -> scheduleCyclic(behavior);
                case WAKER -> scheduleWaker(behavior);
                case EVENT_DRIVEN -> {
                    // Event-driven behaviors are not scheduled, they respond to events
                    log.debug("Event-driven behavior registered: {}", behavior.getBehaviorId());
                }
                case CUSTOM -> scheduleCustom(behavior);
            }
        });
    }
    
    @Override
    public boolean cancel(String behaviorId) {
        ScheduledFuture<?> future = scheduledBehaviors.remove(behaviorId);
        if (future != null) {
            boolean cancelled = future.cancel(false);
            log.debug("Cancelled behavior: {} (success: {})", behaviorId, cancelled);
            return cancelled;
        }
        return false;
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public CompletableFuture<Void> start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting behavior scheduler");
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping behavior scheduler");
            
            return CompletableFuture.runAsync(() -> {
                // Cancel all scheduled behaviors
                scheduledBehaviors.values().forEach(future -> future.cancel(false));
                scheduledBehaviors.clear();
                
                // Shutdown scheduler
                scheduler.shutdown();
            });
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private void scheduleOneShot(Behavior behavior) {
        ScheduledFuture<?> future = scheduler.schedule(() -> executeBehavior(behavior), 0, 
            java.util.concurrent.TimeUnit.MILLISECONDS);
        
        scheduledBehaviors.put(behavior.getBehaviorId(), future);
        log.debug("Scheduled one-shot behavior: {}", behavior.getBehaviorId());
    }
    
    private void scheduleCyclic(Behavior behavior) {
        Duration interval = behavior.getInterval();
        if (interval == null) {
            log.error("Cyclic behavior {} has no interval specified", behavior.getBehaviorId());
            return;
        }
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    if (behavior.isActive()) {
                        executeBehavior(behavior);
                    } else {
                        cancel(behavior.getBehaviorId());
                    }
                } catch (Throwable t) {
                    // Never let exceptions bubble to the scheduler
                    log.error("Scheduled runner failed for behavior: {}", behavior.getBehaviorId(), t);
                }
            },
            0,
            interval.toMillis(),
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
        
        scheduledBehaviors.put(behavior.getBehaviorId(), future);
        log.debug("Scheduled cyclic behavior: {} with interval: {}", 
                 behavior.getBehaviorId(), interval);
    }
    
    private void scheduleWaker(Behavior behavior) {
        // For MVP, treat waker behaviors like one-shot
        // Future versions can add more sophisticated wake conditions
        scheduleOneShot(behavior);
    }
    
    private void scheduleCustom(Behavior behavior) {
        // For custom behaviors, delegate to the behavior itself
        // This allows behaviors to define their own scheduling logic
        CompletableFuture.runAsync(() -> {
            while (behavior.isActive()) {
                executeBehavior(behavior);
                
                // Default interval for custom behaviors
                try {
                    Thread.sleep(behavior.getInterval() != null ? 
                        behavior.getInterval().toMillis() : 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        log.debug("Scheduled custom behavior: {}", behavior.getBehaviorId());
    }
    
    private void executeBehavior(Behavior behavior) {
        // Execute behavior in virtual thread
        Thread.startVirtualThread(() -> {
            try {
                log.trace("Executing behavior: {}", behavior.getBehaviorId());
                behavior.execute().whenComplete((res, ex) -> {
                    if (ex != null) {
                        // Error handling for custom behaviors
                        log.error("Error in behavior execution: {}", behavior.getBehaviorId(), ex);
                    }
                });
            } catch (Exception e) {
                log.error("Error executing behavior: {}", behavior.getBehaviorId(), e);
            }
        });
    }
}