package dev.jentic.runtime.memory.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of model context window sizes.
 * 
 * <p>This class maintains a registry of known LLM models and their
 * maximum context window sizes (in tokens). Context window is the
 * total tokens (prompt + completion) a model can handle.
 * 
 * <p><b>Built-in Models:</b>
 * <ul>
 *   <li><b>OpenAI GPT-3.5:</b> 16,385 tokens</li>
 *   <li><b>OpenAI GPT-4:</b> 8,192 tokens</li>
 *   <li><b>OpenAI GPT-4 Turbo:</b> 128,000 tokens</li>
 *   <li><b>OpenAI GPT-4o:</b> 128,000 tokens</li>
 *   <li><b>Anthropic Claude 3 Opus:</b> 200,000 tokens</li>
 *   <li><b>Anthropic Claude 3 Sonnet:</b> 200,000 tokens</li>
 *   <li><b>Anthropic Claude 3 Haiku:</b> 200,000 tokens</li>
 * </ul>
 * 
 * <p><b>Custom Models:</b> You can register custom models:
 * <pre>{@code
 * ModelTokenLimits.register("my-custom-model", 32768);
 * }</pre>
 * 
 * <p><b>Thread Safety:</b> This class uses a ConcurrentHashMap and is thread-safe.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Get limit for known model
 * int limit = ModelTokenLimits.getLimit("gpt-4");  // 8192
 * 
 * // Check if model is known
 * boolean known = ModelTokenLimits.hasModel("gpt-4");  // true
 * 
 * // Register custom model
 * ModelTokenLimits.register("llama-3-70b", 8192);
 * 
 * // Get all models
 * Set<String> models = ModelTokenLimits.getAllModels();
 * }</pre>
 * 
 * @since 0.6.0
 */
public final class ModelTokenLimits {
    
    /**
     * Default limit for unknown models.
     */
    public static final int DEFAULT_LIMIT = 4096;
    
    /**
     * Registry of model limits.
     */
    private static final Map<String, Integer> LIMITS = new ConcurrentHashMap<>();
    
    // Initialize with known models
    static {
        // OpenAI GPT-3.5 family
        register("gpt-3.5-turbo", 16_385);
        register("gpt-3.5-turbo-16k", 16_385);
        register("gpt-3.5-turbo-1106", 16_385);
        
        // OpenAI GPT-4 family (original)
        register("gpt-4", 8_192);
        register("gpt-4-0314", 8_192);
        register("gpt-4-0613", 8_192);
        register("gpt-4-32k", 32_768);
        
        // OpenAI GPT-4 Turbo
        register("gpt-4-turbo", 128_000);
        register("gpt-4-turbo-preview", 128_000);
        register("gpt-4-1106-preview", 128_000);
        register("gpt-4-0125-preview", 128_000);
        
        // OpenAI GPT-4o family
        register("gpt-4o", 128_000);
        register("gpt-4o-mini", 128_000);
        
        // Anthropic Claude 3 family
        register("claude-3-opus-20240229", 200_000);
        register("claude-3-sonnet-20240229", 200_000);
        register("claude-3-haiku-20240307", 200_000);
        register("claude-3-5-sonnet-20240620", 200_000);
        
        // Anthropic Claude 2 family
        register("claude-2.1", 200_000);
        register("claude-2.0", 100_000);
        register("claude-instant-1.2", 100_000);
        
        // Meta Llama family
        register("llama-2-7b", 4_096);
        register("llama-2-13b", 4_096);
        register("llama-2-70b", 4_096);
        register("llama-3-8b", 8_192);
        register("llama-3-70b", 8_192);
        
        // Mistral family
        register("mistral-7b", 8_192);
        register("mistral-8x7b", 32_768);
        register("mixtral-8x7b", 32_768);
        
        // Google Gemini family
        register("gemini-pro", 32_768);
        register("gemini-ultra", 32_768);
        
        // Cohere family
        register("command", 4_096);
        register("command-light", 4_096);
        register("command-nightly", 8_192);
    }
    
    /**
     * Private constructor - this is a utility class.
     */
    private ModelTokenLimits() {
        throw new AssertionError("Cannot instantiate ModelTokenLimits");
    }
    
    /**
     * Get the context window size for a model.
     * 
     * <p>If the model is not known, returns {@link #DEFAULT_LIMIT}.
     * 
     * <p><b>Example:</b>
     * <pre>{@code
     * int limit = ModelTokenLimits.getLimit("gpt-4");  // 8192
     * int unknown = ModelTokenLimits.getLimit("unknown");  // 4096 (default)
     * }</pre>
     * 
     * @param model the model identifier
     * @return context window size in tokens, or {@link #DEFAULT_LIMIT} if unknown
     * @throws IllegalArgumentException if model is null
     */
    public static int getLimit(String model) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        
        // Normalize model name (lowercase, trim)
        String normalizedModel = model.toLowerCase().trim();
        
        // Check exact match first
        Integer limit = LIMITS.get(normalizedModel);
        if (limit != null) {
            return limit;
        }
        
        // Try partial match (e.g., "gpt-4" matches "gpt-4-0613")
        for (Map.Entry<String, Integer> entry : LIMITS.entrySet()) {
            if (normalizedModel.startsWith(entry.getKey()) || 
                entry.getKey().startsWith(normalizedModel)) {
                return entry.getValue();
            }
        }
        
        // Unknown model - return default
        return DEFAULT_LIMIT;
    }
    
    /**
     * Register a custom model and its context window size.
     * 
     * <p>This can be used to add support for new models or override
     * existing limits.
     * 
     * <p><b>Example:</b>
     * <pre>{@code
     * ModelTokenLimits.register("my-custom-model", 16384);
     * }</pre>
     * 
     * @param model the model identifier
     * @param limit the context window size in tokens
     * @throws IllegalArgumentException if model is null or limit <= 0
     */
    public static void register(String model, int limit) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        
        String normalizedModel = model.toLowerCase().trim();
        LIMITS.put(normalizedModel, limit);
    }
    
    /**
     * Check if a model is registered.
     * 
     * @param model the model identifier
     * @return true if the model is known
     */
    public static boolean hasModel(String model) {
        if (model == null) {
            return false;
        }
        
        String normalizedModel = model.toLowerCase().trim();
        return LIMITS.containsKey(normalizedModel);
    }
    
    /**
     * Get all registered model identifiers.
     * 
     * @return set of all known model identifiers
     */
    public static java.util.Set<String> getAllModels() {
        return java.util.Collections.unmodifiableSet(LIMITS.keySet());
    }
    
    /**
     * Get the number of registered models.
     * 
     * @return count of registered models
     */
    public static int getModelCount() {
        return LIMITS.size();
    }
    
    /**
     * Remove a model from the registry.
     * 
     * <p><b>Note:</b> Built-in models can be removed, but this is
     * generally not recommended.
     * 
     * @param model the model identifier
     * @return true if the model was removed, false if it wasn't registered
     */
    public static boolean unregister(String model) {
        if (model == null) {
            return false;
        }
        
        String normalizedModel = model.toLowerCase().trim();
        return LIMITS.remove(normalizedModel) != null;
    }
    
    /**
     * Clear all registered models.
     * 
     * <p><b>Warning:</b> This removes all models including built-in ones.
     * Use with caution.
     */
    public static void clear() {
        LIMITS.clear();
    }
    
    /**
     * Get limit with fallback value.
     * 
     * <p>Similar to {@link #getLimit(String)}, but allows specifying
     * a custom default instead of using {@link #DEFAULT_LIMIT}.
     * 
     * @param model the model identifier
     * @param defaultLimit the default limit if model is unknown
     * @return context window size
     */
    public static int getLimitOrDefault(String model, int defaultLimit) {
        if (model == null) {
            return defaultLimit;
        }
        
        String normalizedModel = model.toLowerCase().trim();
        return LIMITS.getOrDefault(normalizedModel, defaultLimit);
    }
}
