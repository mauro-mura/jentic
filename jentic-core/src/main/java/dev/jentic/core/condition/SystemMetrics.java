package dev.jentic.core.condition;

import java.time.Instant;

/**
 * System metrics snapshot for condition evaluation
 */
public record SystemMetrics(
    double cpuUsage,
    double memoryUsage,
    long availableMemory,
    int activeThreads,
    Instant timestamp
) {
    
    public static SystemMetrics current() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsage = (double) usedMemory / maxMemory * 100;
        
        return new SystemMetrics(
            getCpuUsage(),
            memoryUsage,
            freeMemory,
            Thread.activeCount(),
            Instant.now()
        );
    }
    
    private static double getCpuUsage() {
        // Simplified CPU usage - in production use OperatingSystemMXBean
        com.sun.management.OperatingSystemMXBean osBean = 
            (com.sun.management.OperatingSystemMXBean) 
            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return osBean.getSystemCpuLoad() * 100;
    }
}