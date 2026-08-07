package com.campusarrive.integration.config;

import com.campusarrive.integration.cdc.BatchSyncService;
import com.campusarrive.integration.cdc.CdcDeadLetterStore;
import com.campusarrive.integration.cdc.CdcEventHandler;
import com.campusarrive.integration.cdc.CdcOffsetStore;
import com.campusarrive.integration.cdc.CdcSinkService;
import com.campusarrive.integration.cdc.CdcSyncMonitor;
import com.campusarrive.integration.cdc.DataMappingService;
import com.campusarrive.integration.idempotent.IdempotentHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CDC 数据同步组件配置。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6 节 CDC 数据同步、MW-2.3 Debezium CDC。
 *
 * 集中声明 CDC 相关 Bean：主数据映射、偏移量存储、下游写入、死信存储、
 * 同步监控、事件处理器、批量同步服务。
 *
 * <p>通过 {@code @ConditionalOnProperty} 控制是否激活 CDC 模块：
 * <ul>
 *   <li>生产环境：{@code campusarrive.cdc.enabled=true}（默认），所有 Bean 注册</li>
 *   <li>测试环境：{@code campusarrive.cdc.enabled=false}，不注册 CDC Bean，
 *       测试类直接构造对象，不依赖 Spring 容器</li>
 * </ul></p>
 */
@Configuration
@ConditionalOnProperty(name = "campusarrive.cdc.enabled", havingValue = "true", matchIfMissing = true)
public class CdcConfig {

    // ================================================================
    // 主数据映射
    // ================================================================

    /**
     * 主数据映射服务 Bean。
     *
     * <p>内存版实现（ConcurrentHashMap）。生产环境应替换为 MySQL id_mapping 表实现。</p>
     *
     * @return 主数据映射服务
     */
    @Bean
    public DataMappingService dataMappingService() {
        return new DataMappingService();
    }

    // ================================================================
    // 偏移量存储
    // ================================================================

    /**
     * CDC 偏移量存储 Bean。
     *
     * <p>内存版实现，重启后偏移量丢失。生产环境应替换为 Redis / MySQL 持久化实现。</p>
     *
     * @return 偏移量存储
     */
    @Bean
    public CdcOffsetStore cdcOffsetStore() {
        CdcOffsetStore store = new CdcOffsetStore.InMemoryCdcOffsetStore();
        store.loadOffset();
        return store;
    }

    // ================================================================
    // 死信存储
    // ================================================================

    /**
     * CDC 死信存储 Bean。
     *
     * <p>内存版实现。生产环境应替换为 MySQL / Redis 持久化实现。</p>
     *
     * @return 死信存储
     */
    @Bean
    public CdcDeadLetterStore cdcDeadLetterStore() {
        return new CdcDeadLetterStore();
    }

    // ================================================================
    // 同步监控
    // ================================================================

    /**
     * CDC 同步监控器 Bean。
     *
     * @return 同步监控器
     */
    @Bean
    public CdcSyncMonitor cdcSyncMonitor() {
        return new CdcSyncMonitor();
    }

    // ================================================================
    // 下游写入服务
    // ================================================================

    /**
     * CDC 下游写入服务 Bean。
     *
     * @param objectMapper Jackson ObjectMapper（Spring Boot 自动配置）
     * @return 下游写入服务
     */
    @Bean
    public CdcSinkService cdcSinkService(ObjectMapper objectMapper) {
        return new CdcSinkService(objectMapper);
    }

    // ================================================================
    // 事件处理器
    // ================================================================

    /**
     * CDC 事件处理器 Bean — CDC 数据同步的核心编排器。
     *
     * @param dataMappingService 主数据映射服务
     * @param sinkService        下游写入服务
     * @param offsetStore        偏移量存储
     * @param deadLetterStore    死信存储
     * @param monitor            同步监控器
     * @param idempotentHandler  幂等处理器（复用事件消费的 @Component）
     * @return CDC 事件处理器
     */
    @Bean
    public CdcEventHandler cdcEventHandler(DataMappingService dataMappingService,
                                           CdcSinkService sinkService,
                                           CdcOffsetStore offsetStore,
                                           CdcDeadLetterStore deadLetterStore,
                                           CdcSyncMonitor monitor,
                                           IdempotentHandler idempotentHandler) {
        return new CdcEventHandler(dataMappingService, sinkService, offsetStore,
                deadLetterStore, monitor, idempotentHandler);
    }

    // ================================================================
    // 批量同步服务
    // ================================================================

    /**
     * 批量同步服务 Bean — 全量初始化与增量补数据。
     *
     * @param eventHandler CDC 事件处理器
     * @return 批量同步服务
     */
    @Bean
    public BatchSyncService batchSyncService(CdcEventHandler eventHandler) {
        return new BatchSyncService(eventHandler);
    }
}
