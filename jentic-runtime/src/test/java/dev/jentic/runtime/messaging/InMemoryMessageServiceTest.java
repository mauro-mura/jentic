package dev.jentic.runtime.messaging;

import dev.jentic.core.Message;
import dev.jentic.core.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for InMemoryMessageService
 */
class InMemoryMessageServiceTest {
    
    private InMemoryMessageService messageService;
    
    @BeforeEach
    void setUp() {
        messageService = new InMemoryMessageService();
    }
    
    @Test
    void shouldSendAndReceiveMessage() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        String topic = "test.topic";
        String content = "test content";
        
        MessageHandler handler = message -> {
            receivedMessage.set(message);
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };
        
        // When
        messageService.subscribe(topic, handler);
        
        Message sentMessage = Message.builder()
            .topic(topic)
            .content(content)
            .build();
        
        messageService.send(sentMessage).join();
        
        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).isNotNull();
        assertThat(receivedMessage.get().topic()).isEqualTo(topic);
        assertThat(receivedMessage.get().content()).isEqualTo(content);
    }
    
    @Test
    void shouldSupportMultipleSubscribers() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger receiveCount = new AtomicInteger(0);
        
        String topic = "test.topic";
        
        MessageHandler handler1 = message -> {
            receiveCount.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };
        
        MessageHandler handler2 = message -> {
            receiveCount.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };
        
        // When
        messageService.subscribe(topic, handler1);
        messageService.subscribe(topic, handler2);
        
        Message message = Message.builder()
            .topic(topic)
            .content("test")
            .build();
        
        messageService.send(message).join();
        
        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receiveCount.get()).isEqualTo(2);
    }
    
    @Test
    void shouldSupportPredicateSubscription() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        MessageHandler handler = message -> {
            receivedMessage.set(message);
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };
        
        // Subscribe to messages with specific header
        messageService.subscribe(
            message -> "important".equals(message.headers().get("priority")),
            handler
        );
        
        // When
        // Send message without priority header - should not be received
        Message normalMessage = Message.builder()
            .topic("test")
            .content("normal message")
            .build();
        messageService.send(normalMessage).join();
        
        // Send message with priority header - should be received
        Message importantMessage = Message.builder()
            .topic("test")
            .content("important message")
            .header("priority", "important")
            .build();
        messageService.send(importantMessage).join();
        
        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).isNotNull();
        assertThat(receivedMessage.get().content()).isEqualTo("important message");
    }
    
    @Test
    void shouldHandleSendAndWait() throws Exception {
        // Given
        String requestTopic = "request";
        String responseTopic = "response";
        String requestContent = "ping";
        String responseContent = "pong";
        
        // Set up responder
        MessageHandler responder = message -> {
            Message response = Message.builder()
                .topic(responseTopic)
                .correlationId(message.id()) // Important: use original message ID as correlation ID
                .content(responseContent)
                .build();
            
            // Simulate some processing delay
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100);
                    messageService.send(response).join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        };
        
        messageService.subscribe(requestTopic, responder);
        
        // When
        Message request = Message.builder()
            .topic(requestTopic)
            .content(requestContent)
            .build();
        
        CompletableFuture<Message> futureResponse = messageService.sendAndWait(request, 5000);
        Message response = futureResponse.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(responseContent);
        assertThat(response.correlationId()).isEqualTo(request.id());
    }
    
    @Test
    void shouldTimeoutOnSendAndWait() {
        // Given
        Message request = Message.builder()
            .topic("no-responder")
            .content("will timeout")
            .build();
        
        // When/Then
        CompletableFuture<Message> futureResponse = messageService.sendAndWait(request, 100);
        
        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }
    
    @Test
    void shouldHandleHandlerExceptions() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        
        MessageHandler faultyHandler = message -> {
            latch.countDown();
            throw new RuntimeException("Handler error");
        };
        
        // When
        messageService.subscribe("error.topic", faultyHandler);
        
        Message message = Message.builder()
            .topic("error.topic")
            .content("test")
            .build();
        
        // Should not throw exception
        assertThatCode(() -> messageService.send(message).join())
            .doesNotThrowAnyException();
        
        // Handler should still be called
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testDirectReceiverSubscription() throws ExecutionException, InterruptedException, TimeoutException {
        InMemoryMessageService service = new InMemoryMessageService();

        CompletableFuture<Message> received = new CompletableFuture<>();

        // Bob subscribes
        service.subscribeToReceiver("bob", msg -> {
            received.complete(msg);
            return CompletableFuture.completedFuture(null);
        });

        // Alice sends to Bob
        Message msg = Message.builder()
                .senderId("alice")
                .receiverId("bob")
                .content("Hello Bob")
                .build();

        service.send(msg).join();

        // Verify delivery
        Message receivedMsg = received.get(1, TimeUnit.SECONDS);
        assertEquals("alice", receivedMsg.senderId());
        assertEquals("bob", receivedMsg.receiverId());
        assertEquals("Hello Bob", receivedMsg.content());
    }

    @Test
    void testHybridRoutingTopicAndReceiver() throws InterruptedException {
        InMemoryMessageService service = new InMemoryMessageService();

        AtomicInteger topicCount = new AtomicInteger(0);
        AtomicInteger receiverCount = new AtomicInteger(0);

        // Subscribe to topic
        service.subscribe("notifications", msg -> {
            topicCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        // Subscribe to receiver
        service.subscribeToReceiver("manager", msg -> {
            receiverCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        // Send with both topic and receiver
        Message msg = Message.builder()
                .receiverId("manager")
                .topic("notifications")
                .content("Important update")
                .build();

        service.send(msg).join();
        Thread.sleep(100); // Wait for async delivery

        // Verify delivered via both routes
        assertEquals(1, topicCount.get());
        assertEquals(1, receiverCount.get());
    }

    @Test
    void testNoReceiversDoesNotThrow() {
        InMemoryMessageService service = new InMemoryMessageService();

        Message msg = Message.builder()
                .receiverId("non-existent-agent")
                .content("Hello")
                .build();

        // Should not throw, just log trace
        assertDoesNotThrow(() -> service.send(msg).join());
    }
}