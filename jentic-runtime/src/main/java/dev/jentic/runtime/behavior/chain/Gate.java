package dev.jentic.runtime.behavior.chain;

import java.util.regex.Pattern;

/**
 * Validation gate for ChainBehavior step outputs.
 * 
 * <p>Gates validate LLM outputs before proceeding to the next step.
 * If validation fails, the configured GateAction determines behavior
 * (abort, retry, or continue).
 * 
 * <p>Example usage:
 * <pre>{@code
 * Gate lengthGate = Gate.minLength(100);
 * Gate contentGate = Gate.contains("Introduction");
 * Gate combined = lengthGate.and(contentGate);
 * }</pre>
 * 
 * @since 0.7.0
 */
@FunctionalInterface
public interface Gate {
    
    /**
     * Validates the output string.
     * 
     * @param output the LLM output to validate
     * @return validation result
     */
    GateResult validate(String output);
    
    /**
     * Combines this gate with another using AND logic.
     * 
     * @param other the other gate
     * @return combined gate that passes only if both pass
     */
    default Gate and(Gate other) {
        return output -> {
            GateResult first = this.validate(output);
            if (!first.passed()) {
                return first;
            }
            return other.validate(output);
        };
    }
    
    /**
     * Combines this gate with another using OR logic.
     * 
     * @param other the other gate
     * @return combined gate that passes if either passes
     */
    default Gate or(Gate other) {
        return output -> {
            GateResult first = this.validate(output);
            if (first.passed()) {
                return first;
            }
            return other.validate(output);
        };
    }
    
    /**
     * Creates a gate that validates minimum output length.
     * 
     * @param minLength minimum character count
     * @return gate instance
     */
    static Gate minLength(int minLength) {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (output.length() >= minLength) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output length " + output.length() + 
                " is less than required " + minLength);
        };
    }
    
    /**
     * Creates a gate that validates maximum output length.
     * 
     * @param maxLength maximum character count
     * @return gate instance
     */
    static Gate maxLength(int maxLength) {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (output.length() <= maxLength) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output length " + output.length() + 
                " exceeds maximum " + maxLength);
        };
    }
    
    /**
     * Creates a gate that checks if output contains specific text.
     * 
     * @param text required substring
     * @return gate instance
     */
    static Gate contains(String text) {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (output.contains(text)) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output does not contain required text: '" + text + "'");
        };
    }
    
    /**
     * Creates a gate that checks if output matches a regex pattern.
     * 
     * @param pattern regex pattern
     * @return gate instance
     */
    static Gate matches(String pattern) {
        Pattern compiled = Pattern.compile(pattern);
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (compiled.matcher(output).find()) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output does not match pattern: " + pattern);
        };
    }
    
    /**
     * Creates a gate that validates JSON format.
     * 
     * @return gate instance
     */
    static Gate jsonValid() {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            String trimmed = output.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                // Basic JSON structure check
                try {
                    validateJsonStructure(trimmed);
                    return GateResult.pass();
                } catch (Exception e) {
                    return GateResult.fail(
                        "Invalid JSON structure: " + e.getMessage());
                }
            }
            return GateResult.fail("Output is not valid JSON");
        };
    }
    
    /**
     * Creates a gate that checks if output starts with specific text.
     * 
     * @param prefix required prefix
     * @return gate instance
     */
    static Gate startsWith(String prefix) {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (output.startsWith(prefix)) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output does not start with: '" + prefix + "'");
        };
    }
    
    /**
     * Creates a gate that checks if output ends with specific text.
     * 
     * @param suffix required suffix
     * @return gate instance
     */
    static Gate endsWith(String suffix) {
        return output -> {
            if (output == null) {
                return GateResult.fail("Output is null");
            }
            if (output.endsWith(suffix)) {
                return GateResult.pass();
            }
            return GateResult.fail(
                "Output does not end with: '" + suffix + "'");
        };
    }
    
    /**
     * Creates a gate that always passes.
     * 
     * @return gate instance
     */
    static Gate alwaysPass() {
        return output -> GateResult.pass();
    }
    
    /**
     * Creates a gate that always fails.
     * 
     * @param message failure message
     * @return gate instance
     */
    static Gate alwaysFail(String message) {
        return output -> GateResult.fail(message);
    }
    
    /**
     * Basic JSON structure validation.
     */
    private static void validateJsonStructure(String json) {
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : json.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            switch (c) {
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
            }
            
            if (braceDepth < 0 || bracketDepth < 0) {
                throw new IllegalArgumentException(
                    "Unmatched closing bracket/brace");
            }
        }
        
        if (braceDepth != 0 || bracketDepth != 0) {
            throw new IllegalArgumentException(
                "Unclosed brackets or braces");
        }
    }
}
