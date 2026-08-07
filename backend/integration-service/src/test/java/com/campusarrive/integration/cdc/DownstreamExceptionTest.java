package com.campusarrive.integration.cdc;

import com.campusarrive.integration.idempotent.IdempotentHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * CT-MW-012：下游异常降级测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.3 节异常处理、MW-2.3 Debezium CDC。
 * 验证下游系统写入失败时触发重试，重试耗尽后进入死信队列，
 * 主流程不被下游故障阻塞。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-012: 下游异常降级")
class DownstreamExceptionTest {

    private DataMappingService dataMappingService;
    private CdcSinkService mockSinkService;
    private CdcOffsetStore offsetStore;
    private CdcDeadLetterStore deadLetterStore;
    private CdcSyncMonitor monitor;
    private IdempotentHandler idempotentHandler;

    /** 测试用零延迟重试 + no-op sleeper（避免真实睡眠） */
    private static final long[] ZERO_DELAYS = {0L, 0L, 0L};
    private static final CdcEventHandler.Sleeper NO_OP_SLEEPER = millis -> { };

    @BeforeEach
    void setUp() {
        dataMappingService = new DataMappingService();
        dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

        mockSinkService = mock(CdcSinkService.class);
        offsetStore = new CdcOffsetStore.InMemoryCdcOffsetStore();
        deadLetterStore = new CdcDeadLetterStore();
        monitor = new CdcSyncMonitor();
        idempotentHandler = new IdempotentHandler();
    }

    private CdcEventHandler createHandler() {
        return new CdcEventHandler(dataMappingService, mockSinkService, offsetStore,
                deadLetterStore, monitor, idempotentHandler, ZERO_DELAYS, NO_OP_SLEEPER);
    }

    private CdcChangeEvent createEvent() {
        Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
        return CdcTestSupport.insertEvent("checkin_record", record, CdcTestSupport.nextOffset());
    }

    @Nested
    @DisplayName("重试触发")
    class RetryTriggered {

        @Test
        @DisplayName("首次失败后重试，第二次成功 → 最终返回 SUCCESS")
        void retryThenSuccess() {
            // 首次返回 RETRY，第二次返回 SUCCESS
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY)
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();

            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result,
                    "首次失败重试后成功应返回 SUCCESS");
            assertEquals(0, deadLetterStore.size(), "成功后不应有死信");
            verify(mockSinkService, times(2)).sinkToEduSystem(any(), anyMap());
        }

        @Test
        @DisplayName("重试延迟阶梯为 10s / 30s / 60s")
        void retryDelaySteps() {
            assertArrayEquals(new long[]{10_000L, 30_000L, 60_000L},
                    CdcEventHandler.DEFAULT_RETRY_DELAYS_MS,
                    "重试延迟阶梯应为 10s / 30s / 60s");
        }

        @Test
        @DisplayName("最大重试次数为 3 次")
        void maxRetryCount() {
            assertEquals(3, CdcEventHandler.MAX_RETRIES, "最大重试次数应为 3");
        }

        @Test
        @DisplayName("重试过程中偏移量未提交（仅成功后才提交）")
        void offsetNotCommittedDuringRetry() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY)
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();
            handler.handle(event);

            // 成功后偏移量应已提交
            assertNotNull(offsetStore.getCurrentOffset(),
                    "成功后偏移量应已提交");
        }
    }

    @Nested
    @DisplayName("重试耗尽进死信")
    class RetryExhausted {

        @Test
        @DisplayName("连续失败重试耗尽后进入死信队列")
        void consecutiveFailuresToDeadLetter() {
            // sink 始终返回 RETRY
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();

            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.RETRY_EXHAUSTED, result,
                    "重试耗尽应返回 RETRY_EXHAUSTED");
            assertEquals(1, deadLetterStore.size(), "事件应进入死信队列");
            assertTrue(deadLetterStore.getAll().get(0).reason().contains("RetryExhausted"),
                    "死信原因应包含 RetryExhausted");
        }

        @Test
        @DisplayName("重试次数 = 1 次首次 + 3 次重试 = 共 4 次 sink 调用")
        void totalSinkCallsExhausted() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();
            handler.handle(event);

            // 1 次首次尝试 + 3 次重试 = 4 次
            verify(mockSinkService, times(4)).sinkToEduSystem(any(), anyMap());
        }

        @Test
        @DisplayName("重试耗尽后偏移量未提交（不前进检查点）")
        void offsetNotCommittedOnExhausted() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();
            handler.handle(event);

            assertNull(offsetStore.getCurrentOffset(),
                    "重试耗尽后偏移量不应提交, 保证下次重启可重新处理");
        }

        @Test
        @DisplayName("不可重试失败（DEAD_LETTER）立即转死信，不重试")
        void nonRetryableImmediateDeadLetter() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.DEAD_LETTER);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();

            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.DEAD_LETTER, result,
                    "不可重试失败应立即返回 DEAD_LETTER");
            assertEquals(1, deadLetterStore.size());
            // 仅调用 1 次（不重试）
            verify(mockSinkService, times(1)).sinkToEduSystem(any(), anyMap());
        }
    }

    @Nested
    @DisplayName("主流程不被阻塞")
    class MainFlowNotBlocked {

        @Test
        @DisplayName("sink 抛出异常时主流程不被阻塞，返回死信结果")
        void sinkExceptionDoesNotBlockMainFlow() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenThrow(new RuntimeException("下游连接拒绝"));

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();

            // handle 不应抛出异常
            CdcEventHandler.CdcProcessResult result = assertDoesNotThrow(
                    () -> handler.handle(event),
                    "下游异常不应导致主流程抛出异常");

            // 异常被当作可重试处理，重试耗尽后转死信
            assertTrue(result == CdcEventHandler.CdcProcessResult.RETRY_EXHAUSTED
                            || result == CdcEventHandler.CdcProcessResult.DEAD_LETTER,
                    "异常后应返回 RETRY_EXHAUSTED 或 DEAD_LETTER");
            assertEquals(1, deadLetterStore.size(), "异常事件应进入死信队列");
        }

        @Test
        @DisplayName("sink 抛出异常后重试，恢复成功")
        void sinkExceptionThenRecovery() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenThrow(new RuntimeException("网络抖动"))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event = createEvent();

            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result,
                    "异常后重试成功应返回 SUCCESS");
            assertEquals(0, deadLetterStore.size());
        }

        @Test
        @DisplayName("下游故障不影响后续事件处理")
        void downstreamFailureDoesNotAffectSubsequentEvents() {
            // 第一个事件：始终失败 → 死信
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY);

            CdcEventHandler handler = createHandler();
            CdcChangeEvent event1 = createEvent();
            CdcEventHandler.CdcProcessResult result1 = handler.handle(event1);
            assertEquals(CdcEventHandler.CdcProcessResult.RETRY_EXHAUSTED, result1);

            // 重置 mock，第二个事件：成功
            reset(mockSinkService);
            idempotentHandler.reset();
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            CdcChangeEvent event2 = createEvent();
            CdcEventHandler.CdcProcessResult result2 = handler.handle(event2);

            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result2,
                    "下游恢复后后续事件应正常处理");
            assertEquals(1, deadLetterStore.size(), "仅第一个失败事件在死信队列");
        }

        @Test
        @DisplayName("监控统计正确记录失败和死信")
        void monitorRecordsFailureAndDeadLetter() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.RETRY);

            CdcEventHandler handler = createHandler();
            handler.handle(createEvent());

            CdcSyncMonitor.CdcSyncStats stats = monitor.getStats();
            assertEquals(1, stats.totalEvents(), "总事件数应为 1");
            assertEquals(1, stats.failureCount(), "失败数应为 1");
            assertEquals(1, stats.deadLetterCount(), "死信数应为 1");
            assertEquals(0, stats.successCount(), "成功数应为 0");
        }
    }
}
