package dev.jentic.examples.console;

import dev.jentic.runtime.JenticRuntime;
import dev.jentic.runtime.agent.BaseAgent;
import dev.jentic.tools.console.WebConsoleServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        WebConsoleServer console = WebConsoleServer.builder()
                .port(8080)
                .runtime(runtime)
                .build();

        console.start();

        logger.info("Web console started at http://localhost:8080");
        logger.info("Press CTRL+C to stop");

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

