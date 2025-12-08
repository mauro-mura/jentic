package dev.jentic.core.console;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleEventListenerTest {

    @Test
    void noOpShouldNotThrowOnBehaviorExecuted() {
        ConsoleEventListener listener = ConsoleEventListener.noOp();
        
        // Should not throw
        assertDoesNotThrow(() -> 
            listener.onBehaviorExecuted("agent-1", "behavior-1", 100L, true, null)
        );
        
        assertDoesNotThrow(() -> 
            listener.onBehaviorExecuted("agent-1", "behavior-1", 50L, false, "Connection refused")
        );
    }
}