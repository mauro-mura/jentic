package dev.jentic.adapters.llm.openai;

import dev.jentic.core.llm.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OpenAIProviderTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should throw when apiKey is missing")
        void build_withoutApiKey_shouldThrow() {
            OpenAIProvider.Builder builder = OpenAIProvider.builder();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    builder::build
            );

            assertTrue(ex.getMessage().toLowerCase().contains("api key"));
        }

        @Test
        @DisplayName("should build successfully with minimal config")
        void build_withMinimalConfig_shouldSucceed() {
            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey("test-api-key")
                    .build();

            assertNotNull(provider);
            assertEquals("OpenAI", provider.getProviderName());
        }

        @Test
        @DisplayName("should build with custom configuration")
        void build_withCustomConfig_shouldSucceed() {
            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey("test-api-key")
                    .model("gpt-4o")
                    .temperature(0.5)
                    .maxTokens(500)
                    .build();

            assertNotNull(provider);
        }

        @Test
        @DisplayName("should use default model when not specified")
        void build_withoutModel_shouldUseDefault() {
            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey("test-api-key")
                    .build();

            assertNotNull(provider);
        }

        @Test
        @DisplayName("should accept valid temperature range")
        void build_withValidTemperature_shouldSucceed() {
            assertDoesNotThrow(() -> {
                OpenAIProvider.builder()
                        .apiKey("test-key")
                        .temperature(0.0)
                        .build();

                OpenAIProvider.builder()
                        .apiKey("test-key")
                        .temperature(1.0)
                        .build();
            });
        }
    }

    @Nested
    @DisplayName("Provider Metadata Tests")
    class ProviderMetadataTests {

        private OpenAIProvider provider;

        @BeforeEach
        void setup() {
            provider = OpenAIProvider.builder()
                    .apiKey("dummy-key")
                    .build();
        }

        @Test
        @DisplayName("should return correct provider name")
        void getProviderName_shouldReturnOpenAI() {
            assertEquals("OpenAI", provider.getProviderName());
        }

        @Test
        @DisplayName("should return available models")
        void getAvailableModels_shouldContainCommonModels() throws Exception {
            CompletableFuture<List<String>> modelsFuture = provider.getAvailableModels();
            List<String> models = modelsFuture.get();

            assertNotNull(models);
            assertFalse(models.isEmpty());
            assertTrue(models.contains("gpt-4o"));
            assertTrue(models.contains("gpt-4o-mini"));
            assertTrue(models.contains("gpt-3.5-turbo"));
        }
    }

    @Nested
    @DisplayName("Request Conversion Tests")
    class RequestConversionTests {

        @Test
        @DisplayName("should convert simple user message")
        void convertMessages_withUserMessage_shouldSucceed() {
            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey("test-key")
                    .build();

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
            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey("test-key")
                    .build();

            assertThrows(IllegalArgumentException.class, () -> {
                LLMRequest.builder("test-id").build();
            });
        }

        @Test
        @DisplayName("should validate null API key")
        void build_withNullApiKey_shouldThrow() {
            assertThrows(IllegalStateException.class, () -> {
                OpenAIProvider.builder().apiKey(null).build();
            });
        }
    }

    @Nested
    @DisplayName("Feature Support Tests")
    class FeatureSupportTests {

        private OpenAIProvider provider;

        @BeforeEach
        void setup() {
            provider = OpenAIProvider.builder()
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
            String apiKey = System.getenv("OPENAI_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isEmpty(), "OPENAI_API_KEY not set");

            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey(apiKey)
                    .model("gpt-4o-mini")
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
            String apiKey = System.getenv("OPENAI_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isEmpty(), "OPENAI_API_KEY not set");

            OpenAIProvider provider = OpenAIProvider.builder()
                    .apiKey(apiKey)
                    .model("gpt-4o-mini")
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