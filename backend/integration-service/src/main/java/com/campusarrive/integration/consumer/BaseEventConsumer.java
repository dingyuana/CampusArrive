package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.idempotent.IdempotentHandler;
import com.campusarrive.integration.publisher.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 事件消费者基类 — 封装幂等判重、重试与死信处理逻辑。
 *
 * <p>规格来源：SIM-CA-2026-08 第 8.2 节消费确认、第 8.3 节死信队列、第 8.4 节重试策略。
 *
 * 消费流程：
 * <pre>
 *   1. 反序列化消息为 EventEnvelope
 *   2. 幂等判重：检查 eventId 是否已处理
 *      - 已处理 → 直接返回（跳过业务逻辑）
 *      - 未处理 → 继续
 *   3. 调用子类 doConsume() 执行业务逻辑
 *      - 成功 → 标记已处理
 *      - NonRetryableException → 发送到死信队列
 *      - RetryableException → 递增 retryCount，发送到重试队列
 *        - retryCount > MAX_RETRY_COUNT → 发送到死信队列
 * </pre>
 */
public abstract class BaseEventConsumer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ObjectMapper objectMapper;
    protected final IdempotentHandler idempotentHandler;
    protected final EventPublisher eventPublisher;

    protected BaseEventConsumer(ObjectMapper objectMapper,
                                 IdempotentHandler idempotentHandler,
                                 EventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.idempotentHandler = idempotentHandler;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 消费事件的核心逻辑。
     *
     * <p>子类实现此方法处理具体业务。如果处理失败：
     * - 抛出 {@link RetryableException} → 进入重试队列
     * - 抛出 {@link NonRetryableException} → 进入死信队列</p>
     *
     * @param envelope 事件信封
     * @throws RetryableException   可重试异常
     * @throws NonRetryableException 不可重试异常
     */
    protected abstract void doConsume(EventEnvelope envelope) throws RetryableException, NonRetryableException;

    /**
     * 获取当前消费者监听的队列名称。
     */
    protected abstract String getQueueName();

    /**
     * 处理消费到的消息。
     *
     * <p>由 {@code @RabbitListener} 方法调用，或在测试中直接调用。</p>
     *
     * @param envelope 事件信封
     * @return 消费结果
     */
    public ConsumeResult consume(EventEnvelope envelope) {
        String eventId = envelope.getEventId();
        log.info("[{}] 收到事件: eventId={}, type={}, retryCount={}",
                getQueueName(), eventId, envelope.getEventType(), envelope.getRetryCount());

        // 1. 幂等判重
        if (!idempotentHandler.tryMarkProcessed(eventId)) {
            log.info("[{}] 重复事件被幂等跳过: eventId={}", getQueueName(), eventId);
            return ConsumeResult.DUPLICATE;
        }

        // 2. 执行业务逻辑
        try {
            doConsume(envelope);
            log.info("[{}] 事件处理成功: eventId={}", getQueueName(), eventId);
            return ConsumeResult.SUCCESS;

        } catch (NonRetryableException e) {
            log.error("[{}] 不可重试异常, 转死信: eventId={}", getQueueName(), eventId, e);
            eventPublisher.publishToDeadLetter(envelope, "NonRetryableException: " + e.getMessage());
            return ConsumeResult.DEAD_LETTER;

        } catch (RetryableException e) {
            return handleRetryable(envelope, e);
        }
    }

    /**
     * 处理可重试异常。
     */
    private ConsumeResult handleRetryable(EventEnvelope envelope, RetryableException e) {
        int currentRetry = envelope.getRetryCount();
        log.warn("[{}] 可重试异常 (retryCount={}): eventId={}, reason={}",
                getQueueName(), currentRetry, envelope.getEventId(), e.getMessage());

        if (currentRetry >= EventConstants.MAX_RETRY_COUNT) {
            log.error("[{}] 重试次数耗尽, 转死信: eventId={}, retryCount={}",
                    getQueueName(), envelope.getEventId(), currentRetry);
            eventPublisher.publishToDeadLetter(envelope,
                    "MaxRetryExceeded: retryCount=" + currentRetry + ", lastError=" + e.getMessage());
            return ConsumeResult.DEAD_LETTER;
        }

        envelope.incrementRetryCount();
        boolean sent = eventPublisher.publishToRetry(envelope, getQueueName());
        if (sent) {
            return ConsumeResult.RETRY;
        } else {
            eventPublisher.publishToDeadLetter(envelope,
                    "RetryPublishFailed: retryCount=" + envelope.getRetryCount());
            return ConsumeResult.DEAD_LETTER;
        }
    }

    /** 消费结果枚举 */
    public enum ConsumeResult {
        /** 处理成功 */
        SUCCESS,
        /** 重复事件，已跳过 */
        DUPLICATE,
        /** 已发送到重试队列 */
        RETRY,
        /** 已发送到死信队列 */
        DEAD_LETTER
    }
}
