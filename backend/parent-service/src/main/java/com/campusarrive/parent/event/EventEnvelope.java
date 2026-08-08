package com.campusarrive.parent.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 事件信封 DTO（与 integration-service EventEnvelope 结构一致）。
 *
 * <p>规格来源：FR-04-05 / SIM-CA-2026-08 第 3 节 —
 * 统一事件信封格式，parent-service 仅消费不发布，此处为反序列化用 DTO。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventEnvelope {

    /** 事件唯一 ID（幂等判重用）。 */
    @JsonProperty("eventId")
    private String eventId;

    /** 事件类型（= RabbitMQ routing key）。 */
    @JsonProperty("eventType")
    private String eventType;

    /** 事件产生时间（ISO 8601）。 */
    @JsonProperty("eventTime")
    private OffsetDateTime eventTime;

    /** 事件来源服务。 */
    @JsonProperty("source")
    private String source;

    /** 链路追踪 ID。 */
    @JsonProperty("traceId")
    private String traceId;

    /** 事件模式版本。 */
    @JsonProperty("version")
    private String version;

    /** 业务负载（签到成功事件含 studentId / name / checkinTime / checkinPoint）。 */
    @JsonProperty("payload")
    private Object payload;

    /** 重试计数（非业务字段）。 */
    @JsonProperty("retryCount")
    private int retryCount;
}
