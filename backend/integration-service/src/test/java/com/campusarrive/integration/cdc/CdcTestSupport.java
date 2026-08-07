package com.campusarrive.integration.cdc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDC 测试辅助工厂 — 构建标准测试 CDC 事件。
 *
 * <p>规格来源：CT-MW-008 ~ CT-MW-012 CDC 数据同步测试。
 * 避免在测试中硬编码魔法值，统一通过工厂方法创建测试事件。</p>
 */
public final class CdcTestSupport {

    private CdcTestSupport() {
    }

    /** 自增偏移量计数器，保证每个事件偏移量唯一 */
    private static final AtomicLong positionCounter = new AtomicLong(1000);

    /**
     * 创建 INSERT 事件。
     *
     * @param table  源表名
     * @param after  变更后数据
     * @param offset binlog 偏移量
     * @return INSERT 事件
     */
    public static CdcChangeEvent insertEvent(String table, Map<String, Object> after, CdcOffset offset) {
        return CdcChangeEvent.builder()
                .sourceTable(table)
                .operation(CdcOperation.INSERT)
                .after(after)
                .timestamp(Instant.now())
                .sourceOffset(offset)
                .transactionId("tx-" + System.nanoTime())
                .build();
    }

    /**
     * 创建 UPDATE 事件。
     *
     * @param table  源表名
     * @param before 变更前数据
     * @param after  变更后数据
     * @param offset binlog 偏移量
     * @return UPDATE 事件
     */
    public static CdcChangeEvent updateEvent(String table, Map<String, Object> before,
                                             Map<String, Object> after, CdcOffset offset) {
        return CdcChangeEvent.builder()
                .sourceTable(table)
                .operation(CdcOperation.UPDATE)
                .before(before)
                .after(after)
                .timestamp(Instant.now())
                .sourceOffset(offset)
                .transactionId("tx-" + System.nanoTime())
                .build();
    }

    /**
     * 创建 DELETE 事件。
     *
     * @param table  源表名
     * @param before 变更前数据
     * @param offset binlog 偏移量
     * @return DELETE 事件
     */
    public static CdcChangeEvent deleteEvent(String table, Map<String, Object> before, CdcOffset offset) {
        return CdcChangeEvent.builder()
                .sourceTable(table)
                .operation(CdcOperation.DELETE)
                .before(before)
                .timestamp(Instant.now())
                .sourceOffset(offset)
                .transactionId("tx-" + System.nanoTime())
                .build();
    }

    /**
     * 创建 SNAPSHOT 事件。
     *
     * @param table  源表名
     * @param after  快照数据
     * @param offset binlog 偏移量
     * @return SNAPSHOT 事件
     */
    public static CdcChangeEvent snapshotEvent(String table, Map<String, Object> after, CdcOffset offset) {
        return CdcChangeEvent.builder()
                .sourceTable(table)
                .operation(CdcOperation.SNAPSHOT)
                .after(after)
                .timestamp(Instant.now())
                .sourceOffset(offset)
                .transactionId("tx-snapshot")
                .build();
    }

    /**
     * 创建 binlog 偏移量。
     *
     * @param file     binlog 文件名
     * @param position 文件内位置
     * @return 偏移量
     */
    public static CdcOffset offset(String file, long position) {
        return new CdcOffset(file, position, null, Instant.now());
    }

    /**
     * 创建自增偏移量（保证唯一性）。
     *
     * @return 唯一偏移量
     */
    public static CdcOffset nextOffset() {
        return new CdcOffset("mysql-bin.000003", positionCounter.incrementAndGet(), null, Instant.now());
    }

    /**
     * 创建学生记录 Map。
     *
     * @param studentId 学号
     * @param name      姓名
     * @return 学生记录
     */
    public static Map<String, Object> studentRecord(String studentId, String name) {
        Map<String, Object> record = new HashMap<>();
        record.put("student_id", studentId);
        record.put("name", name);
        record.put("college_code", "01");
        record.put("status", "ACTIVE");
        return record;
    }

    /**
     * 创建宿舍记录 Map。
     *
     * @param studentId   学号
     * @param dormBuilding 宿舍楼
     * @param roomNo      房间号
     * @return 宿舍记录
     */
    public static Map<String, Object> dormRecord(String studentId, String dormBuilding, String roomNo) {
        Map<String, Object> record = new HashMap<>();
        record.put("student_id", studentId);
        record.put("dorm_building", dormBuilding);
        record.put("room_no", roomNo);
        record.put("bed_no", "1");
        return record;
    }

    /**
     * 创建一卡通记录 Map。
     *
     * @param studentId 学号
     * @param cardId    卡号
     * @return 一卡通记录
     */
    public static Map<String, Object> cardRecord(String studentId, String cardId) {
        Map<String, Object> record = new HashMap<>();
        record.put("student_id", studentId);
        record.put("card_id", cardId);
        record.put("balance", 100.00);
        record.put("status", "ACTIVE");
        return record;
    }
}
