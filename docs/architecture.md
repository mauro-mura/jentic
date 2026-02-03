# Jentic Architecture Guide

This document describes the architecture of Jentic, a modern, interface‑first multi‑agent framework for Java 21+.

- Audience: developers evaluating or building on Jentic
- Scope: high‑level structure, core abstractions, runtime behavior, and extension points

## 1. Architectural Overview

Jentic embraces an interface‑first, modular architecture. Core contracts live in jentic-core, while minimal, ready‑to‑use implementations live in jentic-runtime. Advanced/enterprise adapters will live in jentic-adapters.

```
┌──────────────────┬─────────────────────────┬─────────────────────┐
│   jentic-core    │    jentic-runtime       │    jentic-adapters  │
│  (interfaces)    │ (in‑memory impls)       │ (enterprise impls)  │
├──────────────────┼─────────────────────────┼─────────────────────┤
│ Agent            │ BaseAgent               │ Kafka (Planned)     │
│ Message          │ InMemoryMessageService  │ Consul (Planned)    │
│ MessageService   │ LocalAgentDirectory     │ A2A Adapter         │
│ AgentDirectory   │ SimpleBehaviorScheduler │ LLM Adapters        │
│ Behavior         │ Behaviors (Cyclic…)     │ Redis (Planned)     │
│ BehaviorScheduler│ Discovery/Scanning      │                     │
└──────────────────┴─────────────────────────┴─────────────────────┘
```

Design goals:
- Start simple, scale smart (ADR-004)
- Interface‑first contracts (ADR-002)
- Modern Java 21, virtual threads (ADR-001)
- JSON record-based messages (ADR-005)
- Annotation-based configuration (ADR-006)

## 2. Modules

- jentic-core: Pure interfaces, records, annotations, and exceptions. No heavy dependencies.
- jentic-runtime: Minimal, production‑ready in‑memory implementations to get started fast.
- jentic-adapters: Optional enterprise adapters (messaging, directory, schedulers). Planned.
- jentic-examples: Demonstrates usage patterns and best practices.

## 3. Core Abstractions (jentic-core)

- Agent: Lifecycle contract for autonomous entities; exposes id, status, and context.
- Behavior: Unit of work associated with an Agent. Types include CYCLIC, ONE_SHOT, EVENT_DRIVEN, WAKER.
- Message: Transport‑agnostic payload record (topic, headers, content, metadata).
- MessageService: Send/receive API for inter‑agent messaging.
- AgentDirectory: Register, discover, query, and list agents.
- BehaviorScheduler: Schedules and executes behaviors per their semantics and policy.
- Annotations: @JenticAgent, @JenticBehavior, @JenticMessageHandler declare agents/behaviors/handlers.

These are deliberately small to keep adapters swappable without breaking user code.

## 4. Runtime Implementations (jentic-runtime)

- BaseAgent: Convenience base class wiring message handling, behavior registration, and utilities.
- Behaviors: 
  - CyclicBehavior: executes at fixed intervals
  - OneShotBehavior: runs once and completes
  - EventDrivenBehavior: reacts to incoming messages/events
  - WakerBehavior: runs after a delay
- SimpleBehaviorScheduler: Virtual‑thread friendly scheduler that executes behaviors with minimal overhead.
- InMemoryMessageService: Publish/subscribe per topic within a single JVM.
- LocalAgentDirectory: JVM‑local registry for discovery.
- AgentScanner + AnnotationProcessor: Scans packages for annotated agents/handlers and wires runtime.
- JenticRuntime: Entry point to bootstrap, start, and stop the agent system.

## 5. Concurrency Model

- Jentic targets Java 21 virtual threads (Project Loom) for lightweight concurrency.
- Behaviors are executed in virtual threads by the scheduler when appropriate.
- Blocking operations in behaviors do not monopolize platform threads, simplifying the programming model.
- Message handlers should remain responsive; long‑running work can be delegated to behaviors or separate virtual threads.

## 6. Messaging Flow

1) An Agent publishes a Message via MessageService.send.
2) The MessageService routes the message by topic to interested handlers.
3) Agents subscribe implicitly via @JenticMessageHandler(topic) annotated methods.
4) In-memory implementation delivers messages synchronously/asynchronously within the JVM.
5) Adapters (future) can switch transport (JMS/Kafka) without changing user code.

## 7. Discovery & Lifecycle

- AgentDirectory registers agents at startup and maintains status (STARTING, RUNNING, STOPPED, etc.).
- Agents may query other agents via AgentDirectory using AgentQuery.
- JenticRuntime orchestrates:
  - scanning configured base packages
  - constructing agents via AgentFactory
  - registering agents in the directory
  - scheduling declared behaviors
  - wiring message handlers to the MessageService

## 8. Configuration

- Minimal configuration via code (builder) and/or YAML (planned). Example keys:
  - jentic.runtime.name
  - jentic.agents.auto-discovery
  - jentic.agents.base-package
  - jentic.messaging.provider (in-memory, jms, kafka)
  - jentic.directory.provider (local, db, consul)

Implementations are selected by configuration while code depends only on core interfaces.

## 9. Extensibility Points

To integrate enterprise technologies, implement core contracts:
- MessageService: swap transport (JMS, Kafka, Redis Streams)
- AgentDirectory: swap discovery (DB, Consul, etcd)
- BehaviorScheduler: advanced scheduling (Quartz, cron, priority queues)

Guidelines:
- Keep adapters dependency‑isolated within jentic-adapters submodules.
- Avoid leaking implementation types into user code; rely on core interfaces.

## 10. Error Handling & Observability

- Exceptions derive from JenticException hierarchy (AgentException, MessageException).
- Logging via SLF4J with pluggable backend (logback in tests/examples).
- Planned: metrics for behavior execution, message throughput, and directory health.

## 11. Evolution Path

- MVP: in‑memory runtime for simple single‑JVM systems.
- Future: JMS + DB adapters, management/CLI, Kafka + Consul, clustering, cloud deployment.

See [ADRs](adr/README.md) for rationale and decisions:

## 12. Example Bootstrapping

```java
public class Main {
    public static void main(String[] args) {
        var runtime = JenticRuntime.builder()
            .scanPackage("dev.jentic.examples")
            .build();

        runtime.start();
    }
}
```

Agents are discovered, registered, and their behaviors scheduled automatically.

## 13. Glossary

- Agent: Autonomous unit of computation and coordination.
- Behavior: Scheduled unit of work owned by an Agent.
- Message: Topic‑addressed payload exchanged between agents.
- Directory: Registry that enables discovery and status tracking of agents.
- Scheduler: Component responsible for behavior execution policy.
