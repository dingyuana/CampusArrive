package com.campusarrive.gateway.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 网关统一错误响应体。
 *
 * <p>规格来源：FR-04-02/FR-04-03 — 鉴权失败与限流超限均返回统一 JSON 结构。
 * 所有网关过滤器在拒绝请求时构造本对象并写入响应体。</p>
 *
 * <pre>
 * {"code":40100, "message":"令牌无效或已过期", "request_id":"...", "timestamp":"..."}
 * </pre>
 */
@Data
@AllArgsConstructor
public class ErrorResponse {

    /** 业务错误码（40100 鉴权失败 / 40300 越权 / 90001 学生限流 / 90002 全局限流）。 */
    private int code;

    /** 人类可读的错误描述。 */
    private String message;

    /** 链路追踪请求 ID（X-Request-Id），便于问题定位。 */
    private String requestId;

    /** 错误发生时间（ISO-8601 UTC）。 */
    private String timestamp;
}
