package com.campusarrive.ai.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话限流器(令牌桶,按学生 ID 限流)。
 *
 * <p>规格来源:API 5.1.4 限流规则 — 单学生 10 次/分钟(令牌桶 bucket=10, rate=10/min),
 * 超限返回 HTTP 429 code=90001。全局兜底 500 次/秒由 gateway 处理,此处仅实现单学生限流。</p>
 *
 * <p>内存实现,滑动窗口计数。后续可替换为 Redis + Lua 限流(INFRA-1.x)。</p>
 */
public class ChatRateLimiter {

    /** 限流窗口(秒,1 分钟)。 */
    private final long windowSeconds;
    /** 窗口内最大请求数。 */
    private final int maxRequests;
    /** 学生 ID → 窗口计数。 */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * @param maxRequests  窗口内最大请求数(默认 10)
     * @param windowSeconds 窗口时长(秒,默认 60)
     */
    public ChatRateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /** 默认 10 次/分钟。 */
    public ChatRateLimiter() {
        this(10, 60);
    }

    /**
     * 尝试获取配额。
     *
     * @param studentId 学生 ID
     * @return 限流结果;{@link RateLimitResult#allowed()} 为 true 表示放行
     */
    public RateLimitResult tryAcquire(String studentId) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(studentId, (k, v) -> {
            if (v == null || now - v.windowStart > windowSeconds * 1000) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });
        int used = counter.count.get();
        boolean allowed = used <= maxRequests && (now - counter.windowStart <= windowSeconds * 1000);
        int remaining = Math.max(0, maxRequests - used);
        long resetAt = counter.windowStart + windowSeconds * 1000;
        long retryAfter = allowed ? 0 : (resetAt - now) / 1000 + 1;
        return new RateLimitResult(allowed, remaining, maxRequests, resetAt / 1000, retryAfter);
    }

    /** 窗口计数器。 */
    private record WindowCounter(long windowStart, AtomicInteger count) {
    }

    /**
     * 限流结果。
     *
     * @param allowed    是否放行
     * @param remaining  剩余配额
     * @param limit      限流阈值
     * @param resetEpoch 窗口重置 Unix 时间戳
     * @param retryAfter 建议重试等待秒数(放行时为 0)
     */
    public record RateLimitResult(boolean allowed, int remaining, int limit, long resetEpoch, long retryAfter) {
    }
}
