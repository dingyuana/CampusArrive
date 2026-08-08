package com.campusarrive.ai.mcp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 工具调用频率限制器（AID 7.3 频率限制）。
 *
 * <p>规格来源：FR-01-15 / FR-01-16。
 * 单新生每分钟工具调用不超过 10 次，防止异常刷接口。
 * 滑动窗口计数实现，与对话限流策略一致。</p>
 */
public class McpToolRateLimiter {

    /** 默认频率上限（次/分钟）。 */
    public static final int DEFAULT_MAX_PER_MINUTE = 10;
    /** 窗口大小（毫秒）。 */
    private static final long WINDOW_MS = 60_000L;

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public McpToolRateLimiter() {
        this(DEFAULT_MAX_PER_MINUTE);
    }

    public McpToolRateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    /**
     * 尝试获取调用许可。
     *
     * @param studentId 学生学号
     * @return true 表示允许调用；false 表示超限
     */
    public boolean tryAcquire(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(studentId, (k, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return w.count.get() <= maxPerMinute;
    }

    /** 获取当前窗口内已调用次数（测试用）。 */
    public int currentCount(String studentId) {
        Window w = windows.get(studentId);
        if (w == null || System.currentTimeMillis() - w.windowStart > WINDOW_MS) {
            return 0;
        }
        return w.count.get();
    }

    private record Window(long windowStart, AtomicInteger count) {
    }
}
