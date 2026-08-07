package com.campusarrive.integration.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDC 下游写入服务 — 将变更数据同步到教务/宿管/一卡通系统。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.2 节下游同步、MW-2.3 Debezium CDC。
 *
 * 根据源表路由到对应的下游系统：
 * <ul>
 *   <li>checkin_* / student_* → 教务系统（sinkToEduSystem）</li>
 *   <li>dorm_* → 宿管系统（sinkToDormSystem）</li>
 *   <li>card_* → 一卡通系统（sinkToCardSystem）</li>
 * </ul>
 *
 * <p>每个 sink 方法返回 {@link SinkResult}：
 * <ul>
 *   <li>SUCCESS — 写入成功</li>
 *   <li>RETRY — 写入失败但可重试（网络抖动、临时不可用）</li>
 *   <li>DEAD_LETTER — 写入失败且不可重试（数据格式错误、业务校验失败）</li>
 * </ul>
 *
 * <p>内部追踪写入延迟（平均），供监控告警使用。</p>
 */
public class CdcSinkService {

    private static final Logger log = LoggerFactory.getLogger(CdcSinkService.class);

    private final ObjectMapper objectMapper;

    /** 累计写入延迟（毫秒） */
    private final AtomicLong totalWriteLatencyMs = new AtomicLong(0);

    /** 写入次数 */
    private final AtomicLong writeCount = new AtomicLong(0);

    public CdcSinkService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为 null");
    }

    /**
     * 写入教务系统。
     *
     * <p>将 CDC 变更同步到教务系统（学籍激活、教籍注册等）。</p>
     *
     * @param event        原始 CDC 事件
     * @param mappedRecord 映射后的记录（含翻译后的 id_card、card_id）
     * @return 写入结果
     */
    public SinkResult sinkToEduSystem(CdcChangeEvent event, Map<String, Object> mappedRecord) {
        return doSink("教务系统", "edu-system", event, mappedRecord);
    }

    /**
     * 写入宿管系统。
     *
     * <p>将 CDC 变更同步到宿管系统（床位确认、宿舍分配等）。</p>
     *
     * @param event        原始 CDC 事件
     * @param mappedRecord 映射后的记录
     * @return 写入结果
     */
    public SinkResult sinkToDormSystem(CdcChangeEvent event, Map<String, Object> mappedRecord) {
        return doSink("宿管系统", "dorm-system", event, mappedRecord);
    }

    /**
     * 写入一卡通系统。
     *
     * <p>将 CDC 变更同步到一卡通系统（制卡、账号开通等）。</p>
     *
     * @param event        原始 CDC 事件
     * @param mappedRecord 映射后的记录
     * @return 写入结果
     */
    public SinkResult sinkToCardSystem(CdcChangeEvent event, Map<String, Object> mappedRecord) {
        return doSink("一卡通系统", "card-system", event, mappedRecord);
    }

    /**
     * 执行实际写入逻辑。
     *
     * <p>当前为日志占位实现，生产环境应替换为 HTTP/RPC 调用下游系统 API。
     * 异常分类：
     * <ul>
     *   <li>网络异常 / 超时 → RETRY</li>
     *   <li>数据格式异常 / 业务校验失败 → DEAD_LETTER</li>
     * </ul></p>
     *
     * @param systemName   系统中文名
     * @param systemCode   系统代码
     * @param event        CDC 事件
     * @param mappedRecord 映射后记录
     * @return 写入结果
     */
    protected SinkResult doSink(String systemName, String systemCode,
                                CdcChangeEvent event, Map<String, Object> mappedRecord) {
        long start = System.currentTimeMillis();
        try {
            // 生产环境：调用下游系统 API（HTTP/RPC）
            // 此处为占位实现，仅记录日志
            String recordJson = objectMapper.writeValueAsString(mappedRecord);
            log.info("[CdcSinkService] 写入{}: system={}, table={}, operation={}, record={}",
                    systemName, systemCode, event.getSourceTable(), event.getOperation(), recordJson);

            long latency = System.currentTimeMillis() - start;
            recordWriteLatency(latency);
            return SinkResult.SUCCESS;

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recordWriteLatency(latency);
            // 序列化异常视为不可重试
            if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                log.error("[CdcSinkService] {}写入失败(不可重试): {}", systemName, e.getMessage());
                return SinkResult.DEAD_LETTER;
            }
            // 其他异常视为可重试
            log.warn("[CdcSinkService] {}写入失败(可重试): {}", systemName, e.getMessage());
            return SinkResult.RETRY;
        }
    }

    /**
     * 获取平均写入延迟（毫秒）。
     *
     * @return 平均延迟，无写入记录时返回 0
     */
    public long getAverageWriteLatencyMs() {
        long count = writeCount.get();
        return count > 0 ? totalWriteLatencyMs.get() / count : 0;
    }

    /**
     * 获取总写入次数。
     *
     * @return 写入次数
     */
    public long getWriteCount() {
        return writeCount.get();
    }

    private void recordWriteLatency(long millis) {
        totalWriteLatencyMs.addAndGet(millis);
        writeCount.incrementAndGet();
    }

    /**
     * 下游写入结果枚举。
     */
    public enum SinkResult {
        /** 写入成功 */
        SUCCESS,
        /** 写入失败，可重试 */
        RETRY,
        /** 写入失败，不可重试，转死信 */
        DEAD_LETTER
    }
}
