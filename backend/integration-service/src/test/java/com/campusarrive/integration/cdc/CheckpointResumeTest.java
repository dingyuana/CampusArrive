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
 * CT-MW-009：断点续传测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节断点续传、MW-2.3 Debezium CDC。
 * 验证偏移量保存/加载，服务重启后从已保存偏移量恢复，不重复处理已消费事件。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-009: 断点续传")
class CheckpointResumeTest {

    private DataMappingService dataMappingService;
    private CdcSinkService mockSinkService;
    private CdcOffsetStore offsetStore;
    private CdcDeadLetterStore deadLetterStore;
    private CdcSyncMonitor monitor;
    private IdempotentHandler idempotentHandler;

    /** 测试用零延迟重试 + no-op sleeper */
    private static final long[] ZERO_DELAYS = {0L, 0L, 0L};
    private static final CdcEventHandler.Sleeper NO_OP_SLEEPER = millis -> { };

    @BeforeEach
    void setUp() {
        dataMappingService = new DataMappingService();
        dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

        mockSinkService = mock(CdcSinkService.class);
        when(mockSinkService.sinkToEduSystem(any(), anyMap())).thenReturn(CdcSinkService.SinkResult.SUCCESS);
        when(mockSinkService.sinkToDormSystem(any(), anyMap())).thenReturn(CdcSinkService.SinkResult.SUCCESS);
        when(mockSinkService.sinkToCardSystem(any(), anyMap())).thenReturn(CdcSinkService.SinkResult.SUCCESS);

        offsetStore = new CdcOffsetStore.InMemoryCdcOffsetStore();
        deadLetterStore = new CdcDeadLetterStore();
        monitor = new CdcSyncMonitor();
        idempotentHandler = new IdempotentHandler();
    }

    /** 创建测试用 CdcEventHandler（零延迟 + no-op sleeper） */
    private CdcEventHandler createHandler() {
        return new CdcEventHandler(dataMappingService, mockSinkService, offsetStore,
                deadLetterStore, monitor, idempotentHandler, ZERO_DELAYS, NO_OP_SLEEPER);
    }

    @Nested
    @DisplayName("偏移量保存与加载")
    class OffsetSaveLoad {

        @Test
        @DisplayName("初始状态 getCurrentOffset 返回 null")
        void initialOffsetIsNull() {
            CdcOffsetStore store = new CdcOffsetStore.InMemoryCdcOffsetStore();
            assertNull(store.getCurrentOffset(), "初始状态偏移量应为 null");
        }

        @Test
        @DisplayName("saveOffset 后 getCurrentOffset 返回保存的值")
        void saveAndGetCurrentOffset() {
            CdcOffsetStore store = new CdcOffsetStore.InMemoryCdcOffsetStore();
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 5000);

            store.saveOffset(offset);

            assertEquals(offset, store.getCurrentOffset());
        }

        @Test
        @DisplayName("偏移量不会回退 — 保存更旧的偏移量被忽略")
        void offsetDoesNotRegress() {
            CdcOffsetStore store = new CdcOffsetStore.InMemoryCdcOffsetStore();
            CdcOffset newer = CdcTestSupport.offset("mysql-bin.000003", 5000);
            CdcOffset older = CdcTestSupport.offset("mysql-bin.000003", 3000);

            store.saveOffset(newer);
            store.saveOffset(older);

            assertEquals(5000, store.getCurrentOffset().getBinlogPosition(),
                    "偏移量不应回退到更旧的位置");
        }

        @Test
        @DisplayName("loadOffset 在内存实现中为空操作")
        void loadOffsetNoOp() {
            CdcOffsetStore store = new CdcOffsetStore.InMemoryCdcOffsetStore();
            store.saveOffset(CdcTestSupport.offset("mysql-bin.000003", 5000));

            // loadOffset 不应清除已有偏移量
            store.loadOffset();

            assertNotNull(store.getCurrentOffset());
            assertEquals(5000, store.getCurrentOffset().getBinlogPosition());
        }
    }

    @Nested
    @DisplayName("事件不重复处理")
    class NoReprocessing {

        @Test
        @DisplayName("已处理偏移量的事件被跳过")
        void skipAlreadyProcessedOffset() {
            CdcEventHandler handler = createHandler();

            // 事件 1: offset 1000
            CdcOffset offset1 = CdcTestSupport.offset("mysql-bin.000003", 1000);
            Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event1 = CdcTestSupport.insertEvent("checkin_record", record, offset1);

            CdcEventHandler.CdcProcessResult result1 = handler.handle(event1);
            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result1);
            assertEquals(1000, offsetStore.getCurrentOffset().getBinlogPosition());

            // 再次投递同一事件（相同偏移量）
            CdcEventHandler.CdcProcessResult result2 = handler.handle(event1);
            assertEquals(CdcEventHandler.CdcProcessResult.SKIPPED_DUPLICATE, result2,
                    "相同偏移量的事件应被跳过");

            // sink 仅被调用一次
            verify(mockSinkService, times(1)).sinkToEduSystem(any(), anyMap());
        }

        @Test
        @DisplayName("偏移量更旧的事件被跳过")
        void skipOlderOffset() {
            CdcEventHandler handler = createHandler();

            // 处理 offset=2000 的事件
            CdcOffset offset2 = CdcTestSupport.offset("mysql-bin.000003", 2000);
            Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event2 = CdcTestSupport.insertEvent("checkin_record", record, offset2);
            handler.handle(event2);

            // 投递更旧的 offset=1000 事件
            CdcOffset offset1 = CdcTestSupport.offset("mysql-bin.000003", 1000);
            CdcChangeEvent event1 = CdcTestSupport.insertEvent("checkin_record", record, offset1);
            CdcEventHandler.CdcProcessResult result = handler.handle(event1);

            assertEquals(CdcEventHandler.CdcProcessResult.SKIPPED_DUPLICATE, result,
                    "更旧偏移量的事件应被跳过");
            verify(mockSinkService, times(1)).sinkToEduSystem(any(), anyMap());
        }
    }

    @Nested
    @DisplayName("重启恢复场景")
    class RestartScenario {

        @Test
        @DisplayName("重启后从已保存偏移量恢复，不重复处理旧事件")
        void restartResumesFromSavedOffset() {
            // === 第一次运行（模拟服务首次启动） ===
            CdcOffset offset1 = CdcTestSupport.offset("mysql-bin.000003", 1000);
            Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event1 = CdcTestSupport.insertEvent("checkin_record", record, offset1);

            CdcEventHandler handler1 = createHandler();
            CdcEventHandler.CdcProcessResult result1 = handler1.handle(event1);
            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result1);

            // 验证偏移量已保存
            assertEquals(1000, offsetStore.getCurrentOffset().getBinlogPosition());
            verify(mockSinkService, times(1)).sinkToEduSystem(any(), anyMap());

            // === 模拟重启：创建新 handler，共享同一 offsetStore ===
            // 新的 idempotentHandler（内存丢失），但 offsetStore 保留偏移量
            IdempotentHandler newIdempotent = new IdempotentHandler();
            CdcEventHandler handler2 = new CdcEventHandler(dataMappingService, mockSinkService,
                    offsetStore, deadLetterStore, monitor, newIdempotent,
                    ZERO_DELAYS, NO_OP_SLEEPER);

            // 重启后再次投递 event1（相同偏移量）→ 应被跳过
            CdcEventHandler.CdcProcessResult result1Again = handler2.handle(event1);
            assertEquals(CdcEventHandler.CdcProcessResult.SKIPPED_DUPLICATE, result1Again,
                    "重启后旧事件应被偏移量检查跳过");

            // 重启后投递新事件（更新偏移量）→ 应正常处理
            CdcOffset offset2 = CdcTestSupport.offset("mysql-bin.000003", 2000);
            CdcChangeEvent event2 = CdcTestSupport.insertEvent("checkin_record", record, offset2);
            CdcEventHandler.CdcProcessResult result2 = handler2.handle(event2);
            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result2,
                    "重启后新事件应正常处理");

            // 验证偏移量前进到 2000
            assertEquals(2000, offsetStore.getCurrentOffset().getBinlogPosition());

            // sink 被调用 2 次（event1 第一次 + event2）
            verify(mockSinkService, times(2)).sinkToEduSystem(any(), anyMap());
        }

        @Test
        @DisplayName("重启后跨 binlog 文件恢复")
        void restartAcrossBinlogFiles() {
            // 处理 mysql-bin.000003 的事件
            CdcOffset offset1 = CdcTestSupport.offset("mysql-bin.000003", 5000);
            Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event1 = CdcTestSupport.insertEvent("checkin_record", record, offset1);

            CdcEventHandler handler1 = createHandler();
            handler1.handle(event1);

            // 重启
            IdempotentHandler newIdempotent = new IdempotentHandler();
            CdcEventHandler handler2 = new CdcEventHandler(dataMappingService, mockSinkService,
                    offsetStore, deadLetterStore, monitor, newIdempotent,
                    ZERO_DELAYS, NO_OP_SLEEPER);

            // 新文件 mysql-bin.000004 的事件应被处理（文件名更新）
            CdcOffset offset2 = CdcTestSupport.offset("mysql-bin.000004", 100);
            CdcChangeEvent event2 = CdcTestSupport.insertEvent("checkin_record", record, offset2);
            CdcEventHandler.CdcProcessResult result = handler2.handle(event2);

            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result,
                    "新 binlog 文件的事件应正常处理");
            assertEquals("mysql-bin.000004", offsetStore.getCurrentOffset().getBinlogFile());
        }
    }
}
