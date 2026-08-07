package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.idempotent.IdempotentHandler;
import com.campusarrive.integration.publisher.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 事件消费者集合 — 7 个业务队列的消费者实现。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.1 节队列/交换机拓扑、第 5.3 节核心事件链。
 *
 * 队列与消费者对应关系：
 * - q.dorm.checkin    → 宿舍确认（消费 student.checkin.success）
 * - q.card.issue      → 一卡通制卡（消费 student.checkin.success + student.verified.success）
 * - q.parent.notify   → 家长通知（消费 student.checkin.success）
 * - q.edu.activate    → 教务学籍激活（消费 student.verified.success）
 * - q.edu.register    → 教务教籍注册（消费 student.checkin.completed）
 * - q.dorm.allocate   → 宿舍正式分配（消费 student.payment.completed）
 * - q.flow.complete   → 流程标记完成（消费 student.payment.completed）</p>
 */
@Component
public class EventConsumers {

    private static final Logger log = LoggerFactory.getLogger(EventConsumers.class);

    private final ObjectMapper objectMapper;
    private final IdempotentHandler idempotentHandler;
    private final EventPublisher eventPublisher;
    private final EventChainTracker chainTracker;

    public EventConsumers(ObjectMapper objectMapper,
                          IdempotentHandler idempotentHandler,
                          EventPublisher eventPublisher,
                          EventChainTracker chainTracker) {
        this.objectMapper = objectMapper;
        this.idempotentHandler = idempotentHandler;
        this.eventPublisher = eventPublisher;
        this.chainTracker = chainTracker;
    }

    // ================================================================
    // 事件链一：报到成功 → 宿舍确认 + 一卡通预制卡 + 家长通知
    // ================================================================

    /** q.dorm.checkin — 宿舍床位确认 */
    @RabbitListener(queues = EventConstants.QUEUE_DORM_CHECKIN, ackMode = "MANUAL")
    public void onDormCheckin(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_DORM_CHECKIN, this::handleDormCheckin);
    }

    private void handleDormCheckin(EventEnvelope envelope) {
        log.info("[q.dorm.checkin] 宿舍床位确认: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        chainTracker.record(extractStudentId(envelope), envelope.getEventType());
        // 实际业务逻辑：调用宿舍系统 API 确认床位预留
    }

    /** q.card.issue — 一卡通制卡数据准备 */
    @RabbitListener(queues = EventConstants.QUEUE_CARD_ISSUE, ackMode = "MANUAL")
    public void onCardIssue(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_CARD_ISSUE, this::handleCardIssue);
    }

    private void handleCardIssue(EventEnvelope envelope) {
        log.info("[q.card.issue] 一卡通制卡数据准备: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        // 实际业务逻辑：调用一卡通系统制卡接口
    }

    /** q.parent.notify — 家长报到完成通知 */
    @RabbitListener(queues = EventConstants.QUEUE_PARENT_NOTIFY, ackMode = "MANUAL")
    public void onParentNotify(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_PARENT_NOTIFY, this::handleParentNotify);
    }

    private void handleParentNotify(EventEnvelope envelope) {
        log.info("[q.parent.notify] 推送家长报到完成通知: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        // 实际业务逻辑：调用微信消息推送接口（PARENT-4.3 实现）
    }

    // ================================================================
    // 事件链二：缴费完成 → 宿舍正式分配 + 流程标记完成
    // ================================================================

    /** q.dorm.allocate — 宿舍正式分配 */
    @RabbitListener(queues = EventConstants.QUEUE_DORM_ALLOCATE, ackMode = "MANUAL")
    public void onDormAllocate(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_DORM_ALLOCATE, this::handleDormAllocate);
    }

    private void handleDormAllocate(EventEnvelope envelope) {
        log.info("[q.dorm.allocate] 宿舍正式分配: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        chainTracker.record(extractStudentId(envelope), envelope.getEventType());
        // 实际业务逻辑：调用宿舍系统将预分配床位转为正式分配
    }

    /** q.flow.complete — 流程标记完成 */
    @RabbitListener(queues = EventConstants.QUEUE_FLOW_COMPLETE, ackMode = "MANUAL")
    public void onFlowComplete(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_FLOW_COMPLETE, this::handleFlowComplete);
    }

    private void handleFlowComplete(EventEnvelope envelope) {
        log.info("[q.flow.complete] 标记报到流程完成: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        chainTracker.record(extractStudentId(envelope), envelope.getEventType());
        // 实际业务逻辑：更新报到记录状态为"已完成"
    }

    // ================================================================
    // 事件链三：核验通过 → 学籍激活 + 一卡通账号开通
    // ================================================================

    /** q.edu.activate — 教务学籍激活 */
    @RabbitListener(queues = EventConstants.QUEUE_EDU_ACTIVATE, ackMode = "MANUAL")
    public void onEduActivate(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_EDU_ACTIVATE, this::handleEduActivate);
    }

    private void handleEduActivate(EventEnvelope envelope) {
        log.info("[q.edu.activate] 学籍激活: eventId={}, studentId={}",
                envelope.getEventId(), extractStudentId(envelope));
        chainTracker.record(extractStudentId(envelope), envelope.getEventType());
        // 实际业务逻辑：调用教务系统学籍激活接口
    }

    // ================================================================
    // 事件链四：报到完成 → 教籍注册 + 辅导员通知
    // ================================================================

    /** q.edu.register — 教务教籍注册 */
    @RabbitListener(queues = EventConstants.QUEUE_EDU_REGISTER, ackMode = "MANUAL")
    public void onEduRegister(@Payload EventEnvelope envelope) {
        consume(envelope, EventConstants.QUEUE_EDU_REGISTER, this::handleEduRegister);
    }

    private void handleEduRegister(EventEnvelope envelope) {
        String studentId = extractStudentId(envelope);
        log.info("[q.edu.register] 教籍注册: eventId={}, studentId={}",
                envelope.getEventId(), studentId);

        // 验证前置条件：签到、缴费、核验均已完成
        if (!chainTracker.isCompletionPrerequisitesMet(studentId)) {
            throw new NonRetryableException(
                    "报到完成前置条件不满足: studentId=" + studentId
                            + ", completedSteps=" + chainTracker.getChain(studentId));
        }

        chainTracker.record(studentId, envelope.getEventType());
        // 实际业务逻辑：调用教务系统教籍注册接口
    }

    // ================================================================
    // 消费框架（幂等 + 重试 + 死信）
    // ================================================================

    /**
     * 统一消费入口 — 封装幂等判重、重试与死信处理。
     */
    private void consume(EventEnvelope envelope, String queueName,
                         java.util.function.Consumer<EventEnvelope> handler) {
        String eventId = envelope.getEventId();
        log.info("[{}] 收到事件: eventId={}, type={}, retryCount={}",
                queueName, eventId, envelope.getEventType(), envelope.getRetryCount());

        // 1. 幂等判重
        if (!idempotentHandler.tryMarkProcessed(eventId)) {
            log.info("[{}] 重复事件被幂等跳过: eventId={}", queueName, eventId);
            return;
        }

        // 2. 执行业务逻辑
        try {
            handler.accept(envelope);
            log.info("[{}] 事件处理成功: eventId={}", queueName, eventId);

        } catch (NonRetryableException e) {
            log.error("[{}] 不可重试异常, 转死信: eventId={}", queueName, eventId, e);
            eventPublisher.publishToDeadLetter(envelope, "NonRetryableException: " + e.getMessage());

        } catch (RetryableException e) {
            handleRetryable(envelope, queueName, e);

        } catch (Exception e) {
            // 未预期的异常视为可重试
            handleRetryable(envelope, queueName, new RetryableException("Unexpected: " + e.getMessage(), e));
        }
    }

    private void handleRetryable(EventEnvelope envelope, String queueName, RetryableException e) {
        int currentRetry = envelope.getRetryCount();
        log.warn("[{}] 可重试异常 (retryCount={}): eventId={}, reason={}",
                queueName, currentRetry, envelope.getEventId(), e.getMessage());

        if (currentRetry >= EventConstants.MAX_RETRY_COUNT) {
            log.error("[{}] 重试次数耗尽, 转死信: eventId={}, retryCount={}",
                    queueName, envelope.getEventId(), currentRetry);
            eventPublisher.publishToDeadLetter(envelope,
                    "MaxRetryExceeded: retryCount=" + currentRetry + ", lastError=" + e.getMessage());
            return;
        }

        envelope.incrementRetryCount();
        boolean sent = eventPublisher.publishToRetry(envelope, queueName);
        if (!sent) {
            eventPublisher.publishToDeadLetter(envelope,
                    "RetryPublishFailed: retryCount=" + envelope.getRetryCount());
        }
    }

    /** 从事件负载中提取 studentId */
    private String extractStudentId(EventEnvelope envelope) {
        Object payload = envelope.getPayload();
        if (payload instanceof java.util.Map<?, ?> map) {
            Object id = map.get("studentId");
            return id != null ? id.toString() : "unknown";
        }
        // 处理类型化 payload 对象：通过反射获取 studentId 字段
        if (payload != null) {
            try {
                java.lang.reflect.Field field = payload.getClass().getDeclaredField("studentId");
                field.setAccessible(true);
                Object id = field.get(payload);
                return id != null ? id.toString() : "unknown";
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // 尝试父类
                try {
                    java.lang.reflect.Field field = payload.getClass().getSuperclass().getDeclaredField("studentId");
                    field.setAccessible(true);
                    Object id = field.get(payload);
                    return id != null ? id.toString() : "unknown";
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return "unknown";
    }
}
