package dev.jentic.tools.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jentic.runtime.JenticRuntime;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Embedded web console server for Jentic Framework management and monitoring.
 * 
 * Provides:
 * - REST API for agent management
 * - WebSocket for real-time updates
 * - Static web interface
 * - Health checks and metrics
 * 
 * Usage:
 * <pre>
 * WebConsoleServer console = WebConsoleServer.builder()
 *     .port(8080)
 *     .runtime(runtime)
 *     .build();
 * console.start();
 * </pre>
 */
public class WebConsoleServer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebConsoleServer.class);
    
    private final int port;
    private final JenticRuntime runtime;
    private final ObjectMapper objectMapper;
    private Server server;
    private boolean running;
    
    private WebConsoleServer(Builder builder) {
        this.port = builder.port;
        this.runtime = builder.runtime;
        this.objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Start the web console server.
     */
    public synchronized void start() throws Exception {
        if (running) {
            logger.warn("Web console already running on port {}", port);
            return;
        }
        
        logger.info("Starting web console on port {}", port);
        
        server = new Server();
        
        // Configure connector
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        connector.setIdleTimeout(Duration.ofMinutes(5).toMillis());
        server.addConnector(connector);
        
        // Create servlet context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        
        // Register REST API servlet
        RestAPIHandler restAPI = new RestAPIHandler(runtime, objectMapper);
        ServletHolder restHolder = new ServletHolder("rest-api", restAPI);
        context.addServlet(restHolder, "/api/*");
        
        // Register static resource servlet
        StaticResourceHandler staticHandler = new StaticResourceHandler();
        ServletHolder staticHolder = new ServletHolder("static", staticHandler);
        context.addServlet(staticHolder, "/*");
        
        // Configure WebSocket
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.setMaxTextMessageSize(65536);
            wsContainer.setIdleTimeout(Duration.ofMinutes(10));
            
            WebSocketHandler wsHandler = new WebSocketHandler(runtime, objectMapper);
            wsContainer.addMapping("/ws", wsHandler);
        });
        
        // Start server
        server.start();
        running = true;
        
        logger.info("Web console started successfully at http://localhost:{}", port);
        logger.info("Dashboard: http://localhost:{}/", port);
        logger.info("API: http://localhost:{}/api/", port);
        logger.info("WebSocket: ws://localhost:{}/ws", port);
    }
    
    /**
     * Stop the web console server.
     */
    public synchronized void stop() throws Exception {
        if (!running) {
            logger.warn("Web console not running");
            return;
        }
        
        logger.info("Stopping web console");
        
        if (server != null) {
            server.stop();
            server = null;
        }
        
        running = false;
        logger.info("Web console stopped");
    }
    
    /**
     * Check if server is running.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Get the port number.
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Get the runtime instance.
     */
    public JenticRuntime getRuntime() {
        return runtime;
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for WebConsoleServer.
     */
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
        
        public WebConsoleServer build() {
            if (runtime == null) {
                throw new IllegalStateException("Runtime is required");
            }
            return new WebConsoleServer(this);
        }
    }
}
