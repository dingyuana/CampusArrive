package com.campusarrive.gateway.security;

import io.jsonwebtoken.Claims;

/**
 * JWT 自定义声明安全读取工具。
 *
 * <p>规格来源：API 设计文档 3.2 节 — role/student_id/scope 为自定义声明，
 * 可能缺失。注入下游请求头时不能为 null，统一以空串兜底。</p>
 */
public final class ClaimsAccessors {

    /** 自定义声明：角色。 */
    public static final String CLAIM_ROLE = "role";
    /** 自定义声明：关联学生 ID。 */
    public static final String CLAIM_STUDENT_ID = "student_id";
    /** 自定义声明：权限范围。 */
    public static final String CLAIM_SCOPE = "scope";

    private ClaimsAccessors() {
    }

    /** 读取 role 声明，缺失返回空串。 */
    public static String role(Claims claims) {
        return stringClaim(claims, CLAIM_ROLE);
    }

    /** 读取 student_id 声明，缺失返回空串。 */
    public static String studentId(Claims claims) {
        return stringClaim(claims, CLAIM_STUDENT_ID);
    }

    /** 读取 scope 声明，缺失返回空串。 */
    public static String scope(Claims claims) {
        return stringClaim(claims, CLAIM_SCOPE);
    }

    private static String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value != null ? String.valueOf(value) : "";
    }
}
