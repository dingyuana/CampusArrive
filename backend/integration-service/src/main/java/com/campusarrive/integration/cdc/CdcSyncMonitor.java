package com.campusarrive.integration.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDC 同步监控器 — 追踪同步延迟与处理统计。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.4 节监控指标、MW-2.3 Debezium CDC。
 *
 * 监控指标：
 * <ul>
 *   <li>totalEvents — 总事件数</li>
 *   <li>successCount — 成功数</li>
 *   <li>failureCount — 失败数</li>
 *   <li>deadLetterCount — 死信数</li>
 *   <li>p95LatencyMs — P95 延迟（目标 ≤ 5000ms）</li>
 *   <li>p99LatencyMs — P99 延迟</li>
 * </ul>
 *
 * <p>使用滑动窗口（默认 1000 个样本）计算延迟分位数，
 * 窗口外旧样本自动淘汰，反映近期同步性能。</p>
 */
public class CdcSyncMonitor {

    private static final Logger log = LoggerFactory.getLogger(CdcSyncMonitor.class);

    /** P95 延迟目标（毫秒），超过则判定不健康 */
    public static final long P95_TARGET_MS = 5_000L;

    /** 滑动窗口大小 */
    private static final int WINDOW_SIZE = 1_000;

    // ——— 计数器 ———
    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong deadLetterCount = new AtomicLong();
    private final AtomicLong skippedCount = new AtomicLong();

    // ——— 延迟滑动窗口 ———
    private final List<Long> latencySamples = Collections.synchronizedList(new ArrayList<>());

    /** 总事件数 +1 */
    public void incrementTotal() {
        totalEvents.incrementAndGet();
    }

    /** 成功数 +1 */
    public void incrementSuccess() {
        successCount.incrementAndGet();
    }

    /** 失败数 +1 */
    public void incrementFailure() {
        failureCount.incrementAndGet();
    }

    /** 死信数 +1 */
    public void incrementDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    /** 跳过数 +1（重复/已处理） */
    public void incrementSkipped() {
        skippedCount.incrementAndGet();
    }

    /**
     * 记录一次同步延迟样本。
     *
     * <p>滑动窗口保留最近 {@value #WINDOW_SIZE} 个样本，超出后淘汰最早样本。</p>
     *
     * @param millis 延迟毫秒数
     */
    public void recordLatency(long millis) {
        synchronized (latencySamples) {
            latencySamples.add(millis);
            if (latencySamples.size() > WINDOW_SIZE) {
                latencySamples.remove(0);
            }
        }
    }

    /**
     * 计算 P95 延迟（毫秒）。
     *
     * @return P95 延迟，无样本时返回 0
     */
    public long getP95LatencyMs() {
        return calculatePercentile(95);
    }

    /**
     * 计算 P99 延迟（毫秒）。
     *
     * @return P99 延迟，无样本时返回 0
     */
    public long getP99LatencyMs() {
        return calculatePercentile(99);
    }

    /**
     * 获取同步统计快照。
     *
     * @return 统计快照
     */
    public CdcSyncStats getStats() {
        synchronized (latencySamples) {
            return new CdcSyncStats(
                    totalEvents.get(),
                    successCount.get(),
                    failureCount.get(),
                    deadLetterCount.get(),
                    skippedCount.get(),
                    getP95LatencyMs(),
                    getP99LatencyMs()
            );
        }
    }

    /**
     * 判断同步是否健康。
     *
     * <p>健康条件：P95 延迟 ≤ {@value #P95_TARGET_MS}ms（目标 P95 ≤ 5s）。</p>
     *
     * @return true=健康
     */
    public boolean isHealthy() {
        return getP95LatencyMs() <= P95_TARGET_MS;
    }

    /**
     * 重置所有统计（测试用）。
     */
    public void reset() {
        totalEvents.set(0);
        successCount.set(0);
        failureCount.set(0);
        deadLetterCount.set(0);
        skippedCount.set(0);
        synchronized (latencySamples) {
            latencySamples.clear();
        }
    }

    /**
     * 计算指定分位数的延迟值。
     *
     * @param percentile 分位数（1-100）
     * @return 分位数值，无样本时返回 0
     */
    private long calculatePercentile(int percentile) {
        synchronized (latencySamples) {
            if (latencySamples.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(latencySamples);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }

    /**
     * 同步统计快照。
     *
     * @param totalEvents     总事件数
     * @param successCount    成功数
     * @param failureCount    失败数
     * @param deadLetterCount 死信数
     * @param skippedCount    跳过数
     * @param p95LatencyMs    P95 延迟（毫秒）
     * @param p99LatencyMs    P99 延迟（毫秒）
     */
    public record CdcSyncStats(long totalEvents, long successCount, long failureCount,
                               long deadLetterCount, long skippedCount,
                               long p95LatencyMs, long p99LatencyMs) {
    }
}
