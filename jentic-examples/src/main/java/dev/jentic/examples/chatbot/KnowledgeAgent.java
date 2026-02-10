package dev.jentic.examples.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.dialogue.Conversation;
import dev.jentic.core.dialogue.DialogueHandler;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.dialogue.DialogueCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Knowledge agent that performs RAG using conversation history.
 * <p>
 * Features:
 * - Uses dialogue conversation history for context
 * - Stores user interactions in LONG_TERM memory
 * - Simulated RAG (placeholder for real implementation)
 *
 * @since 0.7.0
 */
@JenticAgent(value = "knowledge-agent",
        capabilities = {"knowledge", "rag"})
public class KnowledgeAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DialogueCapability dialogue = new DialogueCapability(this);

    public KnowledgeAgent() {
        super("knowledge-agent", "Knowledge Agent");
    }

    @Override
    protected void onStart() {
        dialogue.initialize(messageService);
        log.info("KnowledgeAgent started");
    }

    @Override
    protected void onStop() {
        dialogue.shutdown(messageService);
        log.info("KnowledgeAgent stopped");
    }

    @DialogueHandler(performatives = Performative.REQUEST)
    public void handleKnowledgeRequest(DialogueMessage msg) {
        String query = msg.content().toString();
        String conversationId = msg.conversationId();
        String userId = msg.senderId();

        log.info("Processing knowledge request from user {} in conversation {}",
                userId, conversationId);

        // Get conversation for context
        dialogue.getConversationManager()
                .getConversation(conversationId)
                .ifPresentOrElse(
                        conversation -> processWithContext(msg, conversation, query, userId),
                        () -> processWithoutContext(msg, query, userId)
                );
    }

    private void processWithContext(
            DialogueMessage msg,
            Conversation conversation,
            String query,
            String userId) {

        // Get conversation history
        List<DialogueMessage> history = conversation.getHistory();
        String historyContext = formatHistory(history);

        log.debug("Processing with {} messages of history", history.size());

        // Perform RAG
        String answer = performRAG(query, historyContext);

        // Store interaction in the user profile
        storeUserInteraction(userId, query, answer);

        // Reply
        dialogue.inform(msg, answer);
    }

    private void processWithoutContext(DialogueMessage msg, String query, String userId) {
        log.warn("No conversation context found for {}", msg.conversationId());

        String answer = performRAG(query, "No previous context.");
        storeUserInteraction(userId, query, answer);
        dialogue.inform(msg, answer);
    }

    /**
     * Format conversation history for RAG context.
     */
    private String formatHistory(List<DialogueMessage> history) {
        if (history.isEmpty()) {
            return "No previous conversation.";
        }

        return history.stream()
                .limit(10) // Last 10 messages
                .map(m -> String.format("%s: %s",
                        m.senderId(),
                        m.content().toString()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Simulated RAG implementation.
     * In production: integrate with vector DB, embeddings, LLM.
     */
    private String performRAG(String query, String context) {
        log.debug("Performing RAG with query: {} (context length: {} chars)",
                query, context.length());

        // Simulate knowledge retrieval and generation
        String simulatedAnswer = String.format(
                "Based on your question '%s' and our conversation history, " +
                        "here's a simulated answer. [Context: %d chars of history]",
                query,
                context.length()
        );

        return simulatedAnswer;
    }

    /**
     * Store user interaction in long-term memory for future personalization.
     */
    private void storeUserInteraction(String userId, String query, String answer) {
        try {
            // Prepare interaction data
            Map<String, Object> interaction = new HashMap<>();
            interaction.put("query", query);
            interaction.put("answer", answer);
            interaction.put("timestamp", Instant.now().toString());

            // Serialize to JSON string (MemoryEntry.content is String, not Object)
            String contentJson = objectMapper.writeValueAsString(interaction);

            // Build key
            String key = "interaction:" + userId + ":" + System.currentTimeMillis();

            // Store in LONG_TERM memory
            rememberLong(key, contentJson);

            log.debug("Stored interaction for user {} with key {}", userId, key);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize interaction data: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to store user interaction: {}", e.getMessage(), e);
        }
    }
}
