package com.campusarrive.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatRateLimiter} 单元测试。
 *
 * <p>规格来源:API 5.1.4 限流规则 — 单学生 10 次/分钟(令牌桶)。
 * 验证窗口内配额放行与超限拦截。</p>
 */
@DisplayName("UT-AI: 对话限流器")
class ChatRateLimiterTest {

    @Test
    @DisplayName("窗口内 10 次请求放行")
    void withinLimitAllowed() {
        ChatRateLimiter limiter = new ChatRateLimiter(10, 60);
        for (int i = 0; i < 10; i++) {
            ChatRateLimiter.RateLimitResult r = limiter.tryAcquire("STU001");
            assertTrue(r.allowed(), "第 " + (i + 1) + " 次应放行");
            assertTrue(r.remaining() >= 0, "剩余配额非负");
        }
    }

    @Test
    @DisplayName("超出 10 次拦截")
    void overLimitBlocked() {
        ChatRateLimiter limiter = new ChatRateLimiter(10, 60);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("STU001");
        }
        ChatRateLimiter.RateLimitResult r = limiter.tryAcquire("STU001");
        assertFalse(r.allowed(), "第 11 次应拦截");
        assertTrue(r.retryAfter() > 0, "应返回重试等待秒数");
    }

    @Test
    @DisplayName("不同学生独立计数")
    void differentStudentsIndependent() {
        ChatRateLimiter limiter = new ChatRateLimiter(2, 60);
        limiter.tryAcquire("STU001");
        limiter.tryAcquire("STU001");
        // STU001 已用完
        assertFalse(limiter.tryAcquire("STU001").allowed(), "STU001 第 3 次应拦截");
        // STU002 仍可用
        assertTrue(limiter.tryAcquire("STU002").allowed(), "STU002 第 1 次应放行");
    }

    @Test
    @DisplayName("限流响应头信息完整")
    void rateLimitHeadersComplete() {
        ChatRateLimiter limiter = new ChatRateLimiter(10, 60);
        ChatRateLimiter.RateLimitResult r = limiter.tryAcquire("STU001");
        assertTrue(r.limit() == 10, "limit 应为 10");
        assertTrue(r.remaining() == 9, "remaining 应为 9");
        assertTrue(r.resetEpoch() > 0, "resetEpoch 应为正数");
    }

    @Test
    @DisplayName("短窗口过期后重置")
    void shortWindowReset() throws InterruptedException {
        ChatRateLimiter limiter = new ChatRateLimiter(1, 1); // 1 次/1 秒
        assertTrue(limiter.tryAcquire("STU001").allowed(), "第 1 次放行");
        assertFalse(limiter.tryAcquire("STU001").allowed(), "第 2 次拦截");
        Thread.sleep(1100);
        assertTrue(limiter.tryAcquire("STU001").allowed(), "窗口过期后应重置放行");
    }
}
