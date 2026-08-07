package com.campusarrive.gateway.filter;

import com.campusarrive.gateway.testsupport.JwtTestHelper;
import com.campusarrive.gateway.testsupport.MockRouteConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * CT-MW-003：限流触发契约测试。
 *
 * <p>规格来源：FR-04-03 / API 设计文档 — 按接口与租户限流，超阈值返回 429，
 * 附带 Retry-After 头与业务码 90001。</p>
 *
 * <p>本测试类通过 {@code @TestPropertySource} 标记属性获得独立 Spring 上下文，
 * 使其限流器实例与其他集成测试隔离；每个用例前清空桶状态，保证可重复。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MockRouteConfiguration.class)
@TestPropertySource(properties = "gateway.test-scope=rate-limit-isolation")
@DisplayName("CT-MW-003: 网关限流触发")
class RateLimitFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private final JwtTestHelper jwtHelper = new JwtTestHelper();

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
        // 每个用例前清空限流桶，保证桶满初始状态
        rateLimitFilter.clearAll();
    }

    @Test
    @DisplayName("限流阈值内请求通过")
    void testAllowWithinLimit() {
        // Arrange：/api/v1/ai 桶容量 2，携带合法令牌

        // Act & Assert：前 2 次请求均通过
        for (int i = 0; i < 2; i++) {
            webTestClient.get().uri("/api/v1/ai/chat")
                    .header("Authorization", "Bearer " + jwtHelper.validToken())
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("超限请求返回 429")
    void testRejectOverLimit() {
        // Arrange：连续请求直至超限

        // Act：前 2 次通过（桶容量 2）
        for (int i = 0; i < 2; i++) {
            webTestClient.get().uri("/api/v1/ai/chat")
                    .header("Authorization", "Bearer " + jwtHelper.validToken())
                    .exchange()
                    .expectStatus().isOk();
        }

        // Assert：第 3 次超限返回 429 + 业务码 90001
        webTestClient.get().uri("/api/v1/ai/chat")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.code").isEqualTo(90001);
    }

    @Test
    @DisplayName("429 响应包含 Retry-After 头")
    void testRetryAfterHeader() {
        // Arrange：耗尽令牌
        for (int i = 0; i < 2; i++) {
            webTestClient.get().uri("/api/v1/ai/chat")
                    .header("Authorization", "Bearer " + jwtHelper.validToken())
                    .exchange()
                    .expectStatus().isOk();
        }

        // Act & Assert：超限响应含 Retry-After 头且为正整数
        webTestClient.get().uri("/api/v1/ai/chat")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader()
                .valueEquals("Retry-After", "1");
    }
}
