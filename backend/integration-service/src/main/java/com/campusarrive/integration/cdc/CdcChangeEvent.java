package com.campusarrive.integration.cdc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * CDC 变更事件模型 — Debezium 捕获的 MySQL 行级变更。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节 CDC 数据同步、MW-2.3 Debezium CDC。
 *
 * 封装 Debezium 产生的一条变更记录，包含变更前镜像（before）、变更后镜像（after）、
 * 操作类型、来源表名、binlog 偏移量等元数据。</p>
 *
 * <pre>
 * {
 *   "sourceTable": "checkin_record",
 *   "operation": "INSERT",
 *   "before": null,
 *   "after": { "student_id": "20260001", "name": "张三", ... },
 *   "timestamp": "2026-08-28T09:30:15Z",
 *   "sourceOffset": { "binlogFile": "mysql-bin.000003", "binlogPosition": 12345, ... },
 *   "transactionId": "tx-abc123"
 * }
 * </pre>
 *
 * <p>提供 {@link #getAfterField(String)} / {@link #getBeforeField(String)} 便捷方法，
 * 从变更前/后镜像中提取指定字段值。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdcChangeEvent {

    /** 来源表名，如 "checkin_record" */
    @JsonProperty("sourceTable")
    private String sourceTable;

    /** 变更操作类型 */
    @JsonProperty("operation")
    private CdcOperation operation;

    /** 变更前镜像（UPDATE/DELETE 有值，INSERT 为 null） */
    @JsonProperty("before")
    private Map<String, Object> before;

    /** 变更后镜像（INSERT/UPDATE/SNAPSHOT 有值，DELETE 为 null） */
    @JsonProperty("after")
    private Map<String, Object> after;

    /** 事件产生时间戳 */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /** Binlog 偏移量（断点续传检查点） */
    @JsonProperty("sourceOffset")
    private CdcOffset sourceOffset;

    /** 事务 ID */
    @JsonProperty("transactionId")
    private String transactionId;

    // 默认构造器（Jackson 反序列化需要）
    public CdcChangeEvent() {
    }

    private CdcChangeEvent(Builder builder) {
        this.sourceTable = builder.sourceTable;
        this.operation = builder.operation;
        this.before = builder.before;
        this.after = builder.after;
        this.timestamp = builder.timestamp;
        this.sourceOffset = builder.sourceOffset;
        this.transactionId = builder.transactionId;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ——— Getters & Setters ———

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public CdcOperation getOperation() {
        return operation;
    }

    public void setOperation(CdcOperation operation) {
        this.operation = operation;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public void setBefore(Map<String, Object> before) {
        this.before = before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public void setAfter(Map<String, Object> after) {
        this.after = after;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public CdcOffset getSourceOffset() {
        return sourceOffset;
    }

    public void setSourceOffset(CdcOffset sourceOffset) {
        this.sourceOffset = sourceOffset;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    // ——— 便捷方法 ———

    /**
     * 从变更后镜像中提取指定字段值。
     *
     * @param fieldName 字段名
     * @return 字段值，after 为 null 或字段不存在时返回 null
     */
    public Object getAfterField(String fieldName) {
        return after != null ? after.get(fieldName) : null;
    }

    /**
     * 从变更前镜像中提取指定字段值。
     *
     * @param fieldName 字段名
     * @return 字段值，before 为 null 或字段不存在时返回 null
     */
    public Object getBeforeField(String fieldName) {
        return before != null ? before.get(fieldName) : null;
    }

    /**
     * 获取当前有效镜像（after 优先，DELETE 时取 before）。
     *
     * @return 当前有效数据镜像，可能为 null
     */
    @JsonIgnore
    public Map<String, Object> getEffectiveImage() {
        if (after != null) {
            return after;
        }
        return before;
    }

    /**
     * 生成事件唯一标识（基于 binlog 偏移量 + 表名 + 操作类型）。
     *
     * <p>同一 binlog 位置的事件具有相同 eventId，用于幂等判重和死信追踪。
     * 断点续传重启后，同一事件的 eventId 保持一致。</p>
     *
     * @return 事件唯一标识
     */
    @JsonIgnore
    public String getEventId() {
        if (sourceOffset != null && sourceOffset.getBinlogFile() != null) {
            return sourceOffset.getBinlogFile() + ":" + sourceOffset.getBinlogPosition()
                    + ":" + sourceTable + ":" + operation;
        }
        if (transactionId != null) {
            return transactionId + ":" + sourceTable + ":" + operation;
        }
        return sourceTable + ":" + operation + ":" + (timestamp != null ? timestamp : System.nanoTime());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CdcChangeEvent that = (CdcChangeEvent) o;
        return Objects.equals(getEventId(), that.getEventId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEventId());
    }

    @Override
    public String toString() {
        return "CdcChangeEvent{sourceTable='" + sourceTable + "', operation=" + operation
                + ", timestamp=" + timestamp
                + (transactionId != null ? ", transactionId='" + transactionId + '\'' : "")
                + '}';
    }

    // ——— Builder ———

    public static class Builder {
        private String sourceTable;
        private CdcOperation operation;
        private Map<String, Object> before;
        private Map<String, Object> after;
        private Instant timestamp;
        private CdcOffset sourceOffset;
        private String transactionId;

        public Builder sourceTable(String sourceTable) {
            this.sourceTable = sourceTable;
            return this;
        }

        public Builder operation(CdcOperation operation) {
            this.operation = operation;
            return this;
        }

        public Builder before(Map<String, Object> before) {
            this.before = before;
            return this;
        }

        public Builder after(Map<String, Object> after) {
            this.after = after;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sourceOffset(CdcOffset sourceOffset) {
            this.sourceOffset = sourceOffset;
            return this;
        }

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public CdcChangeEvent build() {
            return new CdcChangeEvent(this);
        }
    }
}
