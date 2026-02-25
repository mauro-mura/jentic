# SequentialBehavior - Step-by-Step Execution

## Overview

`SequentialBehavior` executes child behaviors **one after another**, waiting for each to complete before starting the next. It supports both single-pass and repeating-sequence modes.

**Since**: v0.3.0 | **Type**: `BehaviorType.SEQUENTIAL` | **Package**: `dev.jentic.runtime.behavior.composite` | **Extends**: `CompositeBehavior`

---

## Execution Modes

| Mode | `repeatSequence` | Behavior |
|------|-----------------|----------|
| **One-shot** (default) | `false` | Runs all steps once, then becomes inactive |
| **Repeating** | `true` | Each scheduler tick advances one step; wraps around |

In **repeating** mode, the `BehaviorScheduler` calls `execute()` on each tick. Each call executes exactly one step, then returns. When the last step finishes, the index resets to 0.

In **one-shot** mode, a single `execute()` call chains all steps internally via `CompletableFuture` composition and then sets `active=false`.

---

## Basic Usage

### One-shot sequence

```java
SequentialBehavior startup = new SequentialBehavior("startup");
startup.addChild(new ConnectDatabaseBehavior());
startup.addChild(new LoadConfigurationBehavior());
startup.addChild(new RegisterWithDirectoryBehavior());

agent.addBehavior(startup);
```

### Repeating sequence (round-robin)

```java
SequentialBehavior roundRobin = new SequentialBehavior("round-robin", true);
roundRobin.addChild(new ProcessQueueABehavior());
roundRobin.addChild(new ProcessQueueBBehavior());
roundRobin.addChild(new ProcessQueueCBehavior());

agent.addBehavior(roundRobin);
```

---

## Step Timeout

```java
SequentialBehavior pipeline = new SequentialBehavior(
    "pipeline",
    false,
    Duration.ofSeconds(10) // each step must complete within 10s
);
pipeline.addChild(new StepOneBehavior());
pipeline.addChild(new StepTwoBehavior());
```

---

## Constructors

```java
new SequentialBehavior(String behaviorId)
new SequentialBehavior(String behaviorId, boolean repeatSequence)
new SequentialBehavior(String behaviorId, boolean repeatSequence, Duration stepTimeout)
```

---

## API Reference

```java
int current = behavior.getCurrentStep();   // zero-based index of the next step to execute
int total   = behavior.getTotalSteps();    // total number of child behaviors

behavior.reset();                           // restart sequence from step 0

behavior.setStepTimeout(Duration.ofSeconds(5));
Duration t  = behavior.getStepTimeout();
```

---

## Error Handling

- If a step throws or times out, the error is logged, and execution **advances to the next step** regardless.
- The sequence never aborts mid-way due to a single step failure.

---

## Use Cases

- Agent startup / shutdown sequences
- Round-robin polling across multiple queues or endpoints
- Multi-step data migration workflows
- Ordered initialization of subsystems

---

## See Also

- [ParallelBehavior](ParallelBehavior.md) - Concurrent execution
- [FSMBehavior](FSMBehavior.md) - Condition-driven state transitions
- [PipelineBehavior](PipelineBehavior.md) - Data transformation chains
- [Behavior Overview](README.md)

---

**Since**: Jentic 0.3.0  
**Status**: Production Ready ✅
