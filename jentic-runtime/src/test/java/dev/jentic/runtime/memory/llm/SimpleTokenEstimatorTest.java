package dev.jentic.runtime.memory.llm;

import dev.jentic.core.llm.LLMMessage;
import dev.jentic.core.memory.llm.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SimpleTokenEstimator.
 */
class SimpleTokenEstimatorTest {
    
    private SimpleTokenEstimator estimator;
    
    @BeforeEach
    void setUp() {
        estimator = new SimpleTokenEstimator();
    }
    
    // ========== BASIC ESTIMATION TESTS ==========
    
    @Test
    void estimateTokens_shouldReturnZeroForNullText() {
        // When
        int tokens = estimator.estimateTokens((String) null);
        
        // Then
        assertThat(tokens).isZero();
    }
    
    @Test
    void estimateTokens_shouldReturnZeroForEmptyText() {
        // When
        int tokens = estimator.estimateTokens("");
        
        // Then
        assertThat(tokens).isZero();
    }
    
    @Test
    void estimateTokens_shouldEstimateBasedOnCharacterCount() {
        // Given
        String text = "Hello, world!";  // 13 characters
        
        // When
        int tokens = estimator.estimateTokens(text);
        
        // Then - ~1 token per 4 chars = ~3 tokens
        assertThat(tokens).isBetween(2, 4);
    }
    
    @Test
    void estimateTokens_shouldReturnAtLeastOneForNonEmptyText() {
        // Given
        String shortText = "Hi";  // 2 characters
        
        // When
        int tokens = estimator.estimateTokens(shortText);
        
        // Then - should return at least 1
        assertThat(tokens).isGreaterThanOrEqualTo(1);
    }
    
    @Test
    void estimateTokens_shouldHandleLongerText() {
        // Given
        String longText = "This is a longer piece of text that contains multiple sentences. " +
                         "It should estimate more tokens based on the character count.";
        
        // When
        int tokens = estimator.estimateTokens(longText);
        
        // Then - approximately length/4
        int expected = longText.length() / 4;
        assertThat(tokens).isBetween(expected - 5, expected + 5);
    }
    
    // ========== MESSAGE ESTIMATION TESTS ==========
    
    @Test
    void estimateTokens_shouldReturnZeroForNullMessage() {
        // When
        int tokens = estimator.estimateTokens((LLMMessage) null);
        
        // Then
        assertThat(tokens).isZero();
    }
    
    @Test
    void estimateTokens_shouldIncludeMessageOverhead() {
        // Given
        LLMMessage message = LLMMessage.user("Test");
        
        // When
        int tokens = estimator.estimateTokens(message);
        
        // Then - content tokens + overhead (3)
        int contentTokens = estimator.estimateTokens("Test");
        assertThat(tokens).isGreaterThan(contentTokens);
    }
    
    @Test
    void estimateTokens_shouldHandleUserMessage() {
        // Given
        LLMMessage message = LLMMessage.user("What's the weather?");
        
        // When
        int tokens = estimator.estimateTokens(message);
        
        // Then - content (~5) + overhead (3) = ~8
        assertThat(tokens).isBetween(6, 10);
    }
    
    @Test
    void estimateTokens_shouldHandleAssistantMessage() {
        // Given
        LLMMessage message = LLMMessage.assistant("The weather is sunny today.");
        
        // When
        int tokens = estimator.estimateTokens(message);
        
        // Then - content (~7) + overhead (3) = ~10
        assertThat(tokens).isBetween(8, 12);
    }
    
    @Test
    void estimateTokens_shouldHandleSystemMessage() {
        // Given
        LLMMessage message = LLMMessage.system("You are a helpful assistant.");
        
        // When
        int tokens = estimator.estimateTokens(message);
        
        // Then
        assertThat(tokens).isGreaterThan(5);
    }
    
    @Test
    void estimateTokens_shouldHandleEmptyMessage() {
        // Given
        LLMMessage message = LLMMessage.user("");
        
        // When
        int tokens = estimator.estimateTokens(message);
        
        // Then - just overhead
        assertThat(tokens).isEqualTo(3);  // MESSAGE_OVERHEAD
    }
    
    // ========== MESSAGE LIST ESTIMATION TESTS ==========
    
    @Test
    void estimateTokens_shouldReturnZeroForNullList() {
        // When
        int tokens = estimator.estimateTokens((List<LLMMessage>) null);
        
        // Then
        assertThat(tokens).isZero();
    }
    
    @Test
    void estimateTokens_shouldReturnZeroForEmptyList() {
        // When
        int tokens = estimator.estimateTokens(List.of());
        
        // Then
        assertThat(tokens).isZero();
    }
    
    @Test
    void estimateTokens_shouldSumMultipleMessages() {
        // Given
        List<LLMMessage> messages = List.of(
            LLMMessage.user("Hi"),
            LLMMessage.assistant("Hello!"),
            LLMMessage.user("How are you?")
        );
        
        // When
        int totalTokens = estimator.estimateTokens(messages);
        
        // Then - sum of individual estimates
        int expected = messages.stream()
            .mapToInt(estimator::estimateTokens)
            .sum();
        assertThat(totalTokens).isEqualTo(expected);
    }
    
    @Test
    void estimateTokens_shouldHandleManyMessages() {
        // Given - 10 messages
        List<LLMMessage> messages = List.of(
            LLMMessage.system("You are helpful"),
            LLMMessage.user("Hello"),
            LLMMessage.assistant("Hi there!"),
            LLMMessage.user("What's 2+2?"),
            LLMMessage.assistant("4"),
            LLMMessage.user("Thanks"),
            LLMMessage.assistant("You're welcome"),
            LLMMessage.user("Bye"),
            LLMMessage.assistant("Goodbye!"),
            LLMMessage.user("See you")
        );
        
        // When
        int tokens = estimator.estimateTokens(messages);
        
        // Then - should be reasonable sum
        assertThat(tokens).isGreaterThan(30);  // At least 3 per message
    }
    
    // ========== CONTEXT WINDOW TESTS ==========
    
    @Test
    void getContextWindowSize_shouldReturnCorrectSizeForGPT4() {
        // When
        int size = estimator.getContextWindowSize("gpt-4");
        
        // Then
        assertThat(size).isEqualTo(8_192);
    }
    
    @Test
    void getContextWindowSize_shouldReturnCorrectSizeForGPT35() {
        // When
        int size = estimator.getContextWindowSize("gpt-3.5-turbo");
        
        // Then
        assertThat(size).isEqualTo(16_385);
    }
    
    @Test
    void getContextWindowSize_shouldReturnCorrectSizeForClaude() {
        // When
        int size = estimator.getContextWindowSize("claude-3-opus-20240229");
        
        // Then
        assertThat(size).isEqualTo(200_000);
    }
    
    @Test
    void getContextWindowSize_shouldReturnDefaultForUnknownModel() {
        // When
        int size = estimator.getContextWindowSize("unknown-model");
        
        // Then - should return default
        assertThat(size).isEqualTo(4_096);
    }
    
    @Test
    void getContextWindowSize_shouldThrowForNullModel() {
        // When/Then
        assertThatThrownBy(() -> estimator.getContextWindowSize(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    // ========== INTERFACE COMPLIANCE TESTS ==========
    
    @Test
    void shouldImplementTokenEstimatorInterface() {
        // Then
        assertThat(estimator).isInstanceOf(TokenEstimator.class);
    }
    
    @Test
    void getName_shouldReturnCorrectName() {
        // When
        String name = estimator.getName();
        
        // Then
        assertThat(name).isEqualTo("SimpleTokenEstimator");
    }
    
    @Test
    void getAccuracyLevel_shouldReturnLow() {
        // When
        String accuracy = estimator.getAccuracyLevel();
        
        // Then
        assertThat(accuracy).isEqualTo("low");
    }
    
    @Test
    void fitsInContextWindow_shouldReturnTrueWhenFits() {
        // When
        boolean fits = estimator.fitsInContextWindow("gpt-4", 1000);
        
        // Then - 1000 < 8192
        assertThat(fits).isTrue();
    }
    
    @Test
    void fitsInContextWindow_shouldReturnFalseWhenTooLarge() {
        // When
        boolean fits = estimator.fitsInContextWindow("gpt-4", 10000);
        
        // Then - 10000 > 8192
        assertThat(fits).isFalse();
    }
    
    @Test
    void getRemainingTokens_shouldCalculateCorrectly() {
        // When
        int remaining = estimator.getRemainingTokens("gpt-4", 1000);
        
        // Then
        assertThat(remaining).isEqualTo(8192 - 1000);
    }
    
    // ========== CODE ESTIMATION TESTS ==========
    
    @Test
    void estimateTokensForCode_shouldHandleCode() {
        // Given
        String code = "public class Hello { }";
        
        // When
        int tokens = estimator.estimateTokensForCode(code);
        
        // Then - code has more tokens per char (~1/3)
        int expectedMin = code.length() / 4;
        assertThat(tokens).isGreaterThanOrEqualTo(expectedMin);
    }
    
    @Test
    void estimateTokensForCode_shouldReturnZeroForNull() {
        // When
        int tokens = estimator.estimateTokensForCode(null);
        
        // Then
        assertThat(tokens).isZero();
    }
    
    // ========== LANGUAGE-SPECIFIC TESTS ==========
    
    @Test
    void estimateTokensWithLanguage_shouldHandleEnglish() {
        // Given
        String text = "Hello world";
        
        // When
        int tokens = estimator.estimateTokensWithLanguage(text, "en");
        
        // Then - ~1 token per 4 chars
        assertThat(tokens).isEqualTo(text.length() / 4);
    }
    
    @Test
    void estimateTokensWithLanguage_shouldHandleChinese() {
        // Given
        String text = "你好世界";  // 4 characters
        
        // When
        int tokens = estimator.estimateTokensWithLanguage(text, "zh");
        
        // Then - ~1 token per 2 chars for Chinese
        assertThat(tokens).isEqualTo(2);
    }
    
    @Test
    void estimateTokensWithLanguage_shouldHandleCode() {
        // Given
        String code = "int x = 10;";
        
        // When
        int tokens = estimator.estimateTokensWithLanguage(code, "code");
        
        // Then - ~1 token per 3 chars for code
        assertThat(tokens).isGreaterThanOrEqualTo(code.length() / 4);
    }
    
    @Test
    void estimateTokensWithLanguage_shouldDefaultToEnglish() {
        // Given
        String text = "Hello";
        
        // When
        int tokens = estimator.estimateTokensWithLanguage(text, "unknown");
        
        // Then - should use English default
        assertThat(tokens).isEqualTo(text.length() / 4);
    }
}
