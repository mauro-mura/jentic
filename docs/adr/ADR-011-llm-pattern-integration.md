# ADR-011: LLM Pattern Integration Based on Anthropic Guide

**Status**: Approved  
**Date**: 2026-01-20  
**Authors**: Jentic Team  
**Supersedes**: None  
**Related**: ADR-009 (Agent Dialogue Protocol)

## Context

The Anthropic engineering guide ["Building Effective Agents"](https://www.anthropic.com/engineering/building-effective-agents) identifies core patterns for LLM-based systems:

1. **Prompt Chaining**: Sequential LLM calls with validation gates
2. **Orchestrator-Workers**: Dynamic task decomposition
3. **Evaluator-Optimizer**: Iterative refinement loops
4. **Tool Use**: Function calling with registry

Jentic has strong LLM foundations:
- `LLMProvider` interface with Anthropic/OpenAI/Ollama adapters
- `DialogueCapability` for agent communication
- `FSMBehavior` for state machines
- `LLMMemoryManager` for conversation tracking

However, Jentic lacks direct support for the workflow patterns described in the Anthropic guide.

## Decision

We will implement **all four LLM patterns** as first-class Jentic behaviors, integrated with existing infrastructure:

### Pattern 1: ChainBehavior (Prompt Chaining)
**Implementation**: Wrapper over `FSMBehavior`
- Chain steps → FSM states
- LLM calls → State behaviors
- Gates → Transition conditions
- Context passing → FSM shared state

**Rationale**: Reuses proven FSM implementation, reduces development time by 33%

### Pattern 2: OrchestratorBehavior (Orchestrator-Workers)
**Implementation**: New behavior with `WorkerPool`
- LLM plans task decomposition
- Workers execute in parallel
- LLM synthesizes results

**Rationale**: Dynamic planning enables complex task handling (e.g., code modifications)

### Pattern 3: EvaluatorBehavior (Evaluator-Optimizer)
**Implementation**: Loop-based behavior
- Generator LLM produces output
- Evaluator LLM scores quality
- Feedback loop for refinement (max iterations configurable)

**Rationale**: Iterative improvement for quality-critical tasks (e.g., translations)

### Pattern 4: ToolRegistry (Foundation for Autonomous Agents)
**Implementation**: Registry with auto-discovery
- `@JenticTool` annotation
- Package scanning
- Integration with `FunctionDefinition` 

**Rationale**: Foundation for future autonomous agents with tool use

## Architecture

```
Jentic LLM Pattern Integration
│
├─ ChainBehavior
│   └─ Uses: FSMBehavior (existing)
│
├─ OrchestratorBehavior
│   ├─ Uses: LLMProvider
│   └─ New: WorkerPool
│
├─ EvaluatorBehavior 
│   └─ Uses: LLMProvider (1 or 2 instances)
│
└─ ToolRegistry
    └─ Uses: FunctionDefinition (existing)
```

## Implementation Strategy

### Phased Rollout

**1**: ChainBehavior
- Core: `ChainBehavior`, `ChainStep`, `Gate`
- Example: `BlogWriterAgent`

**2**: OrchestratorBehavior
- Core: `OrchestratorBehavior`, `WorkerPool`, `Worker`
- Example: `CodeOrchestratorAgent`

**3**: EvaluatorBehavior
- Core: `EvaluatorBehavior`, `EvaluationResult`
- Example: `TranslationRefinerAgent`

**4**: ToolRegistry
- Core: `ToolRegistry`, `@JenticTool`
- Examples: `FileSearchTool`, `WebSearchTool`

### Package Structure

```
jentic-runtime/src/main/java/dev/jentic/runtime/behavior/
│
├─ chain/                      # NEW PACKAGE
│   ├─ ChainBehavior.java
│   ├─ ChainStep.java
│   ├─ Gate.java
│   └─ GateResult.java
│
├─ orchestrator/                # NEW PACKAGE
│   ├─ OrchestratorBehavior.java
│   ├─ WorkerPool.java
│   └─ Worker.java
│
├─ evaluator/                   # NEW PACKAGE
│   ├─ EvaluatorBehavior.java
│   └─ EvaluationResult.java
│
└─ composite/
    └─ FSMBehavior.java         # EXISTING (reused)

jentic-runtime/src/main/java/dev/jentic/runtime/tools/
│
└─ ToolRegistry.java            # NEW
```

## Rationale

### Why All Four Patterns Together?

1. **Coherent Vision**: Single architectural decision to align with Anthropic guide
2. **Dependency Chain**: Patterns build on each other (Tools needed for Autonomous)
3. **Unified Timeline**: Clear roadmap
4. **Resource Efficiency**: Single ADR covers all decisions

### Why Phased Implementation?

1. **Risk Mitigation**: Each pattern released and tested independently
2. **User Feedback**: Early patterns inform later ones
3. **Progressive Complexity**: Simple → Complex (Chain → Orchestrator → Evaluator → Tools)
4. **Backward Compatibility**: No breaking changes between phases

### Why These Specific Designs?

**ChainBehavior as FSM Wrapper**:
- ✅ Reuses stable `FSMBehavior`
- ✅ Reduces implementation time 33%
- ✅ Clean LLM-specific API over generic FSM

**OrchestratorBehavior with WorkerPool**:
- ✅ Parallel execution via virtual threads
- ✅ Dynamic planning (vs static routing)
- ✅ Aligns with Anthropic's dynamic decomposition pattern

**EvaluatorBehavior with Separate LLMs**:
- ✅ Optional separation (can use same LLM)
- ✅ Configurable iterations and thresholds
- ✅ Feedback incorporation between iterations

**ToolRegistry with Auto-Discovery**:
- ✅ Annotation-based (follows Jentic patterns)
- ✅ Integrates with existing `FunctionDefinition`
- ✅ Foundation for autonomous agents

## Consequences

### Positive

1. **Complete Pattern Coverage**: All Anthropic patterns supported
2. **Fast Implementation**
3. **Backward Compatible**: Zero breaking changes
4. **Reuses Infrastructure**: FSM, LLMProvider, DialogueCapability
5. **Clear Roadmap**
6. **Developer Experience**: Clean, declarative APIs for each pattern
7. **Future-Proof**: Foundation for autonomous agents

### Negative

1. **Increased Complexity**: More behaviors to maintain
   - *Mitigated by*: Each behavior is independent, well-tested
2. **LLM API Costs**: More LLM calls for chains/orchestration/evaluation
   - *Mitigated by*: Configurable, mocking for tests, rate limiting
3. **Learning Curve**: Developers need to learn 4 new patterns
   - *Mitigated by*: Comprehensive docs, examples for each
4. **Testing Overhead**: More integration tests needed
   - *Mitigated by*: Phased rollout allows incremental testing

### Neutral

1. **Package Growth**: 3 new packages added
2. **Documentation Volume**: More guides needed (but better DX)

## Alternatives Considered

### Alternative 1: Implement Only ChainBehavior

**Rejected because**:
- Incomplete alignment with Anthropic guide
- Users would need other patterns eventually
- Piecemeal decisions create inconsistency

### Alternative 2: Four Separate ADRs

**Rejected because**:
- Fragments single architectural vision
- Harder to maintain coherence
- Unnecessarily bureaucratic for related patterns

### Alternative 3: Build from Scratch Without FSM

**Rejected because**:
- Duplicates FSMBehavior functionality
- 33% longer implementation (3 weeks vs 2 for Chain)
- More code to test and maintain

### Alternative 4: Wait for Anthropic SDK

**Rejected because**:
- Timeline uncertain
- May not align with Jentic architecture
- We control our destiny

## Migration Path

### For Existing Users

**No migration needed** - all changes are additive:

```java
// v0.6.0 - Still works
@JenticAgent("my-agent")
public class ExistingAgent extends BaseAgent {
    @Override
    protected void onStart() {
        addBehavior(new CyclicBehavior(/* ... */));  // Still works
    }
}

// v0.7.0+ - New capabilities available
@JenticAgent("my-agent") 
public class EnhancedAgent extends BaseAgent {
    @Override
    protected void onStart() {
        // Can mix old and new
        addBehavior(new CyclicBehavior(/* ... */));  // Old
        addBehavior(ChainBehavior.builder(llm).build());  // New
    }
}
```

### Configuration Evolution

```yaml
# v0.7.0+ - Extended config example
jentic:
  llm:
    provider: anthropic
    api-key: ${ANTHROPIC_API_KEY}
  behaviors:
    chain:
      max-steps: 10
      default-gate-action: ABORT
    orchestrator:
      max-workers: 5
    evaluator:
      max-iterations: 3
    tools:
      auto-discover: true
```

## Success Metrics

### Implementation Success
- [ ] All 4 patterns implemented
- [ ] Zero breaking changes across versions
- [ ] 90%+ test coverage per pattern
- [ ] Documentation complete for each pattern

### Pattern Adoption
- [ ] 3+ examples per pattern
- [ ] Community feedback positive (surveys/GitHub)
- [ ] Performance benchmarks meet targets:
  - ChainBehavior: <2s for 3-step chain
  - OrchestratorBehavior: Parallel execution working
  - EvaluatorBehavior: Convergence <5 iterations
  - ToolRegistry: Auto-discovery <100ms

### Production Readiness
- [ ] Integration tests with real LLM providers
- [ ] Rate limiting and cost controls verified
- [ ] Error handling comprehensive
- [ ] Ready for v1.0.0 (autonomous agents)

## References

- [Anthropic: Building Effective Agents](https://www.anthropic.com/engineering/building-effective-agents)
- Jentic v0.6.0 Implementation (FSMBehavior, LLMProvider, DialogueCapability)
- ADR-002: Interface-First Design
- ADR-004: Progressive Complexity
- ADR-009: Agent Dialogue Protocol

## Examples

### Pattern 1: ChainBehavior

```java
// Example usage:
// ChainBehavior chain = ChainBehavior.builder(llm)
//     .step("outline", "Create outline: ${topic}", Gate.contains("Introduction"))
//     .step("draft", "Write article: ${previous}", Gate.minLength(500))
//     .step("translate", "Translate to Italian: ${previous}")
//     .variable("topic", "AI trends 2025")
//     .build();
```

### Pattern 2: OrchestratorBehavior

```java
// Example usage:
// OrchestratorBehavior orchestrator = new OrchestratorBehavior(
//     "code-orchestrator", llm, workerPool);
// LLM plans: analyze → test → fix → document
// Workers execute in parallel
// LLM synthesizes results
```

### Pattern 3: EvaluatorBehavior

```java
// Example usage:
// EvaluatorBehavior evaluator = EvaluatorBehavior.builder(llm)
//     .maxIterations(3)
//     .acceptanceThreshold(0.8)
//     .criteria("accuracy, fluency, cultural appropriateness")
//     .build();
// Generate → Evaluate → Refine → Repeat until acceptable
```

### Pattern 4: ToolRegistry

```java
@JenticTool(name = "file_search", description = "Search for files")
public class FileSearchTool implements Tool {
    public ToolResult execute(Map<String, Object> args) {
        // Implementation
        return null;
    }
}

// Usage:
// ToolRegistry registry = new ToolRegistry();
// registry.scanPackage("dev.jentic.examples.tools");
```

## Implementation Notes

### Key Design Decisions

1. **ChainBehavior**: Wrapper pattern over FSMBehavior (not inheritance)
2. **Variable Substitution**: `${variable}` syntax in prompt templates
3. **Gate Combinators**: `.and()`, `.or()` for complex validation
4. **Worker Interface**: Simple, testable abstraction
5. **Tool Discovery**: Annotation-based (follows `@JenticAgent` pattern)

### Testing Strategy

**Unit Tests** (per pattern):
- Mock LLMProvider
- Test logic in isolation
- 90%+ coverage

**Integration Tests** (per pattern):
- Real Anthropic API (rate limited)
- End-to-end workflows
- Performance benchmarks

**Cross-Pattern Tests**:
- Combined usage (Chain + Orchestrator)
- Tool use in chains
- Memory integration

### Documentation Deliverables

Per pattern:
- JavaDoc (complete)
- Pattern guide (when/how to use)
- Example agent (working code)
- Configuration reference

Overall:
- This ADR (architectural overview)
- Updated main README

## Revision History

- 2026-01-20: Initial decision (approved)
- All four patterns covered in single ADR for coherence
