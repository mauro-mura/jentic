package dev.jentic.tools.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.jentic.core.Agent;
import dev.jentic.runtime.JenticRuntime;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API handler for web console.
 * 
 * Endpoints:
 * - GET  /api/agents          - List all agents
 * - GET  /api/agents/{id}     - Get agent details
 * - POST /api/agents/{id}/start - Start agent
 * - POST /api/agents/{id}/stop  - Stop agent
 * - GET  /api/messages        - Get recent messages
 * - GET  /api/stats           - Get runtime statistics
 * - GET  /api/health          - Health check
 */
public class RestAPIHandler extends HttpServlet {
    
	@Serial
    private static final long serialVersionUID = 7777219348406725261L;

	private static final Logger logger = LoggerFactory.getLogger(RestAPIHandler.class);
    
    private final JenticRuntime runtime;
    private final ObjectMapper objectMapper;
    
    public RestAPIHandler(JenticRuntime runtime, ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        
        logger.debug("GET request: {}", path);
        
        try {
            if (path == null || path.equals("/")) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
                return;
            }
            
            if (path.equals("/agents")) {
                handleGetAgents(req, resp);
            } else if (path.startsWith("/agents/") && !path.contains("/start") && !path.contains("/stop")) {
                handleGetAgent(req, resp, extractAgentId(path));
            } else if (path.equals("/messages")) {
                handleGetMessages(req, resp);
            } else if (path.equals("/stats")) {
                handleGetStats(req, resp);
            } else if (path.equals("/health")) {
                handleGetHealth(req, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
            
        } catch (Exception e) {
            logger.error("Error handling GET request", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        
        logger.debug("POST request: {}", path);
        
        try {
            if (path == null) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
                return;
            }
            
            if (path.matches("/agents/.+/start")) {
                handleStartAgent(req, resp, extractAgentId(path));
            } else if (path.matches("/agents/.+/stop")) {
                handleStopAgent(req, resp, extractAgentId(path));
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
            
        } catch (Exception e) {
            logger.error("Error handling POST request", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    private void handleGetAgents(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> agentList = runtime.getAgents().stream()
            .map(this::agentToMap)
            .collect(Collectors.toList());
        
        sendSuccess(resp, agentList);
    }
    
    private void handleGetAgent(HttpServletRequest req, HttpServletResponse resp, String agentId) throws IOException {
        Optional<Agent> agent = runtime.getAgent(agentId);
        
        if (!agent.isPresent()) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Agent not found: " + agentId);
            return;
        }
        
        Map<String, Object> agentData = agentToMap(agent.get());
        
        // Note: Detailed behavior and subscription information would require
        // additional methods in the Agent interface or runtime inspection.
        // For now, we return basic agent information.
        
        sendSuccess(resp, agentData);
    }
    
    private void handleStartAgent(HttpServletRequest req, HttpServletResponse resp, String agentId) throws IOException {
        Optional<Agent> agent = runtime.getAgent(agentId);
        
        if (!agent.isPresent()) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Agent not found: " + agentId);
            return;
        }
        
        try {
            agent.get().start();
            
            Map<String, Object> result = Map.of(
                "agentId", agentId,
                "status", "started",
                "message", "Agent started successfully"
            );
            
            sendSuccess(resp, result);
            
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Failed to start agent: " + e.getMessage());
        }
    }
    
    private void handleStopAgent(HttpServletRequest req, HttpServletResponse resp, String agentId) throws IOException {
        Optional<Agent> agent = runtime.getAgent(agentId);
        
        if (!agent.isPresent()) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Agent not found: " + agentId);
            return;
        }
        
        try {
            agent.get().stop();
            
            Map<String, Object> result = Map.of(
                "agentId", agentId,
                "status", "stopped",
                "message", "Agent stopped successfully"
            );
            
            sendSuccess(resp, result);
            
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Failed to stop agent: " + e.getMessage());
        }
    }
    
    private void handleGetMessages(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Get limit parameter (default: 100)
        int limit = 100;
        String limitParam = req.getParameter("limit");
        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (NumberFormatException e) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid limit parameter");
                return;
            }
        }
        
        // TODO: Implement message history storage
        // For now, return empty list
        List<Map<String, Object>> messages = List.of();
        
        sendSuccess(resp, messages);
    }
    
    private void handleGetStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAgents", runtime.getAgents().size());
        stats.put("activeAgents", runtime.getAgents().stream()
            .filter(Agent::isRunning)
            .count());
        
        // Runtime info
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> runtimeInfo = new HashMap<>();
        runtimeInfo.put("totalMemoryMB", rt.totalMemory() / (1024 * 1024));
        runtimeInfo.put("freeMemoryMB", rt.freeMemory() / (1024 * 1024));
        runtimeInfo.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        runtimeInfo.put("maxMemoryMB", rt.maxMemory() / (1024 * 1024));
        runtimeInfo.put("availableProcessors", rt.availableProcessors());
        
        stats.put("runtime", runtimeInfo);
        stats.put("uptime", getUptime());
        
        sendSuccess(resp, stats);
    }
    
    private void handleGetHealth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> health = new HashMap<>();
        
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("agentCount", runtime.getAgents().size());
        
        // Check if any agents are in error state
        long errorCount = runtime.getAgents().stream()
            .filter(a -> !a.isRunning())
            .count();
        
        if (errorCount > 0) {
            health.put("status", "DEGRADED");
            health.put("errors", errorCount);
        }
        
        sendSuccess(resp, health);
    }
    
    private Map<String, Object> agentToMap(Agent agent) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", agent.getAgentId());
        map.put("name", agent.getAgentName());
        map.put("running", agent.isRunning());
        map.put("type", agent.getClass().getSimpleName());
        return map;
    }
    
    private String extractAgentId(String path) {
        // Extract agent ID from path like /agents/{id} or /agents/{id}/start
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    
    private long getUptime() {
        // Simple uptime calculation (would need to track start time in production)
        return System.currentTimeMillis();
    }
    
    private void sendSuccess(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(HttpServletResponse.SC_OK);
        
        // Add CORS headers
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", objectMapper.valueToTree(data));
        response.put("timestamp", Instant.now().toString());
        
        objectMapper.writeValue(resp.getWriter(), response);
    }
    
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(status);
        
        // Add CORS headers
        resp.setHeader("Access-Control-Allow-Origin", "*");
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        
        objectMapper.writeValue(resp.getWriter(), response);
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        // Handle CORS preflight
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
