package dev.jentic.core.config;

import dev.jentic.core.exceptions.JenticException;

public class ConfigurationException extends JenticException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }