package com.campusarrive.gateway.route;

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
 * CT-MW-001：路由分发契约测试。
 *
 * <p>规格来源：FR-04-01 — 统一入口按路径与规则路由至后端服务，非法路由返回 404。
 * 验证各服务路径路由至正确后端（通过 Mock 后端回显服务名），未知路径返回 404。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MockRouteConfiguration.class)
@DisplayName("CT-MW-001: 网关路由分发")
class GatewayRouteTest {

    @Autowired
    private WebTestClient webTestClient;

    private final JwtTestHelper jwtHelper = new JwtTestHelper();

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    @DisplayName("/api/v1/checkin/** 路由至 checkin-service")
    void testRouteToCheckinService() {
        webTestClient.get().uri("/api/v1/checkin/progress")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("checkin-service");
    }

    @Test
    @DisplayName("/api/v1/ai/** 路由至 ai-service")
    void testRouteToAiService() {
        webTestClient.get().uri("/api/v1/ai/chat")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("ai-service");
    }

    @Test
    @DisplayName("/api/v1/parent/** 路由至 parent-service")
    void testRouteToParentService() {
        webTestClient.get().uri("/api/v1/parent/info")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("parent-service");
    }

    @Test
    @DisplayName("/api/v1/integration/** 路由至 integration-service")
    void testRouteToIntegrationService() {
        webTestClient.get().uri("/api/v1/integration/sync")
                .header("Authorization", "Bearer " + jwtHelper.validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("integration-service");
    }

    @Test
    @DisplayName("未知路径返回 404")
    void testUnknownRouteReturns404() {
        // Arrange：不存在的路径前缀

        // Act & Assert：无路由匹配返回 404
        webTestClient.get().uri("/api/v1/nonexistent/whatever")
                .exchange()
                .expectStatus().isNotFound();
    }
}
