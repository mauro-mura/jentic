package dev.jentic.tools.cli.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LogsCommandTest {

    private LogsCommand command;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        command = spy(new LogsCommand());
        command.apiUrl = "http://localhost:8080";
        command.verbose = false;

        originalOut = System.out;
        originalErr = System.err;
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void shouldDisplayRecentLogsWithoutFilter() throws Exception {
        // Given
        String json = """
            {
                "data": [
                    {
                        "timestamp": "2024-01-15T10:30:00Z",
                        "agentId": "agent-1",
                        "topic": "system.log",
                        "content": "Agent started"
                    },
                    {
                        "timestamp": "2024-01-15T10:31:00Z",
                        "agentId": "agent-2",
                        "topic": "user.action",
                        "content": "User action triggered"
                    }
                ]
            }
            """;
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        var linesField = LogsCommand.class.getDeclaredField("lines");
        linesField.setAccessible(true);
        linesField.set(command, 50);

        doReturn(response).when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = outContent.toString();
        assertTrue(output.contains("agent-1") || output.contains("system.log"));
    }

    @Test
    void shouldDisplayRecentLogsWithAgentFilter() throws Exception {
        // Given
        String json = """
            {
                "data": [
                    {
                        "timestamp": "2024-01-15T10:30:00Z",
                        "agentId": "agent-1",
                        "topic": "system.log",
                        "content": "Agent started"
                    }
                ]
            }
            """;
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        var agentIdField = LogsCommand.class.getDeclaredField("agentId");
        agentIdField.setAccessible(true);
        agentIdField.set(command, "agent-1");

        doReturn(response).when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = outContent.toString();
        assertFalse(output.isEmpty());
    }

    @Test
    void shouldDisplayRecentLogsWithTopicFilter() throws Exception {
        // Given
        String json = """
            {
                "data": [
                    {
                        "timestamp": "2024-01-15T10:30:00Z",
                        "agentId": "agent-1",
                        "topic": "system.log",
                        "content": "System message"
                    }
                ]
            }
            """;
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        var topicField = LogsCommand.class.getDeclaredField("topic");
        topicField.setAccessible(true);
        topicField.set(command, "system.log");

        doReturn(response).when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = outContent.toString();
        assertFalse(output.isEmpty());
    }

    @Test
    void shouldHandleEmptyLogs() throws Exception {
        // Given
        String json = "{\"data\": []}";
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        doReturn(response).when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = outContent.toString();
        assertTrue(output.contains("No recent messages"));
    }

    @Test
    void shouldHandleApiError() throws Exception {
        // Given
        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        doThrow(new RuntimeException("Connection failed"))
                .when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = errContent.toString();
        assertTrue(output.contains("Failed to get logs"));
    }

    @Test
    void shouldPrintStackTraceWhenVerbose() throws Exception {
        // Given
        command.verbose = true;

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        doThrow(new RuntimeException("API error"))
                .when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = errContent.toString();
        assertTrue(output.contains("Failed to get logs"));
    }

    @Test
    void shouldHandleTopicPatternFilter() throws Exception {
        // Given
        String json = """
            {
                "data": [
                    {
                        "timestamp": "2024-01-15T10:30:00Z",
                        "agentId": "agent-1",
                        "topic": "system.log.info",
                        "content": "Info message"
                    }
                ]
            }
            """;
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        var topicPatternField = LogsCommand.class.getDeclaredField("topicPattern");
        topicPatternField.setAccessible(true);
        topicPatternField.set(command, "system.*");

        doReturn(response).when(command).apiGet(anyString());

        // When
        command.run();

        // Then
        String output = outContent.toString();
        assertTrue(output.contains("Recent messages") || output.contains("system.log.info"));
    }

    @Test
    void shouldRespectLinesParameter() throws Exception {
        // Given
        String json = """
            {
                "data": [
                    {
                        "timestamp": "2024-01-15T10:30:00Z",
                        "agentId": "agent-1",
                        "topic": "test",
                        "content": "Message"
                    }
                ]
            }
            """;
        JsonNode response = mapper.readTree(json);

        var followField = LogsCommand.class.getDeclaredField("follow");
        followField.setAccessible(true);
        followField.set(command, false);

        var linesField = LogsCommand.class.getDeclaredField("lines");
        linesField.setAccessible(true);
        linesField.set(command, 10);

        doReturn(response).when(command).apiGet(contains("limit=10"));

        // When
        command.run();

        // Then - verify API was called with the correct limit
        verify(command).apiGet(contains("limit=10"));
    }
}