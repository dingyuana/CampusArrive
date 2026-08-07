package com.campusarrive.integration.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 事件统一信封格式。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.4 节消息格式规范。
 * 所有事件消息统一信封格式，业务负载置于 {@code payload} 字段。
 * {@code eventId} 保证消费者可幂等处理。</p>
 *
 * <pre>
 * {
 *   "eventId": "evt-2026-000001",
 *   "eventType": "student.checkin.success",
 *   "eventTime": "2026-08-28T09:30:15+08:00",
 *   "source": "checkin-service",
 *   "traceId": "trace-a1b2c3",
 *   "version": "1.0",
 *   "payload": { ... }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventEnvelope {

    /** 事件唯一 ID，UUID，用于幂等判重 */
    @JsonProperty("eventId")
    private String eventId;

    /** 事件类型，即路由键 */
    @JsonProperty("eventType")
    private String eventType;

    /** 事件产生时间，ISO 8601 */
    @JsonProperty("eventTime")
    private OffsetDateTime eventTime;

    /** 事件来源服务 */
    @JsonProperty("source")
    private String source;

    /** 链路追踪 ID */
    @JsonProperty("traceId")
    private String traceId;

    /** 事件模式版本 */
    @JsonProperty("version")
    private String version;

    /** 业务负载，随事件类型变化 */
    @JsonProperty("payload")
    private Object payload;

    // ——— 重试元数据（由重试机制填充，非业务字段） ———
    @JsonProperty("retryCount")
    private int retryCount;

    // 默认构造器（Jackson 反序列化需要）
    public EventEnvelope() {
    }

    private EventEnvelope(Builder builder) {
        this.eventId = builder.eventId;
        this.eventType = builder.eventType;
        this.eventTime = builder.eventTime;
        this.source = builder.source;
        this.traceId = builder.traceId;
        this.version = builder.version;
        this.payload = builder.payload;
        this.retryCount = builder.retryCount;
    }

    public static Builder builder(EventType type) {
        return new Builder(type);
    }

    // ——— Getters & Setters ———

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(OffsetDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEnvelope that = (EventEnvelope) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "EventEnvelope{eventId='" + eventId + "', eventType='" + eventType
                + "', source='" + source + "', retryCount=" + retryCount + '}';
    }

    // ——— Builder ———

    public static class Builder {
        private String eventId;
        private String eventType;
        private OffsetDateTime eventTime;
        private String source;
        private String traceId;
        private String version = "1.0";
        private Object payload;
        private int retryCount = 0;

        public Builder(EventType type) {
            this.eventType = type.routingKey();
            this.eventId = "evt-" + UUID.randomUUID();
            this.eventTime = OffsetDateTime.now();
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventTime(OffsetDateTime eventTime) {
            this.eventTime = eventTime;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public EventEnvelope build() {
            return new EventEnvelope(this);
        }
    }
}
