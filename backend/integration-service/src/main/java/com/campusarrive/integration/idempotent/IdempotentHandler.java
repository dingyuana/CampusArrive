package com.campusarrive.integration.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 幂等消费处理器。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.4 节、UT-MW-004 幂等消费。
 *
 * 消费者消费前先查是否已处理该 eventId，已处理则直接 ACK 跳过，
 * 避免重复消费副作用。
 *
 * 当前实现为内存版（ConcurrentHashMap），生产环境应替换为 Redis 实现
 * （SETNX + TTL），以保证多实例下幂等判重的正确性。</p>
 */
@Component
public class IdempotentHandler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentHandler.class);

    /** 已处理事件 ID 集合：eventId → 处理时间戳 */
    private final ConcurrentMap<String, Long> processedEvents = new ConcurrentHashMap<>();

    /** 默认幂等窗口（24 小时），超过后自动清理 */
    private static final long IDEMPOTENT_WINDOW_MS = 86_400_000L;

    /**
     * 检查事件是否已被处理。
     *
     * @param eventId 事件唯一 ID
     * @return true=已处理过（应跳过），false=未处理（应执行业务逻辑）
     */
    public boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    /**
     * 标记事件为已处理。
     *
     * @param eventId 事件唯一 ID
     */
    public void markProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        log.debug("[IdempotentHandler] 标记事件已处理: eventId={}", eventId);
    }

    /**
     * 尝试标记事件为已处理。
     *
     * <p>原子操作：如果事件已被标记则返回 false，否则标记并返回 true。
     * 使用 ConcurrentHashMap.compute 实现 CAS 语义。</p>
     *
     * @param eventId 事件唯一 ID
     * @return true=本次标记成功（首次处理），false=已被其他线程标记（重复事件）
     */
    public boolean tryMarkProcessed(String eventId) {
        long now = System.currentTimeMillis();
        Long existing = processedEvents.putIfAbsent(eventId, now);
        if (existing != null) {
            log.info("[IdempotentHandler] 重复事件被幂等过滤: eventId={}", eventId);
            return false;
        }
        return true;
    }

    /**
     * 清理过期的幂等记录（超过幂等窗口）。
     *
     * <p>定时任务调用，避免内存无限增长。</p>
     */
    public void cleanupExpired() {
        long threshold = System.currentTimeMillis() - IDEMPOTENT_WINDOW_MS;
        int removed = 0;
        var iterator = processedEvents.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() < threshold) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[IdempotentHandler] 清理过期幂等记录: count={}", removed);
        }
    }

    /**
     * 获取当前已处理事件数量（测试用）。
     */
    public int size() {
        return processedEvents.size();
    }

    /**
     * 重置所有幂等记录（测试用）。
     */
    public void reset() {
        processedEvents.clear();
    }
}
