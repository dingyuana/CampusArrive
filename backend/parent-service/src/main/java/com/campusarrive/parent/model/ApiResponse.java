package com.campusarrive.parent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 统一 API 响应封装。
 *
 * <p>规格来源：API 2.3 节统一响应格式 —
 * code/message/data/request_id/timestamp 五段式。</p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 业务状态码，0 表示成功。 */
    private int code;

    /** 状态描述。 */
    private String message;

    /** 响应数据。 */
    private T data;

    /** 请求唯一标识。 */
    private String requestId;

    /** 响应时间戳（ISO 8601 UTC）。 */
    private String timestamp;

    /**
     * 构造成功响应。
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("success")
                .data(data)
                .requestId(requestId)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }

    /**
     * 构造错误响应。
     */
    public static <T> ApiResponse<T> error(int code, String message, String requestId) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .requestId(requestId)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }
}
