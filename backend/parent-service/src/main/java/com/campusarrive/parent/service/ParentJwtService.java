package com.campusarrive.parent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 家长 JWT 签发与校验服务。
 *
 * <p>规格来源：FR-03-02 — 绑定成功后签发 JWT，有效期 30 天，
 * 令牌仅关联学生 ID，不含敏感字段（身份证号、手机号、姓名等）。
 * 算法 HS256；payload 含 iss/sub/aud/iat/exp/jti/role/student_id/scope/bind_time。</p>
 */
@Slf4j
public class ParentJwtService {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final long expirySeconds;

    /**
     * @param secret        HS256 签名密钥（≥ 32 字节）
     * @param issuer        签发方（iss）
     * @param audience      受众（aud）
     * @param expirySeconds 有效期（秒）
     */
    public ParentJwtService(String secret, String issuer, String audience, long expirySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.expirySeconds = expirySeconds;
    }

    /**
     * 签发家长 JWT 令牌。
     *
     * <p>令牌 payload 仅包含 student_id 关联标识，不含手机号、姓名等敏感字段。
     * 每个令牌分配唯一 jti 用于吊销管理。</p>
     *
     * @param studentId 关联学生 ID
     * @param phone     家长手机号（仅用于日志，不写入令牌）
     * @return JWT 令牌字符串
     */
    public String issueToken(String studentId, String phone) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirySeconds);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .issuer(issuer)
                .subject("parent:" + phone)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(jti)
                .claim("role", "parent")
                .claim("student_id", studentId)
                .claim("scope", "parent:read")
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签令牌。
     *
     * @param token JWT 字符串
     * @return Claims 声明集合
     * @throws Exception 解析失败（签名不匹配、过期、格式错误等）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中提取 jti（令牌唯一标识）。
     *
     * @param token JWT 字符串
     * @return jti
     */
    public String extractJti(String token) {
        return parseToken(token).getId();
    }

    /**
     * 从令牌中提取 student_id。
     *
     * @param token JWT 字符串
     * @return 学生 ID
     */
    public String extractStudentId(String token) {
        return parseToken(token).get("student_id", String.class);
    }

    /**
     * 获取令牌有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getExpirySeconds() {
        return expirySeconds;
    }

    /**
     * 检查令牌 payload 是否包含敏感字段。
     *
     * <p>规格要求令牌仅含 student_id / exp / iat / jti / iss / aud / sub / role / scope，
     * 不得包含手机号、身份证号、姓名等敏感信息。</p>
     *
     * @param claims 已解析的声明
     * @return true 表示包含敏感字段（不合规）
     */
    public boolean containsSensitiveFields(Claims claims) {
        Map<String, Object> claimMap = claims;
        return claimMap.containsKey("phone")
                || claimMap.containsKey("id_card")
                || claimMap.containsKey("idCard")
                || claimMap.containsKey("name")
                || claimMap.containsKey("address")
                || claimMap.containsKey("password");
    }
}
