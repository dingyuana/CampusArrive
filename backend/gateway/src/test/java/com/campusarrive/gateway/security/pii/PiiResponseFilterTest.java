package com.campusarrive.gateway.security.pii;

import com.campusarrive.gateway.testsupport.MockRouteConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-SEC-001：接口 PII 脱敏契约测试。
 *
 * <p>规格来源：FR-03-07 / SCS-CA-2026-09 第 3.2 节 PII 五层防护第 4 层 —
 * API 响应中 L2/L3 字段必须脱敏返回，网关响应过滤器自动执行脱敏策略。
 * 响应不含明文 PII。</p>
 *
 * <p>本测试包含两个层次：
 * <ul>
 *   <li>单元层：直接测试 {@link PiiResponseFilter#maskPiiInJson} 方法，
 *       覆盖扁平/嵌套/数组 JSON 结构及异常场景。</li>
 *   <li>契约层：通过 {@link WebTestClient} 发送实际请求，验证网关返回的
 *       JSON 响应中 PII 字段已被脱敏，非 PII 字段保持不变。</li>
 * </ul></p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MockRouteConfiguration.class)
@DisplayName("CT-SEC-001：接口 PII 脱敏")
class PiiResponseFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    private PiiResponseFilter piiResponseFilter;

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
        piiResponseFilter = new PiiResponseFilter();
    }

    // ─── 单元层：maskPiiInJson 直接测试 ─────────────────────────

    @Nested
    @DisplayName("JSON 脱敏逻辑")
    class JsonMaskingLogic {

        @Test
        @DisplayName("扁平 JSON 中 PII 字段被脱敏")
        void testFlatJsonPiiMasked() {
            String json = "{\"idCard\":\"110101199001011234\",\"phone\":\"13812345678\"}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("110***********1234");
            assertThat(result).contains("138****5678");
            assertThat(result).doesNotContain("110101199001011234");
            assertThat(result).doesNotContain("13812345678");
        }

        @Test
        @DisplayName("嵌套 JSON 中 PII 字段被脱敏")
        void testNestedJsonPiiMasked() {
            String json = "{\"student\":{\"idCard\":\"110101199001011234\",\"name\":\"张三丰\"}}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("110***********1234");
            assertThat(result).contains("张*丰");
            assertThat(result).doesNotContain("110101199001011234");
        }

        @Test
        @DisplayName("JSON 数组中 PII 字段被脱敏")
        void testArrayJsonPiiMasked() {
            String json = "[{\"idCard\":\"110101199001011234\"},{\"phone\":\"13812345678\"}]";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("110***********1234");
            assertThat(result).contains("138****5678");
        }

        @Test
        @DisplayName("多种 PII 类型同时脱敏")
        void testMultiplePiiTypesMasked() {
            String json = "{\"idCard\":\"110101199001011234\","
                    + "\"phone\":\"13812345678\","
                    + "\"name\":\"张三丰\","
                    + "\"email\":\"zhangsan@example.com\"}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("110***********1234");
            assertThat(result).contains("138****5678");
            assertThat(result).contains("张*丰");
            assertThat(result).contains("z***@example.com");
        }

        @Test
        @DisplayName("非 PII 字段保持不变")
        void testNonPiiFieldsUnchanged() {
            String json = "{\"service\":\"checkin-service\",\"status\":\"completed\"}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("checkin-service");
            assertThat(result).contains("completed");
        }

        @Test
        @DisplayName("snake_case 字段名 PII 被脱敏")
        void testSnakeCaseFieldMasked() {
            String json = "{\"id_card\":\"110101199001011234\",\"phone\":\"13812345678\"}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("110***********1234");
            assertThat(result).contains("138****5678");
        }

        @Test
        @DisplayName("数字类型 PII 字段值不被处理（仅字符串脱敏）")
        void testNumericPiiNotMasked() {
            String json = "{\"phone\":13812345678}";
            String result = piiResponseFilter.maskPiiInJson(json);

            // 数字类型不脱敏（仅字符串值脱敏）
            assertThat(result).contains("13812345678");
        }

        @Test
        @DisplayName("无效 JSON 原样返回")
        void testInvalidJsonReturnedAsIs() {
            String json = "not a json {{{";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("空 JSON 对象保持不变")
        void testEmptyJsonObject() {
            String json = "{}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).isEqualTo("{}");
        }

        @Test
        @DisplayName("无 PII 字段的复杂 JSON 保持不变")
        void testComplexJsonNoPiiUnchanged() {
            String json = "{\"service\":\"checkin-service\","
                    + "\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}";
            String result = piiResponseFilter.maskPiiInJson(json);

            assertThat(result).contains("checkin-service");
            assertThat(result).contains("\"id\":1");
            assertThat(result).contains("\"id\":2");
        }
    }

    // ─── 契约层：WebTestClient 端到端验证 ───────────────────────

    @Nested
    @DisplayName("网关响应脱敏契约")
    class GatewayResponseContract {

        @Test
        @DisplayName("API 响应中身份证号已脱敏")
        void testIdCardMaskedInResponse() {
            webTestClient.get().uri("/api/v1/parent/bind")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.idCard").isEqualTo("110***********1234")
                    .jsonPath("$.idCard").value(value ->
                            assertThat(String.valueOf(value)).doesNotContain("110101199001011234"));
        }

        @Test
        @DisplayName("API 响应中手机号已脱敏")
        void testPhoneMaskedInResponse() {
            webTestClient.get().uri("/api/v1/parent/bind")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.phone").isEqualTo("138****5678")
                    .jsonPath("$.phone").value(value ->
                            assertThat(String.valueOf(value)).doesNotContain("13812345678"));
        }

        @Test
        @DisplayName("API 响应中姓名已脱敏")
        void testNameMaskedInResponse() {
            webTestClient.get().uri("/api/v1/parent/bind")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("张*丰")
                    .jsonPath("$.name").value(value ->
                            assertThat(String.valueOf(value)).doesNotContain("张三丰"));
        }

        @Test
        @DisplayName("API 响应中邮箱已脱敏")
        void testEmailMaskedInResponse() {
            webTestClient.get().uri("/api/v1/parent/bind")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.email").isEqualTo("z***@example.com")
                    .jsonPath("$.email").value(value ->
                            assertThat(String.valueOf(value)).doesNotContain("zhangsan@example.com"));
        }

        @Test
        @DisplayName("非 PII 字段在响应中保持不变")
        void testNonPiiFieldsPreservedInResponse() {
            webTestClient.get().uri("/api/v1/parent/bind")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.service").isEqualTo("parent-service")
                    .jsonPath("$.request_id").isNotEmpty();
        }
    }
}
