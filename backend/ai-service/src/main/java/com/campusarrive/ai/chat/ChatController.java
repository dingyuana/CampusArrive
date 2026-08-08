package com.campusarrive.ai.chat;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * AI 对话控制器(POST /api/v1/ai/chat)。
 *
 * <p>规格来源:FR-01-01(AI 对话入口)、API 5.1.4 接口契约。
 * 学生 JWT 认证由 gateway 统一处理,本控制器聚焦对话逻辑与限流响应。</p>
 *
 * <p>当前实现普通 JSON 响应模式;SSE 流式响应({@code Accept: text/event-stream})
 * 与 WebSocket 流式端点在后续迭代接入(需 DeepSeek 流式 API 支持)。</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        ChatService.ChatResult result = chatService.handle(request);

        HttpHeaders headers = new HttpHeaders();
        ChatRateLimiter.RateLimitResult rl = result.rateLimit();
        headers.add("X-RateLimit-Limit", String.valueOf(rl.limit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(rl.remaining()));
        headers.add("X-RateLimit-Reset", String.valueOf(rl.resetEpoch()));

        if (result.rateLimited()) {
            headers.add("Retry-After", String.valueOf(rl.retryAfter()));
            ApiResponse<Map<String, Object>> body = ApiResponse.error(90001,
                    "AI 对话频率超限(10次/分钟),请稍后再试", requestId);
            // 限流响应体含 retry_after/limit/window
            Map<String, Object> errData = Map.of(
                    "retry_after", rl.retryAfter(),
                    "limit", rl.limit(),
                    "window", 60);
            ApiResponse<Map<String, Object>> withData = new ApiResponse<>(
                    90001, "AI 对话频率超限(10次/分钟),请稍后再试",
                    errData, requestId, java.time.Instant.now().toString());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers).body((ApiResponse) withData);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.success(result.response(), requestId));
    }
}
