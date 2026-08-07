package com.campusarrive.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

/**
 * JWT 解析与校验工具类。
 *
 * <p>规格来源：FR-04-02 / API 设计文档 3.2 节 — 网关对 JWT 校验 exp、nbf、iss、aud，任一不匹配则拒绝。
 * 算法 HS256；payload 含 iss/sub/aud/iat/exp/nbf/jti/role/student_id/scope。</p>
 *
 * <ul>
 *   <li>{@link #parseToken(String)}：验签 + 自动校验 exp/nbf，返回 Claims；非法签名、过期、未生效均抛出 jjwt 异常。</li>
 *   <li>{@link #validateToken(Claims)}：校验 iss 与 aud 是否符合预期，不匹配抛出 {@link InvalidTokenException}。</li>
 *   <li>{@link #extractClaims(String)}：先解析再校验，供过滤器一站式调用。</li>
 * </ul>
 */
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final String expectedIssuer;
    private final Set<String> acceptedAudiences;

    /**
     * @param secret             HS256 签名密钥（≥ 32 字节）
     * @param expectedIssuer     期望的签发方（iss），为 null 则不校验
     * @param acceptedAudiences  接受的受众（aud）集合，为空则不校验
     */
    public JwtUtil(String secret, String expectedIssuer, Set<String> acceptedAudiences) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = expectedIssuer;
        this.acceptedAudiences = acceptedAudiences == null ? Collections.emptySet() : acceptedAudiences;
    }

    /**
     * 解析并验签令牌，自动校验 exp 与 nbf。
     *
     * @param token JWT 字符串
     * @return Claims 声明集合
     * @throws io.jsonwebtoken.ExpiredJwtException      令牌已过期
     * @throws io.jsonwebtoken.PrematureJwtException    令牌尚未生效（nbf 在未来）
     * @throws io.jsonwebtoken.security.SignatureException 签名不匹配
     * @throws io.jsonwebtoken.MalformedJwtException    令牌格式错误
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验令牌声明（iss/aud）。exp/nbf 已在解析阶段校验。
     *
     * @param claims 已解析的声明
     * @throws InvalidTokenException iss 或 aud 不匹配
     */
    public void validateToken(Claims claims) {
        // 校验签发方 iss
        if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
            throw new InvalidTokenException("令牌签发方(iss)不匹配: " + claims.getIssuer());
        }
        // 校验受众 aud（令牌受众需包含至少一个被网关接受的受众）
        if (!acceptedAudiences.isEmpty()) {
            Set<String> tokenAudiences = claims.getAudience();
            boolean matched = tokenAudiences != null
                    && tokenAudiences.stream().anyMatch(acceptedAudiences::contains);
            if (!matched) {
                throw new InvalidTokenException("令牌受众(aud)不被接受: " + tokenAudiences);
            }
        }
    }

    /**
     * 解析并完整校验令牌（解析 + 声明校验）。
     *
     * @param token JWT 字符串
     * @return 校验通过的 Claims
     * @throws Exception 解析或校验失败的各类异常
     */
    public Claims extractClaims(String token) {
        Claims claims = parseToken(token);
        validateToken(claims);
        return claims;
    }
}
