package dev.jentic.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.jentic.core.JenticConfiguration;
import dev.jentic.core.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads Jentic configuration from YAML, JSON, or classpath resources.
 * Supports environment variable substitution.
 */
public class ConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationLoader.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ConfigurationLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper();
    }

    public JenticConfiguration loadFromFile(String path) throws ConfigurationException {
        if (path == null || path.trim().isEmpty()) {
            throw new ConfigurationException("Configuration path cannot be null or empty");
        }

        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            throw new ConfigurationException("Configuration file not found: " + path);
        }

        if (!Files.isReadable(filePath)) {
            throw new ConfigurationException("Configuration file not readable: " + path);
        }

        try {
            String content = Files.readString(filePath);
            content = substituteEnvironmentVariables(content);

            ObjectMapper mapper = selectMapper(path);
            JenticConfigurationWrapper wrapper = mapper.readValue(
                    content,
                    JenticConfigurationWrapper.class
            );

            log.info("Loaded configuration from: {}", path);
            return wrapper.getConfiguration();

        } catch (IOException e) {
            log.error("Failed to load configuration from: {}", path, e);
            throw new ConfigurationException("Failed to load configuration from: " + path, e);
        }
    }

    public JenticConfiguration loadFromClasspath(String resourcePath) throws ConfigurationException {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new ConfigurationException("Resource path cannot be null or empty");
        }

        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new ConfigurationException("Configuration resource not found: " + resourcePath);
        }

        try (inputStream) {
            String content = new String(inputStream.readAllBytes());
            content = substituteEnvironmentVariables(content);

            ObjectMapper mapper = selectMapper(resourcePath);
            JenticConfigurationWrapper wrapper = mapper.readValue(
                    content,
                    JenticConfigurationWrapper.class
            );

            log.info("Loaded configuration from classpath: {}", resourcePath);
            return wrapper.getConfiguration();

        } catch (IOException e) {
            log.error("Failed to load configuration from classpath: {}", resourcePath, e);
            throw new ConfigurationException("Failed to load configuration from classpath: " + resourcePath, e);
        }
    }

    public JenticConfiguration loadFromStream(InputStream inputStream, String format)
            throws ConfigurationException {

        if (inputStream == null) {
            throw new ConfigurationException("Input stream cannot be null");
        }

        try {
            String content = new String(inputStream.readAllBytes());
            content = substituteEnvironmentVariables(content);

            ObjectMapper mapper = "json".equalsIgnoreCase(format) ? jsonMapper : yamlMapper;
            JenticConfigurationWrapper wrapper = mapper.readValue(
                    content,
                    JenticConfigurationWrapper.class
            );

            log.info("Loaded configuration from stream (format: {})", format);
            return wrapper.getConfiguration();

        } catch (IOException e) {
            log.error("Failed to load configuration from stream", e);
            throw new ConfigurationException("Failed to load configuration from stream", e);
        }
    }

    public JenticConfiguration loadDefault() {
        String[] defaultLocations = {
                "jentic.yml",
                "jentic.yaml",
                "config/jentic.yml",
                "config/jentic.yaml"
        };

        for (String location : defaultLocations) {
            Path path = Paths.get(location);
            if (Files.exists(path)) {
                try {
                    log.info("Found default configuration at: {}", location);
                    return loadFromFile(location);
                } catch (ConfigurationException e) {
                    log.warn("Failed to load configuration from {}: {}", location, e.getMessage());
                }
            }
        }

        for (String location : defaultLocations) {
            try {
                return loadFromClasspath(location);
            } catch (ConfigurationException e) {
                // Continue to next location
            }
        }

        log.info("No configuration file found, using defaults");
        return JenticConfiguration.defaults();
    }

    private String substituteEnvironmentVariables(String content) {
        if (content == null) {
            return null;
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varExpression = matcher.group(1);
            String replacement = resolveEnvironmentVariable(varExpression);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveEnvironmentVariable(String expression) {
        String[] parts = expression.split(":", 2);
        String varName = parts[0].trim();
        String defaultValue = parts.length > 1 ? parts[1].trim() : "";

        String value = System.getenv(varName);

        if (value == null) {
            value = System.getProperty(varName);
        }

        if (value == null) {
            if (!defaultValue.isEmpty()) {
                log.debug("Environment variable '{}' not found, using default: {}",
                        varName, defaultValue);
                return defaultValue;
            } else {
                log.warn("Environment variable '{}' not found and no default provided", varName);
                return "${" + expression + "}";
            }
        }

        return value;
    }

    private ObjectMapper selectMapper(String path) {
        String lowercasePath = path.toLowerCase();

        if (lowercasePath.endsWith(".json")) {
            return jsonMapper;
        } else if (lowercasePath.endsWith(".yml") || lowercasePath.endsWith(".yaml")) {
            return yamlMapper;
        } else {
            log.debug("Unknown file extension, defaulting to YAML parser");
            return yamlMapper;
        }
    }

    public void validate(JenticConfiguration config) throws ConfigurationException {
        if (config == null) {
            throw new ConfigurationException("Configuration cannot be null");
        }

        JenticConfiguration.RuntimeConfig runtime = config.runtime();
        if (runtime.name() == null || runtime.name().trim().isEmpty()) {
            throw new ConfigurationException("runtime.name cannot be null or empty");
        }

        String env = runtime.environment();
        if (!env.matches("development|staging|production|test")) {
            log.warn("Unknown environment '{}', expected: development, staging, production, or test", env);
        }

        JenticConfiguration.AgentsConfig agents = config.agents();
        if (agents.autoDiscovery() && agents.getAllScanPackages().isEmpty()) {
            log.warn("autoDiscovery is enabled but no scanPackages configured");
        }

        // Validate package names
        for (String packageName : agents.getAllScanPackages()) {
            if (packageName == null || packageName.trim().isEmpty()) {
                throw new ConfigurationException("scanPackages cannot contain null or empty entries");
            }

            if (!packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$")) {
                throw new ConfigurationException("Invalid package name: " + packageName);
            }
        }

        log.debug("Configuration validation passed");
    }

}