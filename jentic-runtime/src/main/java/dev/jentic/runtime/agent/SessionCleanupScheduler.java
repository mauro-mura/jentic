package dev.jentic.runtime.agent;

import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.BehaviorType;
import dev.jentic.core.dialogue.Conversation;
import dev.jentic.core.dialogue.ConversationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cleans up inactive conversation sessions.
 * Conversations inactive for more than 30 minutes are canceled.
 * 
 * @since 0.7.0
 */
@JenticAgent(value = "session-cleanup-scheduler", 
             capabilities = {"session-management"})
public class SessionCleanupScheduler extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(30);
    
    private final ConversationManager conversationManager;
    
    public SessionCleanupScheduler(ConversationManager conversationManager) {
        super("session-cleanup", "Session Cleanup Scheduler");
        this.conversationManager = conversationManager;
    }
    
    @Override
    protected void onStart() {
        log.info("SessionCleanupScheduler started (timeout: {})", INACTIVITY_TIMEOUT);
    }
    
    /**
     * Runs every 5 minutes to clean up inactive sessions.
     */
    @JenticBehavior(type = BehaviorType.CYCLIC, interval = "5m")
    public void cleanupInactiveSessions() {
        Instant now = Instant.now();
        List<Conversation> activeConversations = conversationManager.getActiveConversations();
        
        int cleanedCount = 0;
        
        for (Conversation conversation : activeConversations) {
            Instant lastActivity = conversation.getLastActivity();
            Duration inactivityDuration = Duration.between(lastActivity, now);
            
            if (inactivityDuration.compareTo(INACTIVITY_TIMEOUT) > 0) {
                log.info("Cancelling inactive conversation {} (last activity: {}, inactive for: {})",
                    conversation.getId(), 
                    lastActivity,
                    inactivityDuration);
                
                conversationManager.cancel(conversation.getId());
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} inactive conversation(s)", cleanedCount);
        } else {
            log.debug("No inactive conversations to clean up (active: {})", 
                activeConversations.size());
        }
    }
}
