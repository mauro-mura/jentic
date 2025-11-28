package dev.jentic.tools.cli.commands;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * View agent logs via WebSocket.
 *
 * <p>Usage: {@code jentic logs <AGENT_ID> [--follow] [--lines N]}
 */
@Command(
    name = "logs",
    description = "View agent logs"
)
public class LogsCommand extends BaseCommand {

    @Parameters(index = "0", arity = "0..1", 
            description = "Agent ID (omit for all)")
    private String agentId;

    @Option(names = {"-f", "--follow"}, 
            description = "Follow log output (stream)")
    private boolean follow;

    @Option(names = {"-n", "--lines"}, 
            description = "Number of lines to show",
            defaultValue = "50")
    private int lines;

    @Override
    public void run() {
        try {
            if (follow) {
                streamLogs();
            } else {
                showRecentLogs();
            }
        } catch (Exception e) {
            printError("Failed to get logs: " + e.getMessage());
            if (isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    private void showRecentLogs() throws Exception {
        String endpoint = "/api/messages?limit=" + lines;
        if (agentId != null && !agentId.isEmpty()) {
            endpoint += "&agentId=" + agentId;
        }

        JsonNode messages = apiGet(endpoint);

        if (!messages.isArray() || messages.isEmpty()) {
            System.out.println("No recent messages.");
            return;
        }

        for (JsonNode msg : messages) {
            printLogEntry(msg);
        }
    }

    private void streamLogs() throws Exception {
        String wsUrl = getApiUrl().replace("http://", "ws://") + "/ws";
        if (isVerbose()) {
            System.out.println("Connecting to " + wsUrl);
        }

        CountDownLatch latch = new CountDownLatch(1);

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                System.out.println("Connected. Streaming logs (Ctrl+C to exit)...\n");
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                try {
                    JsonNode event = mapper.readTree(data.toString());
                    String type = event.path("type").asText("");

                    // Filter by agent if specified
                    if (agentId != null && !agentId.isEmpty()) {
                        String eventAgent = event.path("agentId").asText("");
                        if (!agentId.equals(eventAgent)) {
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }
                    }

                    printEvent(event, type);
                } catch (Exception e) {
                    // Ignore parse errors
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                System.out.println("\nConnection closed.");
                latch.countDown();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                printError("WebSocket error: " + error.getMessage());
                latch.countDown();
            }
        };

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), listener)
                .join();

        // Wait until interrupted
        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
        latch.await();
    }

    private void printLogEntry(JsonNode msg) {
        String timestamp = msg.path("timestamp").asText("");
        String sender = msg.path("senderId").asText("-");
        String topic = msg.path("topic").asText("-");
        String content = msg.path("content").asText("");

        // Truncate timestamp to time only
        if (timestamp.contains("T")) {
            timestamp = timestamp.substring(timestamp.indexOf("T") + 1);
            if (timestamp.contains(".")) {
                timestamp = timestamp.substring(0, timestamp.indexOf("."));
            }
        }

        System.out.printf("[%s] %-15s %-20s %s%n", 
                timestamp, sender, topic, truncate(content, 50));
    }

    private void printEvent(JsonNode event, String type) {
        String timestamp = event.path("timestamp").asText("");
        String agentId = event.path("agentId").asText("-");

        if (timestamp.contains("T")) {
            timestamp = timestamp.substring(timestamp.indexOf("T") + 1);
            if (timestamp.contains(".")) {
                timestamp = timestamp.substring(0, timestamp.indexOf("."));
            }
        }

        String icon = switch (type) {
            case "agent.started" -> "▶";
            case "agent.stopped" -> "■";
            case "message.sent" -> "→";
            case "error.occurred" -> "✗";
            default -> "•";
        };

        System.out.printf("[%s] %s %-15s %s%n", timestamp, icon, agentId, type);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
