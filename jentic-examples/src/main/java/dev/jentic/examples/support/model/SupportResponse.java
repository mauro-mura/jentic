package dev.jentic.examples.support.model;

import java.util.List;

/**
 * Response from the support chatbot.
 */
public record SupportResponse(
    String sessionId,
    String text,
    SupportIntent handledBy,
    double confidence,
    List<String> suggestedActions,
    boolean requiresEscalation
) {
    public static SupportResponse simple(String sessionId, String text, SupportIntent handler) {
        return new SupportResponse(sessionId, text, handler, 1.0, List.of(), false);
    }
    
    public static SupportResponse withConfidence(String sessionId, String text, 
            SupportIntent handler, double confidence) {
        return new SupportResponse(sessionId, text, handler, confidence, List.of(), false);
    }
    
    public static SupportResponse escalate(String sessionId, String reason) {
        return new SupportResponse(sessionId, reason, SupportIntent.ESCALATE, 1.0, List.of(), true);
    }
}
