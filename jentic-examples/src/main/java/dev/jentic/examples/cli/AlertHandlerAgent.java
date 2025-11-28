package dev.jentic.examples.cli;

import java.util.concurrent.CompletableFuture;

import dev.jentic.core.Message;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.filter.TopicFilter;

@JenticAgent(
    value = "alert-agent"
)
public class AlertHandlerAgent extends BaseAgent {

    public AlertHandlerAgent() {
        super("alert-agent", "Alert Handler");
    }

    @Override
    protected void onStart() {
        // Subscribe to alert topics
        messageService.subscribe(TopicFilter.wildcard("sensor.alert.*"), message -> {
        	handleAlert(message);
        	return CompletableFuture.completedFuture(null);
        });
        log.info("Alert Handler subscribed to alert.*");
    }

    private void handleAlert(Message message) {
        String severity = message.headers().getOrDefault("severity", "INFO");
        log.warn("[ALERT][{}] {}: {}", severity, message.topic(), message.content());

        // Acknowledge the alert
        messageService.send(Message.builder()
                .topic("notification.processed")
                .senderId(getAgentId())
                .content("Processed: " + message.topic())
                .correlationId(message.id())
                .build());
    }
}