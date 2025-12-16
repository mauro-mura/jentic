package dev.jentic.adapters.a2a;

import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DialogueA2AConverter.
 */
class DialogueA2AConverterTest {
    
    private DialogueA2AConverter converter;
    
    @BeforeEach
    void setUp() {
        converter = new DialogueA2AConverter();
    }
    
    @Test
    void shouldConvertDialogueMessageToA2AMessage() {
        var msg = DialogueMessage.builder()
            .id("msg-1")
            .conversationId("conv-1")
            .senderId("sender")
            .receiverId("receiver")
            .performative(Performative.INFORM)
            .content("result data")
            .build();
        
        Message a2aMsg = converter.toA2AMessage(msg);
        
        assertThat(a2aMsg.getMessageId()).isEqualTo("msg-1");
        assertThat(a2aMsg.getRole()).isEqualTo(Message.Role.AGENT);
        assertThat(a2aMsg.getParts()).hasSize(1);
        
        TextPart part = (TextPart) a2aMsg.getParts().get(0);
        assertThat(part.getText()).isEqualTo("result data");
    }
    
    @Test
    void shouldConvertA2AMessageToDialogueMessage() {
        TextPart textPart = new TextPart("hello world", null);
        Message a2aMsg = new Message.Builder()
            .messageId("msg-1")
            .role(Message.Role.USER)
            .parts(List.of(textPart))
            .build();
        
        DialogueMessage msg = converter.fromA2AMessage(a2aMsg, "task-1", "external-sender", "local-agent");
        
        assertThat(msg.id()).isEqualTo("msg-1");
        assertThat(msg.conversationId()).isEqualTo("task-1");
        assertThat(msg.senderId()).isEqualTo("external-sender");
        assertThat(msg.receiverId()).isEqualTo("local-agent");
        assertThat(msg.performative()).isEqualTo(Performative.REQUEST);
        assertThat(msg.content()).isEqualTo("hello world");
    }
    
    @Test
    void shouldConvertTaskToDialogueMessage() {
        TextPart textPart = new TextPart("Task completed successfully", null);
        Artifact artifact = new Artifact.Builder()
            .artifactId("art-1")
            .parts(List.of(textPart))
            .build();
        
        TaskStatus status = new TaskStatus(TaskState.COMPLETED, null, null);
        
        Task task = new Task.Builder()
            .id("task-1")
            .status(status)
            .artifacts(List.of(artifact))
            .metadata(Map.of(
                "jentic.conversationId", "conv-1",
                "agentId", "remote-agent"
            ))
            .contextId("ctx-1")
            .build();
        
        DialogueMessage msg = converter.fromTask(task, "local-agent");
        
        assertThat(msg.conversationId()).isEqualTo("conv-1");
        assertThat(msg.senderId()).isEqualTo("remote-agent");
        assertThat(msg.receiverId()).isEqualTo("local-agent");
        assertThat(msg.performative()).isEqualTo(Performative.INFORM);
        assertThat(msg.content().toString()).contains("Task completed successfully");
    }
    
    @Test
    void shouldCreateArtifactFromDialogueMessage() {
        var msg = DialogueMessage.builder()
            .id("msg-1")
            .senderId("sender")
            .performative(Performative.INFORM)
            .content("artifact content")
            .build();
        
        Artifact artifact = converter.toArtifact(msg);
        
        assertThat(artifact.artifactId()).isEqualTo("msg-1");
        assertThat(artifact.name()).isEqualTo("response");
        assertThat(artifact.parts()).hasSize(1);
        
        TextPart part = (TextPart) artifact.parts().get(0);
        assertThat(part.getText()).isEqualTo("artifact content");
    }
    
    @Test
    void shouldMapTaskStateToPerformative() {
        assertThat(converter.mapTaskStateToPerformative(TaskState.COMPLETED))
            .isEqualTo(Performative.INFORM);
        assertThat(converter.mapTaskStateToPerformative(TaskState.FAILED))
            .isEqualTo(Performative.FAILURE);
        assertThat(converter.mapTaskStateToPerformative(TaskState.CANCELED))
            .isEqualTo(Performative.CANCEL);
        assertThat(converter.mapTaskStateToPerformative(TaskState.WORKING))
            .isEqualTo(Performative.AGREE);
        assertThat(converter.mapTaskStateToPerformative(TaskState.SUBMITTED))
            .isEqualTo(Performative.AGREE);
        assertThat(converter.mapTaskStateToPerformative(TaskState.INPUT_REQUIRED))
            .isEqualTo(Performative.QUERY);
    }
    
    @Test
    void shouldMapPerformativeToTaskState() {
        assertThat(converter.mapPerformativeToTaskState(Performative.INFORM))
            .isEqualTo(TaskState.COMPLETED);
        assertThat(converter.mapPerformativeToTaskState(Performative.FAILURE))
            .isEqualTo(TaskState.FAILED);
        assertThat(converter.mapPerformativeToTaskState(Performative.CANCEL))
            .isEqualTo(TaskState.CANCELED);
        assertThat(converter.mapPerformativeToTaskState(Performative.AGREE))
            .isEqualTo(TaskState.WORKING);
        assertThat(converter.mapPerformativeToTaskState(Performative.REFUSE))
            .isEqualTo(TaskState.FAILED);
        assertThat(converter.mapPerformativeToTaskState(Performative.QUERY))
            .isEqualTo(TaskState.INPUT_REQUIRED);
    }
    
    @Test
    void shouldHandleNullContent() {
        var msg = DialogueMessage.builder()
            .id("msg-1")
            .senderId("sender")
            .performative(Performative.AGREE)
            .content(null)
            .build();
        
        Message a2aMsg = converter.toA2AMessage(msg);
        
        assertThat(a2aMsg.getParts()).hasSize(1);
        TextPart part = (TextPart) a2aMsg.getParts().get(0);
        assertThat(part.getText()).isEmpty();
    }
    
    @Test
    void shouldExtractContentFromStatusMessage() {
        TextPart statusTextPart = new TextPart("Status message content", null);
        Message statusMessage = new Message.Builder()
            .messageId("status-msg")
            .role(Message.Role.AGENT)
            .parts(List.of(statusTextPart))
            .build();
        
        TaskStatus status = new TaskStatus(TaskState.COMPLETED, statusMessage, null);
        
        Task task = new Task.Builder()
            .id("task-1")
            .status(status)
            .contextId("ctx-1")
            .build();
        
        DialogueMessage msg = converter.fromTask(task, "local-agent");
        
        assertThat(msg.content()).isEqualTo("Status message content");
    }
    
    @Test
    void shouldUseDefaultSenderIdWhenNotInMetadata() {
        TaskStatus status = new TaskStatus(TaskState.COMPLETED, null, null);
        
        Task task = new Task.Builder()
            .id("task-1")
            .status(status)
            .contextId("ctx-1")
            .build();
        
        DialogueMessage msg = converter.fromTask(task, "local-agent");
        
        assertThat(msg.senderId()).isEqualTo("external-agent");
    }
}