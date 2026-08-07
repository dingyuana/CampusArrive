package com.campusarrive.integration.cdc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Binlog 位置检查点 — CDC 断点续传的偏移量模型。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节断点续传、MW-2.3 Debezium CDC。
 *
 * 记录 MySQL binlog 的消费位置，服务重启后从此位置恢复同步，
 * 保证不丢数据、不重复处理。支持两种定位方式：
 * <ul>
 *   <li>binlog 文件名 + 位置（传统模式）</li>
 *   <li>GTID（全局事务 ID，主从切换场景更健壮）</li>
 * </ul></p>
 *
 * <p>实现 {@link Comparable}，按 binlog 文件名（字典序）+ 位置（数值序）排序，
 * 用于判断事件是否已被处理（事件偏移量 ≤ 当前偏移量 → 跳过）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdcOffset implements Comparable<CdcOffset> {

    /** Binlog 文件名，如 "mysql-bin.000003" */
    @JsonProperty("binlogFile")
    private String binlogFile;

    /** Binlog 文件内位置（字节偏移） */
    @JsonProperty("binlogPosition")
    private long binlogPosition;

    /** GTID 集合（可选），如 "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-23" */
    @JsonProperty("gtid")
    private String gtid;

    /** 偏移量对应的事件时间戳 */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /** 默认构造器（Jackson 反序列化需要） */
    public CdcOffset() {
    }

    /**
     * 全参数构造器。
     *
     * @param binlogFile     binlog 文件名
     * @param binlogPosition binlog 文件内位置
     * @param gtid           GTID（可为 null）
     * @param timestamp      事件时间戳
     */
    public CdcOffset(String binlogFile, long binlogPosition, String gtid, Instant timestamp) {
        this.binlogFile = binlogFile;
        this.binlogPosition = binlogPosition;
        this.gtid = gtid;
        this.timestamp = timestamp;
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public String getGtid() {
        return gtid;
    }

    public void setGtid(String gtid) {
        this.gtid = gtid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 比较两个偏移量的先后顺序。
     *
     * <p>先比较 binlog 文件名（字典序），文件名相同再比较位置（数值序）。
     * 返回负值表示本偏移量在参数之前（更旧），正值表示之后（更新）。</p>
     *
     * @param other 另一个偏移量
     * @return 比较结果
     */
    @Override
    public int compareTo(CdcOffset other) {
        Objects.requireNonNull(other, "CdcOffset 不能为 null");
        int fileCompare = compareStrings(this.binlogFile, other.binlogFile);
        if (fileCompare != 0) {
            return fileCompare;
        }
        return Long.compare(this.binlogPosition, other.binlogPosition);
    }

    /**
     * 判断本偏移量是否在指定偏移量之前（更旧）。
     *
     * @param other 另一个偏移量
     * @return true=本偏移量更旧
     */
    public boolean isBefore(CdcOffset other) {
        return this.compareTo(other) < 0;
    }

    /**
     * 判断本偏移量是否在指定偏移量之后（更新）。
     *
     * @param other 另一个偏移量
     * @return true=本偏移量更新
     */
    public boolean isAfter(CdcOffset other) {
        return this.compareTo(other) > 0;
    }

    private static int compareStrings(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CdcOffset cdcOffset = (CdcOffset) o;
        return binlogPosition == cdcOffset.binlogPosition
                && Objects.equals(binlogFile, cdcOffset.binlogFile)
                && Objects.equals(gtid, cdcOffset.gtid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binlogFile, binlogPosition, gtid);
    }

    @Override
    public String toString() {
        return "CdcOffset{binlogFile='" + binlogFile + "', binlogPosition=" + binlogPosition
                + (gtid != null ? ", gtid='" + gtid + '\'' : "")
                + (timestamp != null ? ", timestamp=" + timestamp : "")
                + '}';
    }
}
