package dev.jentic.core.filter;

import dev.jentic.core.Message;

import java.util.function.Predicate;

/**
 * Interface for filtering messages based on various criteria.
 * Filters can be composed using AND, OR, NOT operations.
 */
@FunctionalInterface
public interface MessageFilter extends Predicate<Message> {
    
    /**
     * Test if the message matches this filter
     * 
     * @param message the message to test
     * @return true if the message matches the filter criteria
     */
    @Override
    boolean test(Message message);
    
    /**
     * Create a filter that matches all messages
     * 
     * @return filter that always returns true
     */
    static MessageFilter acceptAll() {
        return message -> true;
    }
    
    /**
     * Create a filter that rejects all messages
     * 
     * @return filter that always returns false
     */
    static MessageFilter rejectAll() {
        return message -> false;
    }
    
    /**
     * Create a filter from a predicate
     * 
     * @param predicate the predicate to use
     * @return message filter wrapping the predicate
     */
    static MessageFilter of(Predicate<Message> predicate) {
        return predicate::test;
    }
    
    /**
     * Combine this filter with another using AND logic
     * 
     * @param other the other filter
     * @return combined filter (this AND other)
     */
    default MessageFilter and(MessageFilter other) {
        return message -> this.test(message) && other.test(message);
    }
    
    /**
     * Combine this filter with another using OR logic
     * 
     * @param other the other filter
     * @return combined filter (this OR other)
     */
    default MessageFilter or(MessageFilter other) {
        return message -> this.test(message) || other.test(message);
    }
    
    /**
     * Negate this filter
     * 
     * @return negated filter (NOT this)
     */
    default MessageFilter negate() {
        return message -> !this.test(message);
    }
    
    /**
     * Create a filter builder for fluent API
     * 
     * @return new message filter builder
     */
    static MessageFilterBuilder builder() {
        return new MessageFilterBuilder();
    }
}