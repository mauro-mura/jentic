# Jentic

[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#)

> **Jentic** - Modern Multi-Agent Framework for Java

Jentic is a contemporary multi-agent framework that modernizes the concepts pioneered by JADE, bringing them into the cloud-native era with Java 21, virtual threads, and a pragmatic approach to distributed systems.

## 🚀 Vision

Jentic reimagines multi-agent systems with modern Java practices:

- **Start Simple, Scale Smart**: Begin with in-memory implementations, evolve to enterprise solutions
- **Interface-First Design**: Clean abstractions that enable seamless technology transitions  
- **Cloud-Native Ready**: Container-friendly, microservices-oriented, Kubernetes-native
- **Developer Experience**: Hot reload, clear APIs, minimal configuration
- **Virtual Threads**: Leverage Java 21's Project Loom for efficient concurrency

## ⚡ Quick Start

### Prerequisites

- Java 21+ (LTS recommended)
- Maven 3.9+

### Installation

```bash
git clone https://github.com/mauro-mura/jentic.git
cd jentic
mvn clean install
```

### Your First Agent

```java
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
    public void handleGreeting(Message message) {
        log.info("Received: {}", message.getContent());
    }
}
```

### Running

```java
public class HelloWorld {
    public static void main(String[] args) {
        var runtime = JenticRuntime.builder()
            .scanPackage("com.example.agents")
            .build();
            
        runtime.start();
    }
}
```

## 🏗️ Architecture

Jentic follows a modular, interface-first architecture:

For details, read the Architecture Guide at docs/architecture.md.

```
┌──────────────────┬─────────────────┬─────────────────┐
│   jentic-core    │ jentic-runtime  │ jentic-adapters │
│   (interfaces)   │ (basic impls)   │ (enterprise)    │
├──────────────────┼─────────────────┼─────────────────┤
│ Agent            │ BaseAgent       │ KafkaMessage    │
│ MessageService   │ InMemoryMessage │ ConsulDirectory │
│ AgentDirectory   │ LocalDirectory  │ QuartzScheduler │
│ BehaviorScheduler│ SimpleScheduler │ RedisMessage    │
└──────────────────┴─────────────────┴─────────────────┘
```

### Core Components

- **Agent**: Autonomous entity with behaviors and message handling
- **MessageService**: Asynchronous communication between agents
- **AgentDirectory**: Service discovery and registration
- **BehaviorScheduler**: Execution management for agent behaviors

### Evolution Path

```
MVP (In-Memory) → V1.1 (JMS/DB) → V1.2 (Kafka/Consul) → V2.0 (Cloud)
```

## 🔧 Configuration

Simple YAML configuration:

```yaml
jentic:
  runtime:
    name: my-agent-system
  
  agents:
    auto-discovery: true
    base-package: "com.example.agents"
  
  messaging:
    provider: in-memory  # Evolution: jms, kafka
    
  directory:
    provider: local      # Evolution: database, consul
```

## 📦 Modules

### jentic-core
Core interfaces and abstractions. No implementations, just contracts.

```xml
<dependency>
    <groupId>dev.jentic</groupId>
    <artifactId>jentic-core</artifactId>
    <version>0.7.0-SNAPSHOT</version>
</dependency>
```

### jentic-runtime
Basic implementations for getting started quickly.

```xml
<dependency>
    <groupId>dev.jentic</groupId>
    <artifactId>jentic-runtime</artifactId>
    <version>0.7.0-SNAPSHOT</version>
</dependency>
```

### jentic-adapters
Basic implementations for LLMs (OpenAI, Anthropic, Ollama).
Enterprise-grade implementations (coming in future releases).

```xml
<dependency>
    <groupId>dev.jentic</groupId>
    <artifactId>jentic-adapters</artifactId>
    <version>0.7.0-SNAPSHOT</version>
</dependency>
```

### jentic-tools
Web Console and CLI tools.

```xml
<dependency>
    <groupId>dev.jentic</groupId>
    <artifactId>jentic-tools</artifactId>
    <version>0.7.0-SNAPSHOT</version>
</dependency>
```

## 🚀 Features

### Current (MVP)
- [x] Agent lifecycle management
- [x] In-memory message passing
- [x] Local agent directory
- [x] Annotation-based configuration (agents, behaviors, handlers)
- [x] Behavior types: Cyclic, One-shot, Event-driven, Waker
- [x] Composite behaviors: Sequential, Parallel, FSM
- [x] Advanced behaviors: Conditional, Throttled
- [x] Message filtering (topic, header, predicate, composite)
- [x] Rate limiting (token bucket, sliding window)
- [x] File-based persistence utilities
- [x] YAML configuration support
- [x] Web management console
- [x] CLI tools

### Planned (V1.0)
- [ ] JMS message integration
- [ ] Database-backed agent directory
- [ ] Docker containerization

### Future
- [ ] Kafka message streaming
- [ ] Consul service discovery
- [ ] Agent migration
- [ ] Clustering support
- [ ] Kubernetes operators

## 📚 Examples

Check out the `jentic-examples` module for complete examples:

- Ping-Pong: `dev.jentic.examples.PingPongExample`
- Simple bootstrap: `dev.jentic.examples.SimpleExample`
- Weather Station: `dev.jentic.examples.WeatherStationExample`
- Task Manager: `dev.jentic.examples.TaskManagerExample`
- Advanced - Conditional Behavior: `dev.jentic.examples.behaviors.ConditionalBehaviorExample`
- Advanced - Throttled Behavior: `dev.jentic.examples.behaviors.ThrottledExample`
- Filtering: `dev.jentic.examples.filtering.MessageFilterExample`
- Discovery pattern: `dev.jentic.examples.DiscoveryExample`
- E-Commerce orchestration demo: `dev.jentic.examples.ecommerce.ECommerceApplication`

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Setup

```bash
# Clone and setup
git clone https://github.com/mauro-mura/jentic.git
cd jentic

# Build and test
mvn clean test

# Run examples
cd jentic-examples
mvn exec:java -Dexec.mainClass="dev.jentic.examples.PingPongExample"
```

## 📖 Documentation

- [Architecture Guide](docs/architecture.md)
- [Agent Development Guide](docs/agent-development.md)
- [Configuration Reference](docs/configuration.md)

## 🗓️ Roadmap

### Phase 1 (Weeks 1-5) - Foundation ✅
Core interfaces and in-memory implementations

### Phase 2 (Weeks 6-10) - Behaviors 🚧
Advanced behavior patterns and configuration

### Phase 3 (Weeks 11-16) - Management
Web console and monitoring

### Phase 4 (Weeks 17-20) - Production
Docker, documentation, and release

## 💡 Why Jentic?

**vs. JADE:**
- Modern Java (21 vs 8)
- Virtual threads vs traditional threading
- Cloud-native vs desktop-oriented
- Interface-first vs monolithic
- Reactive patterns vs blocking I/O

**vs. Building from Scratch:**
- Proven multi-agent patterns
- Gradual complexity adoption
- Clear migration paths
- Enterprise-ready evolution

## 📄 License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

## 🙋 Support

- 🐛 Issues: [GitHub Issues](https://github.com/mauro-mura/jentic/issues)

## 🏆 Acknowledgments

- **JADE Framework**: For pioneering multi-agent systems in Java
- **Spring Framework**: For inspiration on clean architecture patterns
- **Project Loom**: For making concurrent programming accessible

---

**Built with ❤️ and ☕ for the Java community**