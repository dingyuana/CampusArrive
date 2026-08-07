package com.campusarrive.integration.cdc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-MW-010：同步延迟监控测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.4 节监控指标、MW-2.3 Debezium CDC。
 * 验证延迟记录与 P95 分位数计算，健康判定阈值 P95 ≤ 5000ms。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-010: 同步延迟监控")
class SyncLatencyTest {

    private CdcSyncMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new CdcSyncMonitor();
    }

    @Nested
    @DisplayName("延迟记录与 P95 计算")
    class LatencyRecording {

        @Test
        @DisplayName("无样本时 P95 延迟为 0")
        void noSamplesP95IsZero() {
            assertEquals(0, monitor.getP95LatencyMs(), "无延迟样本时 P95 应为 0");
            assertEquals(0, monitor.getP99LatencyMs(), "无延迟样本时 P99 应为 0");
        }

        @Test
        @DisplayName("单个样本 P95 等于该样本值")
        void singleSampleP95() {
            monitor.recordLatency(200);

            assertEquals(200, monitor.getP95LatencyMs());
            assertEquals(200, monitor.getP99LatencyMs());
        }

        @Test
        @DisplayName("多个相同样本 P95 等于样本值")
        void uniformSamplesP95() {
            for (int i = 0; i < 100; i++) {
                monitor.recordLatency(100);
            }

            assertEquals(100, monitor.getP95LatencyMs(), "100 个 100ms 样本的 P95 应为 100");
            assertEquals(100, monitor.getP99LatencyMs(), "100 个 100ms 样本的 P99 应为 100");
        }

        @Test
        @DisplayName("P95 分位数计算正确 — 10 个样本中 P95 取第 95 百分位")
        void p95CalculationWithMixedSamples() {
            // 10 个样本：9 个 100ms + 1 个 1000ms
            for (int i = 0; i < 9; i++) {
                monitor.recordLatency(100);
            }
            monitor.recordLatency(1000);

            // 排序后：[100, 100, ..., 100, 1000]
            // P95 index = ceil(0.95 * 10) - 1 = 9 → 第 10 个值 = 1000
            assertEquals(1000, monitor.getP95LatencyMs(), "P95 应取第 95 百分位 = 1000ms");
        }

        @Test
        @DisplayName("P99 分位数计算正确")
        void p99Calculation() {
            // 100 个样本：98 个 100ms + 2 个 5000ms
            // P99 需要至少 2% 的样本为高延迟才能被捕获
            for (int i = 0; i < 98; i++) {
                monitor.recordLatency(100);
            }
            monitor.recordLatency(5000);
            monitor.recordLatency(5000);

            // 排序后：[100 x98, 5000, 5000]
            // P99 index = ceil(0.99 * 100) - 1 = 98 → 第 99 个值 = 5000
            assertEquals(5000, monitor.getP99LatencyMs(), "P99 应取第 99 百分位 = 5000ms");
        }

        @Test
        @DisplayName("滑动窗口淘汰旧样本")
        void slidingWindowEviction() {
            // 记录 1200 个样本（超过窗口大小 1000）
            for (int i = 0; i < 1200; i++) {
                monitor.recordLatency(i);
            }

            // 窗口仅保留最近 1000 个样本（200~1199）
            // P95 index = ceil(0.95 * 1000) - 1 = 949 → 排序后第 950 个值
            // 排序后 200~1199，第 950 个值 = 200 + 949 = 1149
            long p95 = monitor.getP95LatencyMs();
            assertTrue(p95 >= 1100 && p95 <= 1200,
                    "P95 应在窗口范围内 (200~1199), 实际=" + p95);
        }
    }

    @Nested
    @DisplayName("健康状态判定")
    class HealthStatus {

        @Test
        @DisplayName("P95 ≤ 5000ms 时判定为健康")
        void healthyWhenP95UnderTarget() {
            for (int i = 0; i < 20; i++) {
                monitor.recordLatency(1000);
            }

            assertTrue(monitor.isHealthy(), "P95=1000ms ≤ 5000ms 应判定健康");
            assertTrue(monitor.getP95LatencyMs() <= CdcSyncMonitor.P95_TARGET_MS);
        }

        @Test
        @DisplayName("P95 > 5000ms 时判定为不健康")
        void unhealthyWhenP95OverTarget() {
            // 10 个样本：9 个 100ms + 1 个 6000ms
            // P95 = 6000ms > 5000ms → 不健康
            for (int i = 0; i < 9; i++) {
                monitor.recordLatency(100);
            }
            monitor.recordLatency(6000);

            assertFalse(monitor.isHealthy(), "P95=6000ms > 5000ms 应判定不健康");
        }

        @Test
        @DisplayName("无延迟样本时判定为健康")
        void healthyWithNoSamples() {
            assertTrue(monitor.isHealthy(), "无延迟样本时 P95=0 ≤ 5000ms 应判定健康");
        }

        @Test
        @DisplayName("P95 恰好等于 5000ms 时判定为健康（边界值）")
        void healthyAtBoundary() {
            for (int i = 0; i < 10; i++) {
                monitor.recordLatency(5000);
            }

            assertTrue(monitor.isHealthy(), "P95=5000ms = 5000ms 应判定健康（≤ 目标）");
        }
    }

    @Nested
    @DisplayName("统计计数")
    class StatisticsCounting {

        @Test
        @DisplayName("计数器正确递增")
        void countersIncrement() {
            monitor.incrementTotal();
            monitor.incrementTotal();
            monitor.incrementSuccess();
            monitor.incrementFailure();
            monitor.incrementDeadLetter();
            monitor.incrementSkipped();

            CdcSyncMonitor.CdcSyncStats stats = monitor.getStats();
            assertEquals(2, stats.totalEvents());
            assertEquals(1, stats.successCount());
            assertEquals(1, stats.failureCount());
            assertEquals(1, stats.deadLetterCount());
            assertEquals(1, stats.skippedCount());
        }

        @Test
        @DisplayName("getStats 包含延迟指标")
        void statsIncludeLatency() {
            monitor.recordLatency(500);
            monitor.recordLatency(800);

            CdcSyncMonitor.CdcSyncStats stats = monitor.getStats();
            assertTrue(stats.p95LatencyMs() > 0, "P95 延迟应大于 0");
            assertTrue(stats.p99LatencyMs() > 0, "P99 延迟应大于 0");
        }

        @Test
        @DisplayName("reset 清零所有统计")
        void resetClearsAll() {
            monitor.incrementTotal();
            monitor.incrementSuccess();
            monitor.recordLatency(1000);

            monitor.reset();

            CdcSyncMonitor.CdcSyncStats stats = monitor.getStats();
            assertEquals(0, stats.totalEvents());
            assertEquals(0, stats.successCount());
            assertEquals(0, stats.p95LatencyMs());
        }
    }
}
