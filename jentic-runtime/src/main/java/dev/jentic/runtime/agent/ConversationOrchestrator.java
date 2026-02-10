package dev.jentic.runtime.agent;

import dev.jentic.core.AgentDirectory;
import dev.jentic.core.AgentQuery;
import dev.jentic.core.AgentStatus;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.conversation.Intent;
import dev.jentic.core.conversation.IntentClassifier;
import dev.jentic.core.dialogue.DialogueHandler;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.runtime.dialogue.DialogueCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates conversations by routing requests to appropriate agents
 * based on intent classification and agent capabilities.
 * <p>
 * Architecture: Orchestrator-Centric
 * - All messages flow through orchestrator
 * - Agents return results to orchestrator (not direct forwarding)
 * - Orchestrator decides if further processing is needed
 * 
 * @since 0.7.0
 */
@JenticAgent(value = "conversation-orchestrator", 
             capabilities = {"orchestration", "routing"})
public class ConversationOrchestrator extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(ConversationOrchestrator.class);
    
    private final DialogueCapability dialogue = new DialogueCapability(this);
    private final IntentClassifier intentClassifier;
    private final AgentDirectory agentDirectory;
    
    // Track the original user sender per conversation
    private final Map<String, String> conversationToUser = new ConcurrentHashMap<>();
    
    public ConversationOrchestrator(
            IntentClassifier classifier,
            AgentDirectory directory) {
        super("conversation-orchestrator", "Conversation Orchestrator");
        this.intentClassifier = classifier;
        this.agentDirectory = directory;
    }
    
    @Override
    protected void onStart() {
        dialogue.initialize(messageService);
        log.info("ConversationOrchestrator started");
    }
    
    @Override
    protected void onStop() {
        dialogue.shutdown(messageService);
        log.info("ConversationOrchestrator stopped");
    }
    
    /**
     * Handles incoming user requests (initial entry point).
     */
    @DialogueHandler(performatives = Performative.REQUEST)
    public void handleUserRequest(DialogueMessage msg) {
        String conversationId = msg.conversationId();
        String userId = msg.senderId();
        String content = msg.content().toString();
        
        log.info("User request from {} in conversation {}", userId, conversationId);
        
        // Track user for this conversation
        conversationToUser.put(conversationId, userId);
        
        // Route message
        routeMessage(conversationId, content, msg);
    }
    
    /**
     * Handles responses from agents (intermediate or final).
     */
    @DialogueHandler(performatives = Performative.INFORM)
    public void handleAgentResponse(DialogueMessage msg) {
        String conversationId = msg.conversationId();
        String senderId = msg.senderId();
        String content = msg.content().toString();
        
        log.debug("Agent response from {} in conversation {}", senderId, conversationId);
        
        // Determine if this is an intermediate or final response
        if (needsFurtherProcessing(msg)) {
            log.debug("Intermediate response from {}, re-routing", senderId);
            // Re-classify and route
            routeMessage(conversationId, content, null);
        } else {
            log.debug("Final response from {}, sending to user", senderId);
            // Send it to a user
            sendToUser(conversationId, content);
        }
    }
    
    /**
     * Routes a message based on intent classification.
     */
    private void routeMessage(String conversationId, String content, DialogueMessage originalUserMsg) {
        intentClassifier.classify(content, conversationId)
            .thenCompose(intent -> {
                log.debug("Classified intent: {} → capability: {}", 
                    intent.name(), intent.requiredCapability());
                
                return agentDirectory.findAgents(
                    AgentQuery.builder()
                        .requiredCapability(intent.requiredCapability())
                        .status(AgentStatus.RUNNING)
                        .build()
                ).thenCombine(
                    CompletableFuture.completedFuture(intent),
                        RoutingDecision::new
                );
            })
            .thenAccept(decision -> {
                if (decision.agents.isEmpty()) {
                    log.warn("No agent for capability: {}", decision.intent.requiredCapability());
                    sendToUser(conversationId, 
                        "I cannot handle this request. No agent available.");
                    return;
                }
                
                String targetAgentId = decision.agents.getFirst().agentId();
                log.debug("Routing to agent: {}", targetAgentId);
                
                // Send a request to an agent
                DialogueMessage request = DialogueMessage.builder()
                    .conversationId(conversationId)
                    .senderId(getAgentId())
                    .receiverId(targetAgentId)
                    .performative(Performative.REQUEST)
                    .content(content)
                    .build();
                
                messageService.send(request.toMessage());
            })
            .exceptionally(error -> {
                log.error("Routing error in conversation {}: {}", 
                    conversationId, error.getMessage(), error);
                sendToUser(conversationId, "An error occurred: " + error.getMessage());
                return null;
            });
    }
    
    /**
     * Determines if the agent response needs further processing.
     * <p>
     * Logic:
     * - If from translator → needs knowledge processing
     * - If from knowledge → final, send to user
     */
    private boolean needsFurtherProcessing(DialogueMessage msg) {
        String senderId = msg.senderId();
        Map<String, Object> metadata = msg.metadata();
        
        // Check if this is from translator (has originalLanguage metadata)
        if (metadata.containsKey("originalLanguage")) {
            log.debug("Message from translator, needs knowledge processing");
            return true;
        }
        
        // Check sender agent type
        if (senderId.contains("translator")) {
            return true;
        }
        
        // If from a knowledge agent, this is final
        if (senderId.contains("knowledge")) {
            return false;
        }
        
        // Default: assume final
        return false;
    }
    
    /**
     * Sends a final response to the user.
     */
    private void sendToUser(String conversationId, String content) {
        String userId = conversationToUser.get(conversationId);
        
        if (userId == null) {
            log.error("No user found for conversation {}", conversationId);
            return;
        }
        
        DialogueMessage response = DialogueMessage.builder()
            .conversationId(conversationId)
            .senderId(getAgentId())
            .receiverId(userId)
            .performative(Performative.INFORM)
            .content(content)
            .build();
        
        messageService.send(response.toMessage());
        log.info("Response sent to user {} in conversation {}", userId, conversationId);
    }
    
    /**
     * Internal record for routing decision.
     */
    private record RoutingDecision(
        java.util.List<dev.jentic.core.AgentDescriptor> agents,
        Intent intent
    ) {}
    
    /**
     * Provides access to the dialogue capability for testing.
     */
    public DialogueCapability getDialogueCapability() {
        return dialogue;
    }
}
