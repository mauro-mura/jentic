package dev.jentic.adapters.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jentic.core.conversation.Intent;
import dev.jentic.core.conversation.IntentClassifier;
import dev.jentic.core.dialogue.Conversation;
import dev.jentic.core.dialogue.ConversationManager;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM-based intent classifier using structured JSON output.
 * <p>
 * Classifies user messages into intents:
 * - simple_query: Direct knowledge queries
 * - multilingual: Translation needed
 * - complex: Multi-step reasoning required
 *
 * @since 0.7.0
 */
public class LLMIntentClassifier implements IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(LLMIntentClassifier.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final LLMProvider llmProvider;
    private final ConversationManager conversationManager;
    private final String model;

    // Intent to capability mapping
    private static final Map<String, String> INTENT_TO_CAPABILITY = Map.of(
            "simple_query", "knowledge",
            "multilingual", "translation",
            "complex", "knowledge",
            "translation_only", "translation"
    );

    public LLMIntentClassifier(
            LLMProvider llmProvider,
            ConversationManager conversationManager,
            String model) {
        this.llmProvider = llmProvider;
        this.conversationManager = conversationManager;
        this.model = model;
    }

    /**
     * Convenience constructor using provider's default model.
     */
    public LLMIntentClassifier(
            LLMProvider llmProvider,
            ConversationManager conversationManager) {
        this(llmProvider, conversationManager, llmProvider.getDefaultModel());
    }

    @Override
    public CompletableFuture<Intent> classify(String userMessage, String conversationId) {
        // Get conversation history for context
        String context = buildContext(conversationId);

        // Build LLM request
        LLMRequest request = LLMRequest.builder(model)
                .systemMessage(buildSystemPrompt())
                .userMessage(buildUserPrompt(userMessage, context))
                .temperature(0.3)  // Low temperature for consistent classification
                .maxTokens(200)    // Short response needed
                .build();

        // Call LLM and parse response
        return llmProvider.chat(request)
                .thenApply(LLMResponse::content)
                .thenApply(this::parseIntentFromResponse)
                .exceptionally(error -> {
                    log.error("Error classifying intent: {}", error.getMessage(), error);
                    // Fallback to simple_query on error
                    return Intent.simple("simple_query", "knowledge");
                });
    }

    private String buildContext(String conversationId) {
        return conversationManager.getConversation(conversationId)
                .map(this::formatConversationHistory)
                .orElse("No previous conversation history.");
    }

    private String formatConversationHistory(Conversation conversation) {
        List<DialogueMessage> history = conversation.getHistory();
        if (history.isEmpty()) {
            return "No previous messages.";
        }

        StringBuilder sb = new StringBuilder("Recent conversation:\n");
        int limit = Math.min(5, history.size());
        for (int i = history.size() - limit; i < history.size(); i++) {
            DialogueMessage msg = history.get(i);
            sb.append("- ").append(msg.senderId()).append(": ")
                    .append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return """
            You are an intent classification system. Classify user messages into one of these intents:
            
            1. "simple_query" - Direct questions in English that can be answered with knowledge retrieval
               Examples: "What is the weather?", "Who is the president?"
            
            2. "multilingual" - Messages in non-English languages that need translation
               Examples: "Quel temps fait-il?", "¿Cuál es el clima?"
            
            3. "complex" - Multi-step reasoning or complex analytical questions
               Examples: "Compare and contrast...", "What are the implications of..."
            
            4. "translation_only" - Explicit requests for translation only
               Examples: "Translate 'hello' to French", "How do you say 'goodbye' in Spanish?"
            
            Respond ONLY with valid JSON in this exact format:
            {
              "intent": "simple_query",
              "confidence": 0.95,
              "language": "en"
            }
            
            Do not include any other text, explanations, or markdown formatting.
            """;
    }

    private String buildUserPrompt(String userMessage, String context) {
        return String.format("""
            Context: %s
            
            User message: "%s"
            
            Classify this message and respond with JSON only.
            """, context, userMessage);
    }

    private Intent parseIntentFromResponse(String response) {
        try {
            // Remove markdown code blocks if present
            String cleaned = response.trim()
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(cleaned);

            String intentName = json.get("intent").asText();
            double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 1.0;
            String language = json.has("language") ? json.get("language").asText() : "unknown";

            // Map intent to capability
            String capability = INTENT_TO_CAPABILITY.getOrDefault(intentName, "knowledge");

            // Build parameters
            Map<String, Object> params = new HashMap<>();
            params.put("confidence", confidence);
            params.put("language", language);

            log.debug("Classified intent: {} (confidence: {}, language: {})",
                    intentName, confidence, language);

            return Intent.withParams(intentName, capability, params);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response as JSON: {}", response, e);
            return Intent.simple("simple_query", "knowledge");
        }
    }
}
