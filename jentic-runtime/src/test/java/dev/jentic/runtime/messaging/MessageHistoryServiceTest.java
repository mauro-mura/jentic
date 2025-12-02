package dev.jentic.runtime.messaging;

import dev.jentic.core.Message;
import dev.jentic.core.MessageService;
import dev.jentic.runtime.messaging.MessageHistoryService.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MessageHistoryService")
class MessageHistoryServiceTest {

    private MessageHistoryService service;

    @BeforeEach
    void setUp() {
        service = new MessageHistoryService(100);
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void shouldCreateWithDefaultSize() {
            var defaultService = new MessageHistoryService();
            assertThat(defaultService.getMaxSize())
                    .isEqualTo(MessageHistoryService.DEFAULT_MAX_SIZE);
        }

        @Test
        void shouldCreateWithCustomSize() {
            var customService = new MessageHistoryService(500);
            assertThat(customService.getMaxSize()).isEqualTo(500);
        }

        @Test
        void shouldRejectTooSmallSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new MessageHistoryService(5));
        }

        @Test
        void shouldRejectTooLargeSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new MessageHistoryService(200_000));
        }
    }

    @Nested
    @DisplayName("Store and Retrieve")
    class StoreAndRetrieveTests {

        @Test
        void shouldStoreAndRetrieveMessage() {
            Message msg = createMessage("test-topic", "sender-1", "Hello");
            service.store(msg);

            var recent = service.getRecent(1);
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).topic()).isEqualTo("test-topic");
            assertThat(recent.get(0).senderId()).isEqualTo("sender-1");
        }

        @Test
        void shouldReturnNewestFirst() {
            service.store(createMessage("topic", "sender", "First"));
            service.store(createMessage("topic", "sender", "Second"));
            service.store(createMessage("topic", "sender", "Third"));

            var recent = service.getRecent(3);
            assertThat(recent.get(0).payload()).isEqualTo("Third");
            assertThat(recent.get(1).payload()).isEqualTo("Second");
            assertThat(recent.get(2).payload()).isEqualTo("First");
        }

        @Test
        void shouldEvictOldestWhenFull() {
            var smallService = new MessageHistoryService(10);
            for (int i = 0; i < 15; i++) {
                smallService.store(createMessage("topic", "sender", "msg-" + i));
            }

            assertThat(smallService.size()).isEqualTo(10);
            var recent = smallService.getRecent(10);
            assertThat(recent.get(0).payload()).isEqualTo("msg-14");
            assertThat(recent.get(9).payload()).isEqualTo("msg-5");
        }
    }

    @Nested
    @DisplayName("Filtering")
    class FilteringTests {

        @BeforeEach
        void setupMessages() {
            service.store(createMessage("orders.created", "order-svc", "order1"));
            service.store(createMessage("orders.updated", "order-svc", "order2"));
            service.store(createMessage("payments.processed", "payment-svc", "payment1"));
            service.store(createMessage("orders.deleted", "order-svc", "order3"));
        }

        @Test
        void shouldFilterByExactTopic() {
            var result = service.getByTopic("orders.created");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).payload()).isEqualTo("order1");
        }

        @Test
        void shouldFilterByTopicPattern() {
            var result = service.getByTopicPattern("orders.*");
            assertThat(result).hasSize(3);
        }

        @Test
        void shouldFilterBySender() {
            var result = service.getBySender("payment-svc");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).topic()).isEqualTo("payments.processed");
        }

        @Test
        void shouldFilterByCustomPredicate() {
            var result = service.getByFilter(m -> 
                    m.topic() != null && m.topic().startsWith("orders."));
            assertThat(result).hasSize(3);
        }

        @Test
        void shouldFindById() {
            var all = service.getRecent(10);
            String targetId = all.get(0).id();

            var found = service.getById(targetId);
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(targetId);
        }
    }

    @Nested
    @DisplayName("MessageService Integration")
    class IntegrationTests {

        @Test
        void shouldAttachToMessageService() throws Exception {
            MessageService messageService = new InMemoryMessageService();
            MessageHistoryService history = new MessageHistoryService(100);

            history.attachTo(messageService);
            assertThat(history.isAttached()).isTrue();

            // Send message through bus
            Message msg = createMessage("test.topic", "test-sender", "test-content");
            messageService.send(msg).get(1, TimeUnit.SECONDS);

            // Allow async processing
            Thread.sleep(100);

            // Verify captured
            assertThat(history.size()).isEqualTo(1);
            var stored = history.getRecent(1);
            assertThat(stored.get(0).topic()).isEqualTo("test.topic");
        }

        @Test
        void shouldRejectDoubleAttach() {
            MessageService ms1 = new InMemoryMessageService();
            MessageService ms2 = new InMemoryMessageService();

            service.attachTo(ms1);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.attachTo(ms2))
                    .withMessageContaining("Already attached");
        }

        @Test
        void shouldDetachFromMessageService() {
            MessageService messageService = new InMemoryMessageService();
            service.attachTo(messageService);
            
            assertThat(service.isAttached()).isTrue();
            
            service.detachFrom(messageService);
            assertThat(service.isAttached()).isFalse();
        }
    }

    @Nested
    @DisplayName("Clear and Size")
    class ClearAndSizeTests {

        @Test
        void shouldClearAllMessages() {
            service.store(createMessage("topic", "sender", "msg1"));
            service.store(createMessage("topic", "sender", "msg2"));
            assertThat(service.size()).isEqualTo(2);

            service.clear();
            assertThat(service.isEmpty()).isTrue();
            assertThat(service.size()).isEqualTo(0);
        }

        @Test
        void shouldReportCorrectSize() {
            assertThat(service.size()).isEqualTo(0);
            assertThat(service.isEmpty()).isTrue();

            service.store(createMessage("topic", "sender", "msg"));
            assertThat(service.size()).isEqualTo(1);
            assertThat(service.isEmpty()).isFalse();
        }
    }

    // Helper method
    private Message createMessage(String topic, String senderId, Object content) {
        return Message.builder()
                .topic(topic)
                .senderId(senderId)
                .content(content)
                .build();
    }
}