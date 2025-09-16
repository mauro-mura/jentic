package dev.jentic.examples;

import dev.jentic.core.Message;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.runtime.agent.BaseAgent;

import static dev.jentic.core.BehaviorType.CYCLIC;

/**
 * Example agent that sends ping messages and responds to pong messages.
 */
@JenticAgent(value = "ping-agent", 
             type = "example", 
             capabilities = {"ping", "messaging"},
             autoStart = true)
public class PingAgent extends BaseAgent {
    
    private int pingCount = 0;
    private int pongReceived = 0;
    
    public PingAgent() {
        super("ping-agent", "Ping Agent");
    }
    
    @JenticBehavior(type = CYCLIC, interval = "3s", autoStart = true)
    public void sendPing() {
        pingCount++;
        
        Message pingMessage = Message.builder()
            .topic("ping")
            .senderId(getAgentId())
            .content("Ping #" + pingCount + " from " + getAgentName())
            .header("ping-number", String.valueOf(pingCount))
            .build();
        
        log.info("Sending: {}", pingMessage.content());
        messageService.send(pingMessage);
    }
    
    @JenticMessageHandler("pong")
    public void handlePong(Message message) {
        pongReceived++;
        log.info("Received: {} (Total pongs received: {})", 
                message.content(), pongReceived);
        
        // Stop after receiving 5 pongs
        if (pongReceived >= 5) {
            log.info("Received 5 pongs, stopping ping agent");
            stop();
        }
    }
    
    @Override
    protected void onStart() {
        log.info("Ping Agent is ready to start pinging!");
    }
    
    @Override
    protected void onStop() {
        log.info("Ping Agent stopped. Sent {} pings, received {} pongs", 
                pingCount, pongReceived);
    }
}