package com.campusarrive.integration.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 批量同步服务 — 初始化全量 + 增量补数据。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节批量初始化、MW-2.3 Debezium CDC。
 *
 * 提供两种同步模式：
 * <ul>
 *   <li><b>全量同步（Full Sync）</b> — 从源表读取全部数据，以 SNAPSHOT 事件形式
 *       逐条投递给 {@link CdcEventHandler}，适用于首次初始化或灾后重建。</li>
 *   <li><b>增量同步（Incremental Sync）</b> — 基于水位线（update_time > lastSyncTime）
 *       读取增量数据，以 UPDATE 事件形式投递，适用于补齐 CDC 断连期间丢失的数据。</li>
 * </ul>
 *
 * <p>批量大小固定为 {@value #BATCH_SIZE} 条/批，分页拉取避免内存溢出。</p>
 */
public class BatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(BatchSyncService.class);

    /** 每批拉取记录数 */
    public static final int BATCH_SIZE = 1000;

    private final CdcEventHandler eventHandler;
    private RecordFetcher fetcher;

    /**
     * 数据拉取函数式接口。
     *
     * <p>生产环境注入 JDBC 分页查询实现；测试环境注入内存数据源。</p>
     *
     * @param tableName  表名
     * @param offset     分页偏移量（从 0 开始）
     * @param batchSize  每批大小
     * @param watermark  水位线时间戳（增量同步用，全量同步为 null）
     * @return 该批记录列表，空列表表示无更多数据
     */
    @FunctionalInterface
    public interface RecordFetcher {
        List<Map<String, Object>> fetch(String tableName, int offset, int batchSize, Instant watermark);
    }

    /**
     * 构造器 — 使用默认空 fetcher（生产环境应通过 {@link #setFetcher} 注入）。
     *
     * @param eventHandler CDC 事件处理器
     */
    public BatchSyncService(CdcEventHandler eventHandler) {
        this(eventHandler, (table, offset, batchSize, watermark) -> List.of());
    }

    /**
     * 构造器 — 注入数据拉取器。
     *
     * @param eventHandler CDC 事件处理器
     * @param fetcher      数据拉取器
     */
    public BatchSyncService(CdcEventHandler eventHandler, RecordFetcher fetcher) {
        this.eventHandler = eventHandler;
        this.fetcher = fetcher;
    }

    /**
     * 设置数据拉取器（用于运行时注入生产 JDBC 实现）。
     *
     * @param fetcher 数据拉取器
     */
    public void setFetcher(RecordFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * 执行全量同步。
     *
     * <p>从源表读取全部记录，以 SNAPSHOT 事件逐条投递给事件处理器。
     * 分页拉取，每批 {@value #BATCH_SIZE} 条，直至无更多数据。</p>
     *
     * @param tableName 源表名
     * @return 同步结果（成功数 / 失败数）
     */
    public BatchSyncResult executeFullSync(String tableName) {
        log.info("[BatchSyncService] 开始全量同步: table={}", tableName);
        int totalSynced = 0;
        int totalFailed = 0;
        int offset = 0;

        while (true) {
            List<Map<String, Object>> batch = fetcher.fetch(tableName, offset, BATCH_SIZE, null);
            if (batch == null || batch.isEmpty()) {
                break;
            }

            for (Map<String, Object> record : batch) {
                CdcChangeEvent event = CdcChangeEvent.builder()
                        .sourceTable(tableName)
                        .operation(CdcOperation.SNAPSHOT)
                        .after(record)
                        .timestamp(Instant.now())
                        .transactionId("batch-full-" + tableName + "-" + offset)
                        .build();

                CdcEventHandler.CdcProcessResult result = eventHandler.handle(event);
                if (result == CdcEventHandler.CdcProcessResult.SUCCESS) {
                    totalSynced++;
                } else {
                    totalFailed++;
                }
            }

            log.info("[BatchSyncService] 全量同步批次完成: table={}, offset={}, batchSize={}, synced={}, failed={}",
                    tableName, offset, batch.size(), totalSynced, totalFailed);

            if (batch.size() < BATCH_SIZE) {
                break;
            }
            offset += BATCH_SIZE;
        }

        log.info("[BatchSyncService] 全量同步完成: table={}, totalSynced={}, totalFailed={}",
                tableName, totalSynced, totalFailed);
        return new BatchSyncResult(totalSynced, totalFailed);
    }

    /**
     * 执行增量同步。
     *
     * <p>基于水位线（update_time > lastOffset.timestamp）读取增量记录，
     * 以 UPDATE 事件逐条投递。适用于补齐 CDC 断连期间丢失的数据。</p>
     *
     * @param tableName  源表名
     * @param lastOffset 上次同步的偏移量（提供水位线时间戳）
     * @return 同步结果（成功数 / 失败数）
     */
    public BatchSyncResult executeIncrementalSync(String tableName, CdcOffset lastOffset) {
        Instant watermark = (lastOffset != null && lastOffset.getTimestamp() != null)
                ? lastOffset.getTimestamp()
                : Instant.EPOCH;
        log.info("[BatchSyncService] 开始增量同步: table={}, watermark={}", tableName, watermark);

        int totalSynced = 0;
        int totalFailed = 0;
        int offset = 0;

        while (true) {
            List<Map<String, Object>> batch = fetcher.fetch(tableName, offset, BATCH_SIZE, watermark);
            if (batch == null || batch.isEmpty()) {
                break;
            }

            for (Map<String, Object> record : batch) {
                CdcChangeEvent event = CdcChangeEvent.builder()
                        .sourceTable(tableName)
                        .operation(CdcOperation.UPDATE)
                        .after(record)
                        .timestamp(Instant.now())
                        .sourceOffset(lastOffset)
                        .transactionId("batch-incr-" + tableName + "-" + offset)
                        .build();

                CdcEventHandler.CdcProcessResult result = eventHandler.handle(event);
                if (result == CdcEventHandler.CdcProcessResult.SUCCESS) {
                    totalSynced++;
                } else {
                    totalFailed++;
                }
            }

            log.info("[BatchSyncService] 增量同步批次完成: table={}, offset={}, batchSize={}, synced={}, failed={}",
                    tableName, offset, batch.size(), totalSynced, totalFailed);

            if (batch.size() < BATCH_SIZE) {
                break;
            }
            offset += BATCH_SIZE;
        }

        log.info("[BatchSyncService] 增量同步完成: table={}, totalSynced={}, totalFailed={}",
                tableName, totalSynced, totalFailed);
        return new BatchSyncResult(totalSynced, totalFailed);
    }

    /**
     * 批量同步结果。
     *
     * @param syncedCount 成功同步数
     * @param failedCount 失败数
     */
    public record BatchSyncResult(int syncedCount, int failedCount) {
    }
}
