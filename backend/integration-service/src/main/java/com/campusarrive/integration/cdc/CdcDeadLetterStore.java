package com.campusarrive.integration.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CDC 死信存储 — 处理失败事件的最终归宿。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.3 节异常处理、MW-2.3 Debezium CDC。
 *
 * 当 CDC 事件经过最大重试次数仍无法成功写入下游系统，或主数据映射缺失时，
 * 事件被转入死信存储，供人工介入或定时补偿任务处理。</p>
 *
 * <p>当前实现为内存版（ConcurrentHashMap），生产环境应替换为 MySQL / Redis 持久化实现，
 * 保证死信数据在服务重启后不丢失。</p>
 */
public class CdcDeadLetterStore {

    private static final Logger log = LoggerFactory.getLogger(CdcDeadLetterStore.class);

    /** 死信条目：eventId → 条目 */
    private final ConcurrentMap<String, DeadLetterEntry> store = new ConcurrentHashMap<>();

    /**
     * 保存死信事件。
     *
     * @param event  原始 CDC 事件
     * @param reason 死信原因（如 "RetryExhausted"、"MappingMissing"）
     */
    public void save(CdcChangeEvent event, String reason) {
        Objects.requireNonNull(event, "CdcChangeEvent 不能为 null");
        String eventId = event.getEventId();
        DeadLetterEntry entry = new DeadLetterEntry(eventId, event, reason, System.currentTimeMillis());
        store.put(eventId, entry);
        log.warn("[CdcDeadLetterStore] 事件转死信: eventId={}, reason={}, table={}",
                eventId, reason, event.getSourceTable());
    }

    /**
     * 获取所有死信条目。
     *
     * @return 死信条目列表（不可变副本）
     */
    public List<DeadLetterEntry> getAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 移除指定死信条目（补偿处理成功后调用）。
     *
     * @param eventId 事件唯一标识
     */
    public void remove(String eventId) {
        store.remove(eventId);
        log.info("[CdcDeadLetterStore] 移除死信条目: eventId={}", eventId);
    }

    /**
     * 获取死信条目数量。
     *
     * @return 死信数量
     */
    public int size() {
        return store.size();
    }

    /**
     * 清空所有死信条目（测试用）。
     */
    public void clear() {
        store.clear();
    }

    /**
     * 死信条目记录。
     *
     * @param eventId   事件唯一标识
     * @param event     原始 CDC 事件
     * @param reason    死信原因
     * @param timestamp 入死信时间戳
     */
    public record DeadLetterEntry(String eventId, CdcChangeEvent event, String reason, long timestamp) {
    }
}
