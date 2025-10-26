package dev.jentic.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields or methods for automatic persistence
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JenticPersist {
    
    /**
     * Field name in persisted state (defaults to field/method name)
     */
    String value() default "";
    
    /**
     * Whether this field is required for state restoration
     */
    boolean required() default false;
    
    /**
     * Whether to encrypt this field when persisting
     */
    boolean encrypted() default false;
}