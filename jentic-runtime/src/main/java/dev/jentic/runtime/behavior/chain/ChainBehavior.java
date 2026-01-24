package dev.jentic.runtime.behavior.chain;

import dev.jentic.runtime.behavior.BaseBehavior;
import dev.jentic.core.llm.LLMProvider;
import dev.jentic.core.llm.LLMRequest;
import dev.jentic.core.llm.LLMResponse;
import dev.jentic.core.BehaviorType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChainBehavior implements Anthropic's Prompt Chaining pattern with
 * direct sequential execution.
 * 
 * <p>Enables sequential LLM execution with:
 * <ul>
 *   <li>Variable substitution (${variable})</li>
 *   <li>Gate validation between steps</li>
 *   <li>Execution history tracking</li>
 *   <li>Sequential step execution</li>
 *   <li>Retry logic with configurable strategies</li>
 * </ul>
 * 
 * <p>Implementation uses direct sequential execution for simplicity
 * and predictability. Each step executes synchronously and validates
 * before proceeding to the next step.
 * 
 * <p>Example usage:
 * <pre>{@code
 * ChainBehavior chain = ChainBehavior.builder(llmProvider)
 *     .step("outline", "Create outline for: ${topic}", Gate.contains("Intro"))
 *     .step("draft", "Write article: ${previous}", Gate.minLength(500))
 *     .variable("topic", "AI trends")
 *     .build();
 * }</pre>
 * 
 * @since 0.7.0
 */
public class ChainBehavior extends BaseBehavior {
    
    private final LLMProvider llmProvider;
    private final List<ChainStep> steps;
    private final Map<String, String> variables;
    private final Map<String, String> executionHistory;
    private final GateAction defaultGateAction;
    private final int maxRetryAttempts;
    
    private int currentStepIndex;
    
    private ChainBehavior(Builder builder) {
        super(builder.name, BehaviorType.CUSTOM, null);
        this.llmProvider = Objects.requireNonNull(builder.llmProvider);
        this.steps = List.copyOf(builder.steps);
        this.variables = new ConcurrentHashMap<>(builder.variables);
        this.executionHistory = new ConcurrentHashMap<>();
        this.defaultGateAction = builder.defaultGateAction;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.currentStepIndex = 0;
        
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Chain must have at least one step");
        }
    }
    
    @Override
    protected void action() {
        executeChain();
    }
    
    /**
     * Execute the chain synchronously.
     * Executes all steps in sequence until completion or failure.
     * 
     * @throws ChainExecutionException if chain execution fails
     * @throws GateValidationException if gate validation fails
     */
    public void executeChain() {
        for (int i = 0; i < steps.size(); i++) {
            executeStep(i);
        }
    }
    
    private void executeStep(int stepIndex) {
        ChainStep step = steps.get(stepIndex);
        currentStepIndex = stepIndex;
        
        String prompt = resolveVariables(step.prompt());
        
        int attempts = 0;
        while (attempts <= maxRetryAttempts) {
            try {
                // Execute LLM call (blocking for simplicity in FSM context)
                LLMResponse response = llmProvider.chat(
                    LLMRequest.builder(llmProvider.getProviderName())
                        .systemMessage(buildSystemMessage())
                        .userMessage(prompt)
                        .build()
                ).join(); // Block and wait for completion
                
                String output = response.content();
                
                // Validate with gate
                if (step.gate() != null) {
                    GateResult result = step.gate().validate(output);
                    
                    if (!result.passed()) {
                        GateAction action = (step.gateAction() != null) ? 
                            step.gateAction() : defaultGateAction;
                        
                        if (action == GateAction.CONTINUE) {
                            // CONTINUE: log warning but save output and proceed
                            System.err.println("WARNING: Gate failed for step '" + 
                                step.name() + "' but continuing: " + result.message());
                            executionHistory.put(step.name(), output);
                            variables.put("previous", output);
                            return; // Exit successfully despite gate failure
                        } else {
                            // ABORT or RETRY: handle failure
                            handleGateFailure(step, result, attempts);
                            attempts++;
                            continue;
                        }
                    }
                }
                
                // Store result (gate passed or no gate)
                executionHistory.put(step.name(), output);
                variables.put("previous", output);
                
                break; // Success, exit retry loop
                
            } catch (GateValidationException e) {
                // Gate validation exceptions should propagate directly
                throw e;
            } catch (Exception e) {
                // LLM or other errors - retry if possible
                if (attempts >= maxRetryAttempts) {
                    throw new ChainExecutionException(
                        "Step '" + step.name() + "' failed after " + 
                        maxRetryAttempts + " attempts", e);
                }
                attempts++;
            }
        }
    }
    
    private String resolveVariables(String template) {
        String result = template;
        
        // Replace ${variable} with actual values
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        
        // Replace ${step:name} with step output
        for (Map.Entry<String, String> entry : executionHistory.entrySet()) {
            String placeholder = "${step:" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        
        return result;
    }
    
    private void handleGateFailure(ChainStep step, GateResult result, 
                                   int attempt) {
        GateAction action = (step.gateAction() != null) ? 
            step.gateAction() : defaultGateAction;
        
        switch (action) {
            case ABORT:
                // Throw directly without wrapping
                throw new GateValidationException(
                    "Gate failed for step '" + step.name() + "': " + 
                    result.message());
            
            case RETRY:
                if (attempt >= maxRetryAttempts) {
                    throw new ChainExecutionException(
                        "Step '" + step.name() + "' failed after " + 
                        maxRetryAttempts + " attempts", 
                        new GateValidationException(result.message()));
                }
                // Continue to retry (return to loop)
                break;
            
            case CONTINUE:
                // Should not reach here - handled in executeStep
                throw new IllegalStateException(
                    "CONTINUE should be handled in executeStep");
        }
    }
    
    private String buildSystemMessage() {
        return "You are part of a multi-step chain. Execute this step " +
               "carefully and follow the requirements.";
    }
    
    /**
     * Gets the execution history of completed steps.
     * 
     * @return unmodifiable map of step names to outputs
     */
    public Map<String, String> getExecutionHistory() {
        return Collections.unmodifiableMap(executionHistory);
    }
    
    /**
     * Gets the current step index being executed.
     * 
     * @return zero-based step index
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }
    
    /**
     * Creates a new ChainBehavior builder.
     * 
     * @param llmProvider the LLM provider to use
     * @return builder instance
     */
    public static Builder builder(LLMProvider llmProvider) {
        return new Builder(llmProvider);
    }
    
    /**
     * Builder for ChainBehavior.
     */
    public static class Builder {
        private final LLMProvider llmProvider;
        private final List<ChainStep> steps = new ArrayList<>();
        private final Map<String, String> variables = new HashMap<>();
        private String name = "chain-behavior";
        private GateAction defaultGateAction = GateAction.ABORT;
        private int maxRetryAttempts = 2;
        
        private Builder(LLMProvider llmProvider) {
            this.llmProvider = Objects.requireNonNull(llmProvider);
        }
        
        /**
         * Sets the behavior name.
         * 
         * @param name behavior name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }
        
        /**
         * Adds a step to the chain.
         * 
         * @param name step name (unique identifier)
         * @param prompt prompt template with ${variables}
         * @return this builder
         */
        public Builder step(String name, String prompt) {
            return step(name, prompt, null);
        }
        
        /**
         * Adds a step with gate validation.
         * 
         * @param name step name (unique identifier)
         * @param prompt prompt template with ${variables}
         * @param gate validation gate (null for no validation)
         * @return this builder
         */
        public Builder step(String name, String prompt, Gate gate) {
            return step(name, prompt, gate, null);
        }
        
        /**
         * Adds a step with gate and custom action.
         * 
         * @param name step name
         * @param prompt prompt template
         * @param gate validation gate
         * @param gateAction action on gate failure
         * @return this builder
         */
        public Builder step(String name, String prompt, Gate gate, 
                          GateAction gateAction) {
            steps.add(new ChainStep(name, prompt, gate, gateAction));
            return this;
        }
        
        /**
         * Sets a variable for substitution.
         * 
         * @param name variable name
         * @param value variable value
         * @return this builder
         */
        public Builder variable(String name, String value) {
            variables.put(Objects.requireNonNull(name), 
                         Objects.requireNonNull(value));
            return this;
        }
        
        /**
         * Sets multiple variables.
         * 
         * @param variables map of variable names to values
         * @return this builder
         */
        public Builder variables(Map<String, String> variables) {
            this.variables.putAll(Objects.requireNonNull(variables));
            return this;
        }
        
        /**
         * Sets default gate action.
         * 
         * @param action default action on gate failure
         * @return this builder
         */
        public Builder defaultGateAction(GateAction action) {
            this.defaultGateAction = Objects.requireNonNull(action);
            return this;
        }
        
        /**
         * Sets maximum retry attempts per step.
         * 
         * @param attempts max attempts (0 = no retry)
         * @return this builder
         */
        public Builder maxRetryAttempts(int attempts) {
            if (attempts < 0) {
                throw new IllegalArgumentException(
                    "Retry attempts must be >= 0");
            }
            this.maxRetryAttempts = attempts;
            return this;
        }
        
        /**
         * Builds the ChainBehavior instance.
         * 
         * @return configured ChainBehavior
         */
        public ChainBehavior build() {
            return new ChainBehavior(this);
        }
    }
}
