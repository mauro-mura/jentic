package dev.jentic.adapters.a2a;

import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for sending messages to external A2A agents.
 * 
 * <p>This client handles communication with agents that implement the A2A protocol,
 * converting Jentic DialogueMessages to A2A format and vice versa.
 * 
 * <p>Usage:
 * <pre>{@code
 * JenticA2AClient client = new JenticA2AClient();
 * 
 * DialogueMessage response = client
 *     .send("https://external-agent.com", dialogueMsg, "my-agent")
 *     .join();
 * }</pre>
 * 
 * @since 0.5.0
 */
public class JenticA2AClient {
    
    private static final Logger log = LoggerFactory.getLogger(JenticA2AClient.class);
    
    private final HttpClient httpClient;
    private final DialogueA2AConverter converter;
    private final Duration timeout;
    private final Map<String, AgentCardCache> agentCardCache;
    
    public JenticA2AClient() {
        this(Duration.ofSeconds(30));
    }
    
    public JenticA2AClient(Duration timeout) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        this.converter = new DialogueA2AConverter();
        this.timeout = timeout;
        this.agentCardCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Sends a DialogueMessage to an external A2A agent.
     * 
     * @param agentUrl the A2A agent endpoint URL
     * @param msg the message to send
     * @param localAgentId the local agent's ID (for response routing)
     * @return the response as DialogueMessage
     */
    public CompletableFuture<DialogueMessage> send(
            String agentUrl,
            DialogueMessage msg,
            String localAgentId) {
        
        log.debug("Sending to external A2A agent {}: {}", agentUrl, msg.performative());
        
        // Convert to A2A format
        DialogueA2AConverter.A2AMessage a2aMsg = converter.toA2AMessage(msg);
        
        // Build JSON-RPC request
        String jsonRpc = buildJsonRpcRequest(a2aMsg);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(agentUrl))
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(jsonRpc))
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response.body(), msg.conversationId(), localAgentId);
                } else {
                    log.error("A2A request failed with status {}: {}", 
                        response.statusCode(), response.body());
                    return createErrorResponse(msg, "HTTP " + response.statusCode());
                }
            })
            .exceptionally(ex -> {
                log.error("Error sending to A2A agent {}: {}", agentUrl, ex.getMessage());
                return createErrorResponse(msg, ex.getMessage());
            });
    }
    
    /**
     * Fetches the Agent Card from an A2A endpoint.
     * 
     * @param agentUrl the agent's base URL
     * @return the agent card information
     */
    public CompletableFuture<AgentCard> fetchAgentCard(String agentUrl) {
        // Check cache first
        AgentCardCache cached = agentCardCache.get(agentUrl);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.card);
        }
        
        String cardUrl = agentUrl.endsWith("/") 
            ? agentUrl + ".well-known/agent.json"
            : agentUrl + "/.well-known/agent.json";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(cardUrl))
            .timeout(timeout)
            .GET()
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    AgentCard card = parseAgentCard(response.body());
                    agentCardCache.put(agentUrl, new AgentCardCache(card));
                    return card;
                }
                throw new RuntimeException("Failed to fetch agent card: " + response.statusCode());
            });
    }
    
    /**
     * Checks if an A2A agent is available.
     * 
     * @param agentUrl the agent URL to check
     * @return true if agent responds
     */
    public CompletableFuture<Boolean> ping(String agentUrl) {
        return fetchAgentCard(agentUrl)
            .thenApply(card -> true)
            .exceptionally(ex -> false);
    }
    
    private String buildJsonRpcRequest(DialogueA2AConverter.A2AMessage msg) {
        // Simplified JSON-RPC 2.0 request
        // In production, use proper JSON serialization (Jackson)
        return String.format("""
            {
                "jsonrpc": "2.0",
                "id": "%s",
                "method": "message/send",
                "params": {
                    "message": {
                        "messageId": "%s",
                        "role": "%s",
                        "parts": [{"type": "text", "text": "%s"}]
                    },
                    "configuration": {
                        "acceptedOutputModes": ["text"]
                    }
                }
            }
            """,
            UUID.randomUUID().toString(),
            msg.messageId(),
            msg.role(),
            escapeJson(msg.content())
        );
    }
    
    private DialogueMessage parseResponse(String jsonBody, String conversationId, String localAgentId) {
        // Simplified parsing - in production use Jackson
        // Extract text content from JSON-RPC response
        String content = extractContentFromJson(jsonBody);
        String status = extractStatusFromJson(jsonBody);
        
        Performative performative = switch (status) {
            case "completed" -> Performative.INFORM;
            case "failed" -> Performative.FAILURE;
            case "canceled" -> Performative.CANCEL;
            default -> Performative.INFORM;
        };
        
        return DialogueMessage.builder()
            .conversationId(conversationId)
            .senderId("external-agent")
            .receiverId(localAgentId)
            .performative(performative)
            .content(content)
            .build();
    }
    
    private DialogueMessage createErrorResponse(DialogueMessage original, String error) {
        return DialogueMessage.builder()
            .conversationId(original.conversationId())
            .senderId("external-agent")
            .receiverId(original.senderId())
            .performative(Performative.FAILURE)
            .content("A2A communication error: " + error)
            .inReplyTo(original.id())
            .build();
    }
    
    private AgentCard parseAgentCard(String json) {
        // Simplified parsing - extract key fields
        String name = extractField(json, "name");
        String description = extractField(json, "description");
        String url = extractField(json, "url");
        String version = extractField(json, "version");
        
        return new AgentCard(name, description, url, version);
    }
    
    private String extractContentFromJson(String json) {
        // Simple extraction - look for text content
        int textIdx = json.indexOf("\"text\":");
        if (textIdx > 0) {
            int start = json.indexOf("\"", textIdx + 7) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end);
            }
        }
        return "";
    }
    
    private String extractStatusFromJson(String json) {
        int statusIdx = json.indexOf("\"state\":");
        if (statusIdx > 0) {
            int start = json.indexOf("\"", statusIdx + 8) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end);
            }
        }
        return "completed";
    }
    
    private String extractField(String json, String field) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx > 0) {
            int start = json.indexOf("\"", idx + pattern.length()) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end);
            }
        }
        return "";
    }
    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    /**
     * A2A Agent Card representation.
     */
    public record AgentCard(
        String name,
        String description,
        String url,
        String version
    ) {}
    
    /**
     * Cache entry for agent cards.
     */
    private static class AgentCardCache {
        final AgentCard card;
        final long timestamp;
        static final long TTL_MS = 5 * 60 * 1000; // 5 minutes
        
        AgentCardCache(AgentCard card) {
            this.card = card;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL_MS;
        }
    }
}