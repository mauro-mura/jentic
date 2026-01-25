package dev.jentic.examples.behaviors.chain;

import dev.jentic.adapters.llm.LLMProviderFactory;
import dev.jentic.adapters.llm.openai.OpenAIProvider;
import dev.jentic.core.llm.LLMProvider;
import dev.jentic.runtime.behavior.chain.ChainBehavior;
import dev.jentic.runtime.behavior.chain.Gate;
import dev.jentic.runtime.behavior.chain.GateAction;

import java.util.Map;

/**
 * Real working example of ChainBehavior with OpenAI for blog post generation.
 * 
 * <p>Demonstrates a 3-step chain:
 * <ol>
 *   <li>Generate outline (must contain Introduction/Conclusion)</li>
 *   <li>Write full draft (minimum 500 chars)</li>
 *   <li>Polish and refine (final quality check)</li>
 * </ol>
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * export OPENAI_API_KEY="sk-..."
 * mvn exec:java -Dexec.mainClass="dev.jentic.examples.chain.BlogWriterAgent" \
 *   -Dexec.args="'AI trends in 2025'"
 * }</pre>
 * 
 * <p><b>Requirements:</b>
 * <ul>
 *   <li>jentic-adapters-openai dependency</li>
 *   <li>OPENAI_API_KEY environment variable</li>
 * </ul>
 * 
 * @since 0.8.0
 */
public class BlogWriterAgent {
    
    private final LLMProvider llmProvider;
    private final ChainBehavior blogChain;
    
    /**
     * Creates a new blog writer agent.
     * 
     * @param llmProvider the LLM provider (e.g., OpenAIProvider)
     */
    public BlogWriterAgent(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
        
        // Build the 3-step chain
        this.blogChain = ChainBehavior.builder(llmProvider)
            .name("blog-writing-chain")
            
            // Step 1: Create outline
            .step("outline", 
                """
                Create a detailed outline for a blog post about: ${topic}
                
                The outline must include:
                - Introduction
                - 3-5 main sections with brief descriptions
                - Conclusion
                
                Format as a numbered list.
                """,
                Gate.contains("Introduction")
                    .and(Gate.contains("Conclusion"))
                    .and(Gate.minLength(100)))
            
            // Step 2: Write draft
            .step("draft",
                """
                Based on this outline:
                ${previous}
                
                Write a complete blog post about ${topic}.
                
                Requirements:
                - Engaging introduction
                - Well-developed sections following the outline
                - Clear conclusion with takeaways
                - Professional but accessible tone
                - Aim for 500-800 words
                """,
                Gate.minLength(500))
            
            // Step 3: Polish and refine
            .step("polish",
                """
                Review and improve this blog post:
                ${previous}
                
                Enhance:
                - Clarity and flow between sections
                - Grammar and style
                - Add concrete examples where helpful
                - Strengthen the conclusion
                
                Return only the polished version (no commentary).
                """,
                Gate.minLength(500)
                    .and(Gate.contains("Conclusion")))
            
            .defaultGateAction(GateAction.RETRY)
            .maxRetryAttempts(2)
            .build();
    }
    
    /**
     * Generates a blog post on the given topic.
     * 
     * @param topic the blog post topic
     * @return the complete execution results (outline, draft, polish)
     */
    public Map<String, String> generateBlogPost(String topic) {
        System.out.println("Starting blog generation for topic: " + topic);
        System.out.println("=" .repeat(60));
        
        // Create a new chain with the topic variable
        ChainBehavior chain = ChainBehavior.builder(llmProvider)
            .name("blog-writing-chain")
            
            .step("outline", 
                """
                Create a detailed outline for a blog post about: ${topic}
                
                The outline must include:
                - Introduction
                - 3-5 main sections with brief descriptions
                - Conclusion
                
                Format as a numbered list.
                """,
                Gate.contains("Introduction")
                    .and(Gate.contains("Conclusion"))
                    .and(Gate.minLength(100)))
            
            .step("draft",
                """
                Based on this outline:
                ${previous}
                
                Write a complete blog post about ${topic}.
                
                Requirements:
                - Engaging introduction
                - Well-developed sections following the outline
                - Clear conclusion with takeaways
                - Professional but accessible tone
                - Aim for 500-800 words
                """,
                Gate.minLength(500))
            
            .step("polish",
                """
                Review and improve this blog post:
                ${previous}
                
                Enhance:
                - Clarity and flow between sections
                - Grammar and style
                - Add concrete examples where helpful
                - Strengthen the conclusion
                
                Return only the polished version (no commentary).
                """,
                Gate.minLength(500)
                    .and(Gate.contains("Conclusion")))
            
            .variable("topic", topic)  // Set the topic variable
            .defaultGateAction(GateAction.RETRY)
            .maxRetryAttempts(2)
            .build();
        
        try {
            // Execute the chain
            System.out.println("\n[1/3] Creating outline...");
            chain.executeChain();
            
            System.out.println("✓ Outline complete");
            System.out.println("✓ Draft complete");
            System.out.println("✓ Polish complete");
            System.out.println("\nBlog post generated successfully!");
            
            return chain.getExecutionHistory();
            
        } catch (Exception e) {
            System.err.println("\n✗ Error generating blog post: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Main method demonstrating real usage with OpenAI.
     * 
     * <p>Requires OPENAI_API_KEY environment variable.
     * 
     * @param args [0] topic (required)
     */
    public static void main(String[] args) {
        // Check arguments
        if (args.length < 1) {
            System.err.println("Usage: BlogWriterAgent <topic>");
            System.err.println("\nExample:");
            System.err.println("  export OPENAI_API_KEY='sk-...'");
            System.err.println("  mvn exec:java -Dexec.mainClass='dev.jentic.examples.chain.BlogWriterAgent' \\");
            System.err.println("    -Dexec.args='\"AI trends in 2025\"'");
            System.exit(1);
        }
        
        String topic = args[0];
        
        // Get API key from environment
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
            System.err.println("\nSet it with:");
            System.err.println("  export OPENAI_API_KEY='sk-...'");
            System.exit(1);
        }
        
        try {
            // Create OpenAI provider
            System.out.println("Initializing OpenAI provider...");
            LLMProvider llm = LLMProviderFactory.openai()
                    .apiKey(apiKey)
                    .modelName("gpt-4o-mini")
                    .temperature(0.7)
                    .build();

            // Create agent
            BlogWriterAgent agent = new BlogWriterAgent(llm);
            
            // Generate blog post
            Map<String, String> results = agent.generateBlogPost(topic);
            
            // Display results
            System.out.println("\n" + "=".repeat(60));
            System.out.println("OUTLINE");
            System.out.println("=".repeat(60));
            System.out.println(results.get("outline"));
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("DRAFT");
            System.out.println("=".repeat(60));
            System.out.println(results.get("draft"));
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("FINAL (POLISHED)");
            System.out.println("=".repeat(60));
            System.out.println(results.get("polish"));
            
            // Save to file (optional)
            String filename = topic.replaceAll("[^a-zA-Z0-9]", "_") + ".md";
            java.nio.file.Files.writeString(
                java.nio.file.Path.of(filename),
                "# " + topic + "\n\n" + results.get("polish")
            );
            System.out.println("\n✓ Saved to: " + filename);
            
        } catch (Exception e) {
            System.err.println("\nFATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
