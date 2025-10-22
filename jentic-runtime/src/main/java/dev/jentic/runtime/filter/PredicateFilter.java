package dev.jentic.runtime.filter;

import dev.jentic.core.Message;
import dev.jentic.core.filter.MessageFilter;

import java.util.function.Predicate;

/**
 * Generic filter from predicate
 */
public class PredicateFilter implements MessageFilter {
    
    private final Predicate<Message> predicate;
    private final String description;
    
    public PredicateFilter(Predicate<Message> predicate) {
        this(predicate, "custom-predicate");
    }
    
    public PredicateFilter(Predicate<Message> predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }
    
    @Override
    public boolean test(Message message) {
        return predicate.test(message);
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return "PredicateFilter[" + description + "]";
    }
}