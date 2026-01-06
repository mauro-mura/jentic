package dev.jentic.examples.support.agents;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.examples.support.knowledge.KnowledgeDocument;
import dev.jentic.examples.support.knowledge.KnowledgeStore;
import dev.jentic.examples.support.llm.LLMResponseGenerator;
import dev.jentic.examples.support.model.SupportIntent;
import dev.jentic.examples.support.model.SupportQuery;
import dev.jentic.examples.support.model.SupportResponse;
import dev.jentic.runtime.agent.BaseAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles FAQ queries using the knowledge base.
 * Supports LLM-enhanced responses (RAG pattern) when available.
 */
@JenticAgent(
    value = "faq-agent",
    type = "handler",
    capabilities = {"knowledge-retrieval", "faq", "rag"}
)
public class FAQAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(FAQAgent.class);
    private static final int TOP_K = 3;
    private static final double MIN_CONFIDENCE = 0.15;
    
    private final KnowledgeStore knowledgeStore;
    private final LLMResponseGenerator llmGenerator;
    
    /**
     * Constructor without LLM (template-based responses).
     */
    public FAQAgent(KnowledgeStore knowledgeStore) {
        this(knowledgeStore, null);
    }
    
    /**
     * Constructor with optional LLM support.
     */
    public FAQAgent(KnowledgeStore knowledgeStore, LLMResponseGenerator llmGenerator) {
        super("faq-agent", "FAQ Handler");
        this.knowledgeStore = knowledgeStore;
        this.llmGenerator = llmGenerator;
    }
    
    @Override
    protected void onStart() {
        // Subscribe to FAQ topic
        messageService.subscribe("support.faq", MessageHandler.sync(this::handleFAQQuery));
        
        // Also handle unclassified queries
        messageService.subscribe("support.unknown", MessageHandler.sync(this::handleFAQQuery));
        
        String llmStatus = llmGenerator != null && llmGenerator.isLLMEnabled() 
            ? "LLM enabled" : "template mode";
        log.info("FAQ Agent started with {} documents ({})", knowledgeStore.size(), llmStatus);
    }
    
    @Override
    protected void onStop() {
        log.info("FAQ Agent stopped");
    }
    
    /**
     * Handles incoming FAQ queries.
     */
    private void handleFAQQuery(Message message) {
        SupportQuery query = extractQuery(message);
        String queryText = query.text();
        
        log.debug("FAQ query: '{}'", queryText);
        
        // Handle greetings and short queries
        if (isGreeting(queryText)) {
            sendResponse(message, createWelcomeResponse(query));
            return;
        }
        
        // Search knowledge base
        List<KnowledgeDocument> matches = knowledgeStore.search(queryText, TOP_K);
        
        SupportResponse response;
        if (matches.isEmpty()) {
            response = createNoMatchResponse(query);
        } else {
            KnowledgeDocument bestMatch = matches.get(0);
            double confidence = bestMatch.relevanceScore(queryText);
            
            if (confidence < MIN_CONFIDENCE) {
                response = createLowConfidenceResponse(query, matches);
            } else {
                response = createResponse(query, bestMatch, confidence, matches);
            }
        }
        
        sendResponse(message, response);
    }
    
    /**
     * Checks if query is a greeting or very short.
     */
    private boolean isGreeting(String text) {
        if (text == null) return true;
        String lower = text.toLowerCase().trim();
        if (lower.length() < 4) return true;
        return lower.matches("^(hi|hey|hello|help|yo|sup|ciao|hola|ola)\\s*[!?.]*$");
    }
    
    /**
     * Creates a welcome response for greetings.
     */
    private SupportResponse createWelcomeResponse(SupportQuery query) {
        String text = """
            👋 **Welcome to FinanceCloud Support!**
            
            I'm here to help you with:
            • **Account** - Balance, profile, linked accounts
            • **Transactions** - History, exports, disputes
            • **Security** - Password, 2FA, devices
            • **Budgets** - Create and track spending limits
            
            How can I assist you today?
            """;
        
        return new SupportResponse(query.sessionId(), text, SupportIntent.FAQ,
            1.0, List.of("View my balance", "Reset password", "Recent transactions"), false);
    }
    
    /**
     * Creates response from matched document.
     * Uses LLM if available, otherwise falls back to template.
     */
    private SupportResponse createResponse(SupportQuery query, KnowledgeDocument match,
            double confidence, List<KnowledgeDocument> alternatives) {
        
        String text;
        
        // Try LLM-enhanced response
        if (llmGenerator != null && llmGenerator.isLLMEnabled()) {
            try {
                text = llmGenerator.generate(query.text(), alternatives, SupportIntent.FAQ);
                log.debug("Generated LLM response for query: '{}'", query.text());
            } catch (Exception e) {
                log.warn("LLM generation failed, using template: {}", e.getMessage());
                text = createTemplateResponse(match, alternatives);
            }
        } else {
            text = createTemplateResponse(match, alternatives);
        }
        
        List<String> actions = List.of(
            "Was this helpful? (yes/no)",
            "Ask another question"
        );
        
        return new SupportResponse(
            query.sessionId(),
            text,
            SupportIntent.FAQ,
            confidence,
            actions,
            false
        );
    }
    
    /**
     * Template-based response from document.
     */
    private String createTemplateResponse(KnowledgeDocument match, List<KnowledgeDocument> alternatives) {
        StringBuilder text = new StringBuilder();
        text.append("**").append(match.title()).append("**\n\n");
        text.append(match.content());
        
        // Add related topics if available
        if (alternatives.size() > 1) {
            text.append("\n\n---\n*Related topics:* ");
            alternatives.stream()
                .skip(1)
                .limit(2)
                .forEach(doc -> text.append(doc.title()).append(" | "));
            // Remove trailing separator
            text.setLength(text.length() - 3);
        }
        
        return text.toString();
    }
    
    /**
     * Response when no documents match.
     */
    private SupportResponse createNoMatchResponse(SupportQuery query) {
        String text;
        
        // Try LLM for no-match response
        if (llmGenerator != null && llmGenerator.isLLMEnabled()) {
            try {
                text = llmGenerator.generate(query.text(), List.of(), SupportIntent.FAQ);
            } catch (Exception e) {
                text = createNoMatchTemplate();
            }
        } else {
            text = createNoMatchTemplate();
        }
        
        return new SupportResponse(
            query.sessionId(),
            text,
            SupportIntent.FAQ,
            0.0,
            List.of("Try a different question", "Speak to an agent"),
            false
        );
    }
    
    private String createNoMatchTemplate() {
        return """
            I couldn't find specific information about your question.
            
            Here's what I can help with:
            - Account setup and management
            - Security settings (password, 2FA)
            - Transaction history and exports
            - Budget creation and alerts
            - Subscription and pricing
            
            Could you rephrase your question, or type 'agent' to speak with a human?
            """;
    }
    
    /**
     * Response when matches have low confidence.
     */
    private SupportResponse createLowConfidenceResponse(SupportQuery query,
            List<KnowledgeDocument> matches) {
        
        StringBuilder text = new StringBuilder();
        text.append("I'm not sure I understood your question correctly. ");
        text.append("Did you mean one of these topics?\n\n");
        
        for (int i = 0; i < Math.min(matches.size(), 3); i++) {
            text.append("- ").append(matches.get(i).title()).append("\n");
        }
        
        text.append("\nPlease select a topic or rephrase your question.");
        
        return new SupportResponse(
            query.sessionId(),
            text.toString(),
            SupportIntent.FAQ,
            0.3,
            matches.stream().map(KnowledgeDocument::title).limit(3).toList(),
            false
        );
    }
    
    /**
     * Sends response back to the user.
     */
    private void sendResponse(Message originalMessage, SupportResponse response) {
        Message responseMsg = Message.builder()
            .topic("support.response")
            .senderId(getAgentId())
            .receiverId(originalMessage.senderId())
            .correlationId(response.sessionId())
            .content(response)
            .header("intent", response.handledBy().code())
            .header("confidence", String.valueOf(response.confidence()))
            .build();
        
        messageService.send(responseMsg);
        log.debug("Sent response for session {} with confidence {}", 
            response.sessionId(), response.confidence());
    }
    
    private SupportQuery extractQuery(Message message) {
        Object content = message.content();
        if (content instanceof SupportQuery q) {
            return q;
        }
        String text = content instanceof String s ? s : content.toString();
        return new SupportQuery(
            message.correlationId() != null ? message.correlationId() : message.id(),
            message.senderId(),
            text
        );
    }
}
