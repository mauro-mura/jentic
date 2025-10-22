package dev.jentic.examples.filtering;

import dev.jentic.core.Message;
import dev.jentic.core.MessageService;
import dev.jentic.core.filter.MessageFilter;
import dev.jentic.core.filter.MessageFilterBuilder;
import dev.jentic.runtime.filter.*;
import dev.jentic.runtime.messaging.InMemoryMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Demonstrates advanced message filtering capabilities
 */
public class MessageFilterExample {
    
    private static final Logger log = LoggerFactory.getLogger(MessageFilterExample.class);
    
    public static void main(String[] args) throws Exception {
        log.info("=".repeat(80));
        log.info("JENTIC - ADVANCED MESSAGE FILTERING EXAMPLE");
        log.info("=".repeat(80));
        
        MessageService messageService = new InMemoryMessageService();
        
        // Example 1: Simple topic filtering
        example1_SimpleTopicFiltering(messageService);
        
        // Example 2: Header-based filtering
        example2_HeaderFiltering(messageService);
        
        // Example 3: Complex filter chains
        example3_ComplexFilterChains(messageService);
        
        // Example 4: Content-based filtering
        example4_ContentFiltering(messageService);
        
        // Example 5: Real-world scenario
        example5_RealWorldScenario(messageService);
        
        Thread.sleep(2000);
        
        log.info("\n" + "=".repeat(80));
        log.info("All filtering examples completed!");
        log.info("=".repeat(80));
    }
    
    /**
     * Example 1: Simple topic-based filtering
     */
    private static void example1_SimpleTopicFiltering(MessageService messageService) {
        log.info("\n" + "-".repeat(80));
        log.info("EXAMPLE 1: Simple Topic Filtering");
        log.info("-".repeat(80));
        
        // Subscribe to all "order.*" topics
        MessageFilter orderFilter = TopicFilter.startsWith("order.");
        
        String subscriptionId = messageService.subscribe(
            orderFilter,
            msg -> {
                log.info("✅ Order handler received: {} - {}", 
                        msg.topic(), msg.content());
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Send various messages
        sendMessage(messageService, "order.created", "Order #1001");
        sendMessage(messageService, "order.updated", "Order #1001 updated");
        sendMessage(messageService, "product.created", "Product #501"); // Not matched
        sendMessage(messageService, "order.shipped", "Order #1001 shipped");
        
        messageService.unsubscribe(subscriptionId);
    }
    
    /**
     * Example 2: Header-based filtering
     */
    private static void example2_HeaderFiltering(MessageService messageService) {
        log.info("\n" + "-".repeat(80));
        log.info("EXAMPLE 2: Header-Based Filtering");
        log.info("-".repeat(80));
        
        // Subscribe to HIGH priority messages only
        MessageFilter highPriorityFilter = HeaderFilter.equals("priority", "HIGH");
        
        messageService.subscribe(
            highPriorityFilter,
            msg -> {
                log.info("🚨 HIGH PRIORITY: {} - {}", 
                        msg.topic(), msg.content());
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Send messages with different priorities
        sendMessageWithHeader(messageService, "alert", "Low priority alert", "priority", "LOW");
        sendMessageWithHeader(messageService, "alert", "High priority alert", "priority", "HIGH");
        sendMessageWithHeader(messageService, "alert", "Critical alert", "priority", "CRITICAL");
        sendMessageWithHeader(messageService, "alert", "Another high priority", "priority", "HIGH");
    }
    
    /**
     * Example 3: Complex filter chains (AND, OR, NOT)
     */
    private static void example3_ComplexFilterChains(MessageService messageService) {
        log.info("\n" + "-".repeat(80));
        log.info("EXAMPLE 3: Complex Filter Chains");
        log.info("-".repeat(80));
        
        // Complex filter: (order.* OR payment.*) AND priority=HIGH
        MessageFilter complexFilter = MessageFilter.builder()
            .operator(MessageFilterBuilder.FilterOperator.OR)
            .topicStartsWith("order.")
            .topicStartsWith("payment.")
            .build()
            .and(HeaderFilter.equals("priority", "HIGH"));
        
        messageService.subscribe(
            complexFilter,
            msg -> {
                log.info("💎 Complex filter matched: {} - {}", 
                        msg.topic(), msg.content());
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Test messages
        sendMessageWithHeader(messageService, "order.created", "Order", "priority", "HIGH"); // ✅
        sendMessageWithHeader(messageService, "payment.processed", "Payment", "priority", "HIGH"); // ✅
        sendMessageWithHeader(messageService, "order.created", "Order", "priority", "LOW"); // ❌
        sendMessageWithHeader(messageService, "product.created", "Product", "priority", "HIGH"); // ❌
    }
    
    /**
     * Example 4: Content-based filtering
     */
    private static void example4_ContentFiltering(MessageService messageService) {
        log.info("\n" + "-".repeat(80));
        log.info("EXAMPLE 4: Content-Based Filtering");
        log.info("-".repeat(80));
        
        // Filter for OrderData content type
        MessageFilter orderDataFilter = ContentFilter.ofType(OrderData.class);
        
        messageService.subscribe(
            orderDataFilter,
            msg -> {
                OrderData order = msg.getContent(OrderData.class);
                log.info("📦 Order data received: {} - Total: ${}", 
                        order.orderId(), order.totalAmount());
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Send different content types
        sendMessage(messageService, "order.created", 
                   new OrderData("ORD-1001", new BigDecimal("999.99")));
        sendMessage(messageService, "order.created", 
                   "Simple string order"); // Not matched
        sendMessage(messageService, "order.created", 
                   new OrderData("ORD-1002", new BigDecimal("1500.00")));
    }
    
    /**
     * Example 5: Real-world scenario - E-commerce order filtering
     */
    private static void example5_RealWorldScenario(MessageService messageService) {
        log.info("\n" + "-".repeat(80));
        log.info("EXAMPLE 5: Real-World Scenario - E-commerce");
        log.info("-".repeat(80));
        
        // Handler 1: High-value orders (>$1000)
        MessageFilter highValueFilter = MessageFilter.builder()
            .topicStartsWith("order.")
            .contentType(OrderData.class)
            .contentPredicate(content -> {
                OrderData order = (OrderData) content;
                return order.totalAmount().compareTo(new BigDecimal("1000")) > 0;
            })
            .build();
        
        messageService.subscribe(
            highValueFilter,
            msg -> {
                OrderData order = msg.getContent(OrderData.class);
                log.info("💰 HIGH VALUE ORDER: {} - ${}", 
                        order.orderId(), order.totalAmount());
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Handler 2: VIP customer orders
        MessageFilter vipFilter = MessageFilter.builder()
            .topicStartsWith("order.")
            .headerEquals("customer-tier", "VIP")
            .build();
        
        messageService.subscribe(
            vipFilter,
            msg -> {
                log.info("👑 VIP CUSTOMER ORDER: {} from customer {}", 
                        msg.topic(), msg.headers().get("customer-id"));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Handler 3: US region orders requiring special handling
        MessageFilter usRegionFilter = MessageFilter.builder()
            .topicStartsWith("order.")
            .headerMatches("region", "us-.*")
            .build();
        
        messageService.subscribe(
            usRegionFilter,
            msg -> {
                log.info("🇺🇸 US REGION ORDER: {} from {}", 
                        msg.topic(), msg.headers().get("region"));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        );
        
        // Send test orders
        log.info("\nSending test orders...\n");
        
        // Order 1: High value, VIP, US region (matches all 3 handlers)
        Message order1 = Message.builder()
            .topic("order.created")
            .content(new OrderData("ORD-2001", new BigDecimal("2500.00")))
            .header("customer-id", "CUST-001")
            .header("customer-tier", "VIP")
            .header("region", "us-east-1")
            .build();
        messageService.send(order1);
        
        // Order 2: Low value, regular customer, EU region (matches no handlers)
        Message order2 = Message.builder()
            .topic("order.created")
            .content(new OrderData("ORD-2002", new BigDecimal("50.00")))
            .header("customer-id", "CUST-002")
            .header("customer-tier", "REGULAR")
            .header("region", "eu-west-1")
            .build();
        messageService.send(order2);
        
        // Order 3: High value, US region (matches 2 handlers)
        Message order3 = Message.builder()
            .topic("order.created")
            .content(new OrderData("ORD-2003", new BigDecimal("1200.00")))
            .header("customer-id", "CUST-003")
            .header("customer-tier", "REGULAR")
            .header("region", "us-west-2")
            .build();
        messageService.send(order3);
    }
    
    // Helper methods
    
    private static void sendMessage(MessageService messageService, String topic, Object content) {
        Message msg = Message.builder()
            .topic(topic)
            .content(content)
            .build();
        messageService.send(msg);
    }
    
    private static void sendMessageWithHeader(MessageService messageService, 
                                             String topic, Object content,
                                             String headerKey, String headerValue) {
        Message msg = Message.builder()
            .topic(topic)
            .content(content)
            .header(headerKey, headerValue)
            .build();
        messageService.send(msg);
    }
    
    // Sample domain object
    record OrderData(String orderId, BigDecimal totalAmount) {}
}