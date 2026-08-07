package com.campusarrive.integration.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-MW-008：CDC 变更事件捕获测试。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节 CDC 数据同步、MW-2.3 Debezium CDC。
 * 验证 INSERT/UPDATE/DELETE 事件被正确捕获，变更前后镜像提取正确。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-008: CDC 变更事件捕获")
class CdcChangeEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("操作类型事件创建")
    class OperationTypeEvents {

        @Test
        @DisplayName("INSERT 事件 — after 镜像有值，before 为 null")
        void insertEvent() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 1000);

            CdcChangeEvent event = CdcChangeEvent.builder()
                    .sourceTable("checkin_record")
                    .operation(CdcOperation.INSERT)
                    .after(after)
                    .timestamp(Instant.now())
                    .sourceOffset(offset)
                    .transactionId("tx-001")
                    .build();

            assertEquals(CdcOperation.INSERT, event.getOperation());
            assertEquals("checkin_record", event.getSourceTable());
            assertNotNull(event.getAfter());
            assertNull(event.getBefore(), "INSERT 事件的 before 镜像应为 null");
            assertEquals("20260001", event.getAfter().get("student_id"));
        }

        @Test
        @DisplayName("UPDATE 事件 — before 和 after 镜像均有值")
        void updateEvent() {
            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            Map<String, Object> after = new HashMap<>(before);
            after.put("status", "VERIFIED");
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 2000);

            CdcChangeEvent event = CdcChangeEvent.builder()
                    .sourceTable("checkin_record")
                    .operation(CdcOperation.UPDATE)
                    .before(before)
                    .after(after)
                    .timestamp(Instant.now())
                    .sourceOffset(offset)
                    .transactionId("tx-002")
                    .build();

            assertEquals(CdcOperation.UPDATE, event.getOperation());
            assertNotNull(event.getBefore(), "UPDATE 事件的 before 镜像应有值");
            assertNotNull(event.getAfter(), "UPDATE 事件的 after 镜像应有值");
            assertEquals("ACTIVE", event.getBefore().get("status"));
            assertEquals("VERIFIED", event.getAfter().get("status"));
        }

        @Test
        @DisplayName("DELETE 事件 — before 镜像有值，after 为 null")
        void deleteEvent() {
            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 3000);

            CdcChangeEvent event = CdcChangeEvent.builder()
                    .sourceTable("checkin_record")
                    .operation(CdcOperation.DELETE)
                    .before(before)
                    .timestamp(Instant.now())
                    .sourceOffset(offset)
                    .transactionId("tx-003")
                    .build();

            assertEquals(CdcOperation.DELETE, event.getOperation());
            assertNotNull(event.getBefore(), "DELETE 事件的 before 镜像应有值");
            assertNull(event.getAfter(), "DELETE 事件的 after 镜像应为 null");
            assertEquals("20260001", event.getBefore().get("student_id"));
        }

        @Test
        @DisplayName("SNAPSHOT 事件 — after 镜像有值，标记为快照操作")
        void snapshotEvent() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 0);

            CdcChangeEvent event = CdcChangeEvent.builder()
                    .sourceTable("checkin_record")
                    .operation(CdcOperation.SNAPSHOT)
                    .after(after)
                    .timestamp(Instant.now())
                    .sourceOffset(offset)
                    .transactionId("tx-snapshot")
                    .build();

            assertEquals(CdcOperation.SNAPSHOT, event.getOperation());
            assertTrue(event.getOperation().isSnapshot());
            assertNotNull(event.getAfter());
        }
    }

    @Nested
    @DisplayName("变更前后镜像提取")
    class ImageExtraction {

        @Test
        @DisplayName("getAfterField 从 after 镜像提取指定字段")
        void getAfterField() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", after,
                    CdcTestSupport.offset("mysql-bin.000003", 1000));

            assertEquals("20260001", event.getAfterField("student_id"));
            assertEquals("张三", event.getAfterField("name"));
            assertEquals("01", event.getAfterField("college_code"));
        }

        @Test
        @DisplayName("getBeforeField 从 before 镜像提取指定字段")
        void getBeforeField() {
            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            Map<String, Object> after = new HashMap<>(before);
            after.put("name", "李四");
            CdcChangeEvent event = CdcTestSupport.updateEvent("checkin_record", before, after,
                    CdcTestSupport.offset("mysql-bin.000003", 2000));

            assertEquals("张三", event.getBeforeField("name"), "before 镜像姓名应为张三");
            assertEquals("李四", event.getAfterField("name"), "after 镜像姓名应为李四");
        }

        @Test
        @DisplayName("字段不存在时 getAfterField 返回 null")
        void nonExistentField() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event = CdcTestSupport.insertEvent("checkin_record", after,
                    CdcTestSupport.offset("mysql-bin.000003", 1000));

            assertNull(event.getAfterField("non_existent_field"));
        }

        @Test
        @DisplayName("DELETE 事件 getAfterField 返回 null（after 镜像为 null）")
        void afterFieldOnDelete() {
            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent event = CdcTestSupport.deleteEvent("checkin_record", before,
                    CdcTestSupport.offset("mysql-bin.000003", 3000));

            assertNull(event.getAfterField("student_id"), "DELETE 事件 after 镜像为 null, 字段应为 null");
            assertEquals("20260001", event.getBeforeField("student_id"), "before 镜像字段应可提取");
        }

        @Test
        @DisplayName("getEffectiveImage — INSERT/UPDATE 返回 after，DELETE 返回 before")
        void effectiveImage() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");

            CdcChangeEvent insert = CdcTestSupport.insertEvent("t", after,
                    CdcTestSupport.offset("f", 1));
            assertEquals(after, insert.getEffectiveImage());

            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "旧名");
            CdcChangeEvent delete = CdcTestSupport.deleteEvent("t", before,
                    CdcTestSupport.offset("f", 2));
            assertEquals(before, delete.getEffectiveImage());
        }
    }

    @Nested
    @DisplayName("事件序列化")
    class Serialization {

        @Test
        @DisplayName("INSERT 事件 JSON 序列化/反序列化字段无损")
        void insertRoundTrip() throws Exception {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcChangeEvent original = CdcTestSupport.insertEvent("checkin_record", after,
                    CdcTestSupport.offset("mysql-bin.000003", 1000));

            String json = objectMapper.writeValueAsString(original);
            CdcChangeEvent deserialized = objectMapper.readValue(json, CdcChangeEvent.class);

            assertEquals(original.getSourceTable(), deserialized.getSourceTable());
            assertEquals(original.getOperation(), deserialized.getOperation());
            assertEquals(original.getAfter().get("student_id"),
                    deserialized.getAfter().get("student_id"));
            assertEquals(original.getSourceOffset().getBinlogFile(),
                    deserialized.getSourceOffset().getBinlogFile());
            assertEquals(original.getSourceOffset().getBinlogPosition(),
                    deserialized.getSourceOffset().getBinlogPosition());
        }

        @Test
        @DisplayName("UPDATE 事件 JSON 序列化/反序列化保留 before 和 after")
        void updateRoundTrip() throws Exception {
            Map<String, Object> before = CdcTestSupport.studentRecord("20260001", "张三");
            Map<String, Object> after = new HashMap<>(before);
            after.put("status", "VERIFIED");
            CdcChangeEvent original = CdcTestSupport.updateEvent("checkin_record", before, after,
                    CdcTestSupport.offset("mysql-bin.000003", 2000));

            String json = objectMapper.writeValueAsString(original);
            CdcChangeEvent deserialized = objectMapper.readValue(json, CdcChangeEvent.class);

            assertNotNull(deserialized.getBefore());
            assertNotNull(deserialized.getAfter());
            assertEquals("ACTIVE", deserialized.getBefore().get("status"));
            assertEquals("VERIFIED", deserialized.getAfter().get("status"));
        }
    }

    @Nested
    @DisplayName("事件 ID 与偏移量")
    class EventIdAndOffset {

        @Test
        @DisplayName("相同偏移量的事件 eventId 一致（幂等判重基础）")
        void sameOffsetSameEventId() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");
            CdcOffset offset = CdcTestSupport.offset("mysql-bin.000003", 1000);

            CdcChangeEvent event1 = CdcTestSupport.insertEvent("checkin_record", after, offset);
            CdcChangeEvent event2 = CdcTestSupport.insertEvent("checkin_record", after, offset);

            assertEquals(event1.getEventId(), event2.getEventId(),
                    "相同偏移量+表名+操作的事件应有相同 eventId");
        }

        @Test
        @DisplayName("不同偏移量的事件 eventId 不同")
        void differentOffsetDifferentEventId() {
            Map<String, Object> after = CdcTestSupport.studentRecord("20260001", "张三");

            CdcChangeEvent event1 = CdcTestSupport.insertEvent("t", after,
                    CdcTestSupport.offset("mysql-bin.000003", 1000));
            CdcChangeEvent event2 = CdcTestSupport.insertEvent("t", after,
                    CdcTestSupport.offset("mysql-bin.000003", 2000));

            assertNotEquals(event1.getEventId(), event2.getEventId());
        }

        @Test
        @DisplayName("CdcOffset 排序 — 同文件按位置排序")
        void offsetOrdering() {
            CdcOffset o1 = CdcTestSupport.offset("mysql-bin.000003", 1000);
            CdcOffset o2 = CdcTestSupport.offset("mysql-bin.000003", 2000);
            CdcOffset o3 = CdcTestSupport.offset("mysql-bin.000003", 3000);

            assertTrue(o1.compareTo(o2) < 0, "1000 < 2000");
            assertTrue(o2.compareTo(o3) < 0, "2000 < 3000");
            assertTrue(o3.compareTo(o1) > 0, "3000 > 1000");
            assertEquals(0, o1.compareTo(o1), "相同偏移量比较为 0");
        }

        @Test
        @DisplayName("CdcOffset 排序 — 不同文件按文件名排序")
        void offsetOrderingByFile() {
            CdcOffset o1 = CdcTestSupport.offset("mysql-bin.000003", 9999);
            CdcOffset o2 = CdcTestSupport.offset("mysql-bin.000004", 100);

            assertTrue(o1.compareTo(o2) < 0, "文件 000003 < 文件 000004");
            assertTrue(o2.compareTo(o1) > 0);
        }
    }
}
