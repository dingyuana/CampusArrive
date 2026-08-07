package com.campusarrive.checkin.event;

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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 报到事件发布器 — checkin-service 侧事件发布能力。
 *
 * <p>规格来源：FR-04-05 报到状态变更事件、SIM-CA-2026-08 第 5 节。
 *
 * checkin-service 作为事件生产者，向 {@code student.events} topic 交换机发布
 * 签到成功、缴费完成、核验通过、报到完成四类核心事件。
 * 事件信封格式与 integration-service 的 EventEnvelope 保持一致。</p>
 */
@Component
public class CheckinEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CheckinEventPublisher.class);

    /** RabbitMQ 主交换机名称（与 integration-service EventConstants 保持一致） */
    private static final String EXCHANGE_STUDENT_EVENTS = "student.events";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public CheckinEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布报到成功事件。
     *
     * @param studentId    学号
     * @param name         姓名
     * @param checkinPoint 签到地点
     */
    public void publishCheckinSuccess(String studentId, String name, String checkinPoint) {
        publish("student.checkin.success", Map.of(
                "studentId", studentId,
                "name", name,
                "checkinTime", OffsetDateTime.now().toString(),
                "checkinPoint", checkinPoint
        ));
    }

    /**
     * 发布缴费完成事件。
     *
     * @param studentId  学号
     * @param payOrderNo 缴费订单号
     * @param payAmount  缴费金额
     * @param payMethod  缴费方式
     */
    public void publishPaymentCompleted(String studentId, String payOrderNo,
                                         double payAmount, String payMethod) {
        publish("student.payment.completed", Map.of(
                "studentId", studentId,
                "payOrderNo", payOrderNo,
                "payAmount", payAmount,
                "payMethod", payMethod
        ));
    }

    /**
     * 发布身份核验通过事件。
     *
     * @param studentId 学号
     * @param name      姓名
     */
    public void publishVerifiedSuccess(String studentId, String name) {
        publish("student.verified.success", Map.of(
                "studentId", studentId,
                "name", name
        ));
    }

    /**
     * 发布报到全流程完成事件。
     *
     * @param studentId 学号
     */
    public void publishCheckinCompleted(String studentId) {
        publish("student.checkin.completed", Map.of(
                "studentId", studentId,
                "completedTime", OffsetDateTime.now().toString()
        ));
    }

    /**
     * 发布事件到 RabbitMQ。
     *
     * @param routingKey 路由键（事件类型）
     * @param payload    业务负载
     */
    private void publish(String routingKey, Map<String, Object> payload) {
        String eventId = "evt-" + UUID.randomUUID();
        Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "eventType", routingKey,
                "eventTime", OffsetDateTime.now().toString(),
                "source", "checkin-service",
                "version", "1.0",
                "payload", payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);

            MessageProperties props = MessagePropertiesBuilder.newInstance()
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(eventId)
                    .setCorrelationId(eventId)
                    .build();

            Message message = MessageBuilder.withBody(json.getBytes())
                    .andProperties(props)
                    .build();

            log.info("[CheckinEventPublisher] 发布事件: eventId={}, type={}", eventId, routingKey);

            rabbitTemplate.send(EXCHANGE_STUDENT_EVENTS, routingKey, message);

        } catch (JsonProcessingException e) {
            log.error("[CheckinEventPublisher] 事件序列化失败: type={}", routingKey, e);
            throw new RuntimeException("事件序列化失败", e);
        }
    }
}
