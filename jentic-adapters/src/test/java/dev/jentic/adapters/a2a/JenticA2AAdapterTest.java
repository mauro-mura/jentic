package dev.jentic.adapters.a2a;

import dev.jentic.core.AgentDescriptor;
import dev.jentic.core.AgentDirectory;
import dev.jentic.core.Message;
import dev.jentic.core.MessageService;
import dev.jentic.core.dialogue.DialogueMessage;
import dev.jentic.core.dialogue.Performative;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class JenticA2AAdapterTest {
    
    private MessageService messageService;
    private AgentDirectory agentDirectory;
    private JenticA2AClient externalClient;
    private JenticA2AAdapter adapter;
    
    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        agentDirectory = mock(AgentDirectory.class);
        externalClient = mock(JenticA2AClient.class);
        
        // Default: no agents registered
        when(agentDirectory.findById(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        
        adapter = new JenticA2AAdapter(
            messageService,
            agentDirectory,
            externalClient,
            "local-agent",
            Duration.ofSeconds(30)
        );
    }
    
    @Test
    void shouldRouteToInternalAgent() {
        // Mock agent as registered
        var descriptor = mock(AgentDescriptor.class);
        when(agentDirectory.findById("internal-agent"))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(descriptor)));
        
        var responseMsg = Message.builder()
            .senderId("internal-agent")
            .receiverId("local-agent")
            .content("done")
            .header("performative", "INFORM")
            .build();
        when(messageService.sendAndWait(any(Message.class), anyLong()))
            .thenReturn(CompletableFuture.completedFuture(responseMsg));
        
        var msg = DialogueMessage.builder()
            .senderId("local-agent")
            .receiverId("internal-agent")
            .performative(Performative.REQUEST)
            .content("do task")
            .build();
        
        var response = adapter.send(msg).join();
        
        assertThat(response.performative()).isEqualTo(Performative.INFORM);
        verify(messageService).sendAndWait(any(Message.class), anyLong());
        verify(externalClient, never()).send(any(), any(), any());
    }
    
    @Test
    void shouldRouteToExternalA2AAgent() {
        // No internal registration (default mock returns empty)
        
        var externalResponse = DialogueMessage.builder()
            .performative(Performative.INFORM)
            .content("external result")
            .senderId("external-agent")
            .build();
        when(externalClient.send(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(externalResponse));
        
        var msg = DialogueMessage.builder()
            .senderId("local-agent")
            .receiverId("https://external-agent.com")
            .performative(Performative.QUERY)
            .content("what is X?")
            .build();
        
        var response = adapter.send(msg).join();
        
        assertThat(response.performative()).isEqualTo(Performative.INFORM);
        verify(externalClient).send(eq("https://external-agent.com"), any(), eq("local-agent"));
        verify(messageService, never()).sendAndWait(any(), anyLong());
    }
    
    @Test
    void shouldFailForUnknownAgent() {
        // No internal registration (default mock returns empty)
        
        var msg = DialogueMessage.builder()
            .senderId("local-agent")
            .receiverId("unknown-agent")
            .performative(Performative.REQUEST)
            .build();
        
        assertThatThrownBy(() -> adapter.send(msg).join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown agent");
    }
    
    @Test
    void shouldFailForNullReceiver() {
        var msg = DialogueMessage.builder()
            .senderId("local-agent")
            .performative(Performative.REQUEST)
            .build();
        
        assertThatThrownBy(() -> adapter.send(msg).join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required");
    }
    
    @Test
    void shouldRecognizeHttpUrls() {
        // No internal registration (default mock returns empty)
        when(externalClient.send(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(
                DialogueMessage.builder().performative(Performative.INFORM).senderId("sender").build()));
        
        var httpMsg = DialogueMessage.builder()
            .receiverId("http://agent.local")
            .senderId("sender")
            .performative(Performative.REQUEST)
            .build();
        
        var httpsMsg = DialogueMessage.builder()
            .receiverId("https://agent.cloud")
            .senderId("sender")
            .performative(Performative.REQUEST)
            .build();
        
        adapter.send(httpMsg).join();
        adapter.send(httpsMsg).join();
        
        verify(externalClient, times(2)).send(any(), any(), any());
    }
    
    @Test
    void shouldPrioritizeInternalOverUrl() {
        // Mock URL-like agent as registered internally
        var descriptor = mock(AgentDescriptor.class);
        when(agentDirectory.findById("https://local-agent.com"))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(descriptor)));
        
        var responseMsg = Message.builder()
            .header("performative", "INFORM")
            .build();
        when(messageService.sendAndWait(any(), anyLong()))
            .thenReturn(CompletableFuture.completedFuture(responseMsg));
        
        var msg = DialogueMessage.builder()
            .receiverId("https://local-agent.com")
            .senderId("local-agent")
            .performative(Performative.REQUEST)
            .build();
        
        adapter.send(msg).join();
        
        verify(messageService).sendAndWait(any(), anyLong());
        verify(externalClient, never()).send(any(), any(), any());
    }
    
    @Test
    void shouldDelegatePingToClient() {
        when(externalClient.ping("https://agent.com"))
            .thenReturn(CompletableFuture.completedFuture(true));
        
        var result = adapter.pingExternal("https://agent.com").join();
        
        assertThat(result).isTrue();
        verify(externalClient).ping("https://agent.com");
    }
}