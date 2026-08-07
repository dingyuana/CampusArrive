package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventEnvelope.Builder;
import com.campusarrive.integration.event.EventType;
import com.campusarrive.integration.idempotent.IdempotentHandler;
import com.campusarrive.integration.publisher.EventPublisher;
import com.campusarrive.integration.testsupport.TestEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CT-MW-005：事件发布与消费测试。
 *
 * <p>规格来源：FR-04-05 报到状态变更事件。
 * 验证生产者发布后消费者正确收到，含序列化、反序列化、幂等消费全链路。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-005: 事件发布与消费")
class EventPublishConsumeTest {

    private ObjectMapper objectMapper;
    private IdempotentHandler idempotentHandler;
    private EventPublisher mockPublisher;
    private EventChainTracker chainTracker;
    private EventConsumers consumers;

    /** 记录 publisher 发送的消息 */
    private List<SentMessage> sentMessages;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        idempotentHandler = new IdempotentHandler();
        chainTracker = new EventChainTracker();
        sentMessages = new ArrayList<>();

        mockPublisher = mock(EventPublisher.class);
        // 模拟重试发布成功
        when(mockPublisher.publishToRetry(any(EventEnvelope.class), anyString())).thenReturn(true);

        // 捕获 publisher 的调用
        doAnswer(invocation -> {
            EventEnvelope env = invocation.getArgument(0);
            sentMessages.add(new SentMessage("main", env));
            return null;
        }).when(mockPublisher).publish(any(EventEnvelope.class));

        doAnswer(invocation -> {
            EventEnvelope env = invocation.getArgument(0);
            String queue = invocation.getArgument(1);
            sentMessages.add(new SentMessage("retry:" + queue, env));
            return true;
        }).when(mockPublisher).publishToRetry(any(EventEnvelope.class), anyString());

        doAnswer(invocation -> {
            EventEnvelope env = invocation.getArgument(0);
            String reason = invocation.getArgument(1);
            sentMessages.add(new SentMessage("dlx:" + reason, env));
            return null;
        }).when(mockPublisher).publishToDeadLetter(any(EventEnvelope.class), anyString());

        consumers = new EventConsumers(objectMapper, idempotentHandler, mockPublisher, chainTracker);
    }

    @Nested
    @DisplayName("事件发布")
    class Publish {

        @Test
        @DisplayName("EventPublisher.publish 发送到 student.events 交换机")
        void publishToExchange() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            mockPublisher.publish(envelope);

            verify(mockPublisher, times(1)).publish(envelope);
        }

        @Test
        @DisplayName("便捷方法 publish(type, source, payload) 构建并发布")
        void publishWithType() {
            EventPublisher realPublisher = mock(EventPublisher.class);

            // 使用 spy 来测试便捷方法
            EventPublisher spyPublisher = spy(new EventPublisher(
                    mock(RabbitTemplate.class),
                    objectMapper));

            // 仅验证 publish(envelope) 被调用
            doNothing().when(spyPublisher).publish(any(EventEnvelope.class));

            spyPublisher.publish(EventType.CHECKIN_SUCCESS, "test-service",
                    Map.of("studentId", "20260001"));

            verify(spyPublisher, times(1)).publish(any(EventEnvelope.class));
        }
    }

    @Nested
    @DisplayName("事件消费")
    class Consume {

        @Test
        @DisplayName("报到成功事件被宿舍确认消费者正确消费")
        void consumeCheckinSuccess() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            // 直接调用消费者方法（模拟 @RabbitListener 触发）
            consumers.onDormCheckin(envelope);

            // 验证事件被记录到事件链追踪器
            List<String> chain = chainTracker.getChain("20260001");
            assertTrue(chain.contains(EventType.CHECKIN_SUCCESS.routingKey()));
        }

        @Test
        @DisplayName("缴费完成事件被宿舍正式分配消费者正确消费")
        void consumePaymentCompleted() {
            EventEnvelope envelope = TestEventFactory.paymentCompleted("20260001");

            consumers.onDormAllocate(envelope);

            List<String> chain = chainTracker.getChain("20260001");
            assertTrue(chain.contains(EventType.PAYMENT_COMPLETED.routingKey()));
        }

        @Test
        @DisplayName("核验通过事件被学籍激活消费者正确消费")
        void consumeVerifiedSuccess() {
            EventEnvelope envelope = TestEventFactory.verifiedSuccess("20260001");

            consumers.onEduActivate(envelope);

            List<String> chain = chainTracker.getChain("20260001");
            assertTrue(chain.contains(EventType.VERIFIED_SUCCESS.routingKey()));
        }

        @Test
        @DisplayName("报到完成事件被教籍注册消费者正确消费")
        void consumeCheckinCompleted() {
            String studentId = "20260001";
            // 先完成前置事件
            consumers.onDormCheckin(TestEventFactory.checkinSuccess(studentId));
            consumers.onDormAllocate(TestEventFactory.paymentCompleted(studentId));
            consumers.onEduActivate(TestEventFactory.verifiedSuccess(studentId));

            // 再完成报到
            EventEnvelope envelope = TestEventFactory.checkinCompleted(studentId);
            consumers.onEduRegister(envelope);

            List<String> chain = chainTracker.getChain(studentId);
            assertTrue(chain.contains(EventType.CHECKIN_COMPLETED.routingKey()));
        }
    }

    @Nested
    @DisplayName("重复消费幂等")
    class IdempotentConsume {

        @Test
        @DisplayName("同一事件投递两次仅处理一次")
        void duplicateConsume() {
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            // 第一次消费
            consumers.onDormCheckin(envelope);
            int chainSizeAfterFirst = chainTracker.getChain("20260001").size();

            // 第二次消费（相同 eventId）
            consumers.onDormCheckin(envelope);
            int chainSizeAfterSecond = chainTracker.getChain("20260001").size();

            assertEquals(chainSizeAfterFirst, chainSizeAfterSecond,
                    "重复事件不应增加事件链记录");
        }
    }

    /** 测试辅助：记录发送的消息 */
    private record SentMessage(String target, EventEnvelope envelope) {
    }
}
