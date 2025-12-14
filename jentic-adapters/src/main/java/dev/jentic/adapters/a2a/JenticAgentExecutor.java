package dev.jentic.adapters.a2a;

import dev.jentic.core.Message;
import dev.jentic.core.MessageService;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Implements A2A AgentExecutor to expose internal Jentic agents.
 * 
 * <p>This class routes incoming A2A requests to internal agents via MessageService,
 * allowing external A2A clients to interact with Jentic agents.
 * 
 * <p>Usage with A2A SDK:
 * <pre>{@code
 * // Register as CDI producer for Quarkus/Jakarta EE
 * @Produces
 * public AgentExecutor produceExecutor(MessageService messageService) {
 *     return new JenticAgentExecutor("my-agent", messageService, Duration.ofMinutes(5));
 * }
 * }</pre>
 * 
 * @since 0.5.0
 */
public class JenticAgentExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(JenticAgentExecutor.class);
    
    private final String internalAgentId;
    private final MessageService messageService;
    private final DialogueA2AConverter converter;
    private final Duration timeout;
    
    public JenticAgentExecutor(
            String internalAgentId,
            MessageService messageService,
            Duration timeout) {
        this.internalAgentId = internalAgentId;
        this.messageService = messageService;
        this.converter = new DialogueA2AConverter();
        this.timeout = timeout;
    }
    
    /**
     * Executes an A2A request by routing to internal agent.
     * 
     * @param request the A2A request
     * @param statusCallback callback for status updates
     * @return the response
     */
    public CompletableFuture<DialogueA2AConverter.A2AResponse> execute(
            DialogueA2AConverter.A2AMessage request,
            Consumer<String> statusCallback) {
        
        log.debug("Executing A2A request for agent {}: {}", internalAgentId, request.messageId());
        
        // Notify started
        if (statusCallback != null) {
            statusCallback.accept("working");
        }
        
        // Convert A2A -> DialogueMessage
        DialogueMessage dialogueMsg = converter.fromA2AMessage(request, internalAgentId);
        
        // Route to internal agent
        return routeToInternalAgent(dialogueMsg)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .thenApply(response -> {
                log.debug("Internal agent responded: {}", response.performative());
                return converter.toA2AResponse(response);
            })
            .exceptionally(ex -> {
                log.error("Error executing A2A request: {}", ex.getMessage(), ex);
                return new DialogueA2AConverter.A2AResponse(
                    request.messageId(),
                    request.contextId(),
                    "Error: " + ex.getMessage(),
                    "failed",
                    true
                );
            });
    }
    
    /**
     * Cancels an ongoing A2A request.
     * 
     * @param contextId the context/task ID to cancel
     * @return true if cancelled successfully
     */
    public CompletableFuture<Boolean> cancel(String contextId) {
        log.debug("Cancelling A2A request: {}", contextId);
        
        // Send CANCEL message to internal agent
        DialogueMessage cancelMsg = DialogueMessage.builder()
            .conversationId(contextId)
            .senderId("a2a-bridge")
            .receiverId(internalAgentId)
            .performative(Performative.CANCEL)
            .build();
        
        Message message = cancelMsg.toMessage();
        return messageService.send(message)
            .thenApply(v -> true)
            .exceptionally(ex -> {
                log.error("Error cancelling request: {}", ex.getMessage());
                return false;
            });
    }
    
    /**
     * Routes a DialogueMessage to the internal agent and waits for response.
     */
    private CompletableFuture<DialogueMessage> routeToInternalAgent(DialogueMessage msg) {
        Message request = msg.toMessage();
        
        return messageService.sendAndWait(request, timeout.toMillis())
            .thenApply(DialogueMessage::fromMessage);
    }
    
    /**
     * @return the internal agent ID this executor routes to
     */
    public String getInternalAgentId() {
        return internalAgentId;
    }
}