package com.campusarrive.gateway.security;

import com.campusarrive.gateway.testsupport.JwtTestHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT-MW-001：JWT 解析与校验单元测试。
 *
 * <p>规格来源：FR-04-02 / API 设计文档 3.2 节 — 网关校验 exp、nbf、iss、aud，
 * 合法令牌通过，非法/过期/签名错误令牌拒绝。</p>
 */
@DisplayName("UT-MW-001: JWT 解析与校验")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private JwtTestHelper helper;

    @BeforeEach
    void setUp() {
        // Arrange：构造与测试令牌同密钥/签发方/受众的 JwtUtil
        helper = new JwtTestHelper();
        jwtUtil = new JwtUtil(JwtTestHelper.SECRET, JwtTestHelper.ISSUER,
                Set.of("student-miniapp", "parent-h5"));
    }

    @Test
    @DisplayName("合法令牌正确解析出所有字段")
    void testParseValidToken() {
        // Arrange
        String token = helper.validToken();

        // Act
        Claims claims = assertDoesNotThrow(() -> jwtUtil.extractClaims(token));

        // Assert：标准声明
        assertEquals("freshman-checkin-system", claims.getIssuer());
        assertEquals("student:STU20260001", claims.getSubject());
        assertEquals("student-miniapp", claims.getAudience().iterator().next());
        // Assert：自定义声明
        assertEquals("student", claims.get("role"));
        assertEquals("STU20260001", claims.get("student_id"));
        assertEquals("student:full", claims.get("scope"));
        // jti 非空
        assertEquals(true, claims.getId() != null && !claims.getId().isBlank());
    }

    @Test
    @DisplayName("过期令牌解析抛出 ExpiredJwtException")
    void testParseExpiredToken() {
        // Arrange
        String token = helper.expiredToken();

        // Act & Assert
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("签名错误令牌解析抛出 SignatureException")
    void testParseInvalidSignature() {
        // Arrange
        String token = helper.invalidSignatureToken();

        // Act & Assert
        assertThrows(SignatureException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("格式错误令牌解析抛出 MalformedJwtException")
    void testParseMalformedToken() {
        // Arrange
        String token = helper.malformedToken();

        // Act & Assert
        assertThrows(MalformedJwtException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("iss/aud/nbf 不匹配时拒绝")
    void testValidateTokenClaims() {
        // Arrange — iss 不匹配
        String wrongIssuerToken = helper.tokenWithIssuer("evil-issuer");
        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> jwtUtil.extractClaims(wrongIssuerToken));

        // Arrange — aud 不匹配
        String wrongAudienceToken = helper.tokenWithAudience("unknown-app");
        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> jwtUtil.extractClaims(wrongAudienceToken));

        // Arrange — nbf 在未来（尚未生效），解析阶段即拒绝
        String futureToken = helper.tokenWithNotBeforeFuture();
        // Act & Assert
        assertThrows(PrematureJwtException.class, () -> jwtUtil.parseToken(futureToken));
    }
}
