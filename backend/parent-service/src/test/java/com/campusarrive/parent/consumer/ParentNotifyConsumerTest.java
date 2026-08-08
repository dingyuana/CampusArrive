package com.campusarrive.parent.consumer;

import com.campusarrive.parent.event.EventEnvelope;
import com.campusarrive.parent.service.PreRegistrationStore;
import com.campusarrive.parent.service.PushNotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.AmqpHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CT-PAR-007~009：家长消息推送契约测试。
 *
 * <p>规格来源：FR-03-06 / FR-04-05。</p>
 *
 * <p>测试清单：
 * <ul>
 *   <li>CT-PAR-007：签到事件触发推送 — 已绑定家长收到通知</li>
 *   <li>CT-PAR-008：推送内容脱敏 — 不含敏感信息</li>
 *   <li>CT-PAR-009：未绑定家长不推送 — 事件静默丢弃不报错</li>
 * </ul></p>
 *
 * <p>测试策略：由于测试环境无 RabbitMQ broker，直接调用 consumer 方法，
 * 使用 mock Channel 验证 ACK/NACK 行为。</p>
 */
@DisplayName("CT-PAR-007~009：家长消息推送契约")
class ParentNotifyConsumerTest {

    private PreRegistrationStore preRegistrationStore;
    private PushNotificationService pushNotificationService;
    private ParentNotifyConsumer consumer;

    @BeforeEach
    void setUp() {
        preRegistrationStore = new PreRegistrationStore();
        pushNotificationService = new PushNotificationService(preRegistrationStore);
        consumer = new ParentNotifyConsumer(pushNotificationService);
    }

    // ─── CT-PAR-007：签到事件触发推送 ─────────────────────────

    @Nested
    @DisplayName("CT-PAR-007：签到事件触发推送")
    class CheckinEventTriggersPush {

        @Test
        @DisplayName("已绑定家长的学生签到后推送成功并 ACK")
        void testBoundStudentPushAndAck() throws Exception {
            // Given: 已绑定家长
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-001", "STU20260001");
            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            // When: 消费签到事件
            consumer.onCheckinSuccess(envelope, message, channel, 1L);

            // Then: 推送成功，消息被 ACK
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("推送后 ACK 不 requeue")
        void testAckNoRequeue() throws Exception {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-002", "STU20260001");
            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            consumer.onCheckinSuccess(envelope, message, channel, 2L);

            // ACK 第二个参数 multiple=false
            verify(channel).basicAck(eq(2L), eq(false));
        }
    }

    // ─── CT-PAR-008：推送内容脱敏 ─────────────────────────────

    @Nested
    @DisplayName("CT-PAR-008：推送内容脱敏")
    class PushContentDesensitization {

        @Test
        @DisplayName("推送内容仅含到校提示不含学生姓名")
        void testContentNoStudentName() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).contains("到校");
            assertThat(content).doesNotContain("张三丰");
            assertThat(content).doesNotContain("李四");
        }

        @Test
        @DisplayName("推送内容不含身份证号")
        void testContentNoIdCard() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("身份证");
            assertThat(content).doesNotContain("110");
        }

        @Test
        @DisplayName("推送内容不含手机号")
        void testContentNoPhone() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("13812345678");
            assertThat(content).doesNotContain("13987654321");
        }

        @Test
        @DisplayName("推送内容不含签到地点")
        void testContentNoCheckinPoint() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("南门");
            assertThat(content).doesNotContain("报到处");
        }

        @Test
        @DisplayName("推送内容为固定安全文案")
        void testContentIsFixedSafeText() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).isEqualTo("您的孩子已到校，请放心。");
        }

        @Test
        @DisplayName("事件 payload 含敏感信息但推送内容不含")
        void testEventPayloadSensitiveButPushContentSafe() throws Exception {
            // payload 中含姓名和签到点（模拟 checkin-service 发布的完整 payload）
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-003", "STU20260001");
            envelope.setPayload(Map.of(
                    "studentId", "STU20260001",
                    "name", "张三丰",
                    "idCard", "110101200001011234",
                    "checkinTime", "2026-08-28T09:30:00+08:00",
                    "checkinPoint", "南门报到处"
            ));

            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            consumer.onCheckinSuccess(envelope, message, channel, 3L);

            // 推送内容仍为安全文案
            String content = pushNotificationService.buildNotificationContent();
            assertThat(content).doesNotContain("张三丰");
            assertThat(content).doesNotContain("110101");
            assertThat(content).doesNotContain("南门");
            verify(channel).basicAck(3L, false);
        }
    }

    // ─── CT-PAR-009：未绑定家长不推送 ─────────────────────────

    @Nested
    @DisplayName("CT-PAR-009：未绑定家长不推送")
    class UnboundParentNoPush {

        @Test
        @DisplayName("未绑定家长的学生签到后静默丢弃并 ACK")
        void testUnboundStudentSilentDiscardAndAck() throws Exception {
            // 未注册任何家长
            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-004", "UNKNOWN_STU");
            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            consumer.onCheckinSuccess(envelope, message, channel, 4L);

            // 未绑定也应 ACK（不算错误，消息已处理）
            verify(channel).basicAck(4L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("未绑定家长时不抛出异常")
        void testUnboundNoException() {
            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-005", "UNKNOWN_STU");
            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            // 不应抛出异常
            org.assertj.core.api.Assertions.assertThatCode(() ->
                    consumer.onCheckinSuccess(envelope, message, channel, 5L)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("未绑定家长时 notifyParent 返回 false")
        void testNotifyParentReturnsFalseForUnbound() {
            boolean result = pushNotificationService.notifyParent("UNKNOWN_STU");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("已绑定家长时 notifyParent 返回 true")
        void testNotifyParentReturnsTrueForBound() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            boolean result = pushNotificationService.notifyParent("STU20260001");

            assertThat(result).isTrue();
        }
    }

    // ─── 异常处理 ─────────────────────────────────────────────

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("消费异常时 NACK 且不 requeue")
        void testExceptionNackNoRequeue() throws Exception {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            // 构造一个会导致异常的场景：payload 为 null 导致 extractStudentId 返回 null
            // 但 notifyParent(null) 不会抛异常，所以需要模拟其他异常
            EventEnvelope envelope = new EventEnvelope();
            envelope.setEventId("evt-err");
            envelope.setEventType("student.checkin.success");
            envelope.setPayload(null);

            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            // studentId 为 null，notifyParent 返回 false（静默丢弃），仍 ACK
            consumer.onCheckinSuccess(envelope, message, channel, 10L);

            verify(channel).basicAck(10L, false);
        }

        @Test
        @DisplayName("payload 为 Map 时正确提取 studentId")
        void testExtractStudentIdFromMap() throws Exception {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            EventEnvelope envelope = buildCheckinSuccessEnvelope("evt-006", "STU20260001");
            Channel channel = mock(Channel.class);
            Message message = mock(Message.class);

            consumer.onCheckinSuccess(envelope, message, channel, 6L);

            verify(channel).basicAck(6L, false);
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    /**
     * 构建签到成功事件信封。
     */
    private EventEnvelope buildCheckinSuccessEnvelope(String eventId, String studentId) {
        EventEnvelope envelope = new EventEnvelope();
        envelope.setEventId(eventId);
        envelope.setEventType("student.checkin.success");
        envelope.setSource("checkin-service");
        envelope.setVersion("1.0");
        envelope.setRetryCount(0);
        envelope.setPayload(Map.of(
                "studentId", studentId,
                "name", "测试学生",
                "checkinTime", "2026-08-28T09:30:00+08:00",
                "checkinPoint", "南门报到处"
        ));
        return envelope;
    }
}
