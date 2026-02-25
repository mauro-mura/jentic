# ParallelBehavior - Concurrent Child Execution

## Overview

`ParallelBehavior` executes multiple child behaviors **concurrently** using virtual threads. It supports four completion strategies to control when the composite behavior itself is considered done.

**Since**: v0.3.0 | **Type**: `BehaviorType.PARALLEL` | **Package**: `dev.jentic.runtime.behavior.composite` | **Extends**: `CompositeBehavior`

---

## Completion Strategies

| Strategy | Completes when… |
|----------|----------------|
| `ALL` *(default)* | All child behaviors have finished |
| `ANY` | At least one child has finished (others continue) |
| `FIRST` | The first child finishes; remaining children are stopped |
| `N_OF_M` | At least N children have finished successfully |

---

## Basic Usage

```java
ParallelBehavior parallel = new ParallelBehavior("data-fetch", CompletionStrategy.ALL);
parallel.addChild(new FetchUsersBehavior());
parallel.addChild(new FetchProductsBehavior());
parallel.addChild(new FetchOrdersBehavior());

agent.addBehavior(parallel);
```

---

## Completion Strategy Examples

### ANY — proceed as soon as one source responds

```java
ParallelBehavior fastest = new ParallelBehavior("fastest-cache", CompletionStrategy.ANY);
fastest.addChild(new ReadFromLocalCacheBehavior());
fastest.addChild(new ReadFromRemoteCacheBehavior());
fastest.addChild(new ReadFromDatabaseBehavior());
agent.addBehavior(fastest);
```

### FIRST — race, cancel losers

```java
ParallelBehavior race = new ParallelBehavior("geo-lookup", CompletionStrategy.FIRST);
race.addChild(new GeoLookupProvider1Behavior());
race.addChild(new GeoLookupProvider2Behavior());
agent.addBehavior(race);
```

### N_OF_M — quorum

```java
// Require 2 of 3 replicas to confirm
ParallelBehavior quorum = new ParallelBehavior("write-quorum", CompletionStrategy.N_OF_M, 2);
quorum.addChild(new WriteToReplica1Behavior());
quorum.addChild(new WriteToReplica2Behavior());
quorum.addChild(new WriteToReplica3Behavior());
agent.addBehavior(quorum);
```

---

## Child Timeouts

```java
ParallelBehavior parallel = new ParallelBehavior(
    "bounded-parallel",
    CompletionStrategy.ALL,
    0,
    Duration.ofSeconds(5) // each child times out after 5s
);
parallel.addChild(new SlowBehavior());
parallel.addChild(new FastBehavior());
```

---

## Constructors

```java
new ParallelBehavior(String behaviorId)
new ParallelBehavior(String behaviorId, CompletionStrategy strategy)
new ParallelBehavior(String behaviorId, CompletionStrategy strategy, int requiredCompletions)
new ParallelBehavior(String behaviorId, CompletionStrategy strategy,
                     int requiredCompletions, Duration childTimeout)
```

`requiredCompletions` is only used with `N_OF_M`. If out of range, falls back to `ALL`.

---

## API Reference

```java
CompletionStrategy s = behavior.getStrategy();
int completed        = behavior.getCompletedCount();  // successful completions
int finished         = behavior.getFinishedCount();   // success + failure

behavior.setChildTimeout(Duration.ofSeconds(10));
Duration t           = behavior.getChildTimeout();
```

---

## Error Handling

- A child that throws or times out increments `finishedCount` but **not** `completedCount`.
- The parallel behavior itself does not fail if a child fails; it logs a warning and continues.
- For `N_OF_M`, only successful completions count towards the quorum.

---

## See Also

- [SequentialBehavior](SequentialBehavior.md) - One-at-a-time execution
- [FSMBehavior](FSMBehavior.md) - State-based composition
- [Behavior Overview](README.md)

---

**Since**: Jentic 0.3.0  
**Status**: Production Ready ✅
