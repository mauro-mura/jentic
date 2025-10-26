package dev.jentic.core.annotations;

import dev.jentic.core.persistence.PersistenceStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configure persistence behavior for an agent
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JenticPersistenceConfig {
    
    /**
     * Persistence strategy to use
     */
    PersistenceStrategy strategy() default PersistenceStrategy.MANUAL;
    
    /**
     * Interval for periodic persistence (e.g., "30s", "5m")
     */
    String interval() default "60s";
    
    /**
     * Whether to create snapshots automatically
     */
    boolean autoSnapshot() default false;
    
    /**
     * Snapshot interval (e.g., "1h", "1d")
     */
    String snapshotInterval() default "1h";
    
    /**
     * Maximum number of snapshots to keep
     */
    int maxSnapshots() default 10;
}