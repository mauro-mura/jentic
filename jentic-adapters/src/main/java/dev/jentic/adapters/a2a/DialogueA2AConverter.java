package dev.jentic.adapters.a2a;

import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;

import java.util.Map;

/**
 * Converts between Jentic DialogueMessage and A2A Message/Task formats.
 * 
 * <p>A2A protocol uses a different message structure than Jentic's dialogue layer.
 * This converter handles bidirectional conversion while preserving semantic intent.
 * 
 * @since 0.5.0
 */
public class DialogueA2AConverter {
    
    /**
     * Converts a DialogueMessage to A2A message format.
     * 
     * @param msg the dialogue message
     * @return A2A message representation
     */
    public A2AMessage toA2AMessage(DialogueMessage msg) {
        return new A2AMessage(
            msg.id(),
            msg.conversationId(),
            extractTextContent(msg.content()),
            mapPerformativeToRole(msg.performative()),
            Map.of(
                "performative", msg.performative().name(),
                "protocol", msg.protocol() != null ? msg.protocol() : "",
                "senderId", msg.senderId(),
                "receiverId", msg.receiverId() != null ? msg.receiverId() : ""
            )
        );
    }
    
    /**
     * Converts an A2A message to DialogueMessage.
     * 
     * @param a2aMsg the A2A message
     * @param localAgentId the local agent receiving the message
     * @return DialogueMessage representation
     */
    public DialogueMessage fromA2AMessage(A2AMessage a2aMsg, String localAgentId) {
        Performative performative = inferPerformative(a2aMsg);
        
        return DialogueMessage.builder()
            .id(a2aMsg.messageId())
            .conversationId(a2aMsg.contextId())
            .senderId(extractSenderId(a2aMsg))
            .receiverId(localAgentId)
            .performative(performative)
            .content(a2aMsg.content())
            .build();
    }
    
    /**
     * Converts a DialogueMessage response to A2A response format.
     * 
     * @param response the dialogue response
     * @return A2A response representation
     */
    public A2AResponse toA2AResponse(DialogueMessage response) {
        return new A2AResponse(
            response.id(),
            response.conversationId(),
            extractTextContent(response.content()),
            mapPerformativeToStatus(response.performative()),
            response.performative() == Performative.FAILURE
        );
    }
    
    /**
     * Infers Performative from A2A message context.
     * A2A doesn't have explicit performatives, so we infer from context.
     */
    private Performative inferPerformative(A2AMessage a2aMsg) {
        // Check metadata for explicit performative
        if (a2aMsg.metadata() != null) {
            String perfStr = a2aMsg.metadata().get("performative");
            if (perfStr != null && !perfStr.isEmpty()) {
                try {
                    return Performative.valueOf(perfStr);
                } catch (IllegalArgumentException e) {
                    // Fall through to inference
                }
            }
        }
        
        // Infer from role
        return switch (a2aMsg.role()) {
            case "user" -> Performative.REQUEST;
            case "assistant" -> Performative.INFORM;
            case "system" -> Performative.NOTIFY;
            default -> Performative.REQUEST;
        };
    }
    
    private String mapPerformativeToRole(Performative performative) {
        return switch (performative) {
            case REQUEST, QUERY, CFP -> "user";
            case INFORM, AGREE, PROPOSE -> "assistant";
            case REFUSE, FAILURE, CANCEL -> "assistant";
            case NOTIFY -> "system";
        };
    }
    
    private String mapPerformativeToStatus(Performative performative) {
        return switch (performative) {
            case INFORM -> "completed";
            case AGREE -> "working";
            case FAILURE -> "failed";
            case REFUSE -> "rejected";
            case CANCEL -> "canceled";
            default -> "completed";
        };
    }
    
    private String extractSenderId(A2AMessage a2aMsg) {
        if (a2aMsg.metadata() != null && a2aMsg.metadata().containsKey("senderId")) {
            return a2aMsg.metadata().get("senderId");
        }
        return "external-agent";
    }
    
    private String extractTextContent(Object content) {
        if (content == null) {
            return "";
        }
        return content.toString();
    }
    
    /**
     * A2A Message representation (simplified).
     */
    public record A2AMessage(
        String messageId,
        String contextId,
        String content,
        String role,
        Map<String, String> metadata
    ) {}
    
    /**
     * A2A Response representation (simplified).
     */
    public record A2AResponse(
        String messageId,
        String contextId,
        String content,
        String status,
        boolean isError
    ) {}
}