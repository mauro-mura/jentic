package dev.jentic.runtime.behavior.chain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Gate interface and factory methods.
 * 
 * @since 0.7.0
 */
class GateTest {
    
    @Test
    @DisplayName("minLength gate should pass for sufficient length")
    void minLengthShouldPass() {
        // Given
        Gate gate = Gate.minLength(10);
        String output = "This is long enough";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
        assertNull(result.message());
    }
    
    @Test
    @DisplayName("minLength gate should fail for insufficient length")
    void minLengthShouldFail() {
        // Given
        Gate gate = Gate.minLength(100);
        String output = "Short";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
        assertNotNull(result.message());
        assertTrue(result.message().contains("less than required"));
    }
    
    @Test
    @DisplayName("maxLength gate should pass for acceptable length")
    void maxLengthShouldPass() {
        // Given
        Gate gate = Gate.maxLength(100);
        String output = "Short text";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("maxLength gate should fail for excessive length")
    void maxLengthShouldFail() {
        // Given
        Gate gate = Gate.maxLength(5);
        String output = "This is too long";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
        assertTrue(result.message().contains("exceeds maximum"));
    }
    
    @Test
    @DisplayName("contains gate should pass when text is present")
    void containsShouldPass() {
        // Given
        Gate gate = Gate.contains("important");
        String output = "This is an important message";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("contains gate should fail when text is missing")
    void containsShouldFail() {
        // Given
        Gate gate = Gate.contains("missing");
        String output = "This text doesn't have it";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
        assertTrue(result.message().contains("does not contain"));
    }
    
    @Test
    @DisplayName("matches gate should pass for matching pattern")
    void matchesShouldPass() {
        // Given
        Gate gate = Gate.matches("\\d{3}-\\d{4}");
        String output = "Call me at 123-4567";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("matches gate should fail for non-matching pattern")
    void matchesShouldFail() {
        // Given
        Gate gate = Gate.matches("\\d{3}-\\d{4}");
        String output = "No phone number here";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("jsonValid gate should pass for valid JSON object")
    void jsonValidShouldPassForObject() {
        // Given
        Gate gate = Gate.jsonValid();
        String output = "{\"name\": \"test\", \"value\": 123}";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("jsonValid gate should pass for valid JSON array")
    void jsonValidShouldPassForArray() {
        // Given
        Gate gate = Gate.jsonValid();
        String output = "[1, 2, 3, \"test\"]";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("jsonValid gate should fail for invalid JSON")
    void jsonValidShouldFail() {
        // Given
        Gate gate = Gate.jsonValid();
        String output = "{invalid json";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("startsWith gate should pass when prefix matches")
    void startsWithShouldPass() {
        // Given
        Gate gate = Gate.startsWith("Hello");
        String output = "Hello world!";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("startsWith gate should fail when prefix doesn't match")
    void startsWithShouldFail() {
        // Given
        Gate gate = Gate.startsWith("Hello");
        String output = "Goodbye world!";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("endsWith gate should pass when suffix matches")
    void endsWithShouldPass() {
        // Given
        Gate gate = Gate.endsWith("end");
        String output = "This is the end";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("endsWith gate should fail when suffix doesn't match")
    void endsWithShouldFail() {
        // Given
        Gate gate = Gate.endsWith("end");
        String output = "This is the beginning";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("AND combinator should pass when both gates pass")
    void andCombinatorShouldPass() {
        // Given
        Gate gate = Gate.minLength(10).and(Gate.contains("test"));
        String output = "This is a test message";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("AND combinator should fail when first gate fails")
    void andCombinatorShouldFailOnFirst() {
        // Given
        Gate gate = Gate.minLength(100).and(Gate.contains("test"));
        String output = "Short test";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("AND combinator should fail when second gate fails")
    void andCombinatorShouldFailOnSecond() {
        // Given
        Gate gate = Gate.minLength(10).and(Gate.contains("notfound"));
        String output = "This is long enough but doesn't have the word";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("OR combinator should pass when first gate passes")
    void orCombinatorShouldPassOnFirst() {
        // Given
        Gate gate = Gate.contains("test").or(Gate.contains("missing"));
        String output = "This has test in it";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("OR combinator should pass when second gate passes")
    void orCombinatorShouldPassOnSecond() {
        // Given
        Gate gate = Gate.contains("missing").or(Gate.contains("test"));
        String output = "This has test in it";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertTrue(result.passed());
    }
    
    @Test
    @DisplayName("OR combinator should fail when both gates fail")
    void orCombinatorShouldFail() {
        // Given
        Gate gate = Gate.contains("missing1").or(Gate.contains("missing2"));
        String output = "Neither text is here";
        
        // When
        GateResult result = gate.validate(output);
        
        // Then
        assertFalse(result.passed());
    }
    
    @Test
    @DisplayName("Complex gate combination should work correctly")
    void complexCombinationShouldWork() {
        // Given
        Gate gate = Gate.minLength(20)
            .and(Gate.contains("Introduction"))
            .and(Gate.contains("Conclusion").or(Gate.contains("Summary")));
        
        String validOutput = "Introduction: This is a long enough text. Summary: Done.";
        String invalidOutput = "Short intro";
        
        // When
        GateResult validResult = gate.validate(validOutput);
        GateResult invalidResult = gate.validate(invalidOutput);
        
        // Then
        assertTrue(validResult.passed());
        assertFalse(invalidResult.passed());
    }
    
    @Test
    @DisplayName("alwaysPass gate should always pass")
    void alwaysPassShouldPass() {
        // Given
        Gate gate = Gate.alwaysPass();
        
        // When/Then
        assertTrue(gate.validate("anything").passed());
        assertTrue(gate.validate("").passed());
        assertTrue(gate.validate(null).passed());
    }
    
    @Test
    @DisplayName("alwaysFail gate should always fail")
    void alwaysFailShouldFail() {
        // Given
        Gate gate = Gate.alwaysFail("Custom failure");
        
        // When
        GateResult result = gate.validate("anything");
        
        // Then
        assertFalse(result.passed());
        assertEquals("Custom failure", result.message());
    }
    
    @Test
    @DisplayName("Gates should handle null input gracefully")
    void shouldHandleNullInput() {
        // Given
        Gate minLengthGate = Gate.minLength(10);
        Gate containsGate = Gate.contains("test");
        
        // When
        GateResult result1 = minLengthGate.validate(null);
        GateResult result2 = containsGate.validate(null);
        
        // Then
        assertFalse(result1.passed());
        assertFalse(result2.passed());
        assertTrue(result1.message().contains("null"));
        assertTrue(result2.message().contains("null"));
    }
}
