package dev.jentic.examples;

import dev.jentic.core.JenticConfiguration;
import dev.jentic.core.config.ConfigurationLoader;
import dev.jentic.runtime.JenticRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickConfigTest {
    private static final Logger log = LoggerFactory.getLogger(QuickConfigTest.class);

    public static void main(String[] args) throws Exception {
        ConfigurationLoader loader = new ConfigurationLoader();
        
        // Test 1: Load from a file
        JenticConfiguration config = loader.loadFromClasspath("jentic-test.yml");
        log.info("Runtime: {}", config.runtime().name());
        log.info("Environment: {}", config.runtime().environment());
        log.info("Packages: {}", config.agents().getAllScanPackages());
        
        // Test 2: Validation
        loader.validate(config);
        log.info("Validation: PASSED");
        
        // Test 3: Use in JenticRuntime
        JenticRuntime runtime = JenticRuntime.builder()
            .fromClasspathConfig("jentic-test.yml")
            .build();

        log.info("Runtime created: {}", runtime.getConfiguration().runtime().name());
    }
}