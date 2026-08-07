package com.campusarrive.integration.cdc;

import com.campusarrive.integration.idempotent.IdempotentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * CDC 事件核心处理器 — 变更事件处理的编排中枢。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节 CDC 数据同步、MW-2.3 Debezium CDC。
 *
 * 处理流程：
 * <pre>
 *   1. 偏移量检查 — 事件偏移量 ≤ 当前已处理偏移量 → 跳过（断点续传幂等）
 *   2. 幂等判重 — eventId 已处理 → 跳过（同会话去重）
 *   3. 主数据映射 — student_id → id_card / card_id 翻译
 *      - 映射缺失 → 转死信（MAPPING_MISSING）
 *   4. 路由写入 — 按源表路由到教务/宿管/一卡通系统
 *   5. 重试退避 — 写入失败按 10s/30s/60s 阶梯重试，共 3 次
 *   6. 死信兜底 — 重试耗尽或不可重试异常 → 转死信
 *   7. 偏移量提交 — 成功后保存 binlog 偏移量（检查点）
 * </pre>
 *
 * <p>所有异常被捕获，主流程不被下游故障阻塞（返回 {@link CdcProcessResult#DEAD_LETTER}）。</p>
 */
public class CdcEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CdcEventHandler.class);

    /** 最大重试次数（不含首次尝试） */
    public static final int MAX_RETRIES = 3;

    /** 默认重试延迟阶梯（毫秒）：10s / 30s / 60s */
    public static final long[] DEFAULT_RETRY_DELAYS_MS = {10_000L, 30_000L, 60_000L};

    private final DataMappingService dataMappingService;
    private final CdcSinkService sinkService;
    private final CdcOffsetStore offsetStore;
    private final CdcDeadLetterStore deadLetterStore;
    private final CdcSyncMonitor monitor;
    private final IdempotentHandler idempotentHandler;
    private final long[] retryDelayMillis;
    private final Sleeper sleeper;

    /**
     * 生产构造器 — 使用默认重试延迟和 Thread.sleep。
     *
     * @param dataMappingService 主数据映射服务
     * @param sinkService        下游写入服务
     * @param offsetStore        偏移量存储
     * @param deadLetterStore    死信存储
     * @param monitor            同步监控器
     * @param idempotentHandler  幂等处理器
     */
    public CdcEventHandler(DataMappingService dataMappingService,
                           CdcSinkService sinkService,
                           CdcOffsetStore offsetStore,
                           CdcDeadLetterStore deadLetterStore,
                           CdcSyncMonitor monitor,
                           IdempotentHandler idempotentHandler) {
        this(dataMappingService, sinkService, offsetStore, deadLetterStore, monitor,
                idempotentHandler, DEFAULT_RETRY_DELAYS_MS, Thread::sleep);
    }

    /**
     * 全参数构造器 — 用于测试注入自定义延迟和 sleeper。
     *
     * @param retryDelayMillis 重试延迟阶梯（毫秒）
     * @param sleeper          线程睡眠函数（测试中可注入 no-op）
     */
    public CdcEventHandler(DataMappingService dataMappingService,
                           CdcSinkService sinkService,
                           CdcOffsetStore offsetStore,
                           CdcDeadLetterStore deadLetterStore,
                           CdcSyncMonitor monitor,
                           IdempotentHandler idempotentHandler,
                           long[] retryDelayMillis,
                           Sleeper sleeper) {
        this.dataMappingService = dataMappingService;
        this.sinkService = sinkService;
        this.offsetStore = offsetStore;
        this.deadLetterStore = deadLetterStore;
        this.monitor = monitor;
        this.idempotentHandler = idempotentHandler;
        this.retryDelayMillis = retryDelayMillis;
        this.sleeper = sleeper;
    }

    /**
     * 简化构造器 — 仅核心依赖，内部创建死信存储、监控器和幂等处理器。
     *
     * @param dataMappingService 主数据映射服务
     * @param sinkService        下游写入服务
     * @param offsetStore        偏移量存储
     */
    public CdcEventHandler(DataMappingService dataMappingService,
                           CdcSinkService sinkService,
                           CdcOffsetStore offsetStore) {
        this(dataMappingService, sinkService, offsetStore,
                new CdcDeadLetterStore(), new CdcSyncMonitor(),
                new IdempotentHandler());
    }

    /**
     * 处理一条 CDC 变更事件。
     *
     * <p>所有异常被捕获，不会向调用方抛出，保证主流程不被下游故障阻塞。</p>
     *
     * @param event CDC 变更事件
     * @return 处理结果
     */
    public CdcProcessResult handle(CdcChangeEvent event) {
        monitor.incrementTotal();
        try {
            return doHandle(event);
        } catch (Exception e) {
            log.error("[CdcEventHandler] 处理异常, 转死信: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            deadLetterStore.save(event, "UnexpectedException: " + e.getMessage());
            monitor.incrementFailure();
            monitor.incrementDeadLetter();
            return CdcProcessResult.DEAD_LETTER;
        }
    }

    /**
     * 实际处理逻辑（不捕获异常，由 {@link #handle} 统一兜底）。
     */
    private CdcProcessResult doHandle(CdcChangeEvent event) {
        String eventId = event.getEventId();
        log.info("[CdcEventHandler] 收到事件: eventId={}, table={}, operation={}",
                eventId, event.getSourceTable(), event.getOperation());

        // 1. 偏移量检查 — 断点续传幂等（跳过已处理的 binlog 位置）
        CdcOffset currentOffset = offsetStore.getCurrentOffset();
        if (currentOffset != null && event.getSourceOffset() != null
                && event.getSourceOffset().compareTo(currentOffset) <= 0) {
            log.info("[CdcEventHandler] 事件偏移量已处理, 跳过: eventId={}, eventOffset={}, currentOffset={}",
                    eventId, event.getSourceOffset(), currentOffset);
            monitor.incrementSkipped();
            return CdcProcessResult.SKIPPED_DUPLICATE;
        }

        // 2. 幂等判重（同会话去重）
        if (!idempotentHandler.tryMarkProcessed(eventId)) {
            log.info("[CdcEventHandler] 重复事件被幂等跳过: eventId={}", eventId);
            monitor.incrementSkipped();
            return CdcProcessResult.SKIPPED_DUPLICATE;
        }

        // 3. 主数据映射 — 翻译 student_id → id_card / card_id
        Map<String, Object> mappedRecord = applyMapping(event);
        if (mappedRecord == null) {
            log.warn("[CdcEventHandler] 主数据映射缺失, 转死信: eventId={}, table={}",
                    eventId, event.getSourceTable());
            deadLetterStore.save(event, "MappingMissing: student_id=" + extractStudentId(event));
            monitor.incrementFailure();
            monitor.incrementDeadLetter();
            return CdcProcessResult.MAPPING_MISSING;
        }

        // 4. 路由写入下游系统（含重试退避）
        CdcSinkService.SinkResult sinkResult = routeToSinkWithRetry(event, mappedRecord);

        // 5. 处理结果
        switch (sinkResult) {
            case SUCCESS:
                // 6. 成功后提交偏移量（检查点）
                if (event.getSourceOffset() != null) {
                    offsetStore.saveOffset(event.getSourceOffset());
                }
                monitor.incrementSuccess();
                log.info("[CdcEventHandler] 事件处理成功: eventId={}", eventId);
                return CdcProcessResult.SUCCESS;

            case DEAD_LETTER:
                deadLetterStore.save(event, "SinkDeadLetter: " + event.getSourceTable());
                monitor.incrementFailure();
                monitor.incrementDeadLetter();
                log.error("[CdcEventHandler] 下游不可重试失败, 转死信: eventId={}", eventId);
                return CdcProcessResult.DEAD_LETTER;

            case RETRY:
            default:
                // 重试耗尽
                deadLetterStore.save(event, "RetryExhausted: table=" + event.getSourceTable());
                monitor.incrementFailure();
                monitor.incrementDeadLetter();
                log.error("[CdcEventHandler] 重试耗尽, 转死信: eventId={}", eventId);
                return CdcProcessResult.RETRY_EXHAUSTED;
        }
    }

    /**
     * 路由到下游系统并执行重试退避。
     *
     * <p>首次尝试 + 最多 {@value #MAX_RETRIES} 次重试，重试延迟阶梯 10s/30s/60s。
     * 返回 RETRY 表示重试耗尽，返回 DEAD_LETTER 表示不可重试失败。</p>
     */
    private CdcSinkService.SinkResult routeToSinkWithRetry(CdcChangeEvent event,
                                                            Map<String, Object> mappedRecord) {
        CdcSinkService.SinkResult result = CdcSinkService.SinkResult.RETRY;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            long start = System.currentTimeMillis();
            try {
                result = routeToSink(event, mappedRecord);
            } catch (Exception e) {
                log.warn("[CdcEventHandler] 下游写入异常 (attempt={}): {}", attempt, e.getMessage());
                result = CdcSinkService.SinkResult.RETRY;
            }
            long latency = System.currentTimeMillis() - start;
            monitor.recordLatency(latency);

            if (result == CdcSinkService.SinkResult.SUCCESS) {
                return result;
            }
            if (result == CdcSinkService.SinkResult.DEAD_LETTER) {
                return result;
            }

            // RETRY — 非最后一次尝试则睡眠退避
            if (attempt < MAX_RETRIES) {
                long delay = retryDelayMillis[Math.min(attempt, retryDelayMillis.length - 1)];
                log.warn("[CdcEventHandler] 下游写入失败, 第{}次重试 (延迟{}ms): table={}",
                        attempt + 1, delay, event.getSourceTable());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[CdcEventHandler] 重试等待被中断, 转死信: eventId={}", event.getEventId());
                    return CdcSinkService.SinkResult.DEAD_LETTER;
                }
            }
        }

        // 所有重试耗尽
        return CdcSinkService.SinkResult.RETRY;
    }

    /**
     * 按源表路由到对应的下游系统。
     */
    private CdcSinkService.SinkResult routeToSink(CdcChangeEvent event,
                                                   Map<String, Object> mappedRecord) {
        String table = event.getSourceTable();
        if (table == null || table.isEmpty()) {
            log.warn("[CdcEventHandler] 源表名为空, 转死信");
            return CdcSinkService.SinkResult.DEAD_LETTER;
        }

        if (table.startsWith("dorm_")) {
            return sinkService.sinkToDormSystem(event, mappedRecord);
        }
        if (table.startsWith("card_")) {
            return sinkService.sinkToCardSystem(event, mappedRecord);
        }
        // 默认路由到教务系统（checkin_* / student_* 等）
        return sinkService.sinkToEduSystem(event, mappedRecord);
    }

    /**
     * 应用主数据映射，将 student_id 翻译为 id_card 和 card_id。
     *
     * @param event CDC 事件
     * @return 映射后的记录，映射缺失时返回 null
     */
    private Map<String, Object> applyMapping(CdcChangeEvent event) {
        Map<String, Object> image = event.getEffectiveImage();
        if (image == null) {
            return new HashMap<>();
        }

        Map<String, Object> mapped = new HashMap<>(image);
        Object studentIdObj = image.get("student_id");

        // 无 student_id 字段的记录无需映射，直接透传
        if (studentIdObj == null) {
            return mapped;
        }

        String studentId = studentIdObj.toString();
        var mapping = dataMappingService.getMapping(studentId);
        if (mapping.isEmpty()) {
            log.warn("[CdcEventHandler] 映射缺失: student_id={}", studentId);
            return null;
        }

        DataMappingService.MappingEntry entry = mapping.get();
        if (entry.idCard() != null) {
            mapped.put("id_card", entry.idCard());
        }
        if (entry.cardId() != null) {
            mapped.put("card_id", entry.cardId());
        }
        return mapped;
    }

    /**
     * 从事件中提取 student_id。
     */
    private String extractStudentId(CdcChangeEvent event) {
        Map<String, Object> image = event.getEffectiveImage();
        if (image != null) {
            Object id = image.get("student_id");
            if (id != null) {
                return id.toString();
            }
        }
        return "unknown";
    }

    /**
     * 获取监控器（供外部读取统计）。
     *
     * @return 同步监控器
     */
    public CdcSyncMonitor getMonitor() {
        return monitor;
    }

    /**
     * 获取死信存储（供外部查询/补偿）。
     *
     * @return 死信存储
     */
    public CdcDeadLetterStore getDeadLetterStore() {
        return deadLetterStore;
    }

    // ——— 枚举与接口 ———

    /**
     * CDC 事件处理结果枚举。
     */
    public enum CdcProcessResult {
        /** 处理成功 */
        SUCCESS,
        /** 跳过（重复事件或偏移量已处理） */
        SKIPPED_DUPLICATE,
        /** 重试耗尽，已转死信 */
        RETRY_EXHAUSTED,
        /** 主数据映射缺失，已转死信 */
        MAPPING_MISSING,
        /** 不可重试失败或异常，已转死信 */
        DEAD_LETTER
    }

    /**
     * 线程睡眠函数接口 — 用于测试注入 no-op sleeper。
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
