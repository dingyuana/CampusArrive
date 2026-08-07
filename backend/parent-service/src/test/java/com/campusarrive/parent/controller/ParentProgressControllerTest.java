package com.campusarrive.parent.controller;

import com.campusarrive.parent.service.ParentJwtService;
import com.campusarrive.parent.service.ProgressStore;
import com.campusarrive.parent.service.TokenRevocationStore;
import com.campusarrive.parent.service.VerificationCodeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * CT-PRG-001~006：报到进度查询契约测试。
 *
 * <p>规格来源：FR-03-03 / API 6.2 节。</p>
 *
 * <p>测试清单：
 * <ul>
 *   <li>CT-PRG-001：有效令牌查询进度成功</li>
 *   <li>CT-PRG-002：无 Authorization 头返回 401</li>
 *   <li>CT-PRG-003：无效令牌返回 401</li>
 *   <li>CT-PRG-004：已吊销令牌返回 401</li>
 *   <li>CT-PRG-005：进度数据脱敏 — 姓名脱敏、不含敏感字段</li>
 *   <li>CT-PRG-006：报到完成学生返回 completed 状态</li>
 * </ul></p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("CT-PRG-001~006：报到进度查询契约")
class ParentProgressControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ParentJwtService parentJwtService;

    @Autowired
    private TokenRevocationStore tokenRevocationStore;

    @Autowired
    private ProgressStore progressStore;

    @SpyBean
    private VerificationCodeService verificationCodeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 预登记手机号（学生 STU20260001）。 */
    private static final String PRE_REGISTERED_PHONE = "13812345678";

    /** 预登记手机号（学生 STU20260002，报到完成）。 */
    private static final String COMPLETED_PHONE = "13987654321";

    /** 测试用固定验证码。 */
    private static final String FIXED_CODE = "123456";

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
        doReturn(FIXED_CODE).when(verificationCodeService).generateCode();
        verificationCodeService.clearAll();
        tokenRevocationStore.clearAll();
    }

    // ─── CT-PRG-001：有效令牌查询进度 ─────────────────────────

    @Nested
    @DisplayName("CT-PRG-001：有效令牌查询进度")
    class ValidTokenQuery {

        @Test
        @DisplayName("有效令牌返回进度数据 code=0")
        void testValidTokenReturnsProgress() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(0)
                    .jsonPath("$.data.student_id").isEqualTo("STU20260001")
                    .jsonPath("$.data.student_name_masked").isNotEmpty()
                    .jsonPath("$.data.arrival_status").isNotEmpty()
                    .jsonPath("$.data.steps").isArray()
                    .jsonPath("$.data.steps.length()").isEqualTo(4)
                    .jsonPath("$.data.total_steps").isEqualTo(4);
        }

        @Test
        @DisplayName("进度包含 4 个环节且环节代码正确")
        void testProgressContainsFourSteps() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode steps = root.path("data").path("steps");

            assertThat(steps.isArray()).isTrue();
            assertThat(steps).hasSize(4);
            assertThat(steps.get(0).path("step_code").asText()).isEqualTo("checkin_success");
            assertThat(steps.get(1).path("step_code").asText()).isEqualTo("payment_completed");
            assertThat(steps.get(2).path("step_code").asText()).isEqualTo("verified_success");
            assertThat(steps.get(3).path("step_code").asText()).isEqualTo("checkin_completed");
        }

        @Test
        @DisplayName("部分完成学生返回 arrived 状态")
        void testPartialCompletionReturnsArrived() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.arrival_status").isEqualTo("arrived")
                    .jsonPath("$.data.arrival_status_display").isEqualTo("已到校")
                    .jsonPath("$.data.completed_steps").isEqualTo(2)
                    .jsonPath("$.data.progress_percent").isEqualTo(50);
        }
    }

    // ─── CT-PRG-002 & 003：鉴权失败 ───────────────────────────

    @Nested
    @DisplayName("CT-PRG-002/003：鉴权失败")
    class AuthFailure {

        @Test
        @DisplayName("无 Authorization 头返回 401")
        void testNoAuthHeaderReturns401() {
            webTestClient.get().uri("/api/v1/parent/progress")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40100);
        }

        @Test
        @DisplayName("Authorization 格式错误返回 401")
        void testMalformedAuthHeaderReturns401() {
            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "InvalidFormat")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40100);
        }

        @Test
        @DisplayName("无效令牌返回 401")
        void testInvalidTokenReturns401() {
            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer invalid.token.here")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40100);
        }

        @Test
        @DisplayName("令牌不含 student_id 返回 401")
        void testTokenWithoutStudentIdReturns401() {
            // 签发不含 student_id 的令牌
            String token = parentJwtService.issueToken("", "13812345678");

            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40100);
        }
    }

    // ─── CT-PRG-004：令牌吊销 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PRG-004：已吊销令牌")
    class RevokedToken {

        @Test
        @DisplayName("已吊销令牌查询进度返回 401")
        void testRevokedTokenReturns401() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            // 吊销令牌
            webTestClient.post().uri("/api/v1/parent/revoke")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(0);

            // 使用已吊销令牌查询进度
            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40101);
        }
    }

    // ─── CT-PRG-005：脱敏验证 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PRG-005：进度数据脱敏")
    class Desensitization {

        @Test
        @DisplayName("学生姓名脱敏（张三丰 → 张*丰）")
        void testStudentNameMasked() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.student_name_masked").isEqualTo("张*丰");
        }

        @Test
        @DisplayName("响应不含身份证号、手机号等敏感字段")
        void testNoSensitiveFieldsInResponse() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode data = objectMapper.readTree(responseBody).path("data");
            assertThat(data.has("id_card")).isFalse();
            assertThat(data.has("phone")).isFalse();
            assertThat(data.has("idCard")).isFalse();
            assertThat(data.has("student_name")).isFalse();
        }

        @Test
        @DisplayName("待办材料列表正确返回")
        void testPendingMaterialsReturned() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode materials = objectMapper.readTree(responseBody)
                    .path("data").path("pending_materials");
            assertThat(materials.isArray()).isTrue();
            assertThat(materials).hasSize(3);
        }

        @Test
        @DisplayName("已完成环节含完成日期，未完成环节日期为 null")
        void testCompletedStepsHaveDate() throws Exception {
            String token = obtainToken(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode steps = objectMapper.readTree(responseBody).path("data").path("steps");
            // 前两个环节已完成（签到 + 缴费）
            assertThat(steps.get(0).path("completed").asBoolean()).isTrue();
            assertThat(steps.get(0).path("completed_at").asText()).isNotEmpty();
            assertThat(steps.get(1).path("completed").asBoolean()).isTrue();
            assertThat(steps.get(1).path("completed_at").asText()).isNotEmpty();
            // 后两个环节未完成
            assertThat(steps.get(2).path("completed").asBoolean()).isFalse();
            assertThat(steps.get(2).path("completed_at").isNull()).isTrue();
            assertThat(steps.get(3).path("completed").asBoolean()).isFalse();
            assertThat(steps.get(3).path("completed_at").isNull()).isTrue();
        }
    }

    // ─── CT-PRG-006：报到完成状态 ─────────────────────────────

    @Nested
    @DisplayName("CT-PRG-006：报到完成状态")
    class CompletedStatus {

        @Test
        @DisplayName("全部环节完成的学生返回 completed 状态")
        void testCompletedStudentReturnsCompletedStatus() throws Exception {
            String token = obtainToken(COMPLETED_PHONE);

            webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.arrival_status").isEqualTo("completed")
                    .jsonPath("$.data.arrival_status_display").isEqualTo("报到完成")
                    .jsonPath("$.data.completed_steps").isEqualTo(4)
                    .jsonPath("$.data.total_steps").isEqualTo(4)
                    .jsonPath("$.data.progress_percent").isEqualTo(100);
        }

        @Test
        @DisplayName("报到完成学生无待办材料")
        void testCompletedStudentNoPendingMaterials() throws Exception {
            String token = obtainToken(COMPLETED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode materials = objectMapper.readTree(responseBody)
                    .path("data").path("pending_materials");
            assertThat(materials.isArray()).isTrue();
            assertThat(materials).isEmpty();
        }

        @Test
        @DisplayName("报到完成学生所有环节均已完成")
        void testCompletedStudentAllStepsCompleted() throws Exception {
            String token = obtainToken(COMPLETED_PHONE);

            String responseBody = webTestClient.get().uri("/api/v1/parent/progress")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode steps = objectMapper.readTree(responseBody).path("data").path("steps");
            for (int i = 0; i < steps.size(); i++) {
                assertThat(steps.get(i).path("completed").asBoolean())
                        .as("环节 %d 应已完成", i)
                        .isTrue();
                assertThat(steps.get(i).path("completed_at").asText())
                        .as("环节 %d 应有完成日期", i)
                        .isNotEmpty();
            }
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    /**
     * 通过绑定接口获取有效 JWT 令牌。
     *
     * @param phone 预登记手机号
     * @return JWT 令牌
     */
    private String obtainToken(String phone) throws Exception {
        // 1. 请求验证码
        webTestClient.post().uri("/api/v1/parent/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\":\"" + phone + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);

        // 2. 用验证码绑定
        String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\":\"" + phone + "\",\"verify_code\":\"" + FIXED_CODE + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("data").path("token").asText();
    }
}
