package dev.jentic.examples.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.jentic.core.console.WebConsole;
import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.tools.console.JettyWebConsole;

public class WebConsoleExample {

    private static final Logger logger = LoggerFactory.getLogger(WebConsoleExample.class);

    public static void main(String[] args) throws Exception {
        logger.info("=== Web Console Example ===");

        JenticRuntime runtime = JenticRuntime.builder().build();
        OrderProcessor orderProcessor = new OrderProcessor();
        InventoryManager inventoryManager = new InventoryManager();
        runtime.registerAgent(orderProcessor);
        runtime.registerAgent(inventoryManager);

        // Start web console
        WebConsole console = JettyWebConsole.builder()
                .port(8080)
                .runtime(runtime)
                .build();

        console.start().join();
        runtime.start().join();

        logger.info("Console: {}", console.getBaseUrl());
        logger.info("API: {}", console.getApiUrl());
        logger.info("WebSocket: {}", console.getWebSocketUrl());
        logger.info("Press CTRL+C to stop");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                console.stop().join();
                runtime.stop().join();
            } catch (Exception e) {
                logger.error("Shutdown error", e);
            }
        }));
        
        
        // Keep running
        Thread.currentThread().join();
    }

    public static class OrderProcessor extends BaseAgent {
        public OrderProcessor() {
            super("order-processor", "Order Processor");
        }

        @Override
        protected void onStart() {
            logger.info("Order Processor started");
        }

        @Override
        protected void onStop() {
            logger.info("Order Processor stopped");
        }
    }

    public static class InventoryManager extends BaseAgent {
        public InventoryManager() {
            super("inventory-manager", "Inventory Manager");
        }

        @Override
        protected void onStart() {
            logger.info("Inventory Manager started");
        }

        @Override
        protected void onStop() {
            logger.info("Inventory Manager stopped");
        }
    }
}

