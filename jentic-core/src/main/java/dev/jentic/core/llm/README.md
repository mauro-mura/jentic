# Jentic LLM Core Package

## Overview

The `dev.jentic.core.llm` package provides a stable, provider-agnostic abstraction for integrating Large Language Models into Jentic agents. This package follows Jentic's architectural pattern of placing interfaces in `jentic-core` and implementations in `jentic-adapters`.

## Package Structure

```
dev.jentic.core.llm/
├── LLMProvider.java          # Core interface for LLM providers
├── LLMRequest.java           # Immutable request with builder
├── LLMResponse.java          # Response with content and metadata
├── LLMMessage.java           # Individual conversation messages
├── StreamingChunk.java       # Chunks for streaming responses
├── FunctionDefinition.java   # Define callable functions
├── FunctionCall.java         # LLM function call requests
├── LLMException.java         # Comprehensive error handling
└── package-info.java         # Package documentation
```

## Core Components

### LLMProvider Interface

The main interface for interacting with LLM providers:

```java
public interface LLMProvider {
    CompletableFuture<LLMResponse> chat(LLMRequest request);
    CompletableFuture<Void> chatStream(LLMRequest request, Consumer<StreamingChunk> chunkHandler);
    CompletableFuture<List<String>> getAvailableModels();
    String getProviderName();
    boolean supportsFunctionCalling();
    boolean supportsStreaming();
}
```

### Request/Response Models

All models are immutable records for thread-safety:

- **LLMRequest**: Immutable request with builder pattern
- **LLMResponse**: Response containing content, usage stats, and metadata
- **LLMMessage**: Individual messages in a conversation
- **StreamingChunk**: Incremental content in streaming mode

### Function Calling

Support for LLM function/tool calling:

- **FunctionDefinition**: Define functions the LLM can call
- **FunctionCall**: LLM's request to call a function

## Design Principles

### 1. Provider Agnostic

Works with any LLM provider. Implementations for OpenAI, Anthropic, and Ollama are in `jentic-adapters`.

### 2. Async First

All operations return `CompletableFuture` for non-blocking execution.

### 3. Immutability

All models are immutable records, ensuring thread-safety.

### 4. Builder Pattern

Complex objects use builders for flexible construction.

### 5. Zero Dependencies

No external dependencies beyond Java standard library.

## Usage Examples

### Basic Chat Completion

```java
LLMProvider provider = new OpenAIProvider(apiKey);

LLMRequest request = LLMRequest.builder("gpt-4")
    .systemMessage("You are a helpful assistant.")
    .userMessage("What is the capital of France?")
    .temperature(0.7)
    .maxTokens(100)
    .build();

provider.chat(request).thenAccept(response -> {
    System.out.println("Response: " + response.content());
    System.out.println("Tokens used: " + response.usage().totalTokens());
});
```

### Streaming Response

```java
provider.chatStream(request, chunk -> {
    if (chunk.hasContent()) {
        System.out.print(chunk.content());
    }
    if (chunk.isLast()) {
        System.out.println("\n[Streaming complete]");
    }
});
```

### Function Calling

```java
// Define function
FunctionDefinition weatherFunc = FunctionDefinition.builder("get_weather")
    .description("Get current weather for a location")
    .stringParameter("location", "City and state, e.g. San Francisco, CA", true)
    .enumParameter("unit", "Temperature unit", false, "celsius", "fahrenheit")
    .build();

// Create request with function
LLMRequest request = LLMRequest.builder("gpt-4")
    .userMessage("What's the weather in Paris?")
    .addFunction(weatherFunc)
    .build();

// Handle response
provider.chat(request).thenAccept(response -> {
    if (response.hasFunctionCalls()) {
        FunctionCall call = response.functionCalls().get(0);
        String location = call.getStringArgument("location");
        String unit = call.getStringArgument("unit");
        
        // Execute function
        String result = getWeather(location, unit);
        
        // Continue conversation with function result
        LLMRequest followUp = LLMRequest.builder("gpt-4")
            .messages(request.messages())
            .addMessage(response.toMessage())
            .addMessage(LLMMessage.function("get_weather", result))
            .build();
            
        provider.chat(followUp).thenAccept(finalResponse -> {
            System.out.println(finalResponse.content());
        });
    }
});
```

### Multi-turn Conversation

```java
List<LLMMessage> conversation = new ArrayList<>();
conversation.add(LLMMessage.system("You are a helpful assistant."));

// First turn
conversation.add(LLMMessage.user("What is photosynthesis?"));
LLMRequest req1 = LLMRequest.builder("gpt-4").messages(conversation).build();

provider.chat(req1).thenAccept(resp1 -> {
    conversation.add(resp1.toMessage());
    
    // Second turn
    conversation.add(LLMMessage.user("Can you explain it in simpler terms?"));
    LLMRequest req2 = LLMRequest.builder("gpt-4").messages(conversation).build();
    
    provider.chat(req2).thenAccept(resp2 -> {
        System.out.println(resp2.content());
    });
});
```

### Error Handling

```java
provider.chat(request).exceptionally(ex -> {
    if (ex.getCause() instanceof LLMException llmEx) {
        switch (llmEx.getErrorType()) {
            case RATE_LIMIT -> {
                System.err.println("Rate limit hit, waiting...");
                // Implement exponential backoff
            }
            case AUTHENTICATION -> {
                System.err.println("Invalid API key");
            }
            case CONTEXT_LENGTH_EXCEEDED -> {
                System.err.println("Context too long, trimming...");
            }
            case NETWORK -> {
                System.err.println("Network error, retrying...");
            }
        }
    }
    return null;
});
```

## Supported Providers

Implementations are available in `jentic-adapters`:

- **OpenAI**: GPT-4, GPT-3.5, etc.
- **Anthropic**: Claude 3 Opus, Sonnet, Haiku
- **Ollama**: Local models (Llama, Mistral, Phi, etc.)

## Integration with Jentic Agents

The LLM capabilities integrate with Jentic agents through the `AIAgent` class in `jentic-runtime`:

```java
@JenticAgent("research-assistant")
public class ResearchAgent extends AIAgent {
    
    public ResearchAgent() {
        super("research-assistant", "Research Assistant",
              new OpenAIProvider(System.getenv("OPENAI_API_KEY")));
        
        // Register tools
        registerTool("search_web", this::searchWeb);
        registerTool("summarize", this::summarize);
    }
    
    private String searchWeb(Map<String, Object> args) {
        String query = (String) args.get("query");
        // Perform web search
        return searchResults;
    }
}
```

## Configuration

Providers can be configured through:

1. **Environment Variables**: API keys and endpoints
2. **Configuration Files**: YAML/JSON configuration
3. **Programmatic**: Direct instantiation with parameters

Example configuration:

```yaml
jentic:
  llm:
    provider: openai
    model: gpt-4
    temperature: 0.7
    maxTokens: 2000
    apiKey: ${OPENAI_API_KEY}
```

## Performance Considerations

### Token Management

Always set appropriate `maxTokens` to control costs:

```java
LLMRequest request = LLMRequest.builder("gpt-4")
    .userMessage(userInput)
    .maxTokens(500)  // Limit response length
    .build();
```

### Conversation History

Trim old messages to stay within context limits:

```java
List<LLMMessage> messages = new ArrayList<>(conversation);
if (messages.size() > 20) {
    // Keep system message and recent messages
    LLMMessage system = messages.get(0);
    messages = new ArrayList<>();
    messages.add(system);
    messages.addAll(conversation.subList(conversation.size() - 19, conversation.size()));
}
```

### Caching

For repeated queries, consider caching responses:

```java
Map<String, LLMResponse> cache = new ConcurrentHashMap<>();

String cacheKey = request.getLastUserMessage().content();
if (cache.containsKey(cacheKey)) {
    return CompletableFuture.completedFuture(cache.get(cacheKey));
}

return provider.chat(request).thenApply(response -> {
    cache.put(cacheKey, response);
    return response;
});
```

## Testing

The package is designed for easy testing:

```java
@Test
void testLLMProvider() {
    // Use mock provider for testing
    LLMProvider mockProvider = new MockLLMProvider();
    
    LLMRequest request = LLMRequest.builder("test-model")
        .userMessage("Test message")
        .build();
    
    LLMResponse response = mockProvider.chat(request).join();
    
    assertEquals("Test response", response.content());
    assertEquals("test-model", response.model());
}
```

## See Also

- [Jentic Architecture](../../README.md)
- [LLM Provider Implementations](../../../jentic-adapters/README.md)
- [AI Agent Guide](../../../jentic-runtime/docs/AIAgent.md)
- [Function Calling Examples](../../../examples/llm/README.md)
