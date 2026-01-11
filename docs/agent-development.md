# Agent Development Guide

This guide shows how to build agents, behaviors, and message handlers with Jentic.

## Prerequisites
- Java 21+
- Maven 3.9+
- Add dependencies:
  - `dev.jentic:jentic-core`
  - `dev.jentic:jentic-runtime`

## Create Your First Agent
```java
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.core.Message;
import dev.jentic.runtime.agent.BaseAgent;

import static dev.jentic.core.BehaviorType.CYCLIC;

@JenticAgent("hello-agent")
public class HelloAgent extends BaseAgent {

    @JenticBehavior(type = CYCLIC, interval = "5s")
    public void sayHello() {
        messageService.send(Message.builder()
            .topic("greetings")
            .content("Hello from " + getAgentId())
            .build());
    }

    @JenticMessageHandler("greetings")
    public void onGreeting(Message message) {
        log.info("Received: {}", message.getContent());
    }
}
```

## Bootstrapping the Runtime
```java
import dev.jentic.runtime.JenticRuntime;

public class App {
    public static void main(String[] args) {
        var runtime = JenticRuntime.builder()
            .scanPackage("com.example.agents")
            .build();

        runtime.start();
    }
}
```

## Behaviors
Supported behavior types (see `jentic-runtime` implementations):
- Cyclic: run at a fixed interval
- One-shot: run once and complete
- Event-driven: react to incoming messages
- Waker: run once after a delay
- Composite: sequential, parallel, FSM (runtime support utilities)
- Advanced: conditional, throttled, batch, retry, circuit breaker, scheduled, pipeline

Annotate public methods on your agent class with `@JenticBehavior` and use `BehaviorType` plus optional timing parameters like `interval` or `delay`.

## Message Handling
Use `@JenticMessageHandler("topic")` on public methods that accept a `Message` parameter. The in-memory message service will deliver matching topic messages within the JVM.

## Persistence
If your agent maintains state, implement `dev.jentic.core.persistence.Stateful` and/or use the file-based `FilePersistenceService` via `PersistenceManager` in `jentic-runtime`.

## Configuration
- Minimal code-based configuration via `JenticRuntime.builder()`
- Optional YAML support via `ConfigurationLoader` (see Configuration Reference)

## Testing
- Use JUnit 5 and Mockito/AssertJ.
- For unit tests, instantiate your agent and invoke behavior methods directly.
- For integration-like tests, bootstrap a `JenticRuntime` with a small package and verify message exchanges.

## Examples
See `jentic-examples`:
- Ping/Pong basic messaging
- Weather Station cyclic producer
- Task Manager event-driven processing
- Advanced: Conditional and Throttled behaviors
- Filtering examples
- E-Commerce orchestration demo
- Discovery examples

## Next Steps
- Read the Architecture Guide (`docs/architecture.md`)
- Explore filters, rate limiting, and composite behaviors in `jentic-runtime`
