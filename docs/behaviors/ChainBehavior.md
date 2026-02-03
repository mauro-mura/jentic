# ChainBehavior - Prompt Chaining Pattern

## Overview

ChainBehavior implements [Anthropic's Prompt Chaining pattern](https://www.anthropic.com/research/building-effective-agents), enabling sequential LLM execution with validation gates and variable substitution.

## When to Use

- **Multi-step content generation**: Blog posts, reports, code documentation
- **Sequential processing with validation**: Each step validates before proceeding
- **Progressive refinement workflows**: Outline → Draft → Polish
- **Complex tasks requiring breakdown**: Large tasks split into verifiable steps

## Features

✅ **Sequential LLM Execution**: Steps execute in order  
✅ **Variable Substitution**: Use `${variable}` in prompts  
✅ **Gate Validation**: Validate outputs before proceeding  
✅ **Execution History**: Access outputs from any step  
✅ **Error Handling**: Configurable retry and failure strategies  
✅ **FSM Integration**: Built on proven FSMBehavior foundation  

## Quick Start

```xml
<dependencies>
    <!-- Core + Runtime for basic agent applications -->
    <dependency>
        <groupId>dev.jentic</groupId>
        <artifactId>jentic-runtime</artifactId>
    </dependency>
    
    <!-- Add adapters for external integrations -->
    <dependency>
        <groupId>dev.jentic</groupId>
        <artifactId>jentic-adapters</artifactId>
    </dependency>
</dependencies>
```

```java
LLMProvider llm = new AnthropicProvider(apiKey, "claude-sonnet-4-5-20250929");

ChainBehavior chain = ChainBehavior.builder(llm)
    .step("outline", "Create outline: ${topic}", 
          Gate.contains("Introduction"))
    .step("draft", "Write article: ${previous}", 
          Gate.minLength(500))
    .step("polish", "Refine: ${previous}", 
          Gate.contains("Conclusion"))
    .variable("topic", "AI safety")
    .build();

agent.addBehavior(chain);
```

## Core Concepts

### 1. Steps

Each step has:
- **Name**: Unique identifier
- **Prompt**: Template with `${variables}`
- **Gate**: Optional output validator
- **Action**: Optional failure handling override

```java
.step("step-name", "Prompt with ${variable}", Gate.minLength(100))
```

### 2. Variables

Two types of variables:

**User Variables**:
```java
.variable("topic", "Machine Learning")
.variable("audience", "developers")
```

**Built-in Variables**:
- `${previous}`: Output from previous step
- `${step:stepName}`: Output from specific step

### 3. Gates

Validators that check step outputs:

```java
// Single gates
Gate.minLength(100)
Gate.maxLength(500)
Gate.contains("required text")
Gate.matches("\\d{3}-\\d{4}")
Gate.jsonValid()
Gate.startsWith("prefix")
Gate.endsWith("suffix")

// Combinators
Gate.minLength(100).and(Gate.contains("Introduction"))
Gate.contains("A").or(Gate.contains("B"))
```

### 4. Gate Actions

What happens when validation fails:

- **ABORT**: Stop immediately (default)
- **RETRY**: Retry step (respects maxRetryAttempts)
- **CONTINUE**: Proceed with warning

```java
.defaultGateAction(GateAction.RETRY)
.maxRetryAttempts(2)
```

## Complete Example

```java
@JenticAgent("blog-writer")
public class BlogWriterAgent extends BaseAgent {
    
    private final LLMProvider llm;
    
    @Override
    protected void onStart() {
        ChainBehavior chain = ChainBehavior.builder(llm)
            .name("blog-writing-chain")
            
            // Step 1: Outline
            .step("outline", 
                """
                Create outline for: ${topic}
                Include: Introduction, 3-5 sections, Conclusion
                """,
                Gate.contains("Introduction")
                    .and(Gate.contains("Conclusion")))
            
            // Step 2: Draft
            .step("draft",
                """
                Write blog post based on:
                ${previous}
                
                Topic: ${topic}
                Minimum 500 words
                """,
                Gate.minLength(500))
            
            // Step 3: Polish
            .step("polish",
                """
                Improve this draft:
                ${previous}
                
                Enhance clarity, grammar, examples
                """,
                Gate.minLength(500)
                    .and(Gate.contains("Conclusion")))
            
            .variable("topic", "AI Trends 2025")
            .defaultGateAction(GateAction.RETRY)
            .maxRetryAttempts(2)
            .build();
        
        addBehavior(chain);
    }
    
    public String getFinalPost() {
        return chain.getExecutionHistory().get("polish");
    }
}
```

## Advanced Usage

### Referencing Specific Steps

```java
.step("intro", "Write introduction about ${topic}")
.step("body", "Write body about ${topic}")
.step("conclusion", "Write conclusion referencing ${step:intro}")
```

### Custom Gate Logic

```java
Gate customGate = output -> {
    boolean valid = /* your validation logic */;
    return valid ? 
        GateResult.pass() : 
        GateResult.fail("Reason for failure");
};
```

### Per-Step Gate Actions

```java
.step("critical", "Important step", 
      Gate.minLength(100), 
      GateAction.ABORT)  // Override default for this step
```

### Accessing Execution History

```java
Map<String, String> history = chain.getExecutionHistory();
String outline = history.get("outline");
String draft = history.get("draft");
int currentStep = chain.getCurrentStepIndex();
```

## Configuration

Via `application.yml`:

```yaml
jentic:
  behaviors:
    chain:
      max-steps: 10
      default-gate-action: ABORT
      retry-attempts: 2
      variable-substitution: true
```

## Testing

### Unit Tests with Mock LLM

```java
@Test
void shouldExecuteChain() {
    // Given
    LLMProvider mockLLM = mock(LLMProvider.class);
    when(mockLLM.chat(any()))
        .thenReturn(new LLMResponse("Step 1", null))
        .thenReturn(new LLMResponse("Step 2", null));
    
    ChainBehavior chain = ChainBehavior.builder(mockLLM)
        .step("s1", "Prompt 1")
        .step("s2", "Prompt 2: ${previous}")
        .build();
    
    // When
    chain.action();
    
    // Then
    assertEquals("Step 2", chain.getExecutionHistory().get("s2"));
}
```

### Integration Tests with Real LLM

```java
@Test
void shouldGenerateBlogPost() {
    LLMProvider llm = new AnthropicProvider(apiKey, model);
    
    ChainBehavior chain = ChainBehavior.builder(llm)
        .step("outline", "Create outline for: ${topic}")
        .step("draft", "Write: ${previous}")
        .variable("topic", "Testing")
        .build();
    
    chain.action();
    
    assertNotNull(chain.getExecutionHistory().get("draft"));
}
```

## Best Practices

### ✅ DO

- Use descriptive step names
- Validate critical outputs with gates
- Keep prompts focused (one task per step)
- Use `${previous}` for sequential dependencies
- Set reasonable retry limits
- Test with mock LLMs first

### ❌ DON'T

- Create overly long chains (>10 steps)
- Skip validation on critical steps
- Use CONTINUE carelessly
- Ignore execution history
- Hard-code values (use variables)

## Performance

- **Latency**: ~2-5s per step (depends on LLM)
- **Memory**: Stores all step outputs
- **Retries**: Each retry adds LLM call latency
- **Parallelization**: Steps execute sequentially (by design)

For parallel execution, see [OrchestratorBehavior](OrchestratorBehavior).

## Architecture

ChainBehavior is built on FSMBehavior:

```
ChainBehavior (wrapper)
    └── FSMBehavior (state machine)
        ├── State per step
        ├── Transitions between steps
        └── Final state on completion
```

This design:
- Reuses proven FSM infrastructure
- Provides clean API for chaining
- Enables complex state management
- Maintains backward compatibility

## Troubleshooting

**Chain doesn't start**:
- Check that at least one step is defined
- Verify LLM provider is configured

**Gate keeps failing**:
- Check gate validation logic
- Review LLM output format
- Consider using RETRY with higher attempts

**Variable not substituting**:
- Ensure variable is defined before step
- Check spelling: `${variable}` (case-sensitive)
- Verify variable format (no spaces in `${}`)

**Out of memory**:
- Reduce chain length
- Clear execution history when done
- Consider streaming for large outputs

## Migration from Manual Chains

**Before (v0.7.0)**:
```java
String outline = callLLM("Create outline");
String draft = callLLM("Write: " + outline);
String final = callLLM("Polish: " + draft);
```

**After (v0.8.0)**:
```java
ChainBehavior chain = ChainBehavior.builder(llm)
    .step("outline", "Create outline")
    .step("draft", "Write: ${previous}")
    .step("polish", "Polish: ${previous}")
    .build();
```

## See Also

- [FSMBehavior](../composite/FSMBehavior.md) - Underlying state machine
- [OrchestratorBehavior](../orchestrator/README.md) - Parallel execution
- [EvaluatorBehavior](../evaluator/README.md) - Iterative refinement
- [ADR-011](../../docs/adr/ADR-011-LLM-Pattern-Integration.md) - Design decisions

## Version

Introduced in: **v0.8.0**  
Status: **Stable**  
License: Apache 2.0
