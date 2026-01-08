package dev.jentic.examples.support;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import dev.jentic.examples.support.a2a.A2AHttpServer;
import dev.jentic.examples.support.context.ConversationContextManager;
import dev.jentic.examples.support.knowledge.EmbeddingConfig;
import dev.jentic.examples.support.knowledge.HybridKnowledgeStore;
import dev.jentic.examples.support.knowledge.KnowledgeStore;
import dev.jentic.examples.support.knowledge.QueryExpander;
import dev.jentic.examples.support.knowledge.SupportKnowledgeData;
import dev.jentic.examples.support.llm.LLMConfig;
import dev.jentic.examples.support.llm.LLMResponseGenerator;
import dev.jentic.examples.support.model.SupportResponse;
import dev.jentic.examples.support.production.*;
import dev.jentic.examples.support.production.LanguageDetector.Language;
import dev.jentic.examples.support.service.MockUserDataService;
import dev.jentic.runtime.JenticRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FinanceCloud Support Chatbot Example - Phase 7
 * 
 * Demonstrates a multi-agent support system with:
 * - RouterAgent: classifies intent with sentiment analysis
 * - FAQAgent: answers using RAG (knowledge base + LLM)
 * - Specialized agents: Account, Transaction, Security, Budget
 * - EscalationAgent: human handoff
 * - Production features: persistence, analytics, localization, rate limiting
 * - A2A protocol support for external agent communication
 * 
 * LLM support:
 * - Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or OLLAMA_BASE_URL env var
 * - Falls back to template-based responses if no LLM configured
 * 
 * Run modes:
 * - Interactive: mvn exec:java (default)
 * - Demo: mvn exec:java -Dexec.args="demo"
 * - A2A Server: mvn exec:java -Dexec.args="--a2a [port]"
 */
public class SupportChatbotExample {
    
    private static final Logger log = LoggerFactory.getLogger(SupportChatbotExample.class);
    
    public static void main(String[] args) throws Exception {
        log.info("=== FinanceCloud Support Chatbot ===");
        
        // ========== PRODUCTION SERVICES ==========
        
        // Persistence (file-based storage)
        Path storagePath = Path.of(System.getProperty("user.home"), ".financecloud", "conversations");
        ConversationRepository conversationRepo = new ConversationRepository(storagePath);
        log.info("Conversation repository initialized at {}", storagePath);
        
        // Analytics
        AnalyticsService analytics = new AnalyticsService();
        log.info("Analytics service initialized");
        
        // Multi-language support
        LanguageDetector languageDetector = new LanguageDetector(Language.ENGLISH);
        LocalizationService localization = new LocalizationService(Language.ENGLISH);
        log.info("Localization service initialized ({} languages)", localization.getSupportedLanguages().size());
        
        // Rate limiting (60 req/min, burst 10/sec)
        RateLimiter rateLimiter = new RateLimiter();
        log.info("Rate limiter initialized");
        
        // ========== KNOWLEDGE & SEARCH ==========
        
        // Initialize embedding configuration
        EmbeddingConfig embeddingConfig = EmbeddingConfig.fromEnvironment();
        
        // Initialize knowledge store with hybrid search (TF-IDF + embeddings)
        HybridKnowledgeStore knowledgeStore = SupportKnowledgeData.createHybridStore(embeddingConfig);
        
        if (knowledgeStore.isEmbeddingsEnabled()) {
            log.info("Loaded {} FAQ documents with hybrid search (TF-IDF + embeddings)", 
                knowledgeStore.size());
        } else {
            log.info("Loaded {} FAQ documents with TF-IDF search (embeddings not configured)", 
                knowledgeStore.size());
        }
        
        // Initialize query expander for synonym support
        QueryExpander queryExpander = new QueryExpander();
        log.info("Query expander initialized with domain synonyms");
        
        MockUserDataService dataService = new MockUserDataService();
        log.info("Mock user data service initialized");
        
        ConversationContextManager contextManager = new ConversationContextManager();
        log.info("Conversation context manager initialized");
        
        // ========== LLM ==========
        
        // Initialize LLM (from environment or fallback to template mode)
        LLMConfig llmConfig = LLMConfig.fromEnvironment();
        LLMResponseGenerator llmGenerator = new LLMResponseGenerator(llmConfig);
        
        if (llmGenerator.isLLMEnabled()) {
            log.info("LLM enabled: {} mode", llmConfig.getProviderType());
        } else {
            log.info("LLM not configured - using template-based responses");
        }
        
        // ========== RUNTIME ==========
        
        // Create runtime with package scanning and service injection
        JenticRuntime runtime = JenticRuntime.builder()
            // Core services
            .service(KnowledgeStore.class, knowledgeStore)
            .service(QueryExpander.class, queryExpander)
            .service(MockUserDataService.class, dataService)
            .service(ConversationContextManager.class, contextManager)
            .service(LLMResponseGenerator.class, llmGenerator)
            // Production services
            .service(ConversationRepository.class, conversationRepo)
            .service(AnalyticsService.class, analytics)
            .service(LanguageDetector.class, languageDetector)
            .service(LocalizationService.class, localization)
            .service(RateLimiter.class, rateLimiter)
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
        
        // Check for A2A server mode
        boolean a2aMode = args.length > 0 && args[0].equals("--a2a");
        int a2aPort = 8081; // Default A2A port (separate from console)
        if (a2aMode && args.length > 1) {
            a2aPort = Integer.parseInt(args[1]);
        }
        
        A2AHttpServer a2aServer = null;
        if (a2aMode) {
            // Start A2A HTTP server
            a2aServer = A2AHttpServer.builder()
                .port(a2aPort)
                .messageService(runtime.getMessageService())
                .build();
            a2aServer.start().join();
        }
        
        // Interactive mode
        if (args.length == 0) {
            runInteractiveMode(runtime);
        } else if (!a2aMode) {
            // Demo mode with sample queries
            runDemoMode(runtime, lastResponse);
        } else {
            // A2A server mode - run interactive alongside
            log.info("A2A Server running on port {}. Starting interactive mode...", a2aPort);
            runInteractiveMode(runtime);
        }
        
        // Shutdown
        log.info("Shutting down...");
        
        // Stop A2A server if running
        if (a2aServer != null) {
            a2aServer.stop().join();
        }
        
        // Print analytics report
        System.out.println("\n" + analytics.generateReport());
        
        contextManager.shutdown();
        rateLimiter.shutdown();
        runtime.stop().join();
        log.info("=== Example completed ===");
    }
    
    /**
     * Interactive console mode with production features.
     */
    private static void runInteractiveMode(JenticRuntime runtime) throws Exception {
        // Get services from runtime (in real app, these would be injected)
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  FinanceCloud Support Chat                                 ║");
        System.out.println("║  Commands: 'quit', 'stats'                                 ║");
        System.out.println("║  Run with --a2a [port] to start A2A server                 ║");
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
            
            // Special commands
            if (input.equalsIgnoreCase("stats")) {
                // Would print analytics in real implementation
                System.out.println("Bot: Use 'quit' to see analytics report on exit.\n");
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
