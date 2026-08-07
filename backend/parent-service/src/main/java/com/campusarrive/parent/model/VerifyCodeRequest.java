package com.campusarrive.parent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 验证码请求 DTO。
 *
 * <p>规格来源：FR-03-01 — 家长输入手机号，系统校验预登记后下发验证码。</p>
 */
@Data
public class VerifyCodeRequest {

    /** 家长手机号（11 位）。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
