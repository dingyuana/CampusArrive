package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventType;
import com.campusarrive.integration.idempotent.IdempotentHandler;
import com.campusarrive.integration.publisher.EventPublisher;
import com.campusarrive.integration.testsupport.TestEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CT-MW-007：重试与死信测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 8.2~8.4 节消息可靠性保障。
 * 验证消费失败后重试超限进死信队列（重试 3 次后进入死信队列）。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-007: 重试与死信")
class RetryDeadLetterTest {

    private ObjectMapper objectMapper;
    private IdempotentHandler idempotentHandler;
    private EventPublisher mockPublisher;
    private EventChainTracker chainTracker;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        idempotentHandler = new IdempotentHandler();
        mockPublisher = mock(EventPublisher.class);
        // 模拟重试发布成功
        when(mockPublisher.publishToRetry(any(EventEnvelope.class), anyString())).thenReturn(true);
        chainTracker = new EventChainTracker();
    }

    // ================================================================
    // 基于 BaseEventConsumer 的测试消费者
    // ================================================================

    /**
     * 可配置的测试消费者 — 可设置抛出异常类型。
     */
    private static class TestConsumer extends BaseEventConsumer {
        private RuntimeException exceptionToThrow;
        private int consumeCount = 0;

        TestConsumer(ObjectMapper objectMapper, IdempotentHandler idempotentHandler,
                     EventPublisher eventPublisher) {
            super(objectMapper, idempotentHandler, eventPublisher);
        }

        void setException(RuntimeException e) {
            this.exceptionToThrow = e;
        }

        int getConsumeCount() {
            return consumeCount;
        }

        @Override
        protected void doConsume(EventEnvelope envelope) throws RetryableException, NonRetryableException {
            consumeCount++;
            if (exceptionToThrow != null) {
                if (exceptionToThrow instanceof RetryableException) {
                    throw (RetryableException) exceptionToThrow;
                }
                if (exceptionToThrow instanceof NonRetryableException) {
                    throw (NonRetryableException) exceptionToThrow;
                }
                throw new RetryableException("Unexpected", exceptionToThrow);
            }
        }

        @Override
        protected String getQueueName() {
            return "q.test";
        }
    }

    @Nested
    @DisplayName("正常消费")
    class Success {

        @Test
        @DisplayName("消费成功返回 SUCCESS")
        void consumeSuccess() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.SUCCESS, result);
            assertEquals(1, consumer.getConsumeCount());
            verify(mockPublisher, never()).publishToDeadLetter(any(), anyString());
            verify(mockPublisher, never()).publishToRetry(any(), anyString());
        }
    }

    @Nested
    @DisplayName("不可重试异常 → 直接死信")
    class NonRetryable {

        @Test
        @DisplayName("NonRetryableException 直接进入死信队列")
        void nonRetryableGoesToDLX() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            consumer.setException(new NonRetryableException("数据格式错误"));
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.DEAD_LETTER, result);
            verify(mockPublisher, times(1)).publishToDeadLetter(eq(envelope), contains("NonRetryableException"));
            verify(mockPublisher, never()).publishToRetry(any(), anyString());
        }
    }

    @Nested
    @DisplayName("可重试异常 → 阶梯重试")
    class Retryable {

        @Test
        @DisplayName("第 1 次可重试异常 → 发送到重试队列 (retryCount=1)")
        void firstRetry() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            consumer.setException(new RetryableException("网络抖动"));
            EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");
            // 清除幂等标记，使后续消费可以执行
            idempotentHandler.reset();

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.RETRY, result);
            assertEquals(1, envelope.getRetryCount(), "retryCount 应递增为 1");
            verify(mockPublisher, times(1)).publishToRetry(eq(envelope), eq("q.test"));
            verify(mockPublisher, never()).publishToDeadLetter(any(), anyString());
        }

        @Test
        @DisplayName("第 2 次可重试异常 → 发送到重试队列 (retryCount=2)")
        void secondRetry() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            consumer.setException(new RetryableException("临时不可用"));
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 1);
            idempotentHandler.reset();

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.RETRY, result);
            assertEquals(2, envelope.getRetryCount());
            verify(mockPublisher, times(1)).publishToRetry(eq(envelope), eq("q.test"));
        }

        @Test
        @DisplayName("第 3 次可重试异常 → 发送到重试队列 (retryCount=3)")
        void thirdRetry() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            consumer.setException(new RetryableException("服务暂时不可用"));
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 2);
            idempotentHandler.reset();

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.RETRY, result);
            assertEquals(3, envelope.getRetryCount());
            verify(mockPublisher, times(1)).publishToRetry(eq(envelope), eq("q.test"));
        }

        @Test
        @DisplayName("重试 3 次后仍失败 → 进入死信队列")
        void maxRetryExceeded() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            consumer.setException(new RetryableException("持续失败"));
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 3);
            idempotentHandler.reset();

            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);

            assertEquals(BaseEventConsumer.ConsumeResult.DEAD_LETTER, result);
            verify(mockPublisher, never()).publishToRetry(any(), anyString());
            verify(mockPublisher, times(1)).publishToDeadLetter(eq(envelope), contains("MaxRetryExceeded"));
        }
    }

    @Nested
    @DisplayName("重试阶梯延迟")
    class RetryDelays {

        @Test
        @DisplayName("重试延迟阶梯为 10s / 30s / 60s")
        void retryDelaySteps() {
            assertArrayEquals(new int[]{10, 30, 60}, EventConstants.RETRY_DELAYS_SECONDS);
        }

        @Test
        @DisplayName("最大重试次数为 3")
        void maxRetryCount() {
            assertEquals(3, EventConstants.MAX_RETRY_COUNT);
        }
    }

    @Nested
    @DisplayName("幂等与重试交互")
    class IdempotentWithRetry {

        @Test
        @DisplayName("重试消息不会被幂等过滤（retryCount > 0 时仍执行）")
        void retryNotBlockedByIdempotent() {
            TestConsumer consumer = new TestConsumer(objectMapper, idempotentHandler, mockPublisher);
            EventEnvelope envelope = TestEventFactory.withRetryCount(
                    TestEventFactory.checkinSuccess("20260001"), 1);
            // 模拟之前已标记过（首次消费时）
            idempotentHandler.markProcessed(envelope.getEventId());

            // 重试消息到达时，tryMarkProcessed 返回 false → 跳过
            BaseEventConsumer.ConsumeResult result = consumer.consume(envelope);
            assertEquals(BaseEventConsumer.ConsumeResult.DUPLICATE, result);
            assertEquals(0, consumer.getConsumeCount(), "重试消息被幂等过滤，业务逻辑不应执行");
        }
    }
}
