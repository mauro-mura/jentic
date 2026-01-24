package dev.jentic.runtime.behavior.chain;

import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import dev.jentic.core.llm.LLMMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChainBehavior.
 * 
 * @since 0.7.0
 */
class ChainBehaviorTest {
    
    private LLMProvider mockLLM;
    
    @BeforeEach
    void setUp() {
        mockLLM = mock(LLMProvider.class);
        when(mockLLM.getProviderName()).thenReturn("mock-provider");
    }
    
    @Test
    @DisplayName("Should execute simple 2-step chain successfully")
    void shouldExecuteSimpleChain() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Step 1 output")
                    .build()))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-2", "mock-provider")
                    .content("Step 2 output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "First prompt")
            .step("step2", "Second prompt: ${previous}")
            .build();
        
        // When
        chain.executeChain(); // Use synchronous execution
        
        // Then
        assertEquals("Step 1 output", chain.getExecutionHistory().get("step1"));
        assertEquals("Step 2 output", chain.getExecutionHistory().get("step2"));
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
    }
    
    @Test
    @DisplayName("Should substitute variables in prompts")
    void shouldSubstituteVariables() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Write about ${topic} in ${style} style")
            .variable("topic", "AI")
            .variable("style", "professional")
            .build();
        
        // When
        chain.executeChain();
        
        // Then - verify the prompt was substituted correctly
        verify(mockLLM).chat(argThat(req -> {
            List<LLMMessage> messages = req.messages();
            if (messages.isEmpty()) return false;
            
            // Find user message
            for (LLMMessage msg : messages) {
                if (msg.isUser()) {
                    String content = msg.content();
                    return content.contains("AI") && content.contains("professional");
                }
            }
            return false;
        }));
    }
    
    @Test
    @DisplayName("Should pass gate validation")
    void shouldPassGateValidation() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("This is a long enough output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt", Gate.minLength(10))
            .build();
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> chain.executeChain());
    }
    
    @Test
    @DisplayName("Should fail on gate validation with ABORT action")
    void shouldFailOnGateValidation() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Short")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt", Gate.minLength(100), GateAction.ABORT)
            .build();
        
        // When/Then
        assertThrows(GateValidationException.class, () -> chain.executeChain());
    }
    
    @Test
    @DisplayName("Should retry on gate failure with RETRY action")
    void shouldRetryOnGateFailure() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Short")
                    .build()))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-2", "mock-provider")
                    .content("This is long enough output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt", Gate.minLength(20), GateAction.RETRY)
            .maxRetryAttempts(2)
            .build();
        
        // When
        chain.executeChain();
        
        // Then - should have retried once
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
        assertEquals("This is long enough output", 
                    chain.getExecutionHistory().get("step1"));
    }
    
    @Test
    @DisplayName("Should continue despite gate failure with CONTINUE action")
    void shouldContinueDespiteGateFailure() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Short")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt", Gate.minLength(100), GateAction.CONTINUE)
            .build();
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> chain.executeChain());
        assertEquals("Short", chain.getExecutionHistory().get("step1"));
    }
    
    @Test
    @DisplayName("Should use previous step output in next step")
    void shouldUsePreviousStepOutput() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("First output")
                    .build()))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-2", "mock-provider")
                    .content("Second output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "First prompt")
            .step("step2", "Build on: ${previous}")
            .build();
        
        // When
        chain.executeChain();
        
        // Then - verify second call contains first output
        verify(mockLLM).chat(argThat(req -> {
            List<LLMMessage> messages = req.messages();
            for (LLMMessage msg : messages) {
                if (msg.isUser() && msg.content().contains("First output")) {
                    return true;
                }
            }
            return false;
        }));
    }
    
    @Test
    @DisplayName("Should reference specific step outputs")
    void shouldReferenceSpecificSteps() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Step A output")
                    .build()))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-2", "mock-provider")
                    .content("Step B output")
                    .build()))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-3", "mock-provider")
                    .content("Final output")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("stepA", "Prompt A")
            .step("stepB", "Prompt B")
            .step("stepC", "Combine ${step:stepA} and ${step:stepB}")
            .build();
        
        // When
        chain.executeChain();
        
        // Then - verify third call references both outputs
        verify(mockLLM).chat(argThat(req -> {
            List<LLMMessage> messages = req.messages();
            for (LLMMessage msg : messages) {
                if (msg.isUser()) {
                    String content = msg.content();
                    return content.contains("Step A output") && 
                           content.contains("Step B output");
                }
            }
            return false;
        }));
    }
    
    @Test
    @DisplayName("Should throw exception on empty steps")
    void shouldThrowOnEmptySteps() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            ChainBehavior.builder(mockLLM).build()
        );
    }
    
    @Test
    @DisplayName("Should throw exception on null LLM provider")
    void shouldThrowOnNullProvider() {
        // When/Then
        assertThrows(NullPointerException.class, () -> 
            ChainBehavior.builder(null)
        );
    }
    
    @Test
    @DisplayName("Should handle LLM exceptions and retry")
    void shouldHandleLLMExceptions() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("API error")))
            .thenReturn(CompletableFuture.completedFuture(
                LLMResponse.builder("resp-1", "mock-provider")
                    .content("Success after retry")
                    .build()));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt")
            .maxRetryAttempts(2)
            .build();
        
        // When
        chain.executeChain();
        
        // Then
        verify(mockLLM, times(2)).chat(any(LLMRequest.class));
        assertEquals("Success after retry", 
                    chain.getExecutionHistory().get("step1"));
    }
    
    @Test
    @DisplayName("Should throw after max retry attempts exceeded")
    void shouldThrowAfterMaxRetries() {
        // Given
        when(mockLLM.chat(any(LLMRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("API error")));
        
        ChainBehavior chain = ChainBehavior.builder(mockLLM)
            .step("step1", "Prompt")
            .maxRetryAttempts(1)
            .build();
        
        // When/Then
        assertThrows(ChainExecutionException.class, () -> chain.executeChain());
        verify(mockLLM, times(2)).chat(any(LLMRequest.class)); // Initial + 1 retry
    }
}
