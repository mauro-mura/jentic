package dev.jentic.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

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

    @JsonCreator
    public JenticConfiguration(
            @JsonProperty("runtime") RuntimeConfig runtime,
            @JsonProperty("agents") AgentsConfig agents,
            @JsonProperty("messaging") MessagingConfig messaging,
            @JsonProperty("directory") DirectoryConfig directory,
            @JsonProperty("scheduler") SchedulerConfig scheduler
    ) {
        this.runtime = runtime != null ? runtime : RuntimeConfig.defaults();
        this.agents = agents != null ? agents : AgentsConfig.defaults();
        this.messaging = messaging != null ? messaging : MessagingConfig.defaults();  // Optional in Phase 2
        this.directory = directory != null ? directory : DirectoryConfig.defaults();   // Optional in Phase 2
        this.scheduler = scheduler != null ? scheduler : SchedulerConfig.defaults();   // Optional in Phase 2
    }

    public record RuntimeConfig(
            @JsonProperty("name") String name,
            @JsonProperty("environment") String environment,
            @JsonProperty("properties") Map<String, String> properties
    ) {
        @JsonCreator
        public RuntimeConfig(
                @JsonProperty("name") String name,
                @JsonProperty("environment") String environment,
                @JsonProperty("properties") Map<String, String> properties
        ) {
            this.name = name != null ? name : "jentic-runtime";
            this.environment = environment != null ? environment : "development";
            this.properties = properties != null ?
                    Map.copyOf(properties) : Collections.emptyMap();
        }

        public static RuntimeConfig defaults() {
            return new RuntimeConfig("jentic-runtime", "development", null);
        }
    }

    /**
     * Create default configuration for Phase 2 (minimal)
     */
    public static JenticConfiguration defaults() {
        return new JenticConfiguration(
                RuntimeConfig.defaults(),
                AgentsConfig.defaults(),
                null,  // messaging - Phase 3
                null,  // directory - Phase 3
                null   // scheduler - Phase 3
        );
    }

    public record AgentsConfig(
            @JsonProperty("autoDiscovery") boolean autoDiscovery,
            @JsonProperty("basePackage") String basePackage,
            @JsonProperty("scanPaths") String[] scanPaths,
            @JsonProperty("scanPackages") List<String> scanPackages,
            @JsonProperty("properties") Map<String, String> properties
    ) {
        @JsonCreator
        public AgentsConfig(
                @JsonProperty("autoDiscovery") boolean autoDiscovery,
                @JsonProperty("basePackage") String basePackage,
                @JsonProperty("scanPaths") String[] scanPaths,
                @JsonProperty("scanPackages") List<String> scanPackages,
                @JsonProperty("properties") Map<String, String> properties
        ) {
            this.autoDiscovery = autoDiscovery;
            this.basePackage = basePackage;
            this.scanPaths = scanPaths;

            // Merge scanPaths and scanPackages
            List<String> allPackages = new ArrayList<>();
            if (scanPackages != null) {
                // Validate no null elements
                for (int i = 0; i < scanPackages.size(); i++) {
                    if (scanPackages.get(i) == null) {
                        throw new IllegalArgumentException(
                                "scanPackages[" + i + "] cannot be null"
                        );
                    }
                }
                allPackages.addAll(scanPackages);
            }
            if (scanPaths != null) {
                allPackages.addAll(Arrays.asList(scanPaths));
            }
            if (basePackage != null && !basePackage.trim().isEmpty()) {
                allPackages.add(basePackage);
            }

            this.scanPackages = allPackages.isEmpty() ?
                    Collections.emptyList() : List.copyOf(allPackages);

            this.properties = properties != null ?
                    Map.copyOf(properties) : Collections.emptyMap();
        }

        public static AgentsConfig defaults() {
            return new AgentsConfig(true, null, null, null, null);
        }

        /**
         * Get all scan packages (merged from basePackage, scanPaths, scanPackages)
         */
        public List<String> getAllScanPackages() {
            return scanPackages;
        }
    }

    public record MessagingConfig(
            @JsonProperty("provider") String provider,
            @JsonProperty("properties") Map<String, String> properties
    ) {
        @JsonCreator
        public MessagingConfig(
                @JsonProperty("provider") String provider,
                @JsonProperty("properties") Map<String, String> properties
        ) {
            this.provider = provider;
            this.properties = properties != null ?
                    Map.copyOf(properties) : Collections.emptyMap();
        }

        public static MessagingConfig defaults() {
            return new MessagingConfig("inmemory", null);
        }
    }

    public record DirectoryConfig(
            @JsonProperty("provider") String provider,
            @JsonProperty("properties") Map<String, String> properties
    ) {
        @JsonCreator
        public DirectoryConfig(
                @JsonProperty("provider") String provider,
                @JsonProperty("properties") Map<String, String> properties
        ) {
            this.provider = provider;
            this.properties = properties != null ?
                    Map.copyOf(properties) : Collections.emptyMap();
        }

        public static DirectoryConfig defaults() {
            return new DirectoryConfig("local", null);
        }
    }

    public record SchedulerConfig(
            @JsonProperty("provider") String provider,
            @JsonProperty("threadPoolSize") int threadPoolSize,
            @JsonProperty("properties") Map<String, String> properties
    ) {
        @JsonCreator
        public SchedulerConfig(
                @JsonProperty("provider") String provider,
                @JsonProperty("threadPoolSize") int threadPoolSize,
                @JsonProperty("properties") Map<String, String> properties
        ) {
            this.provider = provider;
            this.threadPoolSize = threadPoolSize;
            this.properties = properties != null ?
                    Map.copyOf(properties) : Collections.emptyMap();
        }

        public static SchedulerConfig defaults() {
            return new SchedulerConfig("simple", 10, null);
        }
    }
}