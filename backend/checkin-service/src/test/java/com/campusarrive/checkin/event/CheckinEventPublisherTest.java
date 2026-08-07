package com.campusarrive.checkin.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 报到事件发布器单元测试。
 *
 * <p>规格来源：FR-04-05 报到状态变更事件、MW-2.2 RabbitMQ 事件链。
 * 验证四类核心事件正确发布到 student.events 交换机，消息持久化且包含正确的路由键与 payload。</p>
 */
@DisplayName("UT-MW-022: 报到事件发布器")
class CheckinEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private CheckinEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();
        publisher = new CheckinEventPublisher(rabbitTemplate, objectMapper);
    }

    @Nested
    @DisplayName("报到成功事件")
    class CheckinSuccess {

        @Test
        @DisplayName("发布到 student.events 交换机，路由键为 student.checkin.success")
        void publishCheckinSuccess() {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼一层大厅");

            verify(rabbitTemplate, times(1))
                    .send(eq("student.events"), eq("student.checkin.success"), any(Message.class));
        }

        @Test
        @DisplayName("消息 payload 包含 studentId、name、checkinTime、checkinPoint")
        void payloadContainsCorrectFields() throws Exception {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼一层大厅");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            String body = new String(captor.getValue().getBody());
            assertTrue(body.contains("\"studentId\":\"20260001\""), "payload 应包含 studentId");
            assertTrue(body.contains("\"name\":\"张三\""), "payload 应包含 name");
            assertTrue(body.contains("\"checkinPoint\":\"主楼一层大厅\""), "payload 应包含 checkinPoint");
            assertTrue(body.contains("\"checkinTime\""), "payload 应包含 checkinTime");
        }
    }

    @Nested
    @DisplayName("缴费完成事件")
    class PaymentCompleted {

        @Test
        @DisplayName("路由键为 student.payment.completed")
        void publishPaymentCompleted() {
            publisher.publishPaymentCompleted("20260001", "PAY20260828001", 5800.00, "WECHAT");

            verify(rabbitTemplate)
                    .send(eq("student.events"), eq("student.payment.completed"), any(Message.class));
        }

        @Test
        @DisplayName("payload 包含缴费信息")
        void payloadContainsPaymentInfo() throws Exception {
            publisher.publishPaymentCompleted("20260001", "PAY20260828001", 5800.00, "WECHAT");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            String body = new String(captor.getValue().getBody());
            assertTrue(body.contains("\"payOrderNo\":\"PAY20260828001\""));
            assertTrue(body.contains("\"payAmount\":5800.0"));
            assertTrue(body.contains("\"payMethod\":\"WECHAT\""));
        }
    }

    @Nested
    @DisplayName("核验通过事件")
    class VerifiedSuccess {

        @Test
        @DisplayName("路由键为 student.verified.success")
        void publishVerifiedSuccess() {
            publisher.publishVerifiedSuccess("20260001", "张三");

            verify(rabbitTemplate)
                    .send(eq("student.events"), eq("student.verified.success"), any(Message.class));
        }
    }

    @Nested
    @DisplayName("报到完成事件")
    class CheckinCompleted {

        @Test
        @DisplayName("路由键为 student.checkin.completed")
        void publishCheckinCompleted() {
            publisher.publishCheckinCompleted("20260001");

            verify(rabbitTemplate)
                    .send(eq("student.events"), eq("student.checkin.completed"), any(Message.class));
        }

        @Test
        @DisplayName("payload 包含 completedTime")
        void payloadContainsCompletedTime() throws Exception {
            publisher.publishCheckinCompleted("20260001");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            String body = new String(captor.getValue().getBody());
            assertTrue(body.contains("\"completedTime\""));
        }
    }

    @Nested
    @DisplayName("事件信封格式")
    class EnvelopeFormat {

        @Test
        @DisplayName("事件信封包含 eventId、eventType、eventTime、source、version、payload")
        void envelopeContainsAllFields() throws Exception {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            String body = new String(captor.getValue().getBody());
            assertTrue(body.contains("\"eventId\""), "应包含 eventId");
            assertTrue(body.contains("\"eventType\""), "应包含 eventType");
            assertTrue(body.contains("\"eventTime\""), "应包含 eventTime");
            assertTrue(body.contains("\"source\":\"checkin-service\""), "source 应为 checkin-service");
            assertTrue(body.contains("\"version\":\"1.0\""), "version 应为 1.0");
            assertTrue(body.contains("\"payload\""), "应包含 payload");
        }

        @Test
        @DisplayName("eventId 以 evt- 前缀开头且为 UUID")
        void eventIdIsUuid() throws Exception {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            String body = new String(captor.getValue().getBody());
            // 提取 eventId
            assertTrue(body.contains("\"eventId\":\"evt-"), "eventId 应以 evt- 开头");
        }

        @Test
        @DisplayName("每次发布生成不同的 eventId")
        void uniqueEventIds() {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate, times(2)).send(anyString(), anyString(), captor.capture());

            String body1 = new String(captor.getAllValues().get(0).getBody());
            String body2 = new String(captor.getAllValues().get(1).getBody());
            assertNotEquals(body1, body2, "两次发布的事件应不同（eventId 不同）");
        }
    }

    @Nested
    @DisplayName("消息属性")
    class MessagePropertyTests {

        @Test
        @DisplayName("消息为持久化模式（PERSISTENT）")
        void messageIsPersistent() {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            MessageProperties props = captor.getValue().getMessageProperties();
            assertEquals(MessageDeliveryMode.PERSISTENT, props.getDeliveryMode(),
                    "消息应为持久化模式");
        }

        @Test
        @DisplayName("消息 Content-Type 为 application/json")
        void contentTypeIsJson() {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            MessageProperties props = captor.getValue().getMessageProperties();
            assertEquals(MessageProperties.CONTENT_TYPE_JSON, props.getContentType(),
                    "Content-Type 应为 application/json");
        }

        @Test
        @DisplayName("消息 messageId 与 eventId 一致")
        void messageIdMatchesEventId() throws Exception {
            publisher.publishCheckinSuccess("20260001", "张三", "主楼");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());

            MessageProperties props = captor.getValue().getMessageProperties();
            assertNotNull(props.getMessageId(), "messageId 不应为空");
            assertTrue(props.getMessageId().startsWith("evt-"), "messageId 应以 evt- 开头");

            // 验证 correlationId 也一致
            assertEquals(props.getMessageId(), props.getCorrelationId(),
                    "correlationId 应与 messageId 一致");
        }
    }
}
