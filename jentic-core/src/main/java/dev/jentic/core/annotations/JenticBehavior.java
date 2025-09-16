package dev.jentic.core.annotations;

import dev.jentic.core.BehaviorType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as a behavior.
 * The method will be executed according to the behavior type.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JenticBehavior {
    
    /**
     * The behavior type
     * @return the behavior type
     */
    BehaviorType type() default BehaviorType.ONE_SHOT;
    
    /**
     * Interval for cyclic behaviors (e.g., "30s", "1m", "5min")
     * @return the interval string
     */
    String interval() default "";
    
    /**
     * Delay before first execution (e.g., "5s", "1m")
     * @return the initial delay string
     */
    String initialDelay() default "";
    
    /**
     * Whether this behavior should start automatically
     * @return true for auto-start
     */
    boolean autoStart() default true;
}