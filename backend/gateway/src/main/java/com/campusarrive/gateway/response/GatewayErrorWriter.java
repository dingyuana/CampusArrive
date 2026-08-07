package com.campusarrive.gateway.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 网关错误响应写入器。
 *
 * <p>规格来源：FR-04-02/FR-04-03 — 统一以 JSON 格式返回错误，并附带 request_id 与 timestamp。
 * 供各 GlobalFilter 在拒绝请求（401/403/429）时复用，保证响应格式一致。</p>
 */
public final class GatewayErrorWriter {

    /** 链路追踪请求头名称。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private GatewayErrorWriter() {
    }

    /**
     * 向响应写入统一错误体。
     *
     * @param exchange      当前交换
     * @param objectMapper  JSON 序列化器
     * @param status        HTTP 状态码
     * @param code          业务错误码
     * @param message       错误描述
     * @return 写入完成的 Mono
     */
    public static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper,
                                   HttpStatus status, int code, String message) {
        String requestId = resolveRequestId(exchange);
        ErrorResponse error = new ErrorResponse(code, message, requestId, Instant.now().toString());
        String json = safeSerialize(objectMapper, error, code, message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String resolveRequestId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return requestId != null && !requestId.isBlank() ? requestId : java.util.UUID.randomUUID().toString();
    }

    private static String safeSerialize(ObjectMapper objectMapper, ErrorResponse error, int code, String message) {
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            // 序列化失败时的兜底（不应发生）
            return "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
        }
    }
}
