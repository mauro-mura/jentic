package dev.jentic.core.condition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for passing additional information to condition evaluation.
 * Can contain system metrics, environment variables, or custom data.
 */
public class ConditionContext {
    
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    
    public void set(String key, Object value) {
        properties.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    public <T> T getOrDefault(String key, T defaultValue) {
        @SuppressWarnings("unchecked")
        T value = (T) properties.get(key);
        return value != null ? value : defaultValue;
    }
    
    public boolean has(String key) {
        return properties.containsKey(key);
    }
    
    public void clear() {
        properties.clear();
    }
    
    public Map<String, Object> asMap() {
        return Map.copyOf(properties);
    }
}