package com.campusarrive.integration.publisher;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventType;
import com.campusarrive.integration.testsupport.TestEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 事件发布器单元测试。
 *
 * <p>规格来源：MW-2.2 RabbitMQ 事件链、SIM-CA-2026-08 第 8.1 节消息持久化。
 * 验证事件发布到正确交换机、消息持久化、重试/死信路由逻辑。</p>
 */
@DisplayName("UT-MW-022: 事件发布器")
class EventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisher = new EventPublisher(rabbitTemplate, objectMapper);
    }

    @Nested
    @DisplayName("发布事件到主交换机")
    class PublishToMainExchange {

        @Test
        @DisplayName("事件发布到 student.events 交换机，路由键为事件类型")
        void publishToCorrectExchange() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publish(envelope);

            verify(rabbitTemplate).send(
                    eq(EventConstants.EXCHANGE_STUDENT_EVENTS),
                    eq(EventType.CHECKIN_SUCCESS.routingKey()),
                    any(Message.class)
            );
        }

        @Test
        @DisplayName("消息为持久化模式")
        void messageIsPersistent() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publish(envelope);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals(MessageDeliveryMode.PERSISTENT,
                    captor.getValue().getMessageProperties().getDeliveryMode());
        }

        @Test
        @DisplayName("消息 Content-Type 为 application/json")
        void contentTypeIsJson() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publish(envelope);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals(MessageProperties.CONTENT_TYPE_JSON,
                    captor.getValue().getMessageProperties().getContentType());
        }

        @Test
        @DisplayName("messageId 与 envelope eventId 一致")
        void messageIdMatchesEventId() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publish(envelope);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals(envelope.getEventId(),
                    captor.getValue().getMessageProperties().getMessageId());
            assertEquals(envelope.getEventId(),
                    captor.getValue().getMessageProperties().getCorrelationId());
        }

        @Test
        @DisplayName("消息头包含 retryCount")
        void headerContainsRetryCount() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publish(envelope);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            Object retryHeader = captor.getValue().getMessageProperties()
                    .getHeader(EventConstants.HEADER_RETRY_COUNT);
            assertEquals(0, retryHeader, "初始发布 retryCount 应为 0");
        }
    }

    @Nested
    @DisplayName("便捷发布方法")
    class ConveniencePublish {

        @Test
        @DisplayName("publish(EventType, source, payload) 构建并发布事件")
        void conveniencePublishBuildsAndSends() {
            EventEnvelope result = publisher.publish(
                    EventType.CHECKIN_COMPLETED,
                    "checkin-service",
                    java.util.Map.of("studentId", "20260001")
            );

            assertNotNull(result);
            assertEquals(EventType.CHECKIN_COMPLETED.routingKey(), result.getEventType());
            assertEquals("checkin-service", result.getSource());
            verify(rabbitTemplate).send(
                    eq(EventConstants.EXCHANGE_STUDENT_EVENTS),
                    eq(EventType.CHECKIN_COMPLETED.routingKey()),
                    any(Message.class)
            );
        }
    }

    @Nested
    @DisplayName("重试队列发布")
    class PublishToRetry {

        @Test
        @DisplayName("有效重试次数发送到重试交换机")
        void validRetryCountSendsToRetryExchange() {
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 1);

            boolean sent = publisher.publishToRetry(envelope, "q.dorm.checkin");

            assertTrue(sent, "retryCount=1 应发送到重试队列");
            verify(rabbitTemplate).send(
                    eq(EventConstants.EXCHANGE_RETRY),
                    eq("retry.1"),
                    any(Message.class)
            );
        }

        @Test
        @DisplayName("重试消息包含原始队列和路由键头")
        void retryMessageContainsHeaders() {
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 2);

            publisher.publishToRetry(envelope, "q.dorm.checkin");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            MessageProperties props = captor.getValue().getMessageProperties();
            Integer retryCount = (Integer) props.getHeader(EventConstants.HEADER_RETRY_COUNT);
            assertEquals(2, retryCount, "retryCount 头应为 2");
            assertEquals("q.dorm.checkin", props.getHeader(EventConstants.HEADER_ORIGINAL_QUEUE), "原始队列头应匹配");
            assertEquals(envelope.getEventType(),
                    props.getHeader(EventConstants.HEADER_ORIGINAL_ROUTING_KEY));
        }

        @Test
        @DisplayName("超过最大重试次数返回 false 且不发送")
        void exceedsMaxRetryReturnsFalse() {
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"),
                    EventConstants.MAX_RETRY_COUNT + 1);

            boolean sent = publisher.publishToRetry(envelope, "q.dorm.checkin");

            assertFalse(sent, "超过最大重试次数应返回 false");
            verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
        }

        @Test
        @DisplayName("重试消息为持久化模式")
        void retryMessageIsPersistent() {
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 1);

            publisher.publishToRetry(envelope, "q.dorm.checkin");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals(MessageDeliveryMode.PERSISTENT,
                    captor.getValue().getMessageProperties().getDeliveryMode());
        }
    }

    @Nested
    @DisplayName("死信队列发布")
    class PublishToDeadLetter {

        @Test
        @DisplayName("死信事件发送到 DLX 交换机")
        void deadLetterSentToDlxExchange() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publishToDeadLetter(envelope, "MAX_RETRY_EXCEEDED");

            verify(rabbitTemplate).send(
                    eq(EventConstants.EXCHANGE_DLX),
                    eq(envelope.getEventType()),
                    any(Message.class)
            );
        }

        @Test
        @DisplayName("死信消息包含死信原因头")
        void deadLetterMessageContainsReasonHeader() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publishToDeadLetter(envelope, "MAX_RETRY_EXCEEDED");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals("MAX_RETRY_EXCEEDED",
                    captor.getValue().getMessageProperties().getHeader("x-dead-letter-reason"));
        }

        @Test
        @DisplayName("死信消息为持久化模式")
        void deadLetterMessageIsPersistent() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            publisher.publishToDeadLetter(envelope, "DEAD_LETTER");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            assertEquals(MessageDeliveryMode.PERSISTENT,
                    captor.getValue().getMessageProperties().getDeliveryMode());
        }
    }
}
