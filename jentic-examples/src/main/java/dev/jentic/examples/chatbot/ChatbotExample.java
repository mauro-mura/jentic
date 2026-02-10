package dev.jentic.examples.chatbot;

import dev.jentic.adapters.llm.LLMIntentClassifier;
import dev.jentic.adapters.llm.LLMProviderFactory;
import dev.jentic.core.AgentDirectory;
import dev.jentic.core.MessageService;
import dev.jentic.core.conversation.IntentClassifier;
import dev.jentic.core.dialogue.ConversationManager;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.memory.MemoryStore;
import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.ConversationOrchestrator;
import dev.jentic.runtime.agent.SessionCleanupScheduler;
import dev.jentic.runtime.dialogue.DefaultConversationManager;
import dev.jentic.runtime.memory.InMemoryStore;

import java.util.Scanner;
import java.util.UUID;

/**
 * Multi-user chatbot example using dialogue protocol.
 * <p>
 * Architecture:
 * - ConversationOrchestrator: Routes requests based on intent
 * - TranslatorAgent: Handles multilingual queries
 * - KnowledgeAgent: Performs RAG with conversation history
 * - SessionCleanupScheduler: Cleans up inactive sessions (30 min)
 * <p>
 * Features:
 * - Dynamic workflow routing via AgentDirectory
 * - Conversation history via dialogue protocol
 * - User profile in LONG_TERM memory
 * - Session-scoped conversation tracking
 * <p>
 * Usage:
 * mvn exec:java -pl jentic-examples \
 *     -Dexec.mainClass="dev.jentic.examples.chatbot.ChatbotExample"
 * 
 * @since 0.7.0
 */
public class ChatbotExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║     JENTIC MULTI-USER CHATBOT EXAMPLE              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // memory store
        MemoryStore memoryStore = new InMemoryStore();

        // Build runtime
        JenticRuntime runtime = JenticRuntime.builder()
                .memoryStore(memoryStore)
                .build();
        
        // Get services
        MessageService messageService = runtime.getMessageService();
        AgentDirectory agentDirectory = runtime.getAgentDirectory();
        
        // Create a conversation manager for the orchestrator
        ConversationManager orchestratorConvManager = new DefaultConversationManager(
            "conversation-orchestrator",
            messageService
        );
        
        // Create an LLM provider (you need to configure this with your provider)
        LLMProvider llmProvider = createLLMProvider();
        
        // Create intent classifier with model
        IntentClassifier intentClassifier = new LLMIntentClassifier(
            llmProvider,
            orchestratorConvManager,
            "gpt-4o-mini"  // Or specify explicitly: "claude-3-sonnet-20240229"
        );
        
        // Create and register agents
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(
            intentClassifier,
            agentDirectory
        );
        
        TranslatorAgent translator = new TranslatorAgent();
        KnowledgeAgent knowledge = new KnowledgeAgent();
        
        // Create a session cleanup scheduler
        SessionCleanupScheduler cleanup = new SessionCleanupScheduler(
            orchestratorConvManager
        );
        
        // Register all agents
        runtime.registerAgent(orchestrator);
        runtime.registerAgent(translator);
        runtime.registerAgent(knowledge);
        runtime.registerAgent(cleanup);
        
        // Start runtime
        System.out.println("Starting Jentic runtime...");
        runtime.start().join();
        
        System.out.println("✓ Runtime started");
        System.out.println("✓ Agents registered: orchestrator, translator, knowledge, cleanup");
        System.out.println("\n════════════════════════════════════════════════════\n");
        
        // Simulate multi-user console (CLI phase - future implementation)
        System.out.println("NOTE: Console CLI interface will be implemented in next phase.");
        System.out.println("      For now, this example demonstrates agent setup.\n");
        
        // Example: Simulate a user request programmatically
        demonstrateUsage(orchestrator, messageService);
        
        // Keep running for demonstration
        System.out.println("\nPress Enter to shutdown...");
        new Scanner(System.in).nextLine();
        
        // Cleanup
        runtime.stop().join();
        System.out.println("\n✓ Runtime stopped");
    }
    
    /**
     * Demonstrates usage by simulating user requests.
     */
    private static void demonstrateUsage(
            ConversationOrchestrator orchestrator,
            MessageService messageService) {
        
        System.out.println("Simulating user requests:\n");
        
        // User 1: English query
        String user1Id = "user-alice";
        String session1Id = UUID.randomUUID().toString();
        
        sendUserMessage(
            messageService,
            user1Id,
            session1Id,
            "What is the capital of France?"
        );
        
        // User 2: French query (triggers translator)
        String user2Id = "user-bob";
        String session2Id = UUID.randomUUID().toString();
        
        sendUserMessage(
            messageService,
            user2Id,
            session2Id,
            "Quelle est la capitale de la France?"
        );
        
        System.out.println("\n✓ Simulated requests sent");
        System.out.println("  Check logs for agent processing details");
    }
    
    /**
     * Sends a user message to the orchestrator.
     */
    private static void sendUserMessage(
            MessageService messageService,
            String userId,
            String sessionId,
            String message) {
        
        DialogueMessage userRequest = DialogueMessage.builder()
            .conversationId(sessionId)
            .senderId(userId)
            .receiverId("conversation-orchestrator")
            .performative(Performative.REQUEST)
            .content(message)
            .build();
        
        messageService.send(userRequest.toMessage());
        
        System.out.println("  [" + userId + "] " + message);
    }
    
    /**
     * Creates LLM provider.
     * TODO: Configure with actual provider (Anthropic, OpenAI, Ollama)
     * 
     * Examples:
     * - Anthropic: return new AnthropicProvider("your-api-key");
     * - OpenAI: return new OpenAIProvider("your-api-key");
     * - Ollama (local): return new OllamaProvider("http://localhost:11434");
     */
    private static LLMProvider createLLMProvider() {
        return LLMProviderFactory.openai()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .build();
    }
}
