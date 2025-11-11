package dev.jentic.examples.llm;

import dev.jentic.core.*;
import dev.jentic.core.annotations.*;
import dev.jentic.core.llm.*;
import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.adapters.llm.openai.OpenAIProvider;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Research Team Example - Using Annotations & Point-to-Point Communication
 * 
 * Features:
 * - @JenticAgent annotation for auto-discovery
 * - @JenticBehavior for cyclic behaviors
 * - @JenticMessageHandler for message handling
 * - Point-to-point direct messaging between agents
 * - LLM integration with OpenAI GPT-4
 * - Tool/Function calling
 */
public class ResearchTeamExample {
    
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }
        
        // Create LLM provider
        LLMProvider llmProvider = OpenAIProvider.builder()
            .apiKey(apiKey)
            .model("gpt-4")
            .temperature(0.7)
            .maxTokens(1500)
            .build();
        
        // Create runtime with auto-discovery
        JenticRuntime runtime = JenticRuntime.builder()
//            .scanPackages("dev.jentic.examples.llm")
            .build();
        
        // Create agents with LLM provider
        CoordinatorAgentAnnotated coordinator = new CoordinatorAgentAnnotated(llmProvider);
        TechnicalResearcherAnnotated techResearcher = new TechnicalResearcherAnnotated(llmProvider);
        MarketResearcherAnnotated marketResearcher = new MarketResearcherAnnotated(llmProvider);
        CompetitorResearcherAnnotated competitorResearcher = new CompetitorResearcherAnnotated(llmProvider);
        
        // Register agents
        runtime.registerAgent(coordinator);
        runtime.registerAgent(techResearcher);
        runtime.registerAgent(marketResearcher);
        runtime.registerAgent(competitorResearcher);
        
        // Start runtime
        runtime.start().join();
        
        System.out.println("\n=== Research Team Started ===");
                
        // Example: Direct message to coordinator
        String researchTopic = "Quantum Computing Applications in Finance 2025";
        
        System.out.println("Research Topic: " + researchTopic);
        System.out.println("\nSending direct message to coordinator...\n");
        
        // Point-to-point message to coordinator
        Message request = Message.builder()
            .topic("research.request")
            .senderId("user")
            .receiverId("coordinator")  // Direct to coordinator
            .content(Map.of("topic", researchTopic, "priority", "high"))
            .build();
        
        coordinator.getMessageService().send(request);
        
        // Wait for research completion
        Thread.sleep(60000);
        
        // Shutdown
        System.out.println("\n=== Shutting Down ===");
        runtime.stop().join();
    }
}

/**
 * Coordinator Agent with Annotations
 */
@JenticAgent(value = "coordinator", type = "coordinator", capabilities = {"planning", "synthesis"})
class CoordinatorAgentAnnotated extends BaseAgent {
    
    private final LLMProvider llm;
    private final Map<String, ResearchContext> activeResearch = new ConcurrentHashMap<>();
    
    public CoordinatorAgentAnnotated(LLMProvider llm) {
        super("coordinator", "Research Coordinator");
        this.llm = llm;
    }
    
    /**
     * Handle incoming research requests (point-to-point)
     */
    @JenticMessageHandler(value = "research.request", autoSubscribe = true)
    public void handleResearchRequest(Message message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.content();
        String topic = (String) data.get("topic");
        String priority = (String) data.getOrDefault("priority", "normal");
        
        log.info("📋 Coordinator received research request: {} [Priority: {}]", topic, priority);
        
        // Create research context
        String requestId = UUID.randomUUID().toString();
        ResearchContext ctx = new ResearchContext(requestId, topic, priority);
        activeResearch.put(requestId, ctx);
        
        // Use LLM to create research plan
        createResearchPlan(ctx);
    }
    
    /**
     * Periodic status check behavior
     */
    @JenticBehavior(type = BehaviorType.CYCLIC, interval = "10s", autoStart = true)
    public void checkResearchProgress() {
        if (!activeResearch.isEmpty()) {
            log.info("📊 Active research tasks: {}", activeResearch.size());
        }
    }
    
    /**
     * Handle technical findings (point-to-point)
     */
    @JenticMessageHandler(value = "research.findings.technical", autoSubscribe = true)
    public void handleTechnicalFindings(Message message) {
        processFinding(message, "Technical");
    }
    
    /**
     * Handle market findings (point-to-point)
     */
    @JenticMessageHandler(value = "research.findings.market", autoSubscribe = true)
    public void handleMarketFindings(Message message) {
        processFinding(message, "Market");
    }
    
    /**
     * Handle competitor findings (point-to-point)
     */
    @JenticMessageHandler(value = "research.findings.competitor", autoSubscribe = true)
    public void handleCompetitorFindings(Message message) {
        processFinding(message, "Competitor");
    }
    
    private void createResearchPlan(ResearchContext ctx) {
        String planningPrompt = String.format(
            "Create a research plan for: '%s'\n" +
            "Priority: %s\n" +
            "Break into 3 focused research questions:\n" +
            "1. Technical Analysis\n" +
            "2. Market Analysis\n" +
            "3. Competitive Analysis\n" +
            "Be specific and actionable.",
            ctx.topic, ctx.priority
        );
        
        LLMRequest request = LLMRequest.builder("gpt-4")
            .addMessage(LLMMessage.system("You are an expert research planner."))
            .addMessage(LLMMessage.user(planningPrompt))
            .temperature(0.7)
            .maxTokens(400)
            .build();
        
        llm.chat(request).thenAccept(response -> {
            ctx.plan = response.content();
            log.info("📝 Research plan created for: {}", ctx.topic);
            
            // Delegate to specialists via point-to-point messages
            delegateResearch(ctx);
            
        }).exceptionally(ex -> {
            log.error("❌ Error creating research plan", ex);
            return null;
        });
    }
    
    private void delegateResearch(ResearchContext ctx) {
        // Send point-to-point to technical researcher
        Message techMsg = Message.builder()
            .topic("research.task.technical")
            .senderId(getAgentId())
            .receiverId("tech-researcher")  // Point-to-point
            .content(Map.of("requestId", ctx.requestId, "topic", ctx.topic, "plan", ctx.plan))
            .build();
        
        // Send point-to-point to market researcher
        Message marketMsg = Message.builder()
            .topic("research.task.market")
            .senderId(getAgentId())
            .receiverId("market-researcher")  // Point-to-point
            .content(Map.of("requestId", ctx.requestId, "topic", ctx.topic, "plan", ctx.plan))
            .build();
        
        // Send point-to-point to competitor researcher
        Message compMsg = Message.builder()
            .topic("research.task.competitor")
            .senderId(getAgentId())
            .receiverId("competitor-researcher")  // Point-to-point
            .content(Map.of("requestId", ctx.requestId, "topic", ctx.topic, "plan", ctx.plan))
            .build();
        
        messageService.send(techMsg);
        messageService.send(marketMsg);
        messageService.send(compMsg);
        
        log.info("🚀 Research tasks delegated to all specialists");
    }
    
    private void processFinding(Message message, String type) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.content();
        String requestId = (String) data.get("requestId");
        String findings = (String) data.get("findings");
        
        ResearchContext ctx = activeResearch.get(requestId);
        if (ctx == null) {
            log.warn("⚠️ Unknown research request: {}", requestId);
            return;
        }
        
        log.info("✅ Received {} findings", type);
        ctx.addFinding(type, findings);
        
        // Check if all findings collected
        if (ctx.isComplete()) {
            synthesizeReport(ctx);
        }
    }
    
    private void synthesizeReport(ResearchContext ctx) {
        log.info("🔄 Synthesizing final report...");
        
        String allFindings = String.join("\n\n", ctx.findings.values());
        
        String synthPrompt = String.format(
            "Synthesize these research findings into an executive summary:\n\n%s\n\n" +
            "Provide key insights, trends, and strategic recommendations.",
            allFindings
        );
        
        LLMRequest request = LLMRequest.builder("gpt-4")
            .addMessage(LLMMessage.system("You are an expert research synthesizer."))
            .addMessage(LLMMessage.user(synthPrompt))
            .temperature(0.5)
            .maxTokens(800)
            .build();
        
        llm.chat(request).thenAccept(response -> {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("📊 FINAL RESEARCH REPORT - " + ctx.topic);
            System.out.println("=".repeat(70));
            System.out.println(response.content());
            System.out.println("=".repeat(70) + "\n");
            
            activeResearch.remove(ctx.requestId);
            
        }).exceptionally(ex -> {
            log.error("❌ Error synthesizing report", ex);
            return null;
        });
    }
    
    // Research context holder
    static class ResearchContext {
        final String requestId;
        final String topic;
        final String priority;
        String plan;
        final Map<String, String> findings = new ConcurrentHashMap<>();
        
        ResearchContext(String requestId, String topic, String priority) {
            this.requestId = requestId;
            this.topic = topic;
            this.priority = priority;
        }
        
        void addFinding(String type, String finding) {
            findings.put(type, String.format("=== %s Analysis ===\n%s", type, finding));
        }
        
        boolean isComplete() {
            return findings.size() >= 3;
        }
    }
}

/**
 * Technical Researcher with Annotations
 */
@JenticAgent(value = "tech-researcher", type = "specialist", capabilities = {"technical-analysis", "specs-evaluation"})
class TechnicalResearcherAnnotated extends BaseAgent {
    
    private final LLMProvider llm;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    
    public TechnicalResearcherAnnotated(LLMProvider llm) {
        super("tech-researcher", "Technical Researcher");
        this.llm = llm;
    }
    
    /**
     * Handle research tasks via point-to-point message
     */
    @JenticMessageHandler(value = "research.task.technical", autoSubscribe = true)
    public void handleResearchTask(Message message) {
        taskCount.incrementAndGet();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.content();
        String requestId = (String) data.get("requestId");
        String topic = (String) data.get("topic");
        
        log.info("🔧 Technical researcher analyzing: {}", topic);
        
        String analysisPrompt = String.format(
            "Technical analysis of: '%s'\n" +
            "Focus on:\n" +
            "- Core technologies and architecture\n" +
            "- Technical feasibility and challenges\n" +
            "- Infrastructure and scalability requirements\n" +
            "- Innovation opportunities\n" +
            "Provide a detailed technical assessment.",
            topic
        );
        
        // Define technical analysis tool
        FunctionDefinition techTool = FunctionDefinition.builder("evaluate_technology")
            .description("Evaluate technical specifications and requirements")
            .stringParameter("technology", "Technology to evaluate", true)
            .enumParameter("aspect", "Evaluation aspect", true,
                "maturity", "scalability", "security", "performance")
            .build();
        
        LLMRequest request = LLMRequest.builder("gpt-4")
            .addMessage(LLMMessage.system("You are a senior technical architect."))
            .addMessage(LLMMessage.user(analysisPrompt))
            .addFunction(techTool)
            .temperature(0.6)
            .maxTokens(700)
            .build();
        
        llm.chat(request).thenAccept(response -> {
            if (response.hasFunctionCalls()) {
                log.info("🛠️ Using technical evaluation tools");
            }
            
            String findings = response.content();
            log.info("✅ Technical analysis complete");
            
            // Send findings back via point-to-point
            Map<String, Object> contentMap = new java.util.HashMap<>();
            contentMap.put("requestId", requestId);
            contentMap.put("findings", findings);
            
            Message reply = Message.builder()
                .topic("research.findings.technical")
                .senderId(getAgentId())
                .receiverId(message.senderId())  // Back to coordinator
                .content(contentMap)
                .correlationId(message.id())
                .build();
            
            messageService.send(reply);
            
        }).exceptionally(ex -> {
            log.error("❌ Error in technical analysis", ex);
            return null;
        });
    }
    
    /**
     * Report status periodically
     */
    @JenticBehavior(type = BehaviorType.CYCLIC, interval = "15s", autoStart = true)
    public void reportStatus() {
        int count = taskCount.get();
        if (count > 0) {
            log.info("🔧 Technical Researcher - Tasks completed: {}", count);
        }
    }
}

/**
 * Market Researcher with Annotations
 */
@JenticAgent(value = "market-researcher", type = "specialist", capabilities = {"market-analysis", "trend-forecasting"})
class MarketResearcherAnnotated extends BaseAgent {
    
    private final LLMProvider llm;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    
    public MarketResearcherAnnotated(LLMProvider llm) {
        super("market-researcher", "Market Researcher");
        this.llm = llm;
    }
    
    @JenticMessageHandler(value = "research.task.market", autoSubscribe = true)
    public void handleResearchTask(Message message) {
        taskCount.incrementAndGet();
    
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.content();
        String requestId = (String) data.get("requestId");
        String topic = (String) data.get("topic");

        if (requestId == null || requestId.isBlank()) {
            log.error("❌ Missing or empty requestId in message");
            return;
        }
        
        if (topic == null || topic.isBlank()) {
            log.error("❌ Missing or empty topic in message");
            return;
        }
    
        log.info("📈 Market researcher analyzing: {}", topic);
    
        String analysisPrompt = String.format(
            "Market analysis of: '%s'\n" +
            "Focus on:\n" +
            "- Market size and growth projections\n" +
            "- Key market drivers and trends\n" +
            "- Target customer segments\n" +
            "- Market barriers and opportunities\n" +
            "Provide actionable market insights.",
            topic
        );
    
        FunctionDefinition marketTool = FunctionDefinition.builder("analyze_market_segment")
            .description("Analyze specific market segment dynamics")
            .stringParameter("segment", "Market segment to analyze", true)
            .enumParameter("metric", "Metric to focus on", true,
                "size", "growth", "penetration", "competition")
            .build();
    
        LLMRequest request = LLMRequest.builder("gpt-4")
            .addMessage(LLMMessage.system("You are a market research analyst."))
            .addMessage(LLMMessage.user(analysisPrompt))
            .addFunction(marketTool)
            .temperature(0.6)
            .maxTokens(700)
            .build();
    
        llm.chat(request).thenAccept(response -> {
            if (response.hasFunctionCalls()) {
                log.info("📊 Using market analysis tools");
            }
        
            String findings = response.content();

            if (findings == null || findings.isBlank()) {
                log.error("❌ Empty or null findings from LLM response");
                findings = "Analysis could not be completed - no response from LLM";
            }
            
            log.info("✅ Market analysis complete");
        
            Message reply = Message.builder()
                .topic("research.findings.market")
                .senderId(getAgentId())
                .receiverId(message.senderId())
                .content(Map.of("requestId", requestId, "findings", findings))
                .correlationId(message.id())
                .build();
        
            messageService.send(reply);
        
        }).exceptionally(ex -> {
            log.error("❌ Error in market analysis", ex);

            Message errorReply = Message.builder()
                .topic("research.findings.market")
                .senderId(getAgentId())
                .receiverId(message.senderId())
                .content(Map.of(
                    "requestId", requestId,
                    "findings", "Error during analysis: " + ex.getMessage(),
                    "error", true
                ))
                .correlationId(message.id())
                .build();
            
            messageService.send(errorReply);
            return null;
        });
    }
    
    @JenticBehavior(type = BehaviorType.CYCLIC, interval = "15s", autoStart = true)
    public void reportStatus() {
        int count = taskCount.get();
        if (count > 0) {
            log.info("📈 Market Researcher - Tasks completed: {}", count);
        }
    }
}

/**
 * Competitor Researcher with Annotations
 */
@JenticAgent(value = "competitor-researcher", type = "specialist", capabilities = {"competitive-intelligence", "swot-analysis"})
class CompetitorResearcherAnnotated extends BaseAgent {
    
    private final LLMProvider llm;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    
    public CompetitorResearcherAnnotated(LLMProvider llm) {
        super("competitor-researcher", "Competitor Researcher");
        this.llm = llm;
    }
    
    @JenticMessageHandler(value = "research.task.competitor", autoSubscribe = true)
    public void handleResearchTask(Message message) {
        taskCount.incrementAndGet();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.content();
        String requestId = (String) data.get("requestId");
        String topic = (String) data.get("topic");

        if (requestId == null || requestId.isBlank()) {
            log.error("❌ Missing or empty requestId in message");
            return;
        }

        if (topic == null || topic.isBlank()) {
            log.error("❌ Missing or empty topic in message");
            return;
        }

        log.info("🎯 Competitor researcher analyzing: {}", topic);

        String analysisPrompt = String.format(
            "Competitive analysis of: '%s'\n" +
            "Focus on:\n" +
            "- Major players and market positions\n" +
            "- Competitive advantages and differentiators\n" +
            "- Strategic threats and opportunities\n" +
            "- Emerging competitors and disruptions\n" +
            "Provide strategic competitive intelligence.",
            topic
        );

        FunctionDefinition compTool = FunctionDefinition.builder("analyze_competitor_strategy")
            .description("Deep dive into competitor strategy and positioning")
            .stringParameter("competitor", "Competitor name or type", true)
            .enumParameter("focus", "Analysis focus area", true,
                "strategy", "products", "pricing", "marketing")
            .build();

        LLMRequest request = LLMRequest.builder("gpt-4")
            .addMessage(LLMMessage.system("You are a competitive intelligence expert."))
            .addMessage(LLMMessage.user(analysisPrompt))
            .addFunction(compTool)
            .temperature(0.6)
            .maxTokens(700)
            .build();

        llm.chat(request).thenAccept(response -> {
            if (response.hasFunctionCalls()) {
                log.info("🔍 Using competitive analysis tools");
            }
    
            String findings = response.content();

            if (findings == null || findings.isBlank()) {
                log.error("❌ Empty or null findings from LLM response");
                findings = "Analysis could not be completed - no response from LLM";
            }
        
            log.info("✅ Competitive analysis complete");
    
            Message reply = Message.builder()
                .topic("research.findings.competitor")
                .senderId(getAgentId())
                .receiverId(message.senderId())
                .content(Map.of("requestId", requestId, "findings", findings))
                .correlationId(message.id())
                .build();
    
            messageService.send(reply);
    
    }).exceptionally(ex -> {
        log.error("❌ Error in competitor analysis", ex);

        Message errorReply = Message.builder()
            .topic("research.findings.competitor")
            .senderId(getAgentId())
            .receiverId(message.senderId())
            .content(Map.of(
                "requestId", requestId,
                "findings", "Error during analysis: " + ex.getMessage(),
                "error", true
            ))
            .correlationId(message.id())
            .build();
        
        messageService.send(errorReply);
        return null;
    });
}
    
    @JenticBehavior(type = BehaviorType.CYCLIC, interval = "15s", autoStart = true)
    public void reportStatus() {
        int count = taskCount.get();
        if (count > 0) {
            log.info("🎯 Competitor Researcher - Tasks completed: {}", count);
        }
    }
}