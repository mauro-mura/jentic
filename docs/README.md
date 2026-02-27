# Jentic Documentation

This directory contains the reference documentation for the Jentic framework.

New to Jentic? Start with the **[Getting Started Guide](getting-started.md)**.

---

## Core Guides

| Document | Description |
|----------|-------------|
| [Getting Started](getting-started.md) | Build and run your first agent in 5 minutes |
| [Architecture Guide](architecture.md) | Modules, abstractions, and design decisions |
| [Agent Development Guide](agent-development.md) | Lifecycle, annotations, patterns |
| [Configuration Guide](configuration.md) | YAML config, environment variables |
| [LLM Integration Guide](llm-integration.md) | OpenAI, Anthropic, Ollama providers |
| [Memory Guide](memory.md) | MemoryStore, persistence, `@JenticPersist` |
| [Dialogue Protocol](dialog-protocol.md) | A2A protocol, request/reply, CFP |
| [Message Filtering Guide](message-filtering.md) | Filters, rate limiting |

## Behaviors

| Document | Type |
|----------|------|
| [Behaviors Overview](behaviors/README.md) | All behavior types at a glance |
| [CyclicBehavior](behaviors/CyclicBehavior.md) | Repeat at fixed interval |
| [OneShotBehavior](behaviors/OneShotBehavior.md) | Execute once and stop |
| [EventDrivenBehavior](behaviors/EventDrivenBehavior.md) | React to messages |
| [WakerBehavior](behaviors/WakerBehavior.md) | Wake on condition or time |
| [ScheduledBehavior](behaviors/ScheduledBehavior.md) | Cron-based scheduling |
| [SequentialBehavior](behaviors/SequentialBehavior.md) | Step-by-step execution |
| [ParallelBehavior](behaviors/ParallelBehavior.md) | Concurrent child behaviors |
| [FSMBehavior](behaviors/FSMBehavior.md) | Finite State Machine |
| [ConditionalBehavior](behaviors/ConditionalBehavior.md) | Gate on a condition |
| [ThrottledBehavior](behaviors/ThrottledBehavior.md) | Rate-limited execution |
| [RetryBehavior](behaviors/RetryBehavior.md) | Automatic retry with back-off |
| [BatchBehavior](behaviors/BatchBehavior.md) | Bulk item processing |
| [CircuitBreakerBehavior](behaviors/CircuitBreakerBehavior.md) | Fault-tolerance pattern |
| [PipelineBehavior](behaviors/PipelineBehavior.md) | Multi-stage transformation |

## Architecture Decision Records

All architectural decisions are recorded in [`adr/README.md`](adr/README.md).

## Examples

Runnable examples with a structured learning path are in [`jentic-examples/README.md`](../jentic-examples/README.md).
