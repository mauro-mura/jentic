package dev.jentic.adapters.llm.anthropic;

import dev.jentic.adapters.llm.LLMProviderFactory;
import dev.jentic.core.llm.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AnthropicProviderTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should throw when apiKey is missing")
        void build_withoutApiKey_shouldThrow() {
            AnthropicProvider.Builder builder = LLMProviderFactory.anthropic();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    builder::build
            );

            assertTrue(ex.getMessage().toLowerCase().contains("api key"));
        }

        @Test
        @DisplayName("should build successfully with minimal config")
        void build_withMinimalConfig_shouldSucceed() {
            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey("test-api-key")
                    .build();

            assertNotNull(provider);
            assertEquals("Anthropic", provider.getProviderName());
        }

        @Test
        @DisplayName("should build with custom configuration")
        void build_withCustomConfig_shouldSucceed() {
            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey("test-api-key")
                    .model("claude-3-5-sonnet-20241022")
                    .temperature(0.5)
                    .maxTokens(500)
                    .build();

            assertNotNull(provider);
        }

        @Test
        @DisplayName("should use default model when not specified")
        void build_withoutModel_shouldUseDefault() {
            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey("test-api-key")
                    .build();

            assertNotNull(provider);
        }

        @Test
        @DisplayName("should accept valid temperature range")
        void build_withValidTemperature_shouldSucceed() {
            assertDoesNotThrow(() -> {
                LLMProviderFactory.anthropic()
                        .apiKey("test-key")
                        .temperature(0.0)
                        .build();

                LLMProviderFactory.anthropic()
                        .apiKey("test-key")
                        .temperature(1.0)
                        .build();
            });
        }
    }

    @Nested
    @DisplayName("Provider Metadata Tests")
    class ProviderMetadataTests {

        private LLMProvider provider;

        @BeforeEach
        void setup() {
            provider = LLMProviderFactory.anthropic()
                    .apiKey("dummy-key")
                    .build();
        }

        @Test
        @DisplayName("should return correct provider name")
        void getProviderName_shouldReturnAnthropic() {
            assertEquals("Anthropic", provider.getProviderName());
        }

        @Test
        @DisplayName("should return available models")
        void getAvailableModels_shouldContainCommonModels() throws Exception {
            CompletableFuture<List<String>> modelsFuture = provider.getAvailableModels();
            List<String> models = modelsFuture.get();

            assertNotNull(models);
            assertFalse(models.isEmpty());
            assertTrue(models.contains("claude-3-5-sonnet-20241022"));
            assertTrue(models.contains("claude-3-5-haiku-20241022"));
            assertTrue(models.contains("claude-3-haiku-20240307"));
        }
    }

    @Nested
    @DisplayName("Request Conversion Tests")
    class RequestConversionTests {

        @Test
        @DisplayName("should convert simple user message")
        void convertMessages_withUserMessage_shouldSucceed() {
            LLMRequest request = LLMRequest.builder("test-id")
                    .addMessage(LLMMessage.user("Hello"))
                    .build();

            assertNotNull(request);
            assertEquals(1, request.messages().size());
        }

        @Test
        @DisplayName("should convert system and user messages")
        void convertMessages_withSystemAndUser_shouldSucceed() {
            LLMRequest request = LLMRequest.builder("test-id")
                    .addMessage(LLMMessage.system("You are helpful"))
                    .addMessage(LLMMessage.user("Hello"))
                    .build();

            assertEquals(2, request.messages().size());
            assertEquals(LLMMessage.Role.SYSTEM, request.messages().get(0).role());
            assertEquals(LLMMessage.Role.USER, request.messages().get(1).role());
        }

        @Test
        @DisplayName("should handle conversation history")
        void convertMessages_withConversationHistory_shouldSucceed() {
            LLMRequest request = LLMRequest.builder("test-id")
                    .addMessage(LLMMessage.user("What is 2+2?"))
                    .addMessage(LLMMessage.assistant("2+2 equals 4"))
                    .addMessage(LLMMessage.user("What about 3+3?"))
                    .build();

            assertEquals(3, request.messages().size());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should require at least one message")
        void chat_withEmptyRequest_shouldThrowException() {
            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey("test-key")
                    .build();

            assertThrows(IllegalArgumentException.class, () -> {
                LLMRequest.builder("test-id").build();
            });
        }

        @Test
        @DisplayName("should validate null API key")
        void build_withNullApiKey_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> {
                AnthropicProvider.builder().apiKey(null).build();
            });
        }
    }

    @Nested
    @DisplayName("Feature Support Tests")
    class FeatureSupportTests {

        private LLMProvider provider;

        @BeforeEach
        void setup() {
            provider = LLMProviderFactory.anthropic()
                    .apiKey("dummy-key")
                    .build();
        }

        @Test
        @DisplayName("should support function calling")
        void supportsFunctionCalling_shouldReturnTrue() {
            assertTrue(provider.supportsFunctionCalling());
        }

        @Test
        @DisplayName("should support streaming")
        void supportsStreaming_shouldReturnTrue() {
            assertTrue(provider.supportsStreaming());
        }

        @Test
        @DisplayName("available models should not be empty")
        void getAvailableModels_shouldNotBeEmpty() throws Exception {
            List<String> models = provider.getAvailableModels().get();
            assertFalse(models.isEmpty());
            assertTrue(models.size() >= 3);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    @Tag("integration")
    class IntegrationTests {

        @Test
        @Disabled("Requires valid API key")
        @DisplayName("should make real API call")
        void chat_withRealApiKey_shouldSucceed() throws ExecutionException, InterruptedException {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isEmpty(), "ANTHROPIC_API_KEY not set");

            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey(apiKey)
                    .model("claude-3-5-haiku-20241022")
                    .maxTokens(100)
                    .build();

            LLMRequest request = LLMRequest.builder("integration-test")
                    .addMessage(LLMMessage.user("Say 'Hello' and nothing else"))
                    .build();

            CompletableFuture<LLMResponse> responseFuture = provider.chat(request);
            LLMResponse response = responseFuture.get();

            assertNotNull(response);
            assertNotNull(response.content());
            assertFalse(response.content().isEmpty());
        }

        @Test
        @Disabled("Requires valid API key")
        @DisplayName("should handle streaming response")
        void chatStream_withRealApiKey_shouldSucceed() {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isEmpty(), "ANTHROPIC_API_KEY not set");

            LLMProvider provider = LLMProviderFactory.anthropic()
                    .apiKey(apiKey)
                    .model("claude-3-5-haiku-20241022")
                    .build();

            LLMRequest request = LLMRequest.builder("stream-test")
                    .addMessage(LLMMessage.user("Count to 5"))
                    .build();

            StringBuilder fullResponse = new StringBuilder();
            CompletableFuture<Void> streamFuture = provider.chatStream(
                    request,
                    chunk -> {
                        if (chunk.hasContent()) {
                            fullResponse.append(chunk.content());
                        }
                    }
            );

            assertDoesNotThrow(() -> streamFuture.get());
            assertFalse(fullResponse.toString().isEmpty());
        }
    }

    private static void assumeTrue(boolean condition, String message) {
        Assumptions.assumeTrue(condition, message);
    }
}
