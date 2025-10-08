package dev.jentic.examples.ecommerce;

import dev.jentic.core.Message;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.runtime.agent.BaseAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@JenticAgent(
    value = "notification-service",
    type = "Notification",
    capabilities = {"email", "sms", "push-notification"}
)
public class NotificationServiceAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceAgent.class);
    
    public NotificationServiceAgent() {
        super("notification-service");
        log.info("Notification Service Agent ready");
    }

    @JenticMessageHandler("order-notification")
    public void handleOrderNotification(Message message) {
        Map<String, String> content = message.getContent(Map.class);
        
        String orderId = content.get("orderId");
        String customerId = content.get("customerId");
        String notificationMessage = content.get("message");
        
        log.info("📧 Sending notification to customer {}: {}", customerId, notificationMessage);
        log.info("   Order ID: {}", orderId);
        
        // Simulate sending email/SMS
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("✉️  Notification sent successfully");
    }
}