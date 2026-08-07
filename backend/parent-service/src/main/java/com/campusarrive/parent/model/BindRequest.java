package com.campusarrive.parent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定请求 DTO。
 *
 * <p>规格来源：FR-03-01 — 家长通过手机号 + 验证码完成绑定。</p>
 */
@Data
public class BindRequest {

    /** 家长手机号（11 位）。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 6 位数字验证码。 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为 6 位数字")
    private String verifyCode;
}
