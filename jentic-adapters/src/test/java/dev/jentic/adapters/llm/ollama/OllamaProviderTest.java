package dev.jentic.adapters.llm.ollama;

import dev.jentic.core.llm.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaProviderTest {

    @Mock
    private OllamaChatModel mockChatModel;
    
    @Mock
    private OllamaStreamingChatModel mockStreamingModel;
    
    @Mock
    private ChatResponse mockChatResponse;
    
    @Mock
    private ChatResponseMetadata mockMetadata;
    
    @Mock
    private AiMessage mockAiMessage;
    
    private OllamaProvider provider;
    
    @BeforeEach
    void setUp() {
        // Create provider with default settings for testing
        provider = OllamaProvider.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .temperature(0.7)
                .timeout(Duration.ofMinutes(1))
                .build();
    }
    
    @Test
    void shouldCreateProviderWithDefaults() {
        OllamaProvider defaultProvider = OllamaProvider.builder().build();
        
        assertEquals("Ollama", defaultProvider.getProviderName());
        assertEquals("llama3.2", defaultProvider.getDefaultModel());
        assertFalse(defaultProvider.supportsFunctionCalling());
        assertTrue(defaultProvider.supportsStreaming());
    }
    
    @Test
    void shouldCreateProviderWithCustomSettings() {
        OllamaProvider customProvider = OllamaProvider.builder()
                .baseUrl("http://custom-server:11434")
                .modelName("mistral")
                .temperature(0.8)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
                .build();
        
        assertEquals("Ollama", customProvider.getProviderName());
        assertEquals("llama3.2", customProvider.getDefaultModel());
    }
    
    @Test
    void shouldValidateBuilderParameters() {
        // Test empty base URL
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .baseUrl("")
                    .build();
        });
        
        // Test blank base URL
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .baseUrl("   ")
                    .build();
        });
        
        // Test null base URL
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .baseUrl(null)
                    .build();
        });
        
        // Test empty model name
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .modelName("")
                    .build();
        });
        
        // Test blank model name
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .modelName("   ")
                    .build();
        });
        
        // Test null model name
        assertThrows(IllegalArgumentException.class, () -> {
            OllamaProvider.builder()
                    .modelName(null)
                    .build();
        });
    }
    
    @Test
    void shouldReturnAvailableModels() throws Exception {
        CompletableFuture<List<String>> future = provider.getAvailableModels();
        List<String> models = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(models);
        assertFalse(models.isEmpty());
        assertTrue(models.contains("llama3.2"));
        assertTrue(models.contains("llama3.1"));
        assertTrue(models.contains("llama2"));
        assertTrue(models.contains("mistral"));
        assertTrue(models.contains("mixtral"));
        assertTrue(models.contains("codellama"));
        assertTrue(models.contains("phi3"));
        assertTrue(models.contains("gemma2"));
    }
    
    @Test
    void shouldValidateRequest() {
        // Null request
        assertThrows(LLMException.class, () -> {
            provider.validateRequest(null);
        });
        
        // Empty messages
        assertThrows(IllegalArgumentException.class, () -> {
            provider.validateRequest(LLMRequest.builder("llama3.2").build());
        });
        
        // Empty model
        assertThrows(LLMException.class, () -> {
            provider.validateRequest(LLMRequest.builder("")
                    .addMessage(LLMMessage.user("test"))
                    .build());
        });
        
        // Valid request
        LLMRequest validRequest = LLMRequest.builder("llama3.2")
                .addMessage(LLMMessage.user("Hello"))
                .build();
        assertDoesNotThrow(() -> {
            provider.validateRequest(validRequest);
        });
    }
    
    @Test
    void shouldBuildRequestCorrectly() {
        LLMRequest request = LLMRequest.builder("llama3.2")
                .addMessage(LLMMessage.system("You are a helpful assistant."))
                .addMessage(LLMMessage.user("Hello!"))
                .temperature(0.8)
                .maxTokens(100)
                .build();
        
        assertEquals("llama3.2", request.model());
        assertEquals(2, request.messages().size());
        assertEquals(0.8, request.temperature());
        assertEquals(100, request.maxTokens());
    }
    
    @Test
    void shouldHandleBuilderChaining() {
        OllamaProvider customProvider = OllamaProvider.builder()
                .baseUrl("http://custom:11434")
                .modelName("mistral")
                .temperature(0.5)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
                .build();
        
        assertNotNull(customProvider);
        assertEquals("Ollama", customProvider.getProviderName());
    }
    
    @Test
    void shouldProvideCorrectProviderInfo() {
        assertEquals("Ollama", provider.getProviderName());
        assertEquals("llama3.2", provider.getDefaultModel());
        assertFalse(provider.supportsFunctionCalling());
        assertTrue(provider.supportsStreaming());
    }
    
    @Test
    void shouldValidateMessageTypes() {
        // Test system message
        LLMMessage systemMsg = LLMMessage.system("You are helpful");
        assertEquals(LLMMessage.Role.SYSTEM, systemMsg.role());
        assertTrue(systemMsg.isSystem());
        
        // Test user message  
        LLMMessage userMsg = LLMMessage.user("Hello");
        assertEquals(LLMMessage.Role.USER, userMsg.role());
        assertTrue(userMsg.isUser());
        
        // Test assistant message
        LLMMessage assistantMsg = LLMMessage.assistant("Hi there!");
        assertEquals(LLMMessage.Role.ASSISTANT, assistantMsg.role());
        assertTrue(assistantMsg.isAssistant());
    }
    
    @Test
    void shouldHandleTemperatureValidation() {
        // Valid temperature values
        assertDoesNotThrow(() -> {
            OllamaProvider.builder().temperature(0.0).build();
        });
        
        assertDoesNotThrow(() -> {
            OllamaProvider.builder().temperature(1.0).build();
        });
        
        assertDoesNotThrow(() -> {
            OllamaProvider.builder().temperature(0.7).build();
        });
    }
    
    @Test
    void shouldHandleTimeoutSettings() {
        OllamaProvider provider1 = OllamaProvider.builder()
                .timeout(Duration.ofSeconds(30))
                .build();
        assertNotNull(provider1);
        
        OllamaProvider provider2 = OllamaProvider.builder()
                .timeout(Duration.ofMinutes(5))
                .build();
        assertNotNull(provider2);
    }
    
    @Test
    void shouldCreateRequestWithMultipleMessages() {
        LLMRequest request = LLMRequest.builder("llama3.2")
                .addMessage(LLMMessage.system("You are a helpful coding assistant"))
                .addMessage(LLMMessage.user("How do I reverse a string in Java?"))
                .addMessage(LLMMessage.assistant("You can use StringBuilder.reverse()"))
                .addMessage(LLMMessage.user("Can you show me an example?"))
                .temperature(0.3)
                .maxTokens(200)
                .build();
        
        assertEquals(4, request.messages().size());
        assertEquals("llama3.2", request.model());
        assertEquals(0.3, request.temperature());
        assertEquals(200, request.maxTokens());
    }
    
    @Test 
    void shouldHandleStreamingChunkStructure() {
        // Test streaming chunk creation
        StreamingChunk chunk1 = StreamingChunk.of("stream-123", "llama3.2", "Hello", 0);
        assertNotNull(chunk1);
        assertEquals("stream-123", chunk1.id());
        assertEquals("llama3.2", chunk1.model());
        assertEquals("Hello", chunk1.content());
        assertFalse(chunk1.isLast());
        assertTrue(chunk1.hasContent());
        
        // Test final chunk
        StreamingChunk finalChunk = StreamingChunk.of("stream-123", "llama3.2", "", "stop", 5);
        assertNotNull(finalChunk);
        assertTrue(finalChunk.isLast());
        assertFalse(finalChunk.hasContent());
        assertEquals("stop", finalChunk.finishReason());
    }
    
    @Test
    void shouldValidateStreamingChunkParameters() {
        // Valid chunk
        assertDoesNotThrow(() -> {
            StreamingChunk.of("id", "model", "content", 0);
        });
        
        // Valid final chunk
        assertDoesNotThrow(() -> {
            StreamingChunk.of("id", "model", "", "stop", 1);
        });
        
        // Test negative index validation (if implemented)
        assertThrows(IllegalArgumentException.class, () -> {
            new StreamingChunk("id", "model", "content", null, -1, null);
        });
    }
    
    @Test
    void shouldHandleComplexRequestParameters() {
        LLMRequest complexRequest = LLMRequest.builder("mistral")
                .addMessage(LLMMessage.system("You are an expert"))
                .addMessage(LLMMessage.user("Explain quantum physics"))
                .temperature(0.8)
                .maxTokens(500)
                .topP(0.9)
                .presencePenalty(0.1)
                .frequencyPenalty(0.2)
                .build();
        
        assertEquals("mistral", complexRequest.model());
        assertEquals(0.8, complexRequest.temperature());
        assertEquals(500, complexRequest.maxTokens());
        assertEquals(0.9, complexRequest.topP());
        assertEquals(0.1, complexRequest.presencePenalty());
        assertEquals(0.2, complexRequest.frequencyPenalty());
        
        // Should pass validation
        assertDoesNotThrow(() -> {
            provider.validateRequest(complexRequest);
        });
    }
    
    @Test
    void shouldHandleProviderCapabilities() {
        // Test all capability methods
        assertEquals("Ollama", provider.getProviderName());
        assertEquals("llama3.2", provider.getDefaultModel());
        assertFalse(provider.supportsFunctionCalling());
        assertTrue(provider.supportsStreaming());
        
        // Validation should work
        LLMRequest request = LLMRequest.builder("llama3.2")
                .addMessage(LLMMessage.user("test"))
                .build();
        assertDoesNotThrow(() -> provider.validateRequest(request));
    }
    
    @Test
    void shouldCreateValidOllamaProviderInstances() {
        // Test different valid configurations
        OllamaProvider provider1 = OllamaProvider.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .build();
        assertNotNull(provider1);
        
        OllamaProvider provider2 = OllamaProvider.builder()
                .baseUrl("http://remote:11434")
                .modelName("mistral")
                .temperature(0.5)
                .build();
        assertNotNull(provider2);
        
        OllamaProvider provider3 = OllamaProvider.builder()
                .baseUrl("https://secure-ollama:443")
                .modelName("codellama")
                .timeout(Duration.ofMinutes(3))
                .logRequests(true)
                .build();
        assertNotNull(provider3);
    }
    
    @Test
    void shouldHandleLoggingConfiguration() {
        // Test logging enabled
        OllamaProvider logProvider = OllamaProvider.builder()
                .logRequests(true)
                .logResponses(true)
                .build();
        assertNotNull(logProvider);
        
        // Test logging disabled
        OllamaProvider noLogProvider = OllamaProvider.builder()
                .logRequests(false)
                .logResponses(false)
                .build();
        assertNotNull(noLogProvider);
    }
    
    /**
     * Integration test placeholder.
     * This would require an actual Ollama instance running.
     * In CI/CD, this could be marked with @Disabled or @IntegrationTest
     */
    @Test
    void integrationTestPlaceholder() {
        // This test would require actual Ollama instance
        // Marked as placeholder for manual testing
        
        /*
        OllamaProvider realProvider = OllamaProvider.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .build();
        
        LLMRequest request = LLMRequest.builder("llama3.2")
                .addMessage(LLMMessage.user("Say hello"))
                .maxTokens(10)
                .build();
        
        CompletableFuture<LLMResponse> future = realProvider.chat(request);
        LLMResponse response = future.get(30, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().length() > 0);
        */
        
        // For now, just verify provider creation
        assertDoesNotThrow(() -> {
            OllamaProvider.builder().build();
        });
    }
}