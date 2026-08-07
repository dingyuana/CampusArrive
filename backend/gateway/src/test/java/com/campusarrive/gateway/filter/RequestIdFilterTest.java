package com.campusarrive.gateway.filter;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 链路追踪请求 ID 过滤器测试。
 *
 * <p>规格来源：FR-04-01 — X-Request-Id 未提供时网关自动生成 UUID，已提供时保留。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MockRouteConfiguration.class)
@DisplayName("链路追踪：X-Request-Id 生成与保留")
class RequestIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    @DisplayName("未提供 X-Request-Id 时网关自动生成 UUID 并传递下游")
    void testGenerateRequestId() {
        // Arrange：白名单路径 /api/v1/parent/bind，无需鉴权，便于聚焦 request_id 逻辑

        // Act & Assert：下游回显的 request_id 应为合法 UUID
        webTestClient.get().uri("/api/v1/parent/bind")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.request_id").isNotEmpty()
                .jsonPath("$.request_id").value(value -> {
                    // 生成的应为标准 UUID 格式
                    assertThat(UUID.fromString(String.valueOf(value))).isNotNull();
                });
    }

    @Test
    @DisplayName("提供 X-Request-Id 时网关保留原值并传递下游")
    void testPreserveExistingRequestId() {
        // Arrange：自定义 request_id
        String customRequestId = "trace-abc-123-xyz";

        // Act & Assert：下游回显的 request_id 应与传入一致
        webTestClient.get().uri("/api/v1/parent/bind")
                .header(RequestIdFilter.REQUEST_ID_HEADER, customRequestId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.request_id").isEqualTo(customRequestId);
    }
}
