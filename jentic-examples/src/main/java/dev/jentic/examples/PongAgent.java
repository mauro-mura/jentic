package dev.jentic.examples;

import dev.jentic.core.Message;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.runtime.agent.BaseAgent;

/**
 * Example agent that responds to ping messages with pong messages.
 */
@JenticAgent(value = "pong-agent", 
             type = "example", 
             capabilities = {"pong", "messaging"},
             autoStart = true)
public class PongAgent extends BaseAgent {
    
    private int pingReceived = 0;
    private int pongSent = 0;
    
    public PongAgent() {
        super("pong-agent", "Pong Agent");
    }
    
    @JenticMessageHandler("ping")
    public void handlePing(Message message) {
        pingReceived++;
        String pingNumber = message.headers().get("ping-number");
        
        log.info("Received: {} (Ping #{}, Total pings received: {})", 
                message.content(), pingNumber, pingReceived);
        
        // Send pong response
        sendPong(message.senderId(), pingNumber);
    }
    
    private void sendPong(String originalSender, String pingNumber) {
        pongSent++;
        
        Message pongMessage = Message.builder()
            .topic("pong")
            .senderId(getAgentId())
            .receiverId(originalSender)
            .content("Pong #" + pongSent + " from " + getAgentName() + 
                    " (responding to ping #" + pingNumber + ")")
            .header("pong-number", String.valueOf(pongSent))
            .header("original-ping", pingNumber)
            .build();
        
        log.info("Sending: {}", pongMessage.content());
        messageService.send(pongMessage);
    }
    
    @Override
    protected void onStart() {
        log.info("Pong Agent is ready to respond to pings!");
    }
    
    @Override
    protected void onStop() {
        log.info("Pong Agent stopped. Received {} pings, sent {} pongs", 
                pingReceived, pongSent);
    }
}