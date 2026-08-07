package com.campusarrive.parent.model;

import lombok.Builder;
import lombok.Data;

/**
 * 绑定响应数据 DTO。
 *
 * <p>规格来源：API 6.1.5 — 绑定成功后返回 JWT 令牌与关联学生信息。</p>
 */
@Data
@Builder
public class BindResponseData {

    /** 家长 JWT 令牌。 */
    private String token;

    /** 令牌类型，固定 Bearer。 */
    private String tokenType;

    /** 有效期（秒）。 */
    private long expiresIn;

    /** 关联学生 ID。 */
    private String studentId;

    /** 学生脱敏姓名。 */
    private String studentNameMasked;

    /** 绑定记录 ID。 */
    private String bindId;
}
