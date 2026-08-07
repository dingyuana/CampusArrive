package com.campusarrive.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌桶限流器实现。
 *
 * <p>规格来源：FR-04-03 / API 设计文档 — 令牌桶算法（bucket=容量, rate=补充速率）。
 * 不同 key 互不影响；令牌按时间线性补充，补充至上限后不再增长。</p>
 *
 * <p>线程安全：每个 {@link BucketState} 通过自身监视器同步，保证 tryAcquire 原子性。</p>
 */
@Slf4j
public class TokenBucketRateLimiter {

    private final int bucketSize;
    private final long refillRatePerMinute;
    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    /**
     * @param bucketSize           桶容量（最大令牌数）
     * @param refillRatePerMinute  每分钟补充令牌数
     */
    public TokenBucketRateLimiter(int bucketSize, long refillRatePerMinute) {
        this.bucketSize = bucketSize;
        this.refillRatePerMinute = refillRatePerMinute;
    }

    /**
     * 尝试为指定 key 获取一个令牌。
     *
     * @param key 限流维度键（如 "ai:student:STU20260001"）
     * @return 获取成功返回 true，桶空返回 false
     */
    public boolean tryAcquire(String key) {
        BucketState state = buckets.computeIfAbsent(key, k -> new BucketState(bucketSize));
        synchronized (state) {
            refill(state);
            if (state.tokens >= 1.0) {
                state.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /**
     * 查询指定 key 当前可用令牌数（含补充）。
     *
     * @param key 限流维度键
     * @return 可用令牌数（浮点，桶满为 bucketSize）
     */
    public double getAvailableTokens(String key) {
        BucketState state = buckets.get(key);
        if (state == null) {
            return bucketSize;
        }
        synchronized (state) {
            refill(state);
            return state.tokens;
        }
    }

    /**
     * 清空所有桶状态（测试与运维用途）。
     */
    public void clear() {
        buckets.clear();
    }

    private void refill(BucketState state) {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - state.lastRefillTimestamp;
        if (elapsedMillis > 0 && refillRatePerMinute > 0) {
            // 补充令牌 = 经过秒数 × (每分钟速率 / 60)
            double tokensToAdd = (elapsedMillis / 1000.0) * (refillRatePerMinute / 60.0);
            state.tokens = Math.min(bucketSize, state.tokens + tokensToAdd);
            state.lastRefillTimestamp = now;
        }
    }

    public int getBucketSize() {
        return bucketSize;
    }

    public long getRefillRatePerMinute() {
        return refillRatePerMinute;
    }

    /**
     * 桶状态：当前令牌数与上次补充时间戳。
     */
    static class BucketState {
        double tokens;
        long lastRefillTimestamp;

        BucketState(double tokens) {
            this.tokens = tokens;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }
    }
}
