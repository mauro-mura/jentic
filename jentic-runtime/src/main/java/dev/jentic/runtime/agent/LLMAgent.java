package dev.jentic.runtime.agent;

import dev.jentic.core.llm.LLMMessage;
import dev.jentic.core.memory.MemoryEntry;
import dev.jentic.core.memory.llm.ContextWindowStrategy;
import dev.jentic.core.memory.llm.LLMMemoryManager;
import dev.jentic.runtime.memory.llm.ContextWindowStrategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Base class for LLM-powered agents that use language model memory.
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
 * public class ChatBot extends LLMAgent {
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
public abstract class LLMAgent extends BaseAgent {

    /**
     * LLM Memory support (injected by runtime)
     */
    private LLMMemoryManager llmMemoryManager;

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
     * Creates an LLM agent with auto-generated ID.
     */
    protected LLMAgent() {
        super();
    }
    
    /**
     * Creates an LLM agent with specific ID.
     * 
     * @param agentId the agent identifier
     */
    protected LLMAgent(String agentId) {
        super(agentId);
    }
    
    /**
     * Creates an LLM agent with ID and name.
     * 
     * @param agentId the agent identifier
     * @param agentName the agent display name
     */
    protected LLMAgent(String agentId, String agentName) {
        super(agentId, agentName);
    }

    // ========== LLM MEMORY MANAGER ==========

    /**
     * Injects the LLM memory manager (optional).
     * @param llmMemoryManager
     */
    public void setLLMMemoryManager(LLMMemoryManager llmMemoryManager) {
        this.llmMemoryManager = llmMemoryManager;
        log.debug("LLM memory manager configured for agent: {}", getAgentId());
    }

    /**
     * accessor with validation
     * @return llmMemoryManager
     */
    protected LLMMemoryManager getLLMMemoryManager() {
        if (llmMemoryManager == null) {
            throw new IllegalStateException("LLMMemoryManager not initialized");
        }
        return llmMemoryManager;
    }

    /**
     * check llm memory availability
     *
     * @return true if LLM memory is configured
     */
    protected boolean hasLLMMemory() {
        return llmMemoryManager != null;
    }

    // ========== BASE LLM MEMORY OPERATIONS ==========

    /**
     * Adds a message to the LLM conversation history.
     *
     * <p>This is a direct delegation to {@link dev.jentic.core.memory.llm.LLMMemoryManager#addMessage}.
     * For enhanced functionality with auto-summarization, see {@link LLMAgent#addConversationMessage}.
     *
     * @param message the LLM message to add
     * @return a future that completes when the message is added
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     * @see LLMAgent#addConversationMessage for enhanced version
     */
    protected CompletableFuture<Void> addLLMMessage(dev.jentic.core.llm.LLMMessage message) {
        return getLLMMemoryManager().addMessage(message);
    }

    /**
     * Adds multiple messages to the LLM conversation history.
     *
     * <p>This is a direct delegation to {@link dev.jentic.core.memory.llm.LLMMemoryManager#addMessages}.
     *
     * @param messages the messages to add
     * @return a future that completes when messages are added
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected CompletableFuture<Void> addLLMMessages(List<dev.jentic.core.llm.LLMMessage> messages) {
        return getLLMMemoryManager().addMessages(messages);
    }

    /**
     * Gets the LLM conversation history within token budget.
     *
     * <p>This is a direct delegation to {@link dev.jentic.core.memory.llm.LLMMemoryManager#getConversationHistory}.
     *
     * @param maxTokens maximum number of tokens
     * @param strategy the context window strategy to use
     * @return a future with the conversation history
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected CompletableFuture<List<dev.jentic.core.llm.LLMMessage>> getLLMConversationHistory(
            int maxTokens,
            dev.jentic.core.memory.llm.ContextWindowStrategy strategy
    ) {
        return getLLMMemoryManager().getConversationHistory(maxTokens, strategy);
    }

    /**
     * Stores a fact in long-term LLM memory.
     *
     * <p>This is a direct delegation to {@link dev.jentic.core.memory.llm.LLMMemoryManager#remember}.
     * For simplified API with defaults, see {@link LLMAgent#storeFact}.
     *
     * @param key the fact key
     * @param content the fact content
     * @param metadata the metadata
     * @return a future that completes when stored
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     * @see LLMAgent#storeFact for simplified version
     */
    protected CompletableFuture<Void> rememberLLM(
            String key,
            String content,
            Map<String, Object> metadata
    ) {
        return getLLMMemoryManager().remember(key, content, metadata);
    }

    /**
     * Retrieves relevant context from long-term LLM memory.
     *
     * <p>This is a direct delegation to {@link dev.jentic.core.memory.llm.LLMMemoryManager#retrieveRelevantContext}.
     *
     * @param query the search query
     * @param maxTokens maximum tokens for results
     * @return a future with relevant memory entries
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected CompletableFuture<List<MemoryEntry>> retrieveLLMContext(
            String query,
            int maxTokens
    ) {
        return getLLMMemoryManager().retrieveRelevantContext(query, maxTokens);
    }

    /**
     * Clears the LLM conversation history.
     *
     * <p>This clears short-term conversation but preserves long-term facts.
     *
     * @return a future that completes when cleared
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected CompletableFuture<Void> clearLLMConversation() {
        return getLLMMemoryManager().clearConversationHistory();
    }

    /**
     * Gets the current token count of the conversation.
     *
     * @return the current token count
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected int getLLMConversationTokens() {
        return getLLMMemoryManager().getCurrentTokenCount();
    }

    /**
     * Gets the number of messages in the conversation.
     *
     * @return the message count
     * @throws IllegalStateException if LLM memory manager not configured
     * @since 0.6.0
     */
    protected int getLLMConversationMessageCount() {
        return getLLMMemoryManager().getMessageCount();
    }

    // ========== CONVERSATION MANAGEMENT ==========
    
    /**
     * Add a message to the conversation history.
     * 
     * <p>Enhanced version of {@link #addLLMMessage} with auto-summarization.
     * 
     * @param message the LLM message
     * @return future that completes when added
     */
    protected CompletableFuture<Void> addConversationMessage(LLMMessage message) {
        return addLLMMessage(message)
            .thenRun(() -> checkAndSummarizeIfNeeded());
    }
    
    /**
     * Add multiple messages to the conversation history.
     * 
     * <p>Enhanced version of {@link #addLLMMessages} with auto-summarization.
     * 
     * @param messages the messages
     * @return future that completes when added
     */
    protected CompletableFuture<Void> addConversationMessages(List<LLMMessage> messages) {
        return addLLMMessages(messages)
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
        return getLLMConversationHistory(maxTokens, strategy);
    }
    
    /**
     * Clear conversation history (keeps long-term facts).
     * 
     * @return future that completes when cleared
     */
    protected CompletableFuture<Void> clearConversation() {
        return clearLLMConversation();
    }
    
    /**
     * Get current conversation token count.
     * 
     * @return token count
     */
    public int getConversationTokens() {
        return getLLMConversationTokens();
    }
    
    /**
     * Get conversation message count.
     * 
     * @return message count
     */
    public int getConversationMessageCount() {
        return getLLMConversationMessageCount();
    }
    
    // ========== FACTS / LONG-TERM MEMORY ==========
    
    /**
     * Store a fact in long-term memory.
     * 
     * <p>Simplified version of {@link #rememberLLM} with empty metadata.
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
     * <p>Convenience wrapper around {@link #rememberLLM}.
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
        return rememberLLM(key, content, metadata);
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
        return retrieveLLMContext(query, maxTokens);
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
                        .map(entry -> {
                            String key = (String) entry.metadata().getOrDefault("key", "fact");
                            return key + ": " + entry.content();
                        })
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