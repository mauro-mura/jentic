package dev.jentic.adapters.llm.ollama;

import dev.jentic.core.llm.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OllamaProvider implements LLMProvider {

    private final OllamaChatModel chatModel;
    private final OllamaStreamingChatModel streamingModel;
    private final String modelName;
    private final String baseUrl;

    private OllamaProvider(Builder builder) {
        this.modelName = builder.modelName;
        this.baseUrl = builder.baseUrl;

        this.chatModel = OllamaChatModel.builder()
                .baseUrl(builder.baseUrl)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();

        this.streamingModel = OllamaStreamingChatModel.builder()
                .baseUrl(builder.baseUrl)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .timeout(builder.timeout)
                .build();
    }

    @Override
    public CompletableFuture<LLMResponse> chat(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ChatMessage> messages = convertMessages(request.messages());

                ChatRequest.Builder requestBuilder = ChatRequest.builder()
                        .messages(messages);

                if (request.temperature() != null) {
                    requestBuilder.temperature(request.temperature());
                }
                if (request.maxTokens() != null) {
                    requestBuilder.maxOutputTokens(request.maxTokens());
                }

                ChatResponse response = chatModel.chat(requestBuilder.build());

                return LLMResponse.builder(
                                UUID.randomUUID().toString(),
                                modelName != null ? modelName : request.model()
                        )
                        .content(response.aiMessage().text())
                        .role(LLMMessage.Role.ASSISTANT)
                        .finishReason(mapFinishReason(response.metadata() != null ? response.metadata().finishReason() : null))
                        .usage(mapUsage(response.metadata() != null ? response.metadata().tokenUsage() : null))
                        .build();

            } catch (Exception e) {
                throw new RuntimeException(new LLMException("Ollama chat request failed", e));
            }
        });
    }

    @Override
    public CompletableFuture<Void> chatStream(LLMRequest request, Consumer<StreamingChunk> chunkHandler) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        try {
            List<ChatMessage> messages = convertMessages(request.messages());
            final String streamId = UUID.randomUUID().toString();
            final int[] chunkIndex = {0};

            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(messages);

            if (request.temperature() != null) {
                requestBuilder.temperature(request.temperature());
            }
            if (request.maxTokens() != null) {
                requestBuilder.maxOutputTokens(request.maxTokens());
            }

            streamingModel.chat(requestBuilder.build(), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    StreamingChunk chunk = StreamingChunk.of(
                            streamId,
                            modelName != null ? modelName : request.model(),
                            partialResponse,
                            chunkIndex[0]++
                    );
                    chunkHandler.accept(chunk);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    String finishReason = null;
                    if (completeResponse != null && completeResponse.metadata() != null
                            && completeResponse.metadata().finishReason() != null) {
                        finishReason = mapFinishReason(completeResponse.metadata().finishReason());
                    }

                    StreamingChunk finalChunk = StreamingChunk.of(
                            streamId,
                            modelName != null ? modelName : request.model(),
                            "",
                            finishReason,
                            chunkIndex[0]
                    );
                    chunkHandler.accept(finalChunk);
                    result.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    result.completeExceptionally(new LLMException("Ollama streaming failed", error));
                }
            });

        } catch (Exception e) {
            result.completeExceptionally(new LLMException("Ollama streaming request failed", e));
        }

        return result;
    }

    @Override
    public CompletableFuture<List<String>> getAvailableModels() {
        return CompletableFuture.supplyAsync(() -> {
            // Ollama provides model listing through its API
            return Arrays.asList(
                    "llama3.2",
                    "llama3.1",
                    "llama2",
                    "mistral",
                    "mixtral",
                    "codellama",
                    "phi3",
                    "gemma2"
            );

        });
    }

    @Override
    public String getProviderName() {
        return "Ollama";
    }

    @Override
    public boolean supportsFunctionCalling() {
        return false; // Ollama doesn't support function calling in most models
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String getDefaultModel() {
        return "llama3.2";
    }

    private List<ChatMessage> convertMessages(List<LLMMessage> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
    }

    private ChatMessage convertMessage(LLMMessage message) {
        return switch (message.role()) {
            case SYSTEM -> SystemMessage.from(message.content());
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> AiMessage.from(message.content());
            default -> throw new IllegalArgumentException("Unsupported message role: " + message.role());
        };
    }

    private String mapFinishReason(FinishReason finishReason) {
        if (finishReason == null) return null;
        return switch (finishReason) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case TOOL_EXECUTION -> "tool_calls";
            case CONTENT_FILTER -> "content_filter";
            case OTHER -> "other";
        };
    }

    private LLMResponse.Usage mapUsage(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return null;
        }
        return new LLMResponse.Usage(
                tokenUsage.inputTokenCount(),
                tokenUsage.outputTokenCount(),
                tokenUsage.totalTokenCount()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = "http://localhost:11434";
        private String modelName = "llama3.2";
        private Double temperature;
        private Duration timeout = Duration.ofMinutes(5);
        private boolean logRequests = false;
        private boolean logResponses = false;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaProvider build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("Base URL is required");
            }
            if (modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("Model name is required");
            }
            return new OllamaProvider(this);
        }
    }
}