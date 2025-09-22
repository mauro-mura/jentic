package dev.jentic.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Configuration object for the Jentic framework.
 * Can be loaded from YAML, JSON, or properties files.
 */
public record JenticConfiguration(
    @JsonProperty("runtime") RuntimeConfig runtime,
    @JsonProperty("agents") AgentsConfig agents,
    @JsonProperty("messaging") MessagingConfig messaging,
    @JsonProperty("directory") DirectoryConfig directory,
    @JsonProperty("scheduler") SchedulerConfig scheduler
) {
    
    public record RuntimeConfig(
        @JsonProperty("name") String name,
        @JsonProperty("environment") String environment,
        @JsonProperty("properties") Map<String, String> properties
    ) {}
    
    public record AgentsConfig(
        @JsonProperty("autoDiscovery") boolean autoDiscovery,
        @JsonProperty("basePackage") String basePackage,
        @JsonProperty("scanPaths") String[] scanPaths,
        @JsonProperty("properties") Map<String, String> properties
    ) {}
    
    public record MessagingConfig(
        @JsonProperty("provider") String provider,
        @JsonProperty("properties") Map<String, String> properties
    ) {}
    
    public record DirectoryConfig(
        @JsonProperty("provider") String provider,
        @JsonProperty("properties") Map<String, String> properties
    ) {}
    
    public record SchedulerConfig(
        @JsonProperty("provider") String provider,
        @JsonProperty("threadPoolSize") int threadPoolSize,
        @JsonProperty("properties") Map<String, String> properties
    ) {}
}