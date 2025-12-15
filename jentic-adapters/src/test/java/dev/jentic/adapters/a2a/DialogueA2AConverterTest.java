package dev.jentic.adapters.a2a;

import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogueA2AConverterTest {
    
    private DialogueA2AConverter converter;
    
    @BeforeEach
    void setUp() {
        converter = new DialogueA2AConverter();
    }
    
    @Test
    void shouldConvertDialogueMessageToA2A() {
        var msg = DialogueMessage.builder()
            .id("msg-1")
            .conversationId("conv-1")
            .senderId("sender")
            .receiverId("receiver")
            .performative(Performative.REQUEST)
            .content("do task")
            .build();
        
        var a2aMsg = converter.toA2AMessage(msg);
        
        assertThat(a2aMsg.messageId()).isEqualTo("msg-1");
        assertThat(a2aMsg.contextId()).isEqualTo("conv-1");
        assertThat(a2aMsg.content()).isEqualTo("do task");
        assertThat(a2aMsg.role()).isEqualTo("user");
        assertThat(a2aMsg.metadata().get("performative")).isEqualTo("REQUEST");
    }
    
    @Test
    void shouldConvertA2AToDialogueMessage() {
        var a2aMsg = new DialogueA2AConverter.A2AMessage(
            "msg-1",
            "conv-1",
            "hello",
            "user",
            Map.of("performative", "QUERY", "senderId", "external")
        );
        
        var msg = converter.fromA2AMessage(a2aMsg, "local-agent");
        
        assertThat(msg.id()).isEqualTo("msg-1");
        assertThat(msg.conversationId()).isEqualTo("conv-1");
        assertThat(msg.senderId()).isEqualTo("external");
        assertThat(msg.receiverId()).isEqualTo("local-agent");
        assertThat(msg.performative()).isEqualTo(Performative.QUERY);
        assertThat(msg.content()).isEqualTo("hello");
    }
    
    @Test
    void shouldInferPerformativeFromRole() {
        var a2aMsg = new DialogueA2AConverter.A2AMessage(
            "msg-1", "conv-1", "data", "assistant", Map.of()
        );
        
        var msg = converter.fromA2AMessage(a2aMsg, "local");
        
        assertThat(msg.performative()).isEqualTo(Performative.INFORM);
    }
    
    @Test
    void shouldMapPerformativeToCorrectRole() {
        var request = DialogueMessage.builder()
            .performative(Performative.REQUEST).content("").senderId("request").build();
        var inform = DialogueMessage.builder()
            .performative(Performative.INFORM).content("").senderId("inform").build();
        var notify = DialogueMessage.builder()
            .performative(Performative.NOTIFY).content("").senderId("notify").build();
        
        assertThat(converter.toA2AMessage(request).role()).isEqualTo("user");
        assertThat(converter.toA2AMessage(inform).role()).isEqualTo("assistant");
        assertThat(converter.toA2AMessage(notify).role()).isEqualTo("system");
    }
    
    @Test
    void shouldConvertResponseToA2AResponse() {
        var response = DialogueMessage.builder()
            .id("msg-2")
            .conversationId("conv-1")
            .senderId("conversation")
            .performative(Performative.INFORM)
            .content("result data")
            .build();
        
        var a2aResponse = converter.toA2AResponse(response);
        
        assertThat(a2aResponse.messageId()).isEqualTo("msg-2");
        assertThat(a2aResponse.content()).isEqualTo("result data");
        assertThat(a2aResponse.status()).isEqualTo("completed");
        assertThat(a2aResponse.isError()).isFalse();
    }
    
    @Test
    void shouldMarkFailureAsError() {
        var failure = DialogueMessage.builder()
            .performative(Performative.FAILURE)
            .senderId("failure")
            .content("error occurred")
            .build();
        
        var a2aResponse = converter.toA2AResponse(failure);
        
        assertThat(a2aResponse.status()).isEqualTo("failed");
        assertThat(a2aResponse.isError()).isTrue();
    }
    
    @Test
    void shouldHandleNullContent() {
        var msg = DialogueMessage.builder()
            .performative(Performative.AGREE)
            .senderId("null-content")
            .build();
        
        var a2aMsg = converter.toA2AMessage(msg);
        
        assertThat(a2aMsg.content()).isEmpty();
    }
}