package com.campusarrive.ai.chat;

/**
 * 统一响应包装(API 全局响应结构)。
 *
 * <p>规格来源:API 5.1.5 响应体结构。
 * {@code code=0} 表示成功,非零为业务错误码(API 5.1.7 错误码定义)。</p>
 *
 * @param code      业务码(0=成功)
 * @param message   提示信息
 * @param data      业务数据
 * @param requestId 请求 ID
 * @param timestamp 时间戳(ISO-8601)
 * @param <T>       数据类型
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId,
        String timestamp
) {

    /** 成功响应。 */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, "success", data, requestId, now());
    }

    /** 业务错误响应。 */
    public static <T> ApiResponse<T> error(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId, now());
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}
