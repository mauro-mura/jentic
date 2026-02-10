package dev.jentic.examples.chatbot;

import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.dialogue.DialogueHandler;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.dialogue.DialogueCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent that translates messages and returns to the orchestrator.
 * <p>
 * Architecture: Does NOT forward to other agents.
 * Flow:
 * 1. Receive multilingual request from orchestrator
 * 2. Detect language
 * 3. Translate to English
 * 4. Return to orchestrator (with metadata about the original language)
 * 5. Orchestrator decides next steps
 * 
 * @since 1.0.0
 */
@JenticAgent(value = "translator-agent", 
             capabilities = {"translation", "multilingual"})
public class TranslatorAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(TranslatorAgent.class);
    
    private final DialogueCapability dialogue = new DialogueCapability(this);
    
    public TranslatorAgent() {
        super("translator-agent", "Translator Agent");
    }
    
    @Override
    protected void onStart() {
        dialogue.initialize(messageService);
        log.info("TranslatorAgent started");
    }
    
    @Override
    protected void onStop() {
        dialogue.shutdown(messageService);
        log.info("TranslatorAgent stopped");
    }
    
    @DialogueHandler(performatives = Performative.REQUEST)
    public void handleTranslationRequest(DialogueMessage msg) {
        String originalMessage = msg.content().toString();
        String conversationId = msg.conversationId();
        
        log.info("Translating message in conversation {}", conversationId);
        
        // Step 1: Detect language
        String detectedLanguage = detectLanguage(originalMessage);
        log.debug("Detected language: {}", detectedLanguage);
        
        // Step 2: Translate to English
        String translatedToEnglish = translateToEnglish(originalMessage, detectedLanguage);
        log.debug("Translated from {} to English: {}", detectedLanguage, translatedToEnglish);
        
        // Step 3: Return to orchestrator with metadata
        Map<String, Object> metadata = Map.of(
            "originalLanguage", detectedLanguage,
            "originalText", originalMessage
        );
        
        DialogueMessage response = DialogueMessage.builder()
            .conversationId(msg.conversationId())
            .senderId(getAgentId())
            .receiverId(msg.senderId())  // Reply to orchestrator
            .performative(Performative.INFORM)
            .content(translatedToEnglish)
            .metadata(metadata)
            .inReplyTo(msg.id())
            .build();
        
        messageService.send(response.toMessage());
        log.debug("Translation returned to orchestrator");
    }
    
    /**
     * Simulated language detection.
     * In production: use library like lingua or cloud API.
     */
    private String detectLanguage(String text) {
        // Simple heuristic detection
        if (text.matches(".*[àâäéèêëïîôùûüÿæœç].*")) {
            return "fr";
        } else if (text.matches(".*[áéíóúñü¿¡].*")) {
            return "es";
        } else if (text.matches(".*[äöüß].*")) {
            return "de";
        } else if (text.matches(".*[àèéìòù].*")) {
            return "it";
        }
        return "en";
    }
    
    /**
     * Simulated translation to English.
     * In production: use Google Translate API, DeepL, or LLM.
     */
    private String translateToEnglish(String text, String sourceLang) {
        if ("en".equals(sourceLang)) {
            return text;
        }
        
        // Simulate translation (placeholder)
        log.debug("Simulating translation from {} to en", sourceLang);
        
        // Simple simulation - in reality this would call translation API
        return "[Translated from " + sourceLang.toUpperCase() + " to EN]: " + text;
    }
    
    /**
     * Translates from English to target language.
     * Called by orchestrator if needed (future enhancement).
     */
    public String translateFromEnglish(String text, String targetLang) {
        if ("en".equals(targetLang)) {
            return text;
        }
        
        // Simulate translation (placeholder)
        log.debug("Simulating translation from en to {}", targetLang);
        return "[Translated from EN to " + targetLang.toUpperCase() + "]: " + text;
    }
}
