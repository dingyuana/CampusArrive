package com.campusarrive.integration.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDC 偏移量存储接口 — 断点续传的核心抽象。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节断点续传、MW-2.3 Debezium CDC。
 *
 * 负责持久化与加载 binlog 消费位置（{@link CdcOffset}），保证服务重启后
 * 从上次提交的偏移量恢复同步，不丢数据、不重复处理。</p>
 *
 * <p>实现策略：
 * <ul>
 *   <li>开发环境：{@link InMemoryCdcOffsetStore}（内存版，重启丢失）</li>
 *   <li>生产环境：基于 Redis / MySQL cdc_offset 表的持久化实现</li>
 * </ul></p>
 */
public interface CdcOffsetStore {

    /**
     * 保存当前消费偏移量。
     *
     * <p>每条事件处理成功后调用，更新检查点。新偏移量必须大于当前偏移量，
     * 否则忽略（防止回退）。</p>
     *
     * @param offset 新的 binlog 偏移量
     */
    void saveOffset(CdcOffset offset);

    /**
     * 获取当前已保存的偏移量。
     *
     * @return 当前偏移量，未保存过返回 null
     */
    CdcOffset getCurrentOffset();

    /**
     * 从持久化存储加载偏移量（服务启动时调用）。
     *
     * <p>内存实现为空操作；持久化实现从数据库/Redis 读取并设置 currentOffset。</p>
     */
    void loadOffset();

    /**
     * 重置偏移量（测试用）。
     */
    void reset();

    // ================================================================
    // 内存实现 — 开发环境使用
    // ================================================================

    /**
     * 内存版偏移量存储。
     *
     * <p>使用 volatile 变量保证可见性，适用于单实例开发环境。
     * 生产环境应替换为 Redis / MySQL 实现，保证多实例下偏移量一致。</p>
     */
    class InMemoryCdcOffsetStore implements CdcOffsetStore {

        private static final Logger log = LoggerFactory.getLogger(InMemoryCdcOffsetStore.class);

        private volatile CdcOffset currentOffset;

        @Override
        public void saveOffset(CdcOffset offset) {
            if (offset == null) {
                return;
            }
            // 防止偏移量回退：仅当新偏移量大于当前偏移量时才更新
            if (currentOffset != null && offset.compareTo(currentOffset) <= 0) {
                log.debug("[InMemoryCdcOffsetStore] 偏移量未前进, 忽略: current={}, incoming={}",
                        currentOffset, offset);
                return;
            }
            this.currentOffset = offset;
            log.debug("[InMemoryCdcOffsetStore] 保存偏移量: {}", offset);
        }

        @Override
        public CdcOffset getCurrentOffset() {
            return currentOffset;
        }

        @Override
        public void loadOffset() {
            // 内存版无需加载，偏移量仅存在于内存中
            log.info("[InMemoryCdcOffsetStore] 内存模式, 无需加载偏移量");
        }

        @Override
        public void reset() {
            currentOffset = null;
        }
    }
}
