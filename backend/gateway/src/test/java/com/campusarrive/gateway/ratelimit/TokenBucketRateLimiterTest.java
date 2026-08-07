package com.campusarrive.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT-MW-002：令牌桶限流器单元测试。
 *
 * <p>规格来源：FR-04-03 / API 设计文档 — 令牌桶算法准确性。
 * 验证桶容量内放行、桶空拒绝、令牌补充、不同 key 隔离。</p>
 */
@DisplayName("UT-MW-002: 令牌桶限流器")
class TokenBucketRateLimiterTest {

    private static final String KEY_A = "ai:student:STU20260001";
    private static final String KEY_B = "ai:student:STU20260002";

    @BeforeEach
    void setUp() {
        // 每个用例新建限流器，保证桶状态干净
    }

    @Test
    @DisplayName("桶容量内请求全部通过")
    void testAllowRequestsWithinBucketSize() {
        // Arrange：桶容量 5
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 600);

        // Act & Assert：连续 5 次均放行
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(KEY_A), "第 " + (i + 1) + " 次请求应在桶容量内放行");
        }
    }

    @Test
    @DisplayName("桶空时拒绝请求")
    void testRejectWhenBucketEmpty() {
        // Arrange：桶容量 5
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 600);

        // Act：耗尽令牌
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(KEY_A);
        }

        // Assert：第 6 次被拒
        boolean allowed = limiter.tryAcquire(KEY_A);
        assertFalse(allowed, "桶空时第 6 次请求应被拒绝");
    }

    @Test
    @DisplayName("等待后令牌补充可再次通过")
    void testTokenRefill() throws InterruptedException {
        // Arrange：桶容量 5，补充速率 600/分钟（10/秒）
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 600);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(KEY_A);
        }
        // 此时桶空
        assertFalse(limiter.tryAcquire(KEY_A), "耗尽后应被拒绝");

        // Act：等待 150ms，应补充约 1.5 个令牌（≥ 1）
        Thread.sleep(150);

        // Assert：可再次放行至少一次
        assertTrue(limiter.tryAcquire(KEY_A), "等待令牌补充后应可再次放行");
    }

    @Test
    @DisplayName("不同 key 互不影响")
    void testPerKeyIsolation() {
        // Arrange：桶容量 2
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 600);

        // Act：耗尽 key A
        assertTrue(limiter.tryAcquire(KEY_A));
        assertTrue(limiter.tryAcquire(KEY_A));
        assertFalse(limiter.tryAcquire(KEY_A), "key A 耗尽应拒绝");

        // Assert：key B 仍可正常使用
        assertTrue(limiter.tryAcquire(KEY_B), "key B 不应受 key A 影响");
        assertTrue(limiter.tryAcquire(KEY_B));
    }

    @Test
    @DisplayName("getAvailableTokens 返回剩余令牌数")
    void testGetAvailableTokens() {
        // Arrange
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 600);

        // Act：消耗 2 个
        limiter.tryAcquire(KEY_A);
        limiter.tryAcquire(KEY_A);

        // Assert：剩余约 3（补充速率高，短时间内补充极少，近似 3）
        double available = limiter.getAvailableTokens(KEY_A);
        assertEquals(3, (int) Math.floor(available), "消耗 2 个后剩余应为 3");
    }
}
