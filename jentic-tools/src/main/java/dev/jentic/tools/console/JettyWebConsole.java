package dev.jentic.tools.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jentic.core.console.WebConsole;
import dev.jentic.runtime.JenticRuntime;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Jetty-based implementation of WebConsole.
 * 
 * <p>Example:
 * <pre>{@code
 * JettyWebConsole console = JettyWebConsole.builder()
 *     .port(8080)
 *     .runtime(runtime)
 *     .build();
 * console.start().join();
 * }</pre>
 *
 * @since 0.4.0
 */
public class JettyWebConsole implements WebConsole {
    
    private static final Logger logger = LoggerFactory.getLogger(JettyWebConsole.class);
    
    private final int port;
    private final JenticRuntime runtime;
    private final ObjectMapper objectMapper;
    
    private Server server;
    private volatile boolean running;
    
    private JettyWebConsole(Builder builder) {
        this.port = builder.port;
        this.runtime = Objects.requireNonNull(builder.runtime, "runtime required");
        this.objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running) {
                logger.warn("Console already running on port {}", port);
                return;
            }
            
            try {
                logger.info("Starting Jetty console on port {}", port);
                
                server = new Server();
                
                ServerConnector connector = new ServerConnector(server);
                connector.setPort(port);
                connector.setIdleTimeout(Duration.ofMinutes(5).toMillis());
                server.addConnector(connector);
                
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                server.setHandler(context);
                
                // REST API
                RestAPIHandler restServlet = new RestAPIHandler(runtime, objectMapper);
                context.addServlet(new ServletHolder("rest-api", restServlet), "/api/*");
                
                // Static resources
                StaticResourceHandler staticHandler = new StaticResourceHandler();
                context.addServlet(new ServletHolder("static", staticHandler), "/*");
                
                // WebSocket
                JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
                    wsContainer.setMaxTextMessageSize(65536);
                    wsContainer.setIdleTimeout(Duration.ofMinutes(10));
                    wsContainer.addMapping("/ws", new WebSocketHandler(objectMapper));
                });
                
                server.start();
                running = true;
                
                logger.info("Console started: {}", getBaseUrl());
                
            } catch (Exception e) {
                logger.error("Failed to start console", e);
                throw new RuntimeException("Console start failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (!running) return;
            
            try {
                logger.info("Stopping console");
                if (server != null) {
                    server.stop();
                    server = null;
                }
                running = false;
                logger.info("Console stopped");
            } catch (Exception e) {
                logger.error("Error stopping console", e);
            }
        });
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public int getPort() {
        return port;
    }
    
    public JenticRuntime getRuntime() {
        return runtime;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int port = 8080;
        private JenticRuntime runtime;
        
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }
            this.port = port;
            return this;
        }
        
        public Builder runtime(JenticRuntime runtime) {
            this.runtime = runtime;
            return this;
        }
        
        public JettyWebConsole build() {
            return new JettyWebConsole(this);
        }
    }
}