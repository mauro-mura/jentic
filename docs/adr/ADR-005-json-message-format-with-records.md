# ADR-005: JSON Message Format with Records

**Status**: Accepted  
**Date**: 2025-09-16  
**Authors**: Project Team  

### Context

We need a message format that is human-readable, language-agnostic, performant, and leverages modern Java features.

### Decision

We will use **JSON as the message serialization format** with **Java Records** for message representation.

### Message Structure

```java
public record Message(
    String id,                    // Unique message identifier
    String topic,                 // Message topic/channel
    String senderId,              // Sending agent ID
    String receiverId,            // Target agent ID (optional)
    String correlationId,         // For request/response patterns
    Object content,               // Message payload
    Map<String, String> headers,  // Additional metadata
    Instant timestamp             // Message creation time
) {
    // Builder pattern and utility methods
}
```

### Serialization

- **Library**: Jackson (widely adopted, excellent performance)
- **Format**: JSON (human-readable, debugging-friendly)
- **Types**: Full support for Java time types, collections
- **Compatibility**: Cross-language interoperability

### Benefits

- **Immutability**: Records are immutable by default
- **Conciseness**: Less boilerplate compared to traditional classes
- **Performance**: Jackson handles records efficiently
- **Debugging**: JSON is human-readable
- **Interoperability**: JSON works with any language

### Message Examples

```json
{
  "id": "msg-12345",
  "topic": "weather.data",
  "senderId": "weather-agent-1",
  "content": {
    "location": "Rome",
    "temperature": 22.5,
    "humidity": 65
  },
  "headers": {
    "content-type": "weather-data",
    "priority": "normal"
  },
  "timestamp": "2024-03-15T10:30:00Z"
}
```

### Consequences

- **Positive**: Modern, immutable, concise message representation
- **Positive**: Human-readable format aids debugging
- **Positive**: Cross-language compatibility
- **Positive**: Excellent tooling support
- **Negative**: Slightly larger than binary formats
- **Negative**: JSON parsing overhead (acceptable for most use cases)
