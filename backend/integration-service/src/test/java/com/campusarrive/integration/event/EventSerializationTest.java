package com.campusarrive.integration.event;

import com.campusarrive.integration.testsupport.TestEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT-MW-003：事件序列化测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.4 节消息格式规范。
 * 验证事件对象 JSON 序列化/反序列化字段无损，eventId 唯一。</p>
 *
 * <p>TDD 类型：UT（单元测试）</p>
 */
@DisplayName("UT-MW-003: 事件序列化与反序列化")
class EventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("事件信封序列化")
    class EnvelopeSerialization {

        @Test
        @DisplayName("报到成功事件 JSON 序列化/反序列化字段无损")
        void checkinSuccessRoundTrip() throws Exception {
            // Arrange
            EventEnvelope original = TestEventFactory.checkinSuccess("20260001");

            // Act
            String json = objectMapper.writeValueAsString(original);
            EventEnvelope deserialized = objectMapper.readValue(json, EventEnvelope.class);

            // Assert
            assertEquals(original.getEventId(), deserialized.getEventId());
            assertEquals(original.getEventType(), deserialized.getEventType());
            assertEquals(original.getSource(), deserialized.getSource());
            assertEquals(original.getTraceId(), deserialized.getTraceId());
            assertEquals(original.getVersion(), deserialized.getVersion());
            assertEquals(original.getRetryCount(), deserialized.getRetryCount());
        }

        @Test
        @DisplayName("缴费完成事件 JSON 序列化/反序列化字段无损")
        void paymentCompletedRoundTrip() throws Exception {
            EventEnvelope original = TestEventFactory.paymentCompleted("20260001");
            String json = objectMapper.writeValueAsString(original);
            EventEnvelope deserialized = objectMapper.readValue(json, EventEnvelope.class);

            assertEquals(original.getEventId(), deserialized.getEventId());
            assertEquals(original.getEventType(), deserialized.getEventType());
        }

        @Test
        @DisplayName("核验通过事件 JSON 序列化/反序列化字段无损")
        void verifiedSuccessRoundTrip() throws Exception {
            EventEnvelope original = TestEventFactory.verifiedSuccess("20260001");
            String json = objectMapper.writeValueAsString(original);
            EventEnvelope deserialized = objectMapper.readValue(json, EventEnvelope.class);

            assertEquals(original.getEventId(), deserialized.getEventId());
            assertEquals(original.getEventType(), deserialized.getEventType());
        }

        @Test
        @DisplayName("报到完成事件 JSON 序列化/反序列化字段无损")
        void checkinCompletedRoundTrip() throws Exception {
            EventEnvelope original = TestEventFactory.checkinCompleted("20260001");
            String json = objectMapper.writeValueAsString(original);
            EventEnvelope deserialized = objectMapper.readValue(json, EventEnvelope.class);

            assertEquals(original.getEventId(), deserialized.getEventId());
            assertEquals(original.getEventType(), deserialized.getEventType());
        }
    }

    @Nested
    @DisplayName("eventId 唯一性")
    class EventIdUniqueness {

        @Test
        @DisplayName("Builder 自动生成的 eventId 唯一")
        void eventIdUniqueFromBuilder() {
            Set<String> ids = new HashSet<>();
            int count = 1000;

            for (int i = 0; i < count; i++) {
                EventEnvelope envelope = EventEnvelope.builder(EventType.CHECKIN_SUCCESS)
                        .source("test")
                        .build();
                ids.add(envelope.getEventId());
            }

            assertEquals(count, ids.size(), "1000 次生成的 eventId 应全部唯一");
        }

        @Test
        @DisplayName("eventId 格式为 evt-UUID")
        void eventIdFormat() {
            EventEnvelope envelope = EventEnvelope.builder(EventType.CHECKIN_SUCCESS)
                    .source("test")
                    .build();

            assertTrue(envelope.getEventId().startsWith("evt-"),
                    "eventId 应以 'evt-' 前缀开头");
            String uuidPart = envelope.getEventId().substring(4);
            assertDoesNotThrow(() -> UUID.fromString(uuidPart),
                    "eventId 的 UUID 部分应可解析");
        }
    }

    @Nested
    @DisplayName("EventType 路由键")
    class EventTypeRoutingKey {

        @Test
        @DisplayName("事件类型路由键符合三段式命名规范")
        void routingKeyFormat() {
            assertEquals("student.checkin.success", EventType.CHECKIN_SUCCESS.routingKey());
            assertEquals("student.payment.completed", EventType.PAYMENT_COMPLETED.routingKey());
            assertEquals("student.verified.success", EventType.VERIFIED_SUCCESS.routingKey());
            assertEquals("student.checkin.completed", EventType.CHECKIN_COMPLETED.routingKey());
        }

        @Test
        @DisplayName("从路由键解析事件类型")
        void fromRoutingKey() {
            assertEquals(EventType.CHECKIN_SUCCESS,
                    EventType.fromRoutingKey("student.checkin.success"));
            assertEquals(EventType.PAYMENT_COMPLETED,
                    EventType.fromRoutingKey("student.payment.completed"));
        }

        @Test
        @DisplayName("未知路由键抛出 IllegalArgumentException")
        void unknownRoutingKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> EventType.fromRoutingKey("unknown.event.type"));
        }
    }

    @Test
    @DisplayName("retryCount 递增")
    void retryCountIncrement() {
        EventEnvelope envelope = TestEventFactory.checkinSuccess("20260001");
        assertEquals(0, envelope.getRetryCount());

        envelope.incrementRetryCount();
        assertEquals(1, envelope.getRetryCount());

        envelope.incrementRetryCount();
        assertEquals(2, envelope.getRetryCount());
    }

    @Test
    @DisplayName("事件信封 equals/hashCode 基于 eventId")
    void equalsBasedOnEventId() {
        EventEnvelope e1 = TestEventFactory.checkinSuccess("20260001");
        EventEnvelope e2 = EventEnvelope.builder(EventType.CHECKIN_SUCCESS)
                .eventId(e1.getEventId())
                .source("different-source")
                .build();

        assertEquals(e1, e2, "相同 eventId 的事件应相等");
        assertEquals(e1.hashCode(), e2.hashCode());
    }
}
