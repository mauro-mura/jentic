package dev.jentic.runtime.condition;

import dev.jentic.core.Agent;
import dev.jentic.core.condition.Condition;
import dev.jentic.core.condition.SystemMetrics;

/**
 * Pre-built conditions for common system metrics checks
 */
public class SystemCondition {
    
    /**
     * Check if CPU usage is below threshold
     */
    public static Condition cpuBelow(double threshold) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.cpuUsage() < threshold;
        };
    }
    
    /**
     * Check if CPU usage is above threshold
     */
    public static Condition cpuAbove(double threshold) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.cpuUsage() > threshold;
        };
    }
    
    /**
     * Check if memory usage is below threshold (percentage)
     */
    public static Condition memoryBelow(double threshold) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.memoryUsage() < threshold;
        };
    }
    
    /**
     * Check if memory usage is above threshold (percentage)
     */
    public static Condition memoryAbove(double threshold) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.memoryUsage() > threshold;
        };
    }
    
    /**
     * Check if available memory is above threshold (bytes)
     */
    public static Condition availableMemoryAbove(long bytes) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.availableMemory() > bytes;
        };
    }
    
    /**
     * Check if active threads are below threshold
     */
    public static Condition threadsBelow(int threshold) {
        return agent -> {
            SystemMetrics metrics = SystemMetrics.current();
            return metrics.activeThreads() < threshold;
        };
    }
    
    /**
     * System is healthy (CPU < 80%, Memory < 80%)
     */
    public static Condition systemHealthy() {
        return cpuBelow(80.0).and(memoryBelow(80.0));
    }
    
    /**
     * System is under load (CPU > 70% OR Memory > 70%)
     */
    public static Condition systemUnderLoad() {
        return cpuAbove(70.0).or(memoryAbove(70.0));
    }
}