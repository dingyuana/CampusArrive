package com.campusarrive.integration.cdc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 批量同步服务单元测试。
 *
 * <p>规格来源：MW-2.3 Debezium CDC、SIM-CA-2026-08 第 6.2 节批量初始化。
 * 验证全量同步与增量同步的分页拉取、事件投递、结果统计逻辑。</p>
 */
@DisplayName("UT-MW-023: 批量同步服务")
class BatchSyncServiceTest {

    private CdcEventHandler eventHandler;

    @BeforeEach
    void setUp() {
        eventHandler = mock(CdcEventHandler.class);
    }

    @Nested
    @DisplayName("全量同步")
    class FullSync {

        @Test
        @DisplayName("空表同步返回零结果")
        void emptyTableReturnsZero() {
            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, offset, batchSize, watermark) -> List.of());

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(0, result.syncedCount());
            assertEquals(0, result.failedCount());
            verify(eventHandler, never()).handle(any());
        }

        @Test
        @DisplayName("单批数据全部成功同步")
        void singleBatchAllSuccess() {
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三"),
                    CdcTestSupport.studentRecord("20260002", "李四")
            );
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, offset, batchSize, watermark) -> {
                        if (offset == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(2, result.syncedCount());
            assertEquals(0, result.failedCount());
            verify(eventHandler, times(2)).handle(any());
        }

        @Test
        @DisplayName("单批数据部分失败")
        void singleBatchPartialFailure() {
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三"),
                    CdcTestSupport.studentRecord("20260002", "李四"),
                    CdcTestSupport.studentRecord("20260003", "王五")
            );
            when(eventHandler.handle(any()))
                    .thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS)
                    .thenReturn(CdcEventHandler.CdcProcessResult.DEAD_LETTER)
                    .thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, offset, batchSize, watermark) -> {
                        if (offset == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(2, result.syncedCount());
            assertEquals(1, result.failedCount());
        }

        @Test
        @DisplayName("多批数据分页拉取直至完成")
        void multipleBatchesPaged() {
            // 模拟 2 批完整数据 + 1 批不足（触发停止）
            List<Map<String, Object>> batch1 = new ArrayList<>();
            List<Map<String, Object>> batch2 = new ArrayList<>();
            for (int i = 0; i < BatchSyncService.BATCH_SIZE; i++) {
                batch1.add(CdcTestSupport.studentRecord("2026" + i, "学生" + i));
            }
            for (int i = 0; i < 50; i++) {
                batch2.add(CdcTestSupport.studentRecord("2027" + i, "学生" + i));
            }
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            final int[] callCount = {0};
            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, offset, batchSize, watermark) -> {
                        callCount[0]++;
                        if (offset == 0) return batch1;
                        if (offset == BatchSyncService.BATCH_SIZE) return batch2;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(BatchSyncService.BATCH_SIZE + 50, result.syncedCount());
            assertEquals(0, result.failedCount());
            verify(eventHandler, times(BatchSyncService.BATCH_SIZE + 50)).handle(any());
        }

        @Test
        @DisplayName("fetcher 返回 null 时停止同步")
        void nullBatchStopsSync() {
            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, offset, batchSize, watermark) -> null);

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(0, result.syncedCount());
            assertEquals(0, result.failedCount());
        }
    }

    @Nested
    @DisplayName("增量同步")
    class IncrementalSync {

        @Test
        @DisplayName("基于水位线增量同步成功")
        void incrementalSyncWithWatermark() {
            CdcOffset offset = new CdcOffset("mysql-bin.000001", 100, null, Instant.parse("2026-08-28T10:00:00Z"));
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三")
            );
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, off, batchSize, watermark) -> {
                        assertNotNull(watermark, "增量同步应传入水位线");
                        if (off == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeIncrementalSync("student_info", offset);

            assertEquals(1, result.syncedCount());
            assertEquals(0, result.failedCount());
        }

        @Test
        @DisplayName("null 偏移量使用 EPOCH 作为水位线")
        void nullOffsetUsesEpoch() {
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三")
            );
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, off, batchSize, watermark) -> {
                        assertEquals(Instant.EPOCH, watermark, "null offset 应使用 EPOCH 水位线");
                        if (off == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeIncrementalSync("student_info", null);

            assertEquals(1, result.syncedCount());
        }

        @Test
        @DisplayName("偏移量 timestamp 为 null 时使用 EPOCH")
        void nullTimestampUsesEpoch() {
            CdcOffset offset = new CdcOffset("mysql-bin.000001", 100, null, null);
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三")
            );
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, off, batchSize, watermark) -> {
                        if (off == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeIncrementalSync("student_info", offset);

            assertEquals(1, result.syncedCount());
        }

        @Test
        @DisplayName("增量同步部分失败")
        void incrementalPartialFailure() {
            CdcOffset offset = new CdcOffset("mysql-bin.000001", 100, null, Instant.parse("2026-08-28T10:00:00Z"));
            List<Map<String, Object>> records = List.of(
                    CdcTestSupport.studentRecord("20260001", "张三"),
                    CdcTestSupport.studentRecord("20260002", "李四")
            );
            when(eventHandler.handle(any()))
                    .thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS)
                    .thenReturn(CdcEventHandler.CdcProcessResult.DEAD_LETTER);

            BatchSyncService service = new BatchSyncService(eventHandler,
                    (table, off, batchSize, watermark) -> {
                        if (off == 0) return records;
                        return List.of();
                    });

            BatchSyncService.BatchSyncResult result = service.executeIncrementalSync("student_info", offset);

            assertEquals(1, result.syncedCount());
            assertEquals(1, result.failedCount());
        }
    }

    @Nested
    @DisplayName("Fetcher 注入")
    class FetcherInjection {

        @Test
        @DisplayName("默认构造器使用空 fetcher")
        void defaultConstructorUsesEmptyFetcher() {
            BatchSyncService service = new BatchSyncService(eventHandler);

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(0, result.syncedCount());
            assertEquals(0, result.failedCount());
        }

        @Test
        @DisplayName("setFetcher 运行时替换数据拉取器")
        void setFetcherReplacesAtRuntime() {
            BatchSyncService service = new BatchSyncService(eventHandler);
            when(eventHandler.handle(any())).thenReturn(CdcEventHandler.CdcProcessResult.SUCCESS);

            service.setFetcher((table, offset, batchSize, watermark) -> {
                if (offset == 0) return List.of(CdcTestSupport.studentRecord("20260001", "张三"));
                return List.of();
            });

            BatchSyncService.BatchSyncResult result = service.executeFullSync("student_info");

            assertEquals(1, result.syncedCount());
        }
    }
}
