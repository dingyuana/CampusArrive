package com.campusarrive.parent.controller;

import com.campusarrive.parent.service.ParentJwtService;
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
 * CT-PAR-001~005：家长绑定鉴权契约测试。
 *
 * <p>规格来源：FR-03-01 / FR-03-02 / API 6.1 节。</p>
 *
 * <p>测试清单：
 * <ul>
 *   <li>CT-PAR-001：绑定接口 — 预登记手机号绑定成功，非预登记拒绝</li>
 *   <li>CT-PAR-002：验证码 — 有效/过期/错误验证码</li>
 *   <li>CT-PAR-003：限频防刷 — 同一手机号 60s 内重复请求返回 429</li>
 *   <li>CT-PAR-004：JWT 签发 — 令牌含 student_id，30 天有效期，不含敏感字段</li>
 *   <li>CT-PAR-005：令牌吊销 — 已吊销令牌访问被拒返回 401</li>
 * </ul></p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("CT-PAR-001~005：家长绑定鉴权契约")
class ParentBindControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ParentJwtService parentJwtService;

    @Autowired
    private TokenRevocationStore tokenRevocationStore;

    @SpyBean
    private VerificationCodeService verificationCodeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 预登记手机号。 */
    private static final String PRE_REGISTERED_PHONE = "13812345678";

    /** 未预登记手机号。 */
    private static final String UNREGISTERED_PHONE = "13700000000";

    /** 测试用固定验证码。 */
    private static final String FIXED_CODE = "123456";

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build();
        // 桩桩 generateCode 返回固定验证码，便于测试断言
        doReturn(FIXED_CODE).when(verificationCodeService).generateCode();
        // 重置验证码服务状态，确保测试间互不干扰
        verificationCodeService.clearAll();
        // 重置令牌吊销存储
        tokenRevocationStore.clearAll();
    }

    // ─── CT-PAR-001：绑定接口 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PAR-001：绑定接口")
    class BindEndpoint {

        @Test
        @DisplayName("预登记手机号 + 正确验证码绑定成功")
        void testPreRegisteredPhoneBindsSuccessfully() {
            // 1. 先请求验证码
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);

            // 2. 用验证码绑定
            webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(0)
                    .jsonPath("$.data.token").isNotEmpty()
                    .jsonPath("$.data.token_type").isEqualTo("Bearer")
                    .jsonPath("$.data.student_id").isEqualTo("STU20260001")
                    .jsonPath("$.data.student_name_masked").isNotEmpty();
        }

        @Test
        @DisplayName("非预登记手机号绑定被拒绝")
        void testUnregisteredPhoneRejected() {
            webTestClient.post().uri("/api/v1/parent/verify-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + UNREGISTERED_PHONE + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40003)
                    .jsonPath("$.message").value(msg ->
                            assertThat(msg).contains("未在预登记名单中"), String.class);
        }

        @Test
        @DisplayName("手机号格式不正确返回 400")
        void testInvalidPhoneFormat() {
            webTestClient.post().uri("/api/v1/parent/verify-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"123\"}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ─── CT-PAR-002：验证码校验 ───────────────────────────────

    @Nested
    @DisplayName("CT-PAR-002：验证码校验")
    class VerifyCodeValidation {

        @Test
        @DisplayName("错误验证码绑定失败")
        void testWrongCodeFails() {
            requestVerifyCode(PRE_REGISTERED_PHONE);

            webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"000000\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40004)
                    .jsonPath("$.message").value(msg ->
                            assertThat(msg).contains("验证码错误或已过期"), String.class);
        }

        @Test
        @DisplayName("未请求验证码直接绑定失败")
        void testNoCodeFails() {
            // 使用一个新的预登记手机号（未请求过验证码）
            webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"13987654321\",\"verify_code\":\"123456\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40004);
        }

        @Test
        @DisplayName("验证码为空返回校验错误")
        void testEmptyCodeRejected() {
            webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"\"}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ─── CT-PAR-003：限频防刷 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PAR-003：限频防刷")
    class RateLimiting {

        @Test
        @DisplayName("同一手机号 60s 内第二次请求验证码返回 429")
        void testRateLimitOnSecondRequest() {
            // 第一次请求成功
            webTestClient.post().uri("/api/v1/parent/verify-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(0);

            // 第二次请求被限频
            webTestClient.post().uri("/api/v1/parent/verify-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\"}")
                    .exchange()
                    .expectStatus().is4xxClientError()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(90004)
                    .jsonPath("$.message").value(msg ->
                            assertThat(msg).contains("频繁"), String.class);
        }
    }

    // ─── CT-PAR-004：JWT 签发 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PAR-004：JWT 签发")
    class JwtIssuance {

        @Test
        @DisplayName("令牌含 student_id 且有效期 30 天")
        void testTokenContainsStudentIdAndExpiry() throws Exception {
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            String token = root.path("data").path("token").asText();
            long expiresIn = root.path("data").path("expires_in").asLong();

            // 有效期 30 天 = 2592000 秒
            assertThat(expiresIn).isEqualTo(2592000L);

            // 解析令牌验证 student_id
            var claims = parentJwtService.parseToken(token);
            assertThat(claims.get("student_id", String.class)).isEqualTo("STU20260001");
        }

        @Test
        @DisplayName("令牌不含敏感字段（手机号/姓名/身份证号）")
        void testTokenNoSensitiveFields() throws Exception {
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            String token = root.path("data").path("token").asText();

            var claims = parentJwtService.parseToken(token);
            assertThat(parentJwtService.containsSensitiveFields(claims)).isFalse();
            assertThat(claims.containsKey("phone")).isFalse();
            assertThat(claims.containsKey("name")).isFalse();
            assertThat(claims.containsKey("id_card")).isFalse();
        }

        @Test
        @DisplayName("令牌含 role=parent 与 scope=parent:read")
        void testTokenRoleAndScope() throws Exception {
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);

            String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            String token = root.path("data").path("token").asText();

            var claims = parentJwtService.parseToken(token);
            assertThat(claims.get("role", String.class)).isEqualTo("parent");
            assertThat(claims.get("scope", String.class)).isEqualTo("parent:read");
        }

        @Test
        @DisplayName("响应含学生脱敏姓名")
        void testResponseContainsMaskedName() {
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);

            webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.student_name_masked").isEqualTo("张*丰");
        }
    }

    // ─── CT-PAR-005：令牌吊销 ─────────────────────────────────

    @Nested
    @DisplayName("CT-PAR-005：令牌吊销")
    class TokenRevocation {

        @Test
        @DisplayName("已吊销令牌访问被拒返回 401")
        void testRevokedTokenRejected() throws Exception {
            // 1. 绑定获取令牌
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);
            String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            String token = root.path("data").path("token").asText();

            // 2. 吊销令牌
            webTestClient.post().uri("/api/v1/parent/revoke")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(0);

            // 3. 检查令牌状态 — 应返回 401
            webTestClient.get().uri("/api/v1/parent/token/check")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40101);
        }

        @Test
        @DisplayName("有效令牌检查返回 valid=true")
        void testValidTokenCheck() throws Exception {
            String code = requestVerifyCode(PRE_REGISTERED_PHONE);
            String responseBody = webTestClient.post().uri("/api/v1/parent/bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"phone\":\"" + PRE_REGISTERED_PHONE + "\",\"verify_code\":\"" + code + "\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            JsonNode root = objectMapper.readTree(responseBody);
            String token = root.path("data").path("token").asText();

            webTestClient.get().uri("/api/v1/parent/token/check")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.valid").isEqualTo(true)
                    .jsonPath("$.data.student_id").isEqualTo("STU20260001");
        }

        @Test
        @DisplayName("无 Authorization 头返回 401")
        void testNoAuthHeaderReturns401() {
            webTestClient.get().uri("/api/v1/parent/token/check")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(40100);
        }

        @Test
        @DisplayName("无效令牌返回 401")
        void testInvalidTokenReturns401() {
            webTestClient.get().uri("/api/v1/parent/token/check")
                    .header("Authorization", "Bearer invalid.token.here")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    /**
     * 请求验证码并返回固定验证码。
     *
     * <p>验证码不通过 API 返回（仅"已发送"），测试通过 @SpyBean 桩桩
     * generateCode() 返回固定验证码 "123456"。</p>
     */
    private String requestVerifyCode(String phone) {
        webTestClient.post().uri("/api/v1/parent/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\":\"" + phone + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);

        return FIXED_CODE;
    }
}
