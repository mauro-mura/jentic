package dev.jentic.runtime.behavior.advanced;

import dev.jentic.core.BehaviorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ScheduledBehavior.
 */
class ScheduledBehaviorTest {
    
    private ScheduledBehavior behavior;
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final AtomicReference<Exception> lastException = new AtomicReference<>();
    
    @BeforeEach
    void setUp() {
        executionCount.set(0);
        lastException.set(null);
    }
    
    @AfterEach
    void tearDown() {
        if (behavior != null && behavior.isActive()) {
            behavior.stop();
        }
    }
    
    // ========== CRON EXPRESSION TESTS ==========
    
    @Test
    void testCronExpressionParsing() {
        assertDoesNotThrow(() -> CronExpression.parse("0 0 * * * *"));
        assertDoesNotThrow(() -> CronExpression.parse("0 */15 * * * *"));
        assertDoesNotThrow(() -> CronExpression.parse("0 0 9 * * MON-FRI"));
        assertDoesNotThrow(() -> CronExpression.parse("0 30 8 * * *"));
    }
    
    @Test
    void testInvalidCronExpression() {
        assertThrows(IllegalArgumentException.class, () -> 
            CronExpression.parse("invalid"));
        
        assertThrows(IllegalArgumentException.class, () -> 
            CronExpression.parse("* * *")); // Too few fields
        
        assertThrows(IllegalArgumentException.class, () -> 
            CronExpression.parse("60 0 * * * *")); // Invalid second
    }
    
    @Test
    void testCronExpressionMatching() {
        CronExpression cron = CronExpression.parse("0 30 9 * * *"); // 9:30 AM daily
        
        ZonedDateTime matching = ZonedDateTime.of(
            2025, 10, 30, 9, 30, 0, 0, ZoneId.systemDefault()
        );
        assertTrue(cron.matches(matching));
        
        ZonedDateTime notMatching = ZonedDateTime.of(
            2025, 10, 30, 9, 31, 0, 0, ZoneId.systemDefault()
        );
        assertFalse(cron.matches(notMatching));
    }
    
    @Test
    void testCronNextExecution() {
        CronExpression cron = CronExpression.parse("0 0 * * * *"); // Every hour
        
        ZonedDateTime now = ZonedDateTime.of(
            2025, 10, 30, 9, 30, 0, 0, ZoneId.systemDefault()
        );
        
        ZonedDateTime next = cron.getNextExecution(now);
        assertNotNull(next);
        assertEquals(10, next.getHour());
        assertEquals(0, next.getMinute());
        assertEquals(0, next.getSecond());
    }
    
    @Test
    void testCronWithStepValues() {
        CronExpression cron = CronExpression.parse("0 */15 * * * *"); // Every 15 minutes
        
        ZonedDateTime now = ZonedDateTime.of(
            2025, 10, 30, 9, 0, 0, 0, ZoneId.systemDefault()
        );
        
        ZonedDateTime next = cron.getNextExecution(now);
        assertNotNull(next);
        assertEquals(15, next.getMinute());
    }
    
    @Test
    void testCronWithDayOfWeek() {
        CronExpression cron = CronExpression.parse("0 0 9 * * MON-FRI"); // Weekdays at 9 AM
        
        // Start on a Sunday (day 0)
        ZonedDateTime sunday = ZonedDateTime.of(
            2025, 11, 2, 8, 0, 0, 0, ZoneId.systemDefault() // November 2, 2025 is Sunday
        );
        
        ZonedDateTime next = cron.getNextExecution(sunday);
        assertNotNull(next);
        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
        assertEquals(9, next.getHour());
    }
    
    // ========== BASIC BEHAVIOR TESTS ==========
    
    @Test
    @Timeout(5)
    void testBasicScheduledExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // Schedule for 2 seconds from now
        ZonedDateTime now = ZonedDateTime.now();
        int targetSecond = (now.getSecond() + 2) % 60;
        String cron = String.format("0 %d %d * * *", now.getMinute(), now.getHour());
        
        // Use a cron that triggers every second for testing
        behavior = new ScheduledBehavior("test-behavior", "* * * * * *") {
            @Override
            protected void scheduledAction() {
                executionCount.incrementAndGet();
                latch.countDown();
            }
        };
        
        behavior.execute().join();
        
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(executionCount.get() > 0);
    }
    
    @Test
    void testBehaviorType() {
        behavior = createSimpleBehavior("* * * * * *");
        assertEquals(BehaviorType.SCHEDULED, behavior.getType());
    }
    
    @Test
    void testNextExecutionCalculation() {
        behavior = createSimpleBehavior("0 0 * * * *"); // Every hour
        
        ZonedDateTime next = behavior.getNextExecutionTime();
        assertNotNull(next);
        assertTrue(next.isAfter(ZonedDateTime.now()));
        
        Duration timeUntil = behavior.getTimeUntilNextExecution();
        assertNotNull(timeUntil);
        assertTrue(timeUntil.getSeconds() > 0);
    }
    
    // ========== FACTORY METHOD TESTS ==========
    
    @Test
    void testEveryHourFactory() {
        AtomicInteger count = new AtomicInteger(0);
        behavior = ScheduledBehavior.everyHour("hourly", count::incrementAndGet);
        
        assertNotNull(behavior);
        assertEquals("0 0 * * * *", behavior.getCronExpression());
    }
    
    @Test
    void testDailyFactory() {
        AtomicInteger count = new AtomicInteger(0);
        behavior = ScheduledBehavior.daily("daily", 9, 30, count::incrementAndGet);
        
        assertNotNull(behavior);
        assertTrue(behavior.getCronExpression().contains("30"));
        assertTrue(behavior.getCronExpression().contains("9"));
    }
    
    @Test
    void testWeekdaysFactory() {
        AtomicInteger count = new AtomicInteger(0);
        behavior = ScheduledBehavior.weekdays("weekdays", 8, 0, count::incrementAndGet);
        
        assertNotNull(behavior);
        assertTrue(behavior.getCronExpression().contains("MON-FRI"));
    }
    
    // ========== CALLBACK TESTS ==========
    
    @Test
    @Timeout(5)
    void testSuccessCallback() throws Exception {
        CountDownLatch successLatch = new CountDownLatch(1);
        AtomicReference<ScheduledBehavior> callbackBehavior = new AtomicReference<>();
        
        behavior = createSimpleBehavior("* * * * * *");
        behavior.onSuccess(b -> {
            callbackBehavior.set(b);
            successLatch.countDown();
        });
        
        behavior.execute().join();
        
        assertTrue(successLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(callbackBehavior.get());
        assertEquals(behavior, callbackBehavior.get());
    }
    
    @Test
    @Timeout(5)
    void testFailureCallback() throws Exception {
        CountDownLatch failureLatch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("failing", "* * * * * *") {
            @Override
            protected void scheduledAction() throws Exception {
                throw new RuntimeException("Test failure");
            }
        };
        
        behavior.onFailure(e -> {
            lastException.set(e);
            failureLatch.countDown();
        });
        
        behavior.execute().join();
        
        assertTrue(failureLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(lastException.get());
        assertEquals("Test failure", lastException.get().getMessage());
    }
    
    // ========== EXECUTION TIMEOUT TESTS ==========
    
    @Test
    @Timeout(10)
    void testExecutionTimeout() throws Exception {
        CountDownLatch failureLatch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("timeout-test", "* * * * * *") {
            @Override
            protected void scheduledAction() throws Exception {
                Thread.sleep(5000); // Sleep longer than timeout
            }
        };
        
        behavior.setExecutionTimeout(Duration.ofMillis(500));
        behavior.onFailure(e -> failureLatch.countDown());
        
        behavior.execute().join();
        
        assertTrue(failureLatch.await(2, TimeUnit.SECONDS));
        assertTrue(behavior.getFailedExecutions() > 0);
    }
    
    // ========== MISSED EXECUTION TESTS ==========
    
    @Test
    void testMissedExecutionPolicy() {
        behavior = createSimpleBehavior("0 0 * * * *");
        
        // Default should be SKIP
        behavior.setMissedExecutionPolicy(ScheduledBehavior.MissedExecutionPolicy.SKIP);
        
        // Should accept EXECUTE_ONCE
        assertDoesNotThrow(() -> 
            behavior.setMissedExecutionPolicy(ScheduledBehavior.MissedExecutionPolicy.EXECUTE_ONCE)
        );
    }
    
    // ========== METRICS TESTS ==========
    
    @Test
    @Timeout(5)
    void testExecutionMetrics() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        
        behavior = new ScheduledBehavior("metrics-test", "* * * * * *") {
            @Override
            protected void scheduledAction() {
                executionCount.incrementAndGet();
                latch.countDown();
            }
        };
        
        behavior.execute().join();
        Thread.sleep(1500); // Wait for second execution
        behavior.execute().join();
        
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        
        assertTrue(behavior.getTotalExecutions() >= 2);
        assertTrue(behavior.getSuccessfulExecutions() >= 2);
        assertEquals(0, behavior.getFailedExecutions());
        assertEquals(1.0, behavior.getSuccessRate(), 0.01);
    }
    
    @Test
    @Timeout(5)
    void testAverageExecutionTime() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("timing-test", "* * * * * *") {
            @Override
            protected void scheduledAction() throws Exception {
                Thread.sleep(50); // Small delay
                latch.countDown();
            }
        };
        
        behavior.execute().join();
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(behavior.getAverageExecutionTimeMs() >= 40, 
        	    "Expected execution time >= 40ms but was " + behavior.getAverageExecutionTimeMs());
    }
    
    @Test
    void testMetricsSummary() {
        behavior = createSimpleBehavior("0 0 * * * *");
        
        String summary = behavior.getMetricsSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("metrics-test"));
        assertTrue(summary.contains("total="));
        assertTrue(summary.contains("success="));
        assertTrue(summary.contains("failed="));
    }
    
    // ========== TIMEZONE TESTS ==========
    
    @Test
    void testTimezoneHandling() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        behavior = new ScheduledBehavior("tokyo-behavior", "0 0 9 * * *", tokyo) {
            @Override
            protected void scheduledAction() {
                executionCount.incrementAndGet();
            }
        };
        
        assertEquals(tokyo, behavior.getTimezone());
        assertNotNull(behavior.getNextExecutionTime());
    }
    
    @Test
    void testDefaultTimezone() {
        behavior = createSimpleBehavior("0 0 * * * *");
        assertEquals(ZoneId.systemDefault(), behavior.getTimezone());
    }
    
    // ========== LIFECYCLE TESTS ==========
    
    @Test
    void testBehaviorStop() {
        behavior = createSimpleBehavior("* * * * * *");
        
        assertTrue(behavior.isActive());
        behavior.stop();
        assertFalse(behavior.isActive());
    }
    
    @Test
    @Timeout(5)
    void testExecutionState() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("state-test", "* * * * * *") {
            @Override
            protected void scheduledAction() throws Exception {
                startLatch.countDown();
                Thread.sleep(100);
                finishLatch.countDown();
            }
        };
        
        CompletableFuture<Void> future = behavior.execute();
        
        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        assertTrue(behavior.isExecuting() || finishLatch.getCount() == 0);
        
        future.join();
        behavior.stop();
        assertFalse(behavior.isExecuting());
    }
    
    // ========== CONCURRENT EXECUTION TESTS ==========
    
    @Test
    @Timeout(5)
    void testNoConcurrentExecution() throws Exception {
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("concurrent-test", "* * * * * *") {
            @Override
            protected void scheduledAction() throws Exception {
                int current = concurrent.incrementAndGet();
                maxConcurrent.updateAndGet(max -> Math.max(max, current));
                
                Thread.sleep(200); // Simulate work
                
                concurrent.decrementAndGet();
                latch.countDown();
            }
        };
        
        // Try to execute multiple times concurrently
        CompletableFuture.allOf(
            behavior.execute(),
            behavior.execute(),
            behavior.execute()
        ).join();
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        // Should never have more than 1 concurrent execution
        assertEquals(1, maxConcurrent.get());
    }
    
    // ========== EDGE CASE TESTS ==========
    
    @Test
    void testEmptyCronExpression() {
        assertThrows(IllegalArgumentException.class, () -> 
            new ScheduledBehavior("empty-cron", "") {
                @Override
                protected void scheduledAction() {}
            }
        );
    }
    
    @Test
    void testNullCronExpression() {
        assertThrows(IllegalArgumentException.class, () -> 
            new ScheduledBehavior("null-cron", null) {
                @Override
                protected void scheduledAction() {}
            }
        );
    }
    
    @Test
    void testLastExecutionTime() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        behavior = new ScheduledBehavior("last-exec-test", "* * * * * *") {
            @Override
            protected void scheduledAction() {
                latch.countDown();
            }
        };
        
        assertNull(behavior.getLastExecutionTime());
        
        behavior.execute().join();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        assertNotNull(behavior.getLastExecutionTime());
        assertTrue(behavior.getLastExecutionTime().isBefore(ZonedDateTime.now().plusSeconds(1)));
    }
    
    // ========== HELPER METHODS ==========
    
    private ScheduledBehavior createSimpleBehavior(String cron) {
        return new ScheduledBehavior("metrics-test", cron) {
            @Override
            protected void scheduledAction() {
                executionCount.incrementAndGet();
            }
        };
    }
}
