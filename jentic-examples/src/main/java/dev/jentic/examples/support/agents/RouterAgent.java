package dev.jentic.examples.support.agents;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.core.dialogue.DialogueHandler;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.examples.support.model.SupportIntent;
import dev.jentic.examples.support.model.SupportQuery;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.dialogue.DialogueCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Routes incoming support queries to the appropriate specialized agent.
 * Uses keyword-based intent classification.
 */
@JenticAgent(
    value = "router-agent",
    type = "router",
    capabilities = {"intent-classification", "routing"}
)
public class RouterAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(RouterAgent.class);
    
    private final DialogueCapability dialogue;
    
    // Keyword mappings for intent detection
    private static final Map<SupportIntent, Set<String>> INTENT_KEYWORDS = Map.of(
        SupportIntent.ACCOUNT, Set.of(
            "account", "profile", "settings", "register", "signup", "close account", 
            "delete account", "update", "personal info"
        ),
        SupportIntent.SECURITY, Set.of(
            "password", "reset", "2fa", "two factor", "login", "locked", "unauthorized",
            "security", "pin", "biometric", "device", "logout"
        ),
        SupportIntent.TRANSACTION, Set.of(
            "transaction", "payment", "transfer", "history", "activity", "export",
            "statement", "dispute", "charge", "refund", "purchase"
        ),
        SupportIntent.BUDGET, Set.of(
            "budget", "spending", "limit", "alert", "category", "goal", "track"
        ),
        SupportIntent.ESCALATE, Set.of(
            "human", "agent", "speak to", "talk to", "representative", "escalate",
            "complaint", "manager"
        )
    );
    
    public RouterAgent() {
        super("router-agent", "Support Router");
        this.dialogue = new DialogueCapability(this);
    }
    
    @Override
    protected void onStart() {
        dialogue.initialize(getMessageService());
        
        // Subscribe to incoming support requests
        messageService.subscribe("support.query", MessageHandler.sync(this::handleQuery));
        
        log.info("Router Agent started - listening on support.query");
    }
    
    @Override
    protected void onStop() {
        dialogue.shutdown(getMessageService());
        log.info("Router Agent stopped");
    }
    
    /**
     * Handles incoming support queries from the main topic.
     */
    private void handleQuery(Message message) {
        String queryText = extractQueryText(message);
        String sessionId = message.correlationId() != null ? message.correlationId() : message.id();
        String userId = message.senderId();
        
        log.debug("Received query: '{}' from session {}", queryText, sessionId);
        
        // Classify intent
        IntentResult result = classifyIntent(queryText);
        log.info("Classified intent: {} (confidence: {})", result.intent, result.confidence);
        
        // Create enriched query
        SupportQuery query = new SupportQuery(sessionId, userId, queryText)
            .withIntent(result.intent);
        
        // Route to appropriate agent
        String targetTopic = getTargetTopic(result.intent);
        
        Message routedMessage = Message.builder()
            .topic(targetTopic)
            .senderId(getAgentId())
            .correlationId(sessionId)
            .content(query)
            .header("originalQuery", queryText)
            .header("intent", result.intent.code())
            .header("confidence", String.valueOf(result.confidence))
            .build();
        
        messageService.send(routedMessage);
        log.debug("Routed to topic: {}", targetTopic);
    }
    
    /**
     * Classifies the intent of a query using keyword matching.
     */
    private IntentResult classifyIntent(String query) {
        if (query == null || query.isBlank()) {
            return new IntentResult(SupportIntent.UNKNOWN, 0.0);
        }
        
        String lowerQuery = query.toLowerCase();
        
        SupportIntent bestIntent = SupportIntent.FAQ;
        int bestScore = 0;
        
        for (var entry : INTENT_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lowerQuery.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIntent = entry.getKey();
            }
        }
        
        // Calculate confidence
        double confidence = bestScore > 0 ? Math.min(1.0, bestScore * 0.3 + 0.4) : 0.3;
        
        return new IntentResult(bestIntent, confidence);
    }
    
    /**
     * Returns the target topic for an intent.
     */
    private String getTargetTopic(SupportIntent intent) {
        return switch (intent) {
            case ACCOUNT -> "support.account";
            case SECURITY -> "support.security";
            case TRANSACTION -> "support.transaction";
            case BUDGET -> "support.budget";
            case ESCALATE -> "support.escalate";
            default -> "support.faq";
        };
    }
    
    private String extractQueryText(Message message) {
        Object content = message.content();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof SupportQuery q) {
            return q.text();
        }
        return content != null ? content.toString() : "";
    }
    
    private record IntentResult(SupportIntent intent, double confidence) {}
}
