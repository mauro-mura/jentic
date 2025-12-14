package dev.jentic.adapters.a2a;

import dev.jentic.core.AgentDirectory;
import dev.jentic.core.MessageService;
import dev.jentic.core.dialogue.DialogueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Main adapter coordinating A2A integration.
 * 
 * <p>This adapter provides unified message routing that automatically determines
 * whether to route internally (via MessageService) or externally (via A2A protocol).
 * 
 * <p>Routing logic:
 * <ul>
 *   <li>If target agent is registered locally → route via MessageService</li>
 *   <li>If target is a URL (http/https) → route via A2A client</li>
 *   <li>Otherwise → fail with unknown agent error</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * JenticA2AAdapter adapter = new JenticA2AAdapter(
 *     messageService,
 *     agentDirectory,
 *     "my-agent",
 *     Duration.ofMinutes(5)
 * );
 * 
 * // Send to internal agent
 * adapter.send(DialogueMessage.request("my-agent", "other-agent", "do task"));
 * 
 * // Send to external A2A agent
 * adapter.send(DialogueMessage.request("my-agent", "https://external.com", "query"));
 * }</pre>
 * 
 * @since 0.5.0
 */
public class JenticA2AAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(JenticA2AAdapter.class);
    
    private final MessageService messageService;
    private final AgentDirectory agentDirectory;
    private final JenticA2AClient externalClient;
    private final String localAgentId;
    private final Duration timeout;
    
    public JenticA2AAdapter(
            MessageService messageService,
            AgentDirectory agentDirectory,
            String localAgentId,
            Duration timeout) {
        this.messageService = messageService;
        this.agentDirectory = agentDirectory;
        this.externalClient = new JenticA2AClient(timeout);
        this.localAgentId = localAgentId;
        this.timeout = timeout;
    }
    
    /**
     * Creates an adapter with custom A2A client.
     */
    public JenticA2AAdapter(
            MessageService messageService,
            AgentDirectory agentDirectory,
            JenticA2AClient externalClient,
            String localAgentId,
            Duration timeout) {
        this.messageService = messageService;
        this.agentDirectory = agentDirectory;
        this.externalClient = externalClient;
        this.localAgentId = localAgentId;
        this.timeout = timeout;
    }
    
    /**
     * Sends a message, auto-routing to internal or external agent.
     * 
     * @param msg the message to send
     * @return the response message
     */
    public CompletableFuture<DialogueMessage> send(DialogueMessage msg) {
        String targetId = msg.receiverId();
        
        if (targetId == null || targetId.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Target agent ID is required")
            );
        }
        
        // Check if internal agent
        if (isInternalAgent(targetId)) {
            log.debug("Routing to internal agent: {}", targetId);
            return sendInternal(msg);
        }
        
        // Check if external A2A agent (URL format)
        if (isExternalUrl(targetId)) {
            log.debug("Routing to external A2A agent: {}", targetId);
            return sendExternal(msg);
        }
        
        // Unknown agent
        log.warn("Unknown agent: {}", targetId);
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Unknown agent: " + targetId + 
                ". Must be a registered internal agent or an A2A URL (http/https)")
        );
    }
    
    /**
     * Sends a request to an internal agent.
     */
    private CompletableFuture<DialogueMessage> sendInternal(DialogueMessage msg) {
        return messageService
            .sendAndWait(msg.toMessage(), timeout.toMillis())
            .thenApply(DialogueMessage::fromMessage);
    }
    
    /**
     * Sends a request to an external A2A agent.
     */
    private CompletableFuture<DialogueMessage> sendExternal(DialogueMessage msg) {
        return externalClient.send(msg.receiverId(), msg, localAgentId);
    }
    
    /**
     * Checks if a target is a registered internal agent.
     */
    private boolean isInternalAgent(String targetId) {
        if (agentDirectory == null) {
            return false;
        }
        try {
            return agentDirectory.findById(targetId)
                .join()
                .isPresent();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if target is an external URL.
     */
    private boolean isExternalUrl(String targetId) {
        return targetId.startsWith("http://") || targetId.startsWith("https://");
    }
    
    /**
     * Fetches the Agent Card from an external A2A endpoint.
     * 
     * @param agentUrl the agent's base URL
     * @return the agent card
     */
    public CompletableFuture<JenticA2AClient.AgentCard> fetchAgentCard(String agentUrl) {
        return externalClient.fetchAgentCard(agentUrl);
    }
    
    /**
     * Checks if an external A2A agent is available.
     * 
     * @param agentUrl the agent URL to check
     * @return true if agent is reachable
     */
    public CompletableFuture<Boolean> pingExternal(String agentUrl) {
        return externalClient.ping(agentUrl);
    }
    
    /**
     * @return the local agent ID
     */
    public String getLocalAgentId() {
        return localAgentId;
    }
    
    /**
     * @return the A2A client for external communication
     */
    public JenticA2AClient getExternalClient() {
        return externalClient;
    }
}