package dev.jentic.runtime.agent;

import dev.jentic.core.llm.LLMMessage;
import dev.jentic.core.memory.MemoryEntry;
import dev.jentic.core.memory.llm.ContextWindowStrategy;
import dev.jentic.runtime.memory.llm.ContextWindowStrategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Base class for AI agents that use LLM (Large Language Model) memory.
 * 
 * <p>Extends {@link BaseAgent} with convenient methods for:
 * <ul>
 *   <li>Managing conversation history</li>
 *   <li>Building LLM prompts with context</li>
 *   <li>Storing and retrieving facts</li>
 *   <li>Auto-summarization of long conversations</li>
 * </ul>
 * 
 * <p><b>Prerequisites:</b>
 * The runtime must inject an {@link dev.jentic.core.memory.llm.LLMMemoryManager}
 * via {@link #setLLMMemoryManager} before the agent starts.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * @JenticAgent("chat-bot")
 * public class ChatBot extends AIAgent {
 *     
 *     @JenticMessageHandler("user.message")
 *     public void handleUserMessage(Message msg) {
 *         String userInput = msg.getContent(String.class);
 *         
 *         // Add user message to conversation
 *         addConversationMessage(LLMMessage.user(userInput)).join();
 *         
 *         // Build prompt with conversation + context
 *         List<LLMMessage> prompt = buildLLMPrompt(userInput, 2000).join();
 *         
 *         // Call LLM with prompt
 *         String response = callLLM(prompt);
 *         
 *         // Add response to conversation
 *         addConversationMessage(LLMMessage.assistant(response)).join();
 *         
 *         // Send response back
 *         sendMessage(Message.builder()
 *             .topic("bot.response")
 *             .content(response)
 *             .build());
 *     }
 * }
 * }</pre>
 * 
 * @since 0.6.0
 */
public abstract class AIAgent extends BaseAgent {
    
    /**
     * Default context window strategy (SLIDING).
     */
    protected ContextWindowStrategy defaultStrategy = ContextWindowStrategies.SLIDING;
    
    /**
     * Default conversation token budget.
     */
    protected int defaultConversationBudget = 2000;
    
    /**
     * Default context token budget.
     */
    protected int defaultContextBudget = 500;
    
    /**
     * Auto-summarization threshold (tokens).
     * When conversation exceeds this, oldest messages are auto-summarized.
     */
    protected int autoSummarizeThreshold = 5000;
    
    /**
     * Messages to summarize when threshold is reached.
     */
    protected int messagesToSummarize = 10;
    
    /**
     * Creates an AI agent with auto-generated ID.
     */
    protected AIAgent() {
        super();
    }
    
    /**
     * Creates an AI agent with specific ID.
     * 
     * @param agentId the agent identifier
     */
    protected AIAgent(String agentId) {
        super(agentId);
    }
    
    /**
     * Creates an AI agent with ID and name.
     * 
     * @param agentId the agent identifier
     * @param agentName the agent display name
     */
    protected AIAgent(String agentId, String agentName) {
        super(agentId, agentName);
    }
    
    // ========== CONVERSATION MANAGEMENT ==========
    
    /**
     * Add a message to the conversation history.
     * 
     * @param message the LLM message
     * @return future that completes when added
     */
    protected CompletableFuture<Void> addConversationMessage(LLMMessage message) {
        return getLLMMemoryManager().addMessage(message)
            .thenRun(() -> checkAndSummarizeIfNeeded());
    }
    
    /**
     * Add multiple messages to the conversation history.
     * 
     * @param messages the messages
     * @return future that completes when added
     */
    protected CompletableFuture<Void> addConversationMessages(List<LLMMessage> messages) {
        return getLLMMemoryManager().addMessages(messages)
            .thenRun(() -> checkAndSummarizeIfNeeded());
    }
    
    /**
     * Get conversation history with default budget and strategy.
     * 
     * @return future with conversation messages
     */
    protected CompletableFuture<List<LLMMessage>> getConversation() {
        return getConversation(defaultConversationBudget, defaultStrategy);
    }
    
    /**
     * Get conversation history with custom budget and strategy.
     * 
     * @param maxTokens maximum tokens
     * @param strategy selection strategy
     * @return future with conversation messages
     */
    protected CompletableFuture<List<LLMMessage>> getConversation(
        int maxTokens,
        ContextWindowStrategy strategy
    ) {
        return getLLMMemoryManager().getConversationHistory(maxTokens, strategy);
    }
    
    /**
     * Clear conversation history (keeps long-term facts).
     * 
     * @return future that completes when cleared
     */
    protected CompletableFuture<Void> clearConversation() {
        return getLLMMemoryManager().clearConversationHistory();
    }
    
    /**
     * Get current conversation token count.
     * 
     * @return token count
     */
    protected int getConversationTokens() {
        return getLLMMemoryManager().getCurrentTokenCount();
    }
    
    /**
     * Get conversation message count.
     * 
     * @return message count
     */
    protected int getConversationMessageCount() {
        return getLLMMemoryManager().getMessageCount();
    }
    
    // ========== FACTS / LONG-TERM MEMORY ==========
    
    /**
     * Store a fact in long-term memory.
     * 
     * @param key the fact key
     * @param content the fact content
     * @return future that completes when stored
     */
    protected CompletableFuture<Void> storeFact(String key, String content) {
        return storeFact(key, content, Map.of());
    }
    
    /**
     * Store a fact in long-term memory with metadata.
     * 
     * @param key the fact key
     * @param content the fact content
     * @param metadata the metadata
     * @return future that completes when stored
     */
    protected CompletableFuture<Void> storeFact(
        String key,
        String content,
        Map<String, Object> metadata
    ) {
        return getLLMMemoryManager().remember(key, content, metadata);
    }
    
    /**
     * Retrieve relevant facts from long-term memory.
     * 
     * @param query the search query
     * @return future with relevant facts
     */
    protected CompletableFuture<List<MemoryEntry>> retrieveFacts(String query) {
        return retrieveFacts(query, defaultContextBudget);
    }
    
    /**
     * Retrieve relevant facts with custom token budget.
     * 
     * @param query the search query
     * @param maxTokens maximum tokens
     * @return future with relevant facts
     */
    protected CompletableFuture<List<MemoryEntry>> retrieveFacts(String query, int maxTokens) {
        return getLLMMemoryManager().retrieveRelevantContext(query, maxTokens);
    }
    
    // ========== PROMPT BUILDING ==========
    
    /**
     * Build a complete LLM prompt with conversation and context.
     * 
     * <p>The prompt includes:
     * <ul>
     *   <li>System message with relevant facts (if any)</li>
     *   <li>Conversation history (within budget)</li>
     *   <li>New user message</li>
     * </ul>
     * 
     * @param userMessage the new user message
     * @param totalBudget total token budget for prompt
     * @return future with complete prompt
     */
    protected CompletableFuture<List<LLMMessage>> buildLLMPrompt(
        String userMessage,
        int totalBudget
    ) {
        return buildLLMPrompt(userMessage, totalBudget, null);
    }
    
    /**
     * Build a complete LLM prompt with conversation and context.
     * 
     * @param userMessage the new user message
     * @param totalBudget total token budget for prompt
     * @param contextQuery query for retrieving relevant facts (null for no context)
     * @return future with complete prompt
     */
    protected CompletableFuture<List<LLMMessage>> buildLLMPrompt(
        String userMessage,
        int totalBudget,
        String contextQuery
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<LLMMessage> prompt = new ArrayList<>();
            
            int conversationBudget = totalBudget;
            
            // Retrieve context if query provided
            if (contextQuery != null && !contextQuery.trim().isEmpty()) {
                List<MemoryEntry> context = retrieveFacts(contextQuery, defaultContextBudget).join();
                
                if (!context.isEmpty()) {
                    String contextStr = context.stream()
                        .map(MemoryEntry::content)
                        .collect(Collectors.joining(", "));
                    
                    prompt.add(LLMMessage.system("Relevant facts: " + contextStr));
                    
                    // Reduce conversation budget by context tokens
                    int contextTokens = getLLMMemoryManager().getTokenEstimator()
                        .estimateTokens(prompt.get(0));
                    conversationBudget -= contextTokens;
                }
            }
            
            // Add conversation history
            List<LLMMessage> history = getConversation(conversationBudget, defaultStrategy).join();
            prompt.addAll(history);
            
            // Add new user message
            prompt.add(LLMMessage.user(userMessage));
            
            return prompt;
        });
    }
    
    // ========== AUTO-SUMMARIZATION ==========
    
    /**
     * Check if conversation needs summarization and do it if needed.
     */
    private void checkAndSummarizeIfNeeded() {
        if (autoSummarizeThreshold > 0 && getConversationTokens() > autoSummarizeThreshold) {
            log.info("Conversation tokens ({}) exceeded threshold ({}), auto-summarizing...",
                    getConversationTokens(), autoSummarizeThreshold);
            
            getLLMMemoryManager().summarizeOldMessages(messagesToSummarize)
                .thenAccept(summary -> {
                    log.info("Auto-summarized {} messages: {}", messagesToSummarize, summary);
                })
                .exceptionally(e -> {
                    log.error("Failed to auto-summarize conversation", e);
                    return null;
                });
        }
    }
    
    /**
     * Manually trigger summarization of old messages.
     * 
     * @param count number of oldest messages to summarize
     * @return future with summary text
     */
    protected CompletableFuture<String> summarizeConversation(int count) {
        return getLLMMemoryManager().summarizeOldMessages(count);
    }
    
    // ========== CONFIGURATION ==========
    
    /**
     * Set the default context window strategy.
     * 
     * @param strategy the strategy
     */
    protected void setDefaultStrategy(ContextWindowStrategy strategy) {
        this.defaultStrategy = strategy;
    }
    
    /**
     * Set the default conversation token budget.
     * 
     * @param budget the budget
     */
    protected void setDefaultConversationBudget(int budget) {
        this.defaultConversationBudget = budget;
    }
    
    /**
     * Set the default context token budget.
     * 
     * @param budget the budget
     */
    protected void setDefaultContextBudget(int budget) {
        this.defaultContextBudget = budget;
    }
    
    /**
     * Configure auto-summarization.
     * 
     * @param threshold token threshold (0 to disable)
     * @param messagesToSummarize number of messages to summarize
     */
    protected void configureAutoSummarization(int threshold, int messagesToSummarize) {
        this.autoSummarizeThreshold = threshold;
        this.messagesToSummarize = messagesToSummarize;
    }
    
    /**
     * Disable auto-summarization.
     */
    protected void disableAutoSummarization() {
        this.autoSummarizeThreshold = 0;
    }
}
