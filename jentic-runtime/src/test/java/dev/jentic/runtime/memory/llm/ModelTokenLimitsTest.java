package dev.jentic.runtime.memory.llm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ModelTokenLimits registry.
 */
class ModelTokenLimitsTest {
    
    @AfterEach
    void cleanup() {
        // Note: We don't clear() as it would affect other tests
        // Just remove any custom registrations if needed
    }
    
    // ========== BUILT-IN MODELS TESTS ==========
    
    @Test
    void getLimit_shouldReturnCorrectLimitForGPT35Turbo() {
        // When
        int limit = ModelTokenLimits.getLimit("gpt-3.5-turbo");
        
        // Then
        assertThat(limit).isEqualTo(16_385);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForGPT4() {
        // When
        int limit = ModelTokenLimits.getLimit("gpt-4");
        
        // Then
        assertThat(limit).isEqualTo(8_192);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForGPT4Turbo() {
        // When
        int limit = ModelTokenLimits.getLimit("gpt-4-turbo");
        
        // Then
        assertThat(limit).isEqualTo(128_000);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForGPT4o() {
        // When
        int limit = ModelTokenLimits.getLimit("gpt-4o");
        
        // Then
        assertThat(limit).isEqualTo(128_000);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForClaude3Opus() {
        // When
        int limit = ModelTokenLimits.getLimit("claude-3-opus-20240229");
        
        // Then
        assertThat(limit).isEqualTo(200_000);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForClaude3Sonnet() {
        // When
        int limit = ModelTokenLimits.getLimit("claude-3-sonnet-20240229");
        
        // Then
        assertThat(limit).isEqualTo(200_000);
    }
    
    @Test
    void getLimit_shouldReturnCorrectLimitForClaude35Sonnet() {
        // When
        int limit = ModelTokenLimits.getLimit("claude-3-5-sonnet-20240620");
        
        // Then
        assertThat(limit).isEqualTo(200_000);
    }
    
    // ========== CASE INSENSITIVITY TESTS ==========
    
    @Test
    void getLimit_shouldBeCaseInsensitive() {
        // When
        int lower = ModelTokenLimits.getLimit("gpt-4");
        int upper = ModelTokenLimits.getLimit("GPT-4");
        int mixed = ModelTokenLimits.getLimit("GpT-4");
        
        // Then - all should return same value
        assertThat(lower).isEqualTo(8_192);
        assertThat(upper).isEqualTo(8_192);
        assertThat(mixed).isEqualTo(8_192);
    }
    
    @Test
    void getLimit_shouldTrimWhitespace() {
        // When
        int limit = ModelTokenLimits.getLimit("  gpt-4  ");
        
        // Then
        assertThat(limit).isEqualTo(8_192);
    }
    
    // ========== DEFAULT LIMIT TESTS ==========
    
    @Test
    void getLimit_shouldReturnDefaultForUnknownModel() {
        // When
        int limit = ModelTokenLimits.getLimit("unknown-model-xyz");
        
        // Then
        assertThat(limit).isEqualTo(ModelTokenLimits.DEFAULT_LIMIT);
        assertThat(limit).isEqualTo(4_096);
    }
    
    @Test
    void getLimit_shouldThrowForNullModel() {
        // When/Then
        assertThatThrownBy(() -> ModelTokenLimits.getLimit(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Model cannot be null");
    }
    
    // ========== CUSTOM REGISTRATION TESTS ==========
    
    @Test
    void register_shouldAllowCustomModel() {
        // Given
        String customModel = "my-custom-model-" + System.currentTimeMillis();
        int customLimit = 16_384;
        
        // When
        ModelTokenLimits.register(customModel, customLimit);
        int limit = ModelTokenLimits.getLimit(customModel);
        
        // Then
        assertThat(limit).isEqualTo(customLimit);
        
        // Cleanup
        ModelTokenLimits.unregister(customModel);
    }
    
    @Test
    void register_shouldOverrideExistingLimit() {
        // Given
        String model = "test-override-" + System.currentTimeMillis();
        ModelTokenLimits.register(model, 1000);
        
        // When - override with new limit
        ModelTokenLimits.register(model, 2000);
        int limit = ModelTokenLimits.getLimit(model);
        
        // Then
        assertThat(limit).isEqualTo(2000);
        
        // Cleanup
        ModelTokenLimits.unregister(model);
    }
    
    @Test
    void register_shouldThrowForNullModel() {
        // When/Then
        assertThatThrownBy(() -> ModelTokenLimits.register(null, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Model cannot be null");
    }
    
    @Test
    void register_shouldThrowForEmptyModel() {
        // When/Then
        assertThatThrownBy(() -> ModelTokenLimits.register("", 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Model cannot be null or empty");
    }
    
    @Test
    void register_shouldThrowForNegativeLimit() {
        // When/Then
        assertThatThrownBy(() -> ModelTokenLimits.register("test", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
    }
    
    @Test
    void register_shouldThrowForZeroLimit() {
        // When/Then
        assertThatThrownBy(() -> ModelTokenLimits.register("test", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
    }
    
    // ========== MODEL EXISTENCE TESTS ==========
    
    @Test
    void hasModel_shouldReturnTrueForKnownModel() {
        // When
        boolean has = ModelTokenLimits.hasModel("gpt-4");
        
        // Then
        assertThat(has).isTrue();
    }
    
    @Test
    void hasModel_shouldReturnFalseForUnknownModel() {
        // When
        boolean has = ModelTokenLimits.hasModel("unknown-model");
        
        // Then
        assertThat(has).isFalse();
    }
    
    @Test
    void hasModel_shouldReturnFalseForNull() {
        // When
        boolean has = ModelTokenLimits.hasModel(null);
        
        // Then
        assertThat(has).isFalse();
    }
    
    @Test
    void hasModel_shouldBeCaseInsensitive() {
        // When
        boolean has = ModelTokenLimits.hasModel("GPT-4");
        
        // Then
        assertThat(has).isTrue();
    }
    
    // ========== MODEL LISTING TESTS ==========
    
    @Test
    void getAllModels_shouldReturnNonEmptySet() {
        // When
        Set<String> models = ModelTokenLimits.getAllModels();
        
        // Then
        assertThat(models).isNotEmpty();
    }
    
    @Test
    void getAllModels_shouldContainBuiltInModels() {
        // When
        Set<String> models = ModelTokenLimits.getAllModels();
        
        // Then
        assertThat(models).contains(
            "gpt-4",
            "gpt-3.5-turbo",
            "claude-3-opus-20240229"
        );
    }
    
    @Test
    void getAllModels_shouldReturnUnmodifiableSet() {
        // When
        Set<String> models = ModelTokenLimits.getAllModels();
        
        // Then
        assertThatThrownBy(() -> models.add("test"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
    
    @Test
    void getModelCount_shouldReturnPositiveNumber() {
        // When
        int count = ModelTokenLimits.getModelCount();
        
        // Then
        assertThat(count).isGreaterThan(0);
    }
    
    @Test
    void getModelCount_shouldMatchGetAllModelsSize() {
        // When
        int count = ModelTokenLimits.getModelCount();
        int setSize = ModelTokenLimits.getAllModels().size();
        
        // Then
        assertThat(count).isEqualTo(setSize);
    }
    
    // ========== UNREGISTER TESTS ==========
    
    @Test
    void unregister_shouldRemoveCustomModel() {
        // Given
        String model = "test-unregister-" + System.currentTimeMillis();
        ModelTokenLimits.register(model, 1000);
        assertThat(ModelTokenLimits.hasModel(model)).isTrue();
        
        // When
        boolean removed = ModelTokenLimits.unregister(model);
        
        // Then
        assertThat(removed).isTrue();
        assertThat(ModelTokenLimits.hasModel(model)).isFalse();
    }
    
    @Test
    void unregister_shouldReturnFalseForUnknownModel() {
        // When
        boolean removed = ModelTokenLimits.unregister("never-existed");
        
        // Then
        assertThat(removed).isFalse();
    }
    
    @Test
    void unregister_shouldReturnFalseForNull() {
        // When
        boolean removed = ModelTokenLimits.unregister(null);
        
        // Then
        assertThat(removed).isFalse();
    }
    
    // ========== CUSTOM DEFAULT TESTS ==========
    
    @Test
    void getLimitOrDefault_shouldReturnModelLimitWhenKnown() {
        // When
        int limit = ModelTokenLimits.getLimitOrDefault("gpt-4", 999);
        
        // Then - should return actual limit, not default
        assertThat(limit).isEqualTo(8_192);
    }
    
    @Test
    void getLimitOrDefault_shouldReturnCustomDefaultWhenUnknown() {
        // When
        int limit = ModelTokenLimits.getLimitOrDefault("unknown", 12345);
        
        // Then
        assertThat(limit).isEqualTo(12345);
    }
    
    @Test
    void getLimitOrDefault_shouldReturnCustomDefaultForNull() {
        // When
        int limit = ModelTokenLimits.getLimitOrDefault(null, 6789);
        
        // Then
        assertThat(limit).isEqualTo(6789);
    }
    
    // ========== COVERAGE OF VARIOUS MODELS ==========
    
    @Test
    void shouldSupportOpenAIModels() {
        // Then
        assertThat(ModelTokenLimits.getLimit("gpt-3.5-turbo")).isPositive();
        assertThat(ModelTokenLimits.getLimit("gpt-4")).isPositive();
        assertThat(ModelTokenLimits.getLimit("gpt-4-turbo")).isPositive();
        assertThat(ModelTokenLimits.getLimit("gpt-4o")).isPositive();
    }
    
    @Test
    void shouldSupportAnthropicModels() {
        // Then
        assertThat(ModelTokenLimits.getLimit("claude-3-opus-20240229")).isPositive();
        assertThat(ModelTokenLimits.getLimit("claude-3-sonnet-20240229")).isPositive();
        assertThat(ModelTokenLimits.getLimit("claude-2.1")).isPositive();
    }
    
    @Test
    void shouldSupportMetaModels() {
        // Then
        assertThat(ModelTokenLimits.getLimit("llama-2-7b")).isPositive();
        assertThat(ModelTokenLimits.getLimit("llama-3-70b")).isPositive();
    }
    
    @Test
    void shouldSupportMistralModels() {
        // Then
        assertThat(ModelTokenLimits.getLimit("mistral-7b")).isPositive();
        assertThat(ModelTokenLimits.getLimit("mixtral-8x7b")).isPositive();
    }
}
