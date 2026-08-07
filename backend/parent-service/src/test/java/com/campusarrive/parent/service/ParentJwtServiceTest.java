package com.campusarrive.parent.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT-PAR-002：JWT 载荷解析单元测试。
 *
 * <p>规格来源：FR-03-02 — 令牌仅含 student_id / exp / iat，
 * 不含敏感字段（身份证号、姓名、手机号等），有效期 30 天。</p>
 */
@DisplayName("UT-PAR-002：JWT 载荷解析")
class ParentJwtServiceTest {

    private static final String SECRET = "campus-arrive-parent-jwt-secret-key-2026-hs256-signing";
    private static final String ISSUER = "freshman-checkin-system";
    private static final String AUDIENCE = "parent-h5";

    private ParentJwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new ParentJwtService(SECRET, ISSUER, AUDIENCE, 2592000L);
    }

    @Nested
    @DisplayName("令牌签发")
    class TokenIssuance {

        @Test
        @DisplayName("签发的令牌为三段式 JWT 格式")
        void testTokenFormat() {
            String token = jwtService.issueToken("STU20260001", "13812345678");

            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("令牌有效期正确反映为 30 天")
        void testExpirySeconds() {
            assertThat(jwtService.getExpirySeconds()).isEqualTo(2592000L);
        }
    }

    @Nested
    @DisplayName("载荷字段完整性")
    class PayloadFields {

        @Test
        @DisplayName("令牌包含 student_id")
        void testContainsStudentId() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.get("student_id", String.class)).isEqualTo("STU20260001");
        }

        @Test
        @DisplayName("令牌包含 iat（签发时间）")
        void testContainsIat() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getIssuedAt()).isNotNull();
        }

        @Test
        @DisplayName("令牌包含 exp（过期时间）且为 30 天后")
        void testContainsExp() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getExpiration()).isNotNull();
            long diffSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
            assertThat(diffSeconds).isEqualTo(2592000L);
        }

        @Test
        @DisplayName("令牌包含 jti（唯一标识）")
        void testContainsJti() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("令牌包含 iss（签发方）")
        void testContainsIssuer() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        }

        @Test
        @DisplayName("令牌包含 aud（受众）")
        void testContainsAudience() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getAudience()).contains(AUDIENCE);
        }

        @Test
        @DisplayName("令牌包含 role=parent")
        void testContainsRole() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.get("role", String.class)).isEqualTo("parent");
        }

        @Test
        @DisplayName("令牌包含 scope=parent:read")
        void testContainsScope() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.get("scope", String.class)).isEqualTo("parent:read");
        }
    }

    @Nested
    @DisplayName("不含敏感字段")
    class NoSensitiveFields {

        @Test
        @DisplayName("令牌不含手机号")
        void testNoPhoneInToken() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.containsKey("phone")).isFalse();
        }

        @Test
        @DisplayName("令牌不含身份证号")
        void testNoIdCardInToken() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.containsKey("id_card")).isFalse();
            assertThat(claims.containsKey("idCard")).isFalse();
        }

        @Test
        @DisplayName("令牌不含姓名")
        void testNoNameInToken() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims.containsKey("name")).isFalse();
        }

        @Test
        @DisplayName("containsSensitiveFields 返回 false")
        void testContainsSensitiveFieldsFalse() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(jwtService.containsSensitiveFields(claims)).isFalse();
        }
    }

    @Nested
    @DisplayName("令牌解析与校验")
    class TokenParsing {

        @Test
        @DisplayName("正确密钥可解析令牌")
        void testParseWithCorrectKey() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            Claims claims = jwtService.parseToken(token);

            assertThat(claims).isNotNull();
        }

        @Test
        @DisplayName("错误密钥解析失败")
        void testParseWithWrongKey() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            ParentJwtService wrongService = new ParentJwtService(
                    "wrong-secret-key-with-at-least-32-bytes-for-hs256!!", ISSUER, AUDIENCE, 2592000L);

            assertThatThrownBy(() -> wrongService.parseToken(token))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("提取 jti 正确")
        void testExtractJti() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            String jti = jwtService.extractJti(token);

            assertThat(jti).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("提取 student_id 正确")
        void testExtractStudentId() {
            String token = jwtService.issueToken("STU20260001", "13812345678");
            String studentId = jwtService.extractStudentId(token);

            assertThat(studentId).isEqualTo("STU20260001");
        }

        @Test
        @DisplayName("两个令牌的 jti 不同")
        void testDifferentJtis() {
            String token1 = jwtService.issueToken("STU20260001", "13812345678");
            String token2 = jwtService.issueToken("STU20260001", "13812345678");

            assertThat(jwtService.extractJti(token1))
                    .isNotEqualTo(jwtService.extractJti(token2));
        }
    }
}
