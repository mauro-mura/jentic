package dev.jentic.examples.support;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.examples.support.context.ConversationContextManager;
import dev.jentic.examples.support.knowledge.KnowledgeStore;
import dev.jentic.examples.support.knowledge.SupportKnowledgeData;
import dev.jentic.examples.support.model.SupportResponse;
import dev.jentic.examples.support.service.MockUserDataService;
import dev.jentic.runtime.JenticRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FinanceCloud Support Chatbot Example - Phase 1
 * 
 * Demonstrates a multi-agent support system with:
 * - RouterAgent: classifies intent and routes queries
 * - FAQAgent: answers using knowledge base retrieval
 * 
 * Run this example and interact via console.
 */
public class SupportChatbotExample {
    
    private static final Logger log = LoggerFactory.getLogger(SupportChatbotExample.class);
    
    public static void main(String[] args) throws Exception {
        log.info("=== FinanceCloud Support Chatbot ===");
        
        // Initialize services
        KnowledgeStore knowledgeStore = SupportKnowledgeData.createPopulatedStore();
        log.info("Loaded {} FAQ documents", knowledgeStore.size());
        
        MockUserDataService dataService = new MockUserDataService();
        log.info("Mock user data service initialized");
        
        ConversationContextManager contextManager = new ConversationContextManager();
        log.info("Conversation context manager initialized");
        
        // Create runtime with package scanning and service injection
        JenticRuntime runtime = JenticRuntime.builder()
            // Register services for dependency injection
            .service(KnowledgeStore.class, knowledgeStore)
            .service(MockUserDataService.class, dataService)
            .service(ConversationContextManager.class, contextManager)
            // Scan package for @JenticAgent annotated classes
            .scanPackage("dev.jentic.examples.support.agents")
            .build();
        
        // Start runtime (agents discovered and created automatically)
        runtime.start().join();
        log.info("Runtime started with {} agents", runtime.getAgents().size());
        
        // Setup response handler
        AtomicReference<SupportResponse> lastResponse = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        
        runtime.getMessageService().subscribe("support.response", MessageHandler.sync(msg -> {
            Object content = msg.content();
            if (content instanceof SupportResponse response) {
                lastResponse.set(response);
                printResponse(response);
            } else {
                log.info("Response: {}", content);
            }
            responseLatch.countDown();
        }));
        
        // Interactive mode
        if (args.length == 0) {
            runInteractiveMode(runtime);
        } else {
            // Demo mode with sample queries
            runDemoMode(runtime, lastResponse);
        }
        
        // Shutdown
        log.info("Shutting down...");
        contextManager.shutdown();
        runtime.stop().join();
        log.info("=== Example completed ===");
    }
    
    /**
     * Interactive console mode.
     */
    private static void runInteractiveMode(JenticRuntime runtime) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  FinanceCloud Support Chat                                 ║");
        System.out.println("║  Type your question or 'quit' to exit                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        while (true) {
            System.out.print("You: ");
            String input = reader.readLine();
            
            if (input == null || input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                break;
            }
            
            if (input.isBlank()) {
                continue;
            }
            
            // Send query
            Message query = Message.builder()
                .topic("support.query")
                .senderId("user-console")
                .correlationId(sessionId)
                .content(input)
                .build();
            
            runtime.getMessageService().send(query);
            
            // Wait for response (simple approach - in production use CompletableFuture)
            Thread.sleep(500);
        }
    }
    
    /**
     * Demo mode with predefined queries.
     */
    private static void runDemoMode(JenticRuntime runtime, 
            AtomicReference<SupportResponse> lastResponse) throws Exception {
        
        String[] demoQueries = {
            // Greeting
            "hi",
            // FAQ queries
            "What banks do you support?",
            // Account queries
            "What's my account balance?",
            // Transaction queries
            "Show my recent transactions",
            // Security queries  
            "Show my trusted devices",
            // Budget queries
            "Show my budgets",
            // Escalation test
            "I need to speak to a human agent",
            // Sentiment test (frustrated)
            "This is frustrating! Nothing is working!!"
        };
        
        String sessionId = "demo-session";
        
        System.out.println("\n=== Demo Mode: Sample Queries ===\n");
        
        for (String query : demoQueries) {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Query: " + query);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            
            Message msg = Message.builder()
                .topic("support.query")
                .senderId("demo-user")
                .correlationId(sessionId)
                .content(query)
                .build();
            
            runtime.getMessageService().send(msg);
            
            // Wait for response
            Thread.sleep(1000);
            System.out.println();
        }
    }
    
    /**
     * Prints response in a formatted way.
     */
    private static void printResponse(SupportResponse response) {
        System.out.println("\n┌─ Bot (" + response.handledBy().code() + 
            ", confidence: " + String.format("%.0f%%", response.confidence() * 100) + ") ─┐");
        System.out.println(response.text());
        
        if (!response.suggestedActions().isEmpty()) {
            System.out.println("\n[Suggested: " + String.join(" | ", response.suggestedActions()) + "]");
        }
        System.out.println("└────────────────────────────────────────────┘\n");
    }
}
