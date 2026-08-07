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
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * CT-MW-002：鉴权拦截契约测试。
 *
 * <p>规格来源：FR-04-02 — 统一鉴权，无 Token 返回 401，过期/无效 Token 返回 401，
 * 合法 Token 通过并转发下游，白名单路径无需鉴权。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MockRouteConfiguration.class)
@DisplayName("CT-MW-002: 网关鉴权拦截")
class JwtAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    private final JwtTestHelper jwtHelper = new JwtTestHelper();

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    @DisplayName("无 Authorization 头返回 401")
    void testNoTokenReturns401() {
        // Arrange：非白名单路径，不携带令牌

        // Act & Assert
        webTestClient.get().uri("/api/v1/checkin/progress")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(40100);
    }

    @Test
    @DisplayName("过期令牌返回 401")
    void testExpiredTokenReturns401() {
        // Arrange
        String expiredToken = jwtHelper.expiredToken();

        // Act & Assert
        webTestClient.get().uri("/api/v1/checkin/progress")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(40100);
    }

    @Test
    @DisplayName("无效令牌返回 401")
    void testInvalidTokenReturns401() {
        // Arrange：签名错误的令牌
        String invalidToken = jwtHelper.invalidSignatureToken();

        // Act & Assert
        webTestClient.get().uri("/api/v1/checkin/progress")
                .header("Authorization", "Bearer " + invalidToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(40100);
    }

    @Test
    @DisplayName("合法令牌请求通过并转发下游")
    void testValidTokenPasses() {
        // Arrange
        String validToken = jwtHelper.validToken();

        // Act & Assert：通过鉴权，转发至下游 Mock，返回 200 并回显注入的角色
        webTestClient.get().uri("/api/v1/checkin/progress")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("checkin-service")
                .jsonPath("$.role").isEqualTo("student")
                .jsonPath("$.student_id").isEqualTo("STU20260001");
    }

    @Test
    @DisplayName("白名单路径无需鉴权直接放行")
    void testWhitelistPathNoAuth() {
        // Arrange：白名单路径 /api/v1/parent/bind，不携带令牌

        // Act & Assert：跳过鉴权，转发下游返回 200
        webTestClient.get().uri("/api/v1/parent/bind")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("parent-service");
    }
}
