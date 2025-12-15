package dev.jentic.examples.dialogue;

import dev.jentic.core.Agent;
import dev.jentic.core.Behavior;
import dev.jentic.core.MessageService;
import dev.jentic.core.dialogue.DialogueHandler;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.runtime.dialogue.DialogueCapability;
import dev.jentic.runtime.messaging.InMemoryMessageService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runnable example: Query Protocol (Knowledge Base).
 * 
 * <p>Run with:
 * <pre>
 * mvn exec:java -pl jentic-examples \
 *     -Dexec.mainClass="dev.jentic.examples.dialogue.QueryProtocolExample"
 * </pre>
 */
public class QueryProtocolExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║      QUERY PROTOCOL EXAMPLE - Knowledge Retrieval        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");
        
        // Shared message service
        InMemoryMessageService messageService = new InMemoryMessageService();
        
        // Create agents
        KnowledgeBase kb = new KnowledgeBase(messageService);
        QueryClient client = new QueryClient(messageService);
        
        // Start agents
        kb.start().join();
        client.start().join();
        Thread.sleep(100);
        
        // === Queries ===
        String[] queries = {
            "capital:France",
            "capital:Japan",
            "population:Germany",
            "unknown:xyz",
            "status"
        };
        
        for (int i = 0; i < queries.length; i++) {
            String q = queries[i];
            System.out.println("┌─────────────────────────────────────────────────────────┐");
            System.out.printf("│ Query %d: %-46s │%n", i + 1, q);
            System.out.println("└─────────────────────────────────────────────────────────┘");
            
            DialogueMessage r = client.ask(q).get(5, TimeUnit.SECONDS);
            System.out.println("Response: " + r.performative() + " - " + r.content() + "\n");
        }
        
        // Cleanup
        client.stop().join();
        kb.stop().join();
        
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                    EXAMPLE COMPLETE                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }
    
    // =========================================================================
    // KNOWLEDGE BASE AGENT
    // =========================================================================
    
    static class KnowledgeBase implements Agent {
        
        private final MessageService messageService;
        private final DialogueCapability dialogue = new DialogueCapability(this);
        private boolean running;
        
        // Simple KB
        private final Map<String, String> facts = Map.of(
            "capital:France", "Paris",
            "capital:Germany", "Berlin",
            "capital:Japan", "Tokyo",
            "capital:Italy", "Rome",
            "population:Japan", "125 million",
            "population:Germany", "83 million"
        );
        
        KnowledgeBase(MessageService ms) { this.messageService = ms; }
        
        @Override public String getAgentId() { return "kb"; }
        @Override public String getAgentName() { return "Knowledge Base"; }
        @Override public boolean isRunning() { return running; }
        @Override public void addBehavior(Behavior b) {}
        @Override public void removeBehavior(String id) {}
        @Override public MessageService getMessageService() { return messageService; }
        
        @Override
        public CompletableFuture<Void> start() {
            return CompletableFuture.runAsync(() -> {
                dialogue.initialize(messageService);
                running = true;
                System.out.println("[KB] Started with " + facts.size() + " facts");
            });
        }
        
        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.runAsync(() -> {
                dialogue.shutdown(messageService);
                running = false;
            });
        }
        
        @DialogueHandler(performatives = Performative.QUERY)
        public void handleQuery(DialogueMessage msg) {
            String query = msg.content() != null ? msg.content().toString() : "";
            System.out.println("[KB] Query: " + query);
            
            if ("status".equals(query)) {
                dialogue.inform(msg, "Online. Facts: " + facts.size());
                return;
            }
            
            String answer = facts.get(query);
            if (answer != null) {
                System.out.println("[KB] Found: " + answer);
                dialogue.inform(msg, answer);
            } else {
                System.out.println("[KB] Not found");
                dialogue.refuse(msg, "Unknown: " + query);
            }
        }
    }
    
    // =========================================================================
    // QUERY CLIENT
    // =========================================================================
    
    static class QueryClient implements Agent {
        
        private final MessageService messageService;
        private final DialogueCapability dialogue = new DialogueCapability(this);
        private boolean running;
        
        QueryClient(MessageService ms) { this.messageService = ms; }
        
        @Override public String getAgentId() { return "client"; }
        @Override public String getAgentName() { return "Client"; }
        @Override public boolean isRunning() { return running; }
        @Override public void addBehavior(Behavior b) {}
        @Override public void removeBehavior(String id) {}
        @Override public MessageService getMessageService() { return messageService; }
        
        @Override
        public CompletableFuture<Void> start() {
            return CompletableFuture.runAsync(() -> {
                dialogue.initialize(messageService);
                running = true;
                System.out.println("[Client] Started\n");
            });
        }
        
        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.runAsync(() -> {
                dialogue.shutdown(messageService);
                running = false;
            });
        }
        
        CompletableFuture<DialogueMessage> ask(String query) {
            System.out.println("[Client] Asking: " + query);
            return dialogue.query("kb", query, Duration.ofSeconds(5));
        }
    }
}