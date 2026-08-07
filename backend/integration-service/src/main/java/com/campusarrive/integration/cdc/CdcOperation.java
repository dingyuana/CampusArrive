package com.campusarrive.integration.cdc;

/**
 * CDC 变更操作类型枚举。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节 CDC 数据同步、MW-2.3 Debezium CDC。
 * 对应 Debezium 捕获的 MySQL binlog 操作类型，SNAPSHOT 为初始全量快照阶段产生。</p>
 */
public enum CdcOperation {

    /** 插入操作 — 对应 binlog WRITE_ROWS_EVENT */
    INSERT,

    /** 更新操作 — 对应 binlog UPDATE_ROWS_EVENT */
    UPDATE,

    /** 删除操作 — 对应 binlog DELETE_ROWS_EVENT */
    DELETE,

    /** 快照操作 — Debezium 初始全量快照阶段产生 */
    SNAPSHOT;

    /**
     * 判断是否为快照操作。
     *
     * @return true=快照操作
     */
    public boolean isSnapshot() {
        return this == SNAPSHOT;
    }

    /**
     * 判断是否包含变更后数据（after 镜像）。
     *
     * <p>INSERT 和 UPDATE 有 after 镜像；DELETE 无 after 镜像，仅 before。</p>
     *
     * @return true=存在 after 镜像
     */
    public boolean hasAfterImage() {
        return this == INSERT || this == UPDATE || this == SNAPSHOT;
    }
}
