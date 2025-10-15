package dev.jentic.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jentic.core.JenticConfiguration;

/**
 * Wrapper for YAML/JSON configuration files.
 * Represents the root structure with "jentic:" element.
 */
record JenticConfigurationWrapper(
    @JsonProperty("jentic") JenticConfiguration jentic
) {
    
    @JsonCreator
    public JenticConfigurationWrapper(
        @JsonProperty("jentic") JenticConfiguration jentic
    ) {
        this.jentic = jentic != null ? jentic : JenticConfiguration.defaults();
    }
    
    public JenticConfiguration getConfiguration() {
        return jentic;
    }
}