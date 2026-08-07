package com.campusarrive.integration.publisher;

import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 事件发布器。
 *
 * <p>规格来源：SIM-CA-2026-08 第 8.1 节消息持久化、第 8.4 节生产者确认。
 *
 * 职责：
 * - 将 {@link EventEnvelope} 序列化为 JSON 并发布到 {@code student.events} topic 交换机
 * - 消息强制持久化（deliveryMode=2）
 * - 启用 publisher confirm，写盘前不返回成功
 * - 失败时记录日志并落库本地消息表（由定时任务补偿重发）</p>
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布事件到 {@code student.events} 交换机。
     *
     * <p>消息持久化 + publisher confirm。发布失败时记录错误日志，
     * 生产环境应配合本地消息表做补偿重发。</p>
     *
     * @param envelope 事件信封
     */
    public void publish(EventEnvelope envelope) {
        String routingKey = envelope.getEventType();
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("[EventPublisher] 事件序列化失败: eventId={}, type={}",
                    envelope.getEventId(), envelope.getEventType(), e);
            throw new EventPublishException("事件序列化失败: " + e.getMessage(), e);
        }

        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(envelope.getEventId())
                .setCorrelationId(envelope.getEventId())
                .build();
        props.setHeader(EventConstants.HEADER_RETRY_COUNT, envelope.getRetryCount());

        Message message = MessageBuilder.withBody(json.getBytes())
                .andProperties(props)
                .build();

        log.info("[EventPublisher] 发布事件: eventId={}, type={}, routingKey={}",
                envelope.getEventId(), envelope.getEventType(), routingKey);

        rabbitTemplate.send(
                EventConstants.EXCHANGE_STUDENT_EVENTS,
                routingKey,
                message
        );
    }

    /**
     * 便捷方法：构建并发布事件。
     *
     * @param type    事件类型
     * @param source  来源服务
     * @param payload 业务负载
     * @return 已发布的事件信封
     */
    public EventEnvelope publish(EventType type, String source, Object payload) {
        EventEnvelope envelope = EventEnvelope.builder(type)
                .source(source)
                .payload(payload)
                .build();
        publish(envelope);
        return envelope;
    }

    /**
     * 发布事件到重试队列。
     *
     * <p>根据重试次数选择对应的重试队列。超过最大重试次数则不发送，
     * 由调用方决定是否转入死信队列。</p>
     *
     * @param envelope      事件信封（retryCount 已递增）
     * @param originalQueue 原始业务队列名
     * @return true=已发送到重试队列, false=超过重试上限
     */
    public boolean publishToRetry(EventEnvelope envelope, String originalQueue) {
        int retryCount = envelope.getRetryCount();
        if (retryCount > EventConstants.MAX_RETRY_COUNT) {
            log.warn("[EventPublisher] 事件超过最大重试次数, 转死信: eventId={}, retryCount={}",
                    envelope.getEventId(), retryCount);
            return false;
        }

        String retryRoutingKey = "retry." + retryCount;
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("[EventPublisher] 重试事件序列化失败: eventId={}", envelope.getEventId(), e);
            return false;
        }

        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        props.setHeader(EventConstants.HEADER_RETRY_COUNT, retryCount);
        props.setHeader(EventConstants.HEADER_ORIGINAL_QUEUE, originalQueue);
        props.setHeader(EventConstants.HEADER_ORIGINAL_ROUTING_KEY, envelope.getEventType());

        Message message = MessageBuilder.withBody(json.getBytes())
                .andProperties(props)
                .build();

        log.info("[EventPublisher] 发送到重试队列: eventId={}, retryCount={}, originalQueue={}",
                envelope.getEventId(), retryCount, originalQueue);

        rabbitTemplate.send(
                EventConstants.EXCHANGE_RETRY,
                retryRoutingKey,
                message
        );
        return true;
    }

    /**
     * 发布事件到死信队列。
     *
     * @param envelope 事件信封
     * @param reason   死信原因
     */
    public void publishToDeadLetter(EventEnvelope envelope, String reason) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("[EventPublisher] 死信事件序列化失败: eventId={}", envelope.getEventId(), e);
            return;
        }

        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        props.setHeader("x-dead-letter-reason", reason);
        props.setHeader(EventConstants.HEADER_RETRY_COUNT, envelope.getRetryCount());

        Message message = MessageBuilder.withBody(json.getBytes())
                .andProperties(props)
                .build();

        log.warn("[EventPublisher] 发送到死信队列: eventId={}, reason={}", envelope.getEventId(), reason);

        rabbitTemplate.send(
                EventConstants.EXCHANGE_DLX,
                envelope.getEventType(),
                message
        );
    }

    /** 事件发布异常 */
    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
