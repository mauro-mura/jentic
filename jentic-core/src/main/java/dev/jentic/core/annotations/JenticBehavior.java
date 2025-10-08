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

    // Advanced behavior parameters

    /**
     * Condition expression for CONDITIONAL behaviors
     * Examples: "system.cpu < 50", "time.businessHours", "agent.running"
     * @return condition expression
     */
    String condition() default "";

    /**
     * Rate limit for THROTTLED behaviors
     * Examples: "10/s", "100/m", "1000/h"
     * @return rate limit specification
     */
    String rateLimit() default "";

    /**
     * Batch size for BATCH behaviors
     * @return maximum batch size
     */
    int batchSize() default 10;

    /**
     * Max wait time for BATCH behaviors (e.g., "5s")
     * @return max wait time string
     */
    String maxWaitTime() default "5s";

    /**
     * Max retry attempts for RETRY behaviors
     * @return maximum retry attempts
     */
    int maxRetries() default 3;

    /**
     * Backoff strategy for RETRY behaviors
     * Options: "fixed", "exponential", "linear"
     * @return backoff strategy name
     */
    String backoff() default "exponential";

    /**
     * Cron expression for SCHEDULED behaviors
     * Example: "0 0 * * * *" (every hour)
     * @return cron expression
     */
    String cron() default "";

    /**
     * For SEQUENTIAL behaviors: whether to repeat the sequence after completion
     */
    boolean repeatSequence() default false;

    /**
     * For SEQUENTIAL behaviors: timeout for each step (e.g., "30s", "5m")
     * Empty string means no timeout
     */
    String stepTimeout() default "";

    /**
     * For PARALLEL behaviors: completion strategy (ALL, ANY, FIRST, N_OF_M)
     */
    String parallelStrategy() default "ALL";

    /**
     * For PARALLEL with N_OF_M strategy: number of required completions
     */
    int requiredCompletions() default 0;

    /**
     * For FSM behaviors: initial state name
     */
    String fsmInitialState() default "START";
}