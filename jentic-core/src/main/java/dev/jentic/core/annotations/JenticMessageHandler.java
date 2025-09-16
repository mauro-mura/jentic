package dev.jentic.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as a message handler.
 * The method will be called when messages matching the topic are received.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JenticMessageHandler {
    
    /**
     * The message topic to subscribe to
     * @return the topic string
     */
    String value();
    
    /**
     * Whether to automatically subscribe when agent starts
     * @return true for auto-subscribe
     */
    boolean autoSubscribe() default true;
}