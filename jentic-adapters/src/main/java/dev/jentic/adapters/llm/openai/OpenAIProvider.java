package dev.jentic.adapters.llm.openai;

import dev.jentic.adapters.llm.ToolConversionUtils;
import dev.jentic.core.llm.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OpenAIProvider implements LLMProvider {

    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingModel;
    private final String modelName;

    private OpenAIProvider(Builder builder) {
        this.modelName = builder.modelName;
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(builder.apiKey)
                .baseUrl(builder.baseUrl)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .maxTokens(builder.maxTokens)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
        this.streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(builder.apiKey)
                .baseUrl(builder.baseUrl)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .maxTokens(builder.maxTokens)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
    }

    @Override
    public CompletableFuture<LLMResponse> chat(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            ChatRequest.Builder chatRequestBuilder = ChatRequest.builder()
                    .messages(convertMessages(request));

            // ✅ ADD FUNCTION CALLING SUPPORT
            if (request.hasFunctions()) {
                List<ToolSpecification> toolSpecs = ToolConversionUtils.convertFunctionsToToolSpecs(request.functions());
                chatRequestBuilder.toolSpecifications(toolSpecs);
            }

            ChatResponse response = chatModel.chat(chatRequestBuilder.build());

            LLMResponse.Builder builder = LLMResponse.builder(response.id(), modelName);
            builder.role(LLMMessage.Role.ASSISTANT);

            if (response.aiMessage() != null && response.aiMessage().text() != null) {
                builder.content(response.aiMessage().text());
            }

            // Handle tool execution requests (function calls)
            if (response.aiMessage() != null && response.aiMessage().hasToolExecutionRequests()) {
                List<FunctionCall> functionCalls = new ArrayList<>();
                response.aiMessage().toolExecutionRequests().forEach(toolExecutionRequest -> {
                    functionCalls.add(new FunctionCall(
                            toolExecutionRequest.id(),
                            toolExecutionRequest.name(),
                            toolExecutionRequest.arguments()
                    ));
                });
                builder.functionCalls(functionCalls);
            }

            if (response.tokenUsage() != null) {
                builder.usage(
                        response.tokenUsage().inputTokenCount(),
                        response.tokenUsage().outputTokenCount(),
                        response.tokenUsage().totalTokenCount()
                );
            }

            if (response.finishReason() != null) {
                builder.finishReason(response.finishReason().toString());
            }

            Map<String, Object> metadata = new HashMap<>();
            builder.metadata(metadata);
            return builder.build();
        });
    }

    @Override
    public CompletableFuture<Void> chatStream(LLMRequest request, Consumer<StreamingChunk> handler) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        final String streamId = UUID.randomUUID().toString();
        final int[] idx = new int[] { 0 };

        ChatRequest.Builder chatRequestBuilder = ChatRequest.builder()
                .messages(convertMessages(request));

        // ✅ ADD FUNCTION CALLING SUPPORT FOR STREAMING
        if (request.hasFunctions()) {
            List<ToolSpecification> toolSpecs = ToolConversionUtils.convertFunctionsToToolSpecs(request.functions());
            chatRequestBuilder.toolSpecifications(toolSpecs);
        }

        streamingModel.chat(
                chatRequestBuilder.build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        String content = (completeResponse != null && completeResponse.aiMessage() != null)
                                ? completeResponse.aiMessage().text()
                                : "";
                        String finish = (completeResponse != null && completeResponse.finishReason() != null)
                                ? completeResponse.finishReason().toString()
                                : "stop";
                        if (content != null && !content.isEmpty()) {
                            handler.accept(StreamingChunk.of(streamId, modelName, content, idx[0]++));
                        }
                        handler.accept(StreamingChunk.of(streamId, modelName, "", finish, idx[0]));
                        future.complete(null);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                }
        );
        return future;
    }

    @Override
    public CompletableFuture<List<String>> getAvailableModels() {
        return CompletableFuture.completedFuture(
                List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo"));
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }

    // ========================================================================
    // Conversion Methods
    // ========================================================================

    private List<ChatMessage> convertMessages(LLMRequest request) {
        return request.messages().stream().map(msg -> {
            return switch (msg.role()) {
                case SYSTEM -> (ChatMessage) SystemMessage.from(msg.content());
                case USER -> (ChatMessage) UserMessage.from(msg.content());
                case ASSISTANT -> (ChatMessage) AiMessage.from(msg.content());
                default -> (ChatMessage) UserMessage.from(msg.content());
            };
        }).collect(Collectors.toList());
    }

    /**
     * Convert Jentic FunctionDefinition to LangChain4j ToolSpecification.
     */
    private List<ToolSpecification> convertFunctionsToToolSpecs(List<FunctionDefinition> functions) {
        return functions.stream()
                .map(this::convertFunctionToToolSpec)
                .collect(Collectors.toList());
    }

    /**
     * Convert single FunctionDefinition to ToolSpecification.
     */
    private ToolSpecification convertFunctionToToolSpec(FunctionDefinition func) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(func.name())
                .description(func.description());

        // Convert parameters from Jentic format to LangChain4j JsonObjectSchema
        if (func.parameters() != null && !func.parameters().isEmpty()) {
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();

            Map<String, Object> params = func.parameters();

            // Extract properties
            if (params.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) params.get("properties");

                Map<String, JsonSchemaElement> convertedProps = new HashMap<>();
                properties.forEach((propName, propDef) -> {
                    JsonSchemaElement element = convertPropertyToJsonSchema(propDef);
                    convertedProps.put(propName, element);
                });

                schemaBuilder.addProperties(convertedProps);
            }

            // Extract required fields
            if (params.containsKey("required")) {
                @SuppressWarnings("unchecked")
                List<String> required = (List<String>) params.get("required");
                required.forEach(schemaBuilder::required);
            }

            builder.parameters(schemaBuilder.build());
        }

        return builder.build();
    }

    /**
     * Convert property definition to JsonSchemaElement.
     */
    private JsonSchemaElement convertPropertyToJsonSchema(Object propDef) {
        if (!(propDef instanceof Map)) {
            return dev.langchain4j.model.chat.request.json.JsonStringSchema.builder().build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) propDef;
        String type = (String) prop.getOrDefault("type", "string");
        String description = (String) prop.get("description");

        return switch (type) {
            case "string" -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                    .description(description)
                    .build();
            case "integer", "number" -> dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                    .description(description)
                    .build();
            case "boolean" -> dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder()
                    .description(description)
                    .build();
            case "array" -> dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                    .description(description)
                    .build();
            default -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                    .description(description)
                    .build();
        };
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private String modelName = "gpt-4o";
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;
        private Duration timeout = Duration.ofSeconds(60);
        private boolean logRequests = false;
        private boolean logResponses = false;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder model(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public OpenAIProvider build() {
            if (apiKey == null) throw new IllegalStateException("API key required");
            return new OpenAIProvider(this);
        }
    }
}