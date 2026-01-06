package dev.jentic.examples.support.agents;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.examples.support.context.ConversationContext;
import dev.jentic.examples.support.context.ConversationContextManager;
import dev.jentic.examples.support.context.SentimentAnalyzer;
import dev.jentic.examples.support.context.SentimentAnalyzer.SentimentResult;
import dev.jentic.examples.support.model.SupportIntent;
import dev.jentic.examples.support.model.SupportQuery;
import dev.jentic.examples.support.model.SupportResponse;
import dev.jentic.runtime.agent.BaseAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes incoming support queries to the appropriate specialized agent.
 * Uses keyword-based intent classification with sentiment analysis
 * and conversation context tracking.
 */
@JenticAgent(
    value = "router-agent",
    type = "router",
    capabilities = {"intent-classification", "routing", "sentiment-analysis"}
)
public class RouterAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(RouterAgent.class);
    
    private final ConversationContextManager contextManager;
    private final SentimentAnalyzer sentimentAnalyzer;
    
    // Keyword mappings for intent detection
    private static final Map<SupportIntent, Set<String>> INTENT_KEYWORDS = Map.of(
        SupportIntent.ACCOUNT, Set.of(
            "account", "profile", "settings", "register", "signup", "close account", 
            "delete account", "update", "personal info", "balance", "linked"
        ),
        SupportIntent.SECURITY, Set.of(
            "password", "reset", "2fa", "two factor", "login", "locked", "unauthorized",
            "security", "pin", "biometric", "device", "logout", "trusted"
        ),
        SupportIntent.TRANSACTION, Set.of(
            "transaction", "payment", "transfer", "history", "activity", "export",
            "statement", "dispute", "charge", "refund", "purchase", "pending"
        ),
        SupportIntent.BUDGET, Set.of(
            "budget", "spending", "limit", "alert", "category", "goal", "track"
        ),
        SupportIntent.ESCALATE, Set.of(
            "human", "agent", "speak to", "talk to", "representative", "escalate",
            "complaint", "manager", "real person", "supervisor"
        )
    );
    
    public RouterAgent(ConversationContextManager contextManager) {
        super("router-agent", "Support Router");
        this.contextManager = contextManager;
        this.sentimentAnalyzer = new SentimentAnalyzer();
    }
    
    @Override
    protected void onStart() {
        // Subscribe to incoming support requests
        messageService.subscribe("support.query", MessageHandler.sync(this::handleQuery));
        
        log.info("Router Agent started - listening on support.query");
    }
    
    @Override
    protected void onStop() {
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
        
        // Analyze sentiment
        SentimentResult sentiment = sentimentAnalyzer.analyze(queryText);
        log.debug("Sentiment: {} (score: {}, frustration: {})", 
            sentiment.getSentimentLabel(), sentiment.score(), sentiment.frustrationLevel());
        
        // Classify intent
        IntentResult result = classifyIntent(queryText);
        
        // Override to escalation if sentiment strongly suggests it
        if (sentiment.suggestEscalation() && result.intent != SupportIntent.ESCALATE) {
            log.info("Sentiment suggests escalation, overriding intent from {} to ESCALATE", result.intent);
            result = new IntentResult(SupportIntent.ESCALATE, 0.9);
        }
        
        log.info("Classified intent: {} (confidence: {})", result.intent, result.confidence);
        
        // Update conversation context
        ConversationContext context = contextManager.recordUserMessage(
            sessionId, userId, queryText, result.intent, sentiment.score());
        
        // Check if we should proactively suggest escalation
        if (context.shouldSuggestEscalation() && result.intent != SupportIntent.ESCALATE) {
            // Send a suggestion but still route to the appropriate agent
            sendEscalationSuggestion(sessionId, context);
        }
        
        // Create enriched query
        SupportQuery query = new SupportQuery(sessionId, userId, queryText)
            .withIntent(result.intent)
            .withContext(Map.of(
                "sentiment", sentiment.getSentimentLabel(),
                "sentimentScore", sentiment.score(),
                "frustrationLevel", context.getFrustrationLevel(),
                "turnCount", context.getTurnCount()
            ));
        
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
            .header("sentiment", sentiment.getSentimentLabel())
            .header("frustration", String.valueOf(context.getFrustrationLevel()))
            .build();
        
        messageService.send(routedMessage);
        log.debug("Routed to topic: {}", targetTopic);
    }
    
    /**
     * Sends a proactive escalation suggestion to the user.
     */
    private void sendEscalationSuggestion(String sessionId, ConversationContext context) {
        String text = """
            💡 **Would you like to speak with a support agent?**
            
            I notice you might be having difficulty getting the help you need.
            I can connect you with a human agent who may be able to assist better.
            
            Just type "speak to agent" if you'd like that, or continue with your question.
            """;
        
        SupportResponse suggestion = new SupportResponse(
            sessionId, text, SupportIntent.FAQ, 0.8,
            List.of("Speak to agent", "Continue with bot"), false
        );
        
        Message suggestionMsg = Message.builder()
            .topic("support.response")
            .senderId(getAgentId())
            .correlationId(sessionId)
            .content(suggestion)
            .header("type", "escalation-suggestion")
            .build();
        
        messageService.send(suggestionMsg);
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
