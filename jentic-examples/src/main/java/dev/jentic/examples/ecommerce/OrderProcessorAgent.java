package dev.jentic.examples.ecommerce;

import dev.jentic.core.*;
import dev.jentic.core.annotations.JenticAgent;
import dev.jentic.core.annotations.JenticBehavior;
import dev.jentic.core.annotations.JenticMessageHandler;
import dev.jentic.core.composite.CompletionStrategy;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.runtime.behavior.OneShotBehavior;
import dev.jentic.runtime.behavior.composite.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@JenticAgent(
    value = "order-processor",
    type = "OrderProcessing",
    capabilities = {"order-management", "payment", "fulfillment"}
)
public class OrderProcessorAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(OrderProcessorAgent.class);
    
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, ValidationResult> validationResults = new ConcurrentHashMap<>();
    
    private FSMBehavior orderStateMachine;
    
    public OrderProcessorAgent() {
        super("order-processor");
    }
    
    @JenticBehavior(type = BehaviorType.FSM)
    public void setup() {
        log.info("Setting up Order Processor Agent");
        
        // Build the Order Processing State Machine
        orderStateMachine = buildOrderStateMachine();
        addBehavior(orderStateMachine);
        
        log.info("Order Processor Agent ready");
    }
    
    /**
     * Build the complete order processing FSM
     */
    private FSMBehavior buildOrderStateMachine() {
        return FSMBehavior.builder("order-processing-fsm", "IDLE")
            // States
            .state("IDLE", createIdleBehavior())
            .state("VALIDATING", createValidationBehavior())
            .state("PROCESSING_PAYMENT", createPaymentBehavior())
            .state("FULFILLING", createFulfillmentBehavior())
            .state("COMPLETED", createCompletionBehavior())
            .state("FAILED", createFailureBehavior())
            
            // Transitions
            .transition("IDLE", "VALIDATING", 
                fsm -> hasOrderToProcess())
            .transition("VALIDATING", "PROCESSING_PAYMENT", 
                fsm -> isValidationSuccessful())
            .transition("VALIDATING", "FAILED", 
                fsm -> isValidationFailed())
            .transition("PROCESSING_PAYMENT", "FULFILLING", 
                fsm -> isPaymentSuccessful())
            .transition("PROCESSING_PAYMENT", "FAILED", 
                fsm -> isPaymentFailed())
            .transition("FULFILLING", "COMPLETED", 
                fsm -> isFulfillmentComplete())
            .transition("FAILED", "IDLE", 
                fsm -> true) // Always return to IDLE after failure
            .transition("COMPLETED", "IDLE", 
                fsm -> true) // Always return to IDLE after completion
            
            .build();
    }
    
    // =========================================================================
    // STATE BEHAVIORS
    // =========================================================================
    
    private Behavior createIdleBehavior() {
        return new OneShotBehavior("idle-behavior") {
            @Override
            protected void action() {
                log.debug("Order processor in IDLE state, waiting for orders...");
            }
        };
    }
    
    /**
     * VALIDATING state: Run parallel validations
     */
    private Behavior createValidationBehavior() {
        ParallelBehavior validations = new ParallelBehavior(
            "order-validations", 
            CompletionStrategy.ALL
        );
        
        // Customer validation
        validations.addChildBehavior(new OneShotBehavior("validate-customer") {
            @Override
            protected void action() {
                log.info("Validating customer...");
                simulateWork(100);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    // Simulate customer validation
                    boolean valid = order.customerId() != null && !order.customerId().isEmpty();
                    validationResults.put("customer", 
                        valid ? ValidationResult.success() 
                              : ValidationResult.failure("Invalid customer ID"));
                    log.info("Customer validation: {}", valid ? "PASSED" : "FAILED");
                }
            }
        });
        
        // Inventory validation
        validations.addChildBehavior(new OneShotBehavior("validate-inventory") {
            @Override
            protected void action() {
                log.info("Validating inventory...");
                simulateWork(150);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    // Simulate inventory check
                    boolean valid = order.items().stream()
                        .allMatch(item -> item.quantity() > 0 && item.quantity() <= 100);
                    validationResults.put("inventory", 
                        valid ? ValidationResult.success() 
                              : ValidationResult.failure("Insufficient inventory"));
                    log.info("Inventory validation: {}", valid ? "PASSED" : "FAILED");
                }
            }
        });
        
        // Payment method validation
        validations.addChildBehavior(new OneShotBehavior("validate-payment-method") {
            @Override
            protected void action() {
                log.info("Validating payment method...");
                simulateWork(120);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    // Simulate payment method validation
                    boolean valid = order.totalAmount().compareTo(BigDecimal.ZERO) > 0;
                    validationResults.put("payment", 
                        valid ? ValidationResult.success() 
                              : ValidationResult.failure("Invalid payment amount"));
                    log.info("Payment method validation: {}", valid ? "PASSED" : "FAILED");
                }
            }
        });
        
        return validations;
    }
    
    /**
     * PROCESSING_PAYMENT state
     */
    private Behavior createPaymentBehavior() {
        return new OneShotBehavior("process-payment") {
            @Override
            protected void action() {
                log.info("Processing payment...");
                simulateWork(300);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    // Simulate payment processing
                    String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
                    Order updatedOrder = order.withPaymentId(paymentId)
                        .withStatus(OrderStatus.PAYMENT_PROCESSING);
                    orders.put(order.orderId(), updatedOrder);
                    
                    log.info("Payment processed successfully: {}", paymentId);
                    validationResults.put("payment-processed", ValidationResult.success());
                }
            }
        };
    }
    
    /**
     * FULFILLING state: Sequential fulfillment steps
     */
    private Behavior createFulfillmentBehavior() {
        SequentialBehavior fulfillment = new SequentialBehavior(
            "order-fulfillment", 
            false // Don't repeat
        );
        
        // Step 1: Reserve inventory
        fulfillment.addChildBehavior(new OneShotBehavior("reserve-inventory") {
            @Override
            protected void action() {
                log.info("Reserving inventory...");
                simulateWork(200);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    log.info("Inventory reserved for order: {}", order.orderId());
                }
            }
        });
        
        // Step 2: Prepare shipment
        fulfillment.addChildBehavior(new OneShotBehavior("prepare-shipment") {
            @Override
            protected void action() {
                log.info("Preparing shipment...");
                simulateWork(250);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8);
                    Order updatedOrder = order.withShipmentId(shipmentId)
                        .withStatus(OrderStatus.PREPARING);
                    orders.put(order.orderId(), updatedOrder);
                    
                    log.info("Shipment prepared: {}", shipmentId);
                }
            }
        });
        
        // Step 3: Ship order
        fulfillment.addChildBehavior(new OneShotBehavior("ship-order") {
            @Override
            protected void action() {
                log.info("Shipping order...");
                simulateWork(100);
                
                Order order = getCurrentOrder();
                if (order != null) {
                    Order updatedOrder = order.withStatus(OrderStatus.SHIPPED);
                    orders.put(order.orderId(), updatedOrder);
                    
                    log.info("Order shipped: {} (Tracking: {})", 
                            order.orderId(), order.shipmentId());
                    validationResults.put("fulfillment-complete", ValidationResult.success());
                }
            }
        });
        
        return fulfillment;
    }
    
    /**
     * COMPLETED state
     */
    private Behavior createCompletionBehavior() {
        return new OneShotBehavior("complete-order") {
            @Override
            protected void action() {
                Order order = getCurrentOrder();
                if (order != null) {
                    Order completedOrder = order.withStatus(OrderStatus.DELIVERED);
                    orders.put(order.orderId(), completedOrder);
                    
                    log.info("✅ Order completed successfully: {}", order.orderId());
                    log.info("   Customer: {}", order.customerId());
                    log.info("   Total: ${}", order.totalAmount());
                    log.info("   Payment ID: {}", order.paymentId());
                    log.info("   Shipment ID: {}", order.shipmentId());
                    
                    // Send notification
                    sendNotification(order, "Order delivered successfully!");
                    
                    // Clear validation results
                    validationResults.clear();
                }
            }
        };
    }
    
    /**
     * FAILED state
     */
    private Behavior createFailureBehavior() {
        return new OneShotBehavior("handle-failure") {
            @Override
            protected void action() {
                Order order = getCurrentOrder();
                if (order != null) {
                    Order failedOrder = order.withStatus(OrderStatus.FAILED);
                    orders.put(order.orderId(), failedOrder);
                    
                    String reason = validationResults.values().stream()
                        .filter(r -> !r.valid())
                        .map(ValidationResult::reason)
                        .findFirst()
                        .orElse("Unknown error");
                    
                    log.error("❌ Order failed: {} - Reason: {}", order.orderId(), reason);
                    
                    // Send notification
                    sendNotification(order, "Order failed: " + reason);
                    
                    // Clear validation results
                    validationResults.clear();
                }
            }
        };
    }
    
    // =========================================================================
    // MESSAGE HANDLERS
    // =========================================================================
    
    @JenticMessageHandler("new-order")
    public void handleNewOrder(Message message) {
        Order order = message.getContent(Order.class);
        log.info("📦 Received new order: {} from customer: {}", 
                order.orderId(), order.customerId());
        
        orders.put(order.orderId(), order);
        
        // Reply with acknowledgment
        Message reply = message.reply("Order received")
            .topic("order-ack")
            .senderId(getAgentId())
            .build();
        
        getMessageService().send(reply);
    }
    
    @JenticMessageHandler("cancel-order")
    public void handleCancelOrder(Message message) {
        String orderId = message.getContent(String.class);
        Order order = orders.get(orderId);
        
        if (order != null && order.status() == OrderStatus.PENDING) {
            Order cancelledOrder = order.withStatus(OrderStatus.CANCELLED);
            orders.put(orderId, cancelledOrder);
            log.info("Order cancelled: {}", orderId);
        } else {
            log.warn("Cannot cancel order: {} (status: {})", 
                    orderId, order != null ? order.status() : "NOT_FOUND");
        }
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private Order getCurrentOrder() {
        return orders.values().stream()
            .filter(o -> o.status() != OrderStatus.DELIVERED && 
                        o.status() != OrderStatus.FAILED &&
                        o.status() != OrderStatus.CANCELLED)
            .findFirst()
            .orElse(null);
    }
    
    private boolean hasOrderToProcess() {
        return getCurrentOrder() != null;
    }
    
    private boolean isValidationSuccessful() {
        if (validationResults.size() < 3) return false;
        return validationResults.values().stream().allMatch(ValidationResult::valid);
    }
    
    private boolean isValidationFailed() {
        if (validationResults.isEmpty()) return false;
        return validationResults.values().stream().anyMatch(r -> !r.valid());
    }
    
    private boolean isPaymentSuccessful() {
        ValidationResult result = validationResults.get("payment-processed");
        return result != null && result.valid();
    }
    
    private boolean isPaymentFailed() {
        ValidationResult result = validationResults.get("payment-processed");
        return result != null && !result.valid();
    }
    
    private boolean isFulfillmentComplete() {
        ValidationResult result = validationResults.get("fulfillment-complete");
        return result != null && result.valid();
    }
    
    private void sendNotification(Order order, String message) {
        Message notification = Message.builder()
            .topic("order-notification")
            .senderId(getAgentId())
            .receiverId("notification-service")
            .content(Map.of(
                "orderId", order.orderId(),
                "customerId", order.customerId(),
                "message", message
            ))
            .build();
        
        getMessageService().send(notification);
    }
    
    private void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}