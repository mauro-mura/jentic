# ADR-004: Progressive Complexity Strategy

**Status**: Accepted  
**Date**: 2025-09-16  
**Authors**: Project Team  

### Context

Different users have different needs - from simple prototypes to enterprise production systems. We want to accommodate both without forcing complexity on simple use cases.

### Decision

We will implement a **Progressive Complexity Strategy** where users can start simple and evolve to more sophisticated implementations as needed.

### Evolution Path

```
Level 1: In-Memory     → Level 2: Persistent    → Level 3: Distributed
├─ InMemoryMessage     → ├─ JmsMessageService   → ├─ KafkaMessageService
├─ LocalDirectory      → ├─ DatabaseDirectory   → ├─ ConsulDirectory  
└─ SimpleScheduler     → └─ QuartzScheduler     → └─ KubernetesScheduler
```

### Implementation Strategy

1. **Start Simple**: MVP uses in-memory implementations
2. **Add Persistence**: V1.1 adds database and JMS options
3. **Scale Distributed**: V1.2 adds Kafka, Consul, etc.
4. **Cloud Native**: V2.0 adds Kubernetes, service mesh support

### Configuration Evolution

```yaml
# Level 1: Minimal configuration
jentic:
  messaging:
    provider: in-memory

# Level 2: Adding persistence  
jentic:
  messaging:
    provider: database
    properties:
      url: jdbc:postgresql://localhost/jentic

# Level 3: Distributed systems
jentic:
  messaging:
    provider: kafka
    properties:
      bootstrap-servers: kafka:9092
      consumer-group: jentic-agents
```

### Benefits

- **Low Barrier to Entry**: New users can start immediately
- **No Over-Engineering**: Simple use cases stay simple
- **Clear Upgrade Path**: Evolution path is documented and supported
- **Reduced Lock-In**: Users can migrate implementations without code changes

### Consequences

- **Positive**: Appeals to both beginners and enterprise users
- **Positive**: Allows organic growth of complexity
- **Positive**: Reduces initial learning curve
- **Negative**: Need to maintain multiple implementations
- **Negative**: Documentation must cover all complexity levels
