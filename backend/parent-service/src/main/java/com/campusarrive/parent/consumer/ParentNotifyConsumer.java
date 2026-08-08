package com.campusarrive.parent.consumer;

import com.campusarrive.parent.config.ParentRabbitMqConfig;
import com.campusarrive.parent.event.EventEnvelope;
import com.campusarrive.parent.service.PushNotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 家长签到通知消费者。
 *
 * <p>规格来源：FR-03-06 / FR-04-05 —
 * 监听 q.parent.notify.push 队列，消费 student.checkin.success 事件，
 * 触发家长端消息推送。</p>
 *
 * <p>消费流程：
 * <ol>
 *   <li>接收事件信封，提取 studentId</li>
 *   <li>调用 PushNotificationService 推送通知</li>
 *   <li>推送成功或未绑定家长均 ACK（静默丢弃）</li>
 *   <li>异常情况 NACK + requeue（重试）</li>
 * </ol></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParentNotifyConsumer {

    private final PushNotificationService pushNotificationService;

    /**
     * 消费签到成功事件。
     *
     * @param envelope 事件信封
     * @param message  RabbitMQ 原始消息
     * @param channel  RabbitMQ 通道
     * @param deliveryTag 投递标签
     */
    @RabbitListener(queues = ParentRabbitMqConfig.QUEUE_PARENT_NOTIFY_PUSH, ackMode = "MANUAL")
    public void onCheckinSuccess(
            @Payload EventEnvelope envelope,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String eventId = envelope.getEventId();
        String studentId = extractStudentId(envelope);

        log.info("收到签到事件: eventId={}, studentId={}", eventId, studentId);

        try {
            // 推送通知（未绑定家长时静默丢弃，返回 false）
            boolean pushed = pushNotificationService.notifyParent(studentId);

            if (pushed) {
                log.info("签到通知推送成功: eventId={}, studentId={}", eventId, studentId);
            } else {
                log.info("签到通知未推送（未绑定家长）: eventId={}, studentId={}", eventId, studentId);
            }

            // 无论是否推送成功，都 ACK 消息（未绑定不算错误）
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("签到通知消费异常: eventId={}, studentId={}, error={}",
                    eventId, studentId, e.getMessage(), e);
            try {
                // 异常时 NACK，不重新入队（避免无限重试）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("NACK 失败: deliveryTag={}", deliveryTag, nackEx);
            }
        }
    }

    /**
     * 从事件信封中提取 studentId。
     *
     * <p>payload 反序列化后可能是 LinkedHashMap 或原始对象，
     * 此方法兼容两种情况。</p>
     *
     * @param envelope 事件信封
     * @return 学生 ID，提取失败返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractStudentId(EventEnvelope envelope) {
        Object payload = envelope.getPayload();
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            Object id = map.get("studentId");
            return id != null ? id.toString() : null;
        }
        // 尝试反射获取 studentId 字段
        try {
            var field = payload.getClass().getDeclaredField("studentId");
            field.setAccessible(true);
            Object id = field.get(payload);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            log.warn("无法从 payload 提取 studentId: {}", e.getMessage());
            return null;
        }
    }
}
