# Configuration Reference

Jentic supports simple configuration via code (builder) and YAML files. This page documents the current configuration keys and formats.

## Loading Configuration

Use `ConfigurationLoader` from `jentic-core` to load configuration from various sources:

```java
import dev.jentic.core.config.ConfigurationLoader;

var loader = new ConfigurationLoader();
var config = loader.loadDefault();                 // classpath:jentic.yml or jentic.yaml if present
// or
var config = loader.loadFromFile("./jentic.yml"); // explicit file path
// or
var config = loader.loadFromClasspath("jentic-test.yml"); // from classpath
```

Environment variable substitution is supported using `${ENV_VAR}` syntax inside YAML content.

## YAML Structure

```yaml
jentic:
  runtime:
    name: my-agent-system

  agents:
    autoDiscovery: true
    basePackage: "dev.jentic.examples"
    # Or use scanPackages for multiple packages:
    # scanPackages:
    #   - "dev.jentic.examples"
    #   - "com.example.other"

  messaging:
    provider: in-memory   # future: jms, kafka

  directory:
    provider: local       # future: database, consul

  scheduler:
    # default in-memory simple scheduler; future options may be added
    mode: simple
```

Notes:
- Keys map to `dev.jentic.core.JenticConfiguration` via `JenticConfigurationWrapper`.
- Unknown keys are ignored by the loader.

## Programmatic Configuration

You can configure the runtime without YAML:

```java
import dev.jentic.runtime.JenticRuntime;

public class App {
    public static void main(String[] args) {
        var runtime = JenticRuntime.builder()
            .scanPackage("dev.jentic.examples")
            .build();

        runtime.start();
    }
}
```

## Persistence

`jentic-runtime` provides a file-based persistence service suitable for development/testing. Configure persistence at the agent or service level as needed. For now, persistence is enabled programmatically via `PersistenceManager`.

## Logging

Logging uses SLF4J. In tests/examples, Logback is included; provide your own `logback.xml` or `logback-test.xml` as desired.

## Example File

See `jentic-examples/src/main/resources/jentic-test.yml` for a working example used in tests/examples.
