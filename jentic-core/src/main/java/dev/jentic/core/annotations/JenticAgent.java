package dev.jentic.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a Jentic agent.
 * Used for automatic discovery and registration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JenticAgent {
    
    /**
     * The agent name (defaults to class simple name)
     * @return the agent name
     */
    String value() default "";
    
    /**
     * The agent type for categorization
     * @return the agent type
     */
    String type() default "";
    
    /**
     * Agent capabilities
     * @return array of capability strings
     */
    String[] capabilities() default {};
    
    /**
     * Whether this agent should be automatically started
     * @return true for auto-start
     */
    boolean autoStart() default true;
}