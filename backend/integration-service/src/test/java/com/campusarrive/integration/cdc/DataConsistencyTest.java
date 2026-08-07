package com.campusarrive.integration.cdc;

import com.campusarrive.integration.idempotent.IdempotentHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * CT-MW-011：数据一致性测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.1 节主数据映射、MW-2.3 Debezium CDC。
 * 验证 student_id → id_card / card_id 翻译正确，所有字段在 sink 中正确映射，
 * 映射缺失时正确降级为死信。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-011: 数据一致性")
class DataConsistencyTest {

    private DataMappingService dataMappingService;
    private CdcSinkService mockSinkService;
    private CdcOffsetStore offsetStore;
    private CdcDeadLetterStore deadLetterStore;
    private CdcSyncMonitor monitor;
    private IdempotentHandler idempotentHandler;

    private static final long[] ZERO_DELAYS = {0L, 0L, 0L};
    private static final CdcEventHandler.Sleeper NO_OP_SLEEPER = millis -> { };

    @BeforeEach
    void setUp() {
        dataMappingService = new DataMappingService();
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

    @Nested
    @DisplayName("主数据映射翻译")
    class MappingTranslation {

        @Test
        @DisplayName("student_id → id_card 翻译正确")
        void studentIdToIdCardMapping() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

            var result = dataMappingService.mapStudentIdToIdCard("20260001");

            assertTrue(result.isPresent());
            assertEquals("330***********1234", result.get());
        }

        @Test
        @DisplayName("id_card → student_id 反向映射正确")
        void idCardToStudentIdMapping() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

            var result = dataMappingService.mapIdCardToStudentId("330***********1234");

            assertTrue(result.isPresent());
            assertEquals("20260001", result.get());
        }

        @Test
        @DisplayName("card_id → student_id 映射正确")
        void cardIdToStudentIdMapping() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

            var result = dataMappingService.mapCardId("CARD20260001");

            assertTrue(result.isPresent());
            assertEquals("20260001", result.get());
        }

        @Test
        @DisplayName("getMapping 返回完整映射条目")
        void getFullMapping() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");

            var mapping = dataMappingService.getMapping("20260001");

            assertTrue(mapping.isPresent());
            assertEquals("20260001", mapping.get().studentId());
            assertEquals("330***********1234", mapping.get().idCard());
            assertEquals("CARD20260001", mapping.get().cardId());
        }

        @Test
        @DisplayName("不存在的学号映射返回 Optional.empty")
        void nonExistentMapping() {
            var result = dataMappingService.mapStudentIdToIdCard("99999999");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 学号映射返回 Optional.empty")
        void nullStudentId() {
            var result = dataMappingService.mapStudentIdToIdCard(null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Sink 字段映射一致性")
    class SinkFieldMapping {

        @Test
        @DisplayName("CDC 事件的 student_id 被翻译为 id_card 和 card_id 传入 sink")
        void mappedRecordContainsTranslatedIds() {
            // 准备映射
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            // 创建 CDC 事件（仅含 student_id，无 id_card / card_id）
            Map<String, Object> record = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", record,
                    CdcTestSupport.nextOffset());

            // 处理事件
            CdcEventHandler handler = createHandler();
            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            // 验证处理成功
            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result);

            // 捕获传入 sink 的 mappedRecord
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(mockSinkService).sinkToEduSystem(any(), captor.capture());

            Map<String, Object> mapped = captor.getValue();
            assertEquals("20260001", mapped.get("student_id"), "原始 student_id 应保留");
            assertEquals("330***********1234", mapped.get("id_card"), "id_card 应被翻译填入");
            assertEquals("CARD20260001", mapped.get("card_id"), "card_id 应被翻译填入");
            assertEquals("张三", mapped.get("name"), "其他字段应保留");
        }

        @Test
        @DisplayName("UPDATE 事件的 after 镜像同样被正确映射")
        void updateEventMapping() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            after.put("status", "VERIFIED");

            CdcChangeEvent event = CdcTestSupport.updateEvent("checkin_record", before, after,
                    CdcTestSupport.nextOffset());

            CdcEventHandler handler = createHandler();
            handler.handle(event);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(mockSinkService).sinkToEduSystem(any(), captor.capture());

            Map<String, Object> mapped = captor.getValue();
            assertEquals("330***********1234", mapped.get("id_card"));
            assertEquals("CARD20260001", mapped.get("card_id"));
            assertEquals("VERIFIED", mapped.get("status"), "after 镜像的更新字段应保留");
        }

        @Test
        @DisplayName("不同源表路由到不同下游系统")
        void tableRoutingToDifferentSinks() {
            dataMappingService.createMapping("20260001", "330***********1234", "CARD20260001");
            when(mockSinkService.sinkToDormSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);
            when(mockSinkService.sinkToCardSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            CdcEventHandler handler = createHandler();

            // dorm_ 开头 → 宿管系统
            Map<String, Object> dormRecord = CdcTestSupport.dormRecord("20260001", "3号楼", "301");
            CdcChangeEvent dormEvent = CdcTestSupport.insertEvent("dorm_allocation", dormRecord,
                    CdcTestSupport.nextOffset());
            handler.handle(dormEvent);
            verify(mockSinkService).sinkToDormSystem(any(), anyMap());

            // card_ 开头 → 一卡通系统
            idempotentHandler.reset();
            Map<String, Object> cardRecord = CdcTestSupport.cardRecord("20260001", "CARD20260001");
            CdcChangeEvent cardEvent = CdcTestSupport.insertEvent("card_account", cardRecord,
                    CdcTestSupport.nextOffset());
            handler.handle(cardEvent);
            verify(mockSinkService).sinkToCardSystem(any(), anyMap());
        }
    }

    @Nested
    @DisplayName("映射缺失处理")
    class MissingMappingHandling {

        @Test
        @DisplayName("student_id 无映射时返回 MAPPING_MISSING")
        void mappingMissingReturnsDeadLetter() {
            // 不创建映射
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            Map<String, Object> record = CdcTestSupport.studentRecord("99999999", "未知");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", record,
                    CdcTestSupport.nextOffset());

            CdcEventHandler handler = createHandler();
            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.MAPPING_MISSING, result,
                    "映射缺失应返回 MAPPING_MISSING");
        }

        @Test
        @DisplayName("映射缺失时事件进入死信队列")
        void mappingMissingGoesToDeadLetter() {
            Map<String, Object> record = CdcTestSupport.studentRecord("99999999", "未知");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", record,
                    CdcTestSupport.nextOffset());

            CdcEventHandler handler = createHandler();
            handler.handle(event);

            assertEquals(1, deadLetterStore.size(), "映射缺失事件应进入死信队列");
            assertFalse(deadLetterStore.getAll().isEmpty());
            assertTrue(deadLetterStore.getAll().get(0).reason().contains("MappingMissing"));
        }

        @Test
        @DisplayName("映射缺失时不调用 sink")
        void mappingMissingDoesNotCallSink() {
            Map<String, Object> record = CdcTestSupport.studentRecord("99999999", "未知");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", record,
                    CdcTestSupport.nextOffset());

            CdcEventHandler handler = createHandler();
            handler.handle(event);

            verify(mockSinkService, never()).sinkToEduSystem(any(), anyMap());
            verify(mockSinkService, never()).sinkToDormSystem(any(), anyMap());
            verify(mockSinkService, never()).sinkToCardSystem(any(), anyMap());
        }

        @Test
        @DisplayName("无 student_id 字段的记录无需映射，直接透传")
        void noStudentIdNoMappingRequired() {
            when(mockSinkService.sinkToEduSystem(any(), anyMap()))
                    .thenReturn(CdcSinkService.SinkResult.SUCCESS);

            // 记录不含 student_id
            Map<String, Object> record = Map.of("config_key", "timeout", "config_value", "30");
            CdcChangeEvent event = CdcTestSupport.insertEvent("system_config", record,
                    CdcTestSupport.nextOffset());

            CdcEventHandler handler = createHandler();
            CdcEventHandler.CdcProcessResult result = handler.handle(event);

            assertEquals(CdcEventHandler.CdcProcessResult.SUCCESS, result,
                    "无 student_id 的记录应直接处理, 无需映射");
            verify(mockSinkService).sinkToEduSystem(any(), anyMap());
        }
    }
}
