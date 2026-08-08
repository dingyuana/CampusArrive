package com.campusarrive.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CT-AI-001:POST /api/v1/ai/chat 契约测试。
 *
 * <p>规格来源:FR-01-01(AI 对话入口)、API 5.1.4 接口契约。
 * 验证请求/响应契约一致:响应字段齐全(session_id/message_id/reply/sources/content_label 等),
 * 限流响应头(X-RateLimit-* )正确返回。</p>
 *
 * <p>使用 @SpringBootTest 加载完整上下文(内存知识库 + 本地生成器,无外部依赖)。
 * SSE 流式响应(CT-AI-002)与 WebSocket 在 DeepSeek 流式接入后补测。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("CT-AI-001: 对话接口契约")
class ChatContractTest {

    @Autowired
    private ChatController chatController;

    @Test
    @DisplayName("正常对话请求 → 响应契约字段完整")
    void chatResponseContractComplete() {
        ChatRequest request = new ChatRequest(
                "STU20260001", null, "报到需要带什么材料", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp = chatController.chat(request, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "HTTP 200");
        // 限流响应头
        assertTrue(resp.getHeaders().containsKey("X-RateLimit-Limit"), "含 X-RateLimit-Limit");
        assertTrue(resp.getHeaders().containsKey("X-RateLimit-Remaining"), "含 X-RateLimit-Remaining");
        assertTrue(resp.getHeaders().containsKey("X-RateLimit-Reset"), "含 X-RateLimit-Reset");

        ApiResponse<ChatResponse> body = resp.getBody();
        assertNotNull(body, "响应体非空");
        assertEquals(0, body.code(), "code=0 成功");
        ChatResponse data = body.data();
        assertAll("响应字段契约",
                () -> assertNotNull(data.sessionId(), "session_id 非空"),
                () -> assertNotNull(data.messageId(), "message_id 非空"),
                () -> assertNotNull(data.reply(), "reply 非空"),
                () -> assertNotNull(data.sources(), "sources 非空"),
                () -> assertNotNull(data.contentLabel(), "content_label 非空"),
                () -> assertNotNull(data.tokens(), "tokens 非空"),
                () -> assertFalse(data.transferToHuman(), "正常对话不转人工")
        );
        assertTrue(data.contentLabel().isAiGenerated(), "标识为 AI 生成");
    }

    @Test
    @DisplayName("POI 查询 → sources 含 POI 来源")
    void poiQueryReturnsPoiSources() {
        ChatRequest request = new ChatRequest("STU20260001", null, "食堂在哪里", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp = chatController.chat(request, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ChatResponse data = resp.getBody().data();
        assertFalse(data.sources().isEmpty(), "应返回来源");
    }

    @Test
    @DisplayName("多轮对话:携带 session_id 复用会话")
    void multiTurnWithSessionId() {
        ChatRequest req1 = new ChatRequest("STU20260001", null, "学费怎么交", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp1 = chatController.chat(req1, null);
        String sessionId = resp1.getBody().data().sessionId();

        ChatRequest req2 = new ChatRequest("STU20260001", sessionId, "宿舍在哪", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp2 = chatController.chat(req2, null);

        assertEquals(sessionId, resp2.getBody().data().sessionId(), "复用同一 session_id");
    }

    @Test
    @DisplayName("限流:超 10 次/分钟返回 429")
    void rateLimitReturns429() {
        String studentId = "STU_RATELIMIT";
        // 前 10 次放行
        for (int i = 0; i < 10; i++) {
            ChatRequest req = new ChatRequest(studentId, null, "测试问题" + i, null, false);
            assertEquals(HttpStatus.OK, chatController.chat(req, null).getStatusCode(),
                    "第 " + (i + 1) + " 次应放行");
        }
        // 第 11 次拦截
        ChatRequest req = new ChatRequest(studentId, null, "超限请求", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp = chatController.chat(req, null);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode(), "第 11 次应 429");
        assertEquals(90001, resp.getBody().code(), "code=90001 限流");
        assertTrue(resp.getHeaders().containsKey("Retry-After"), "含 Retry-After 头");
    }

    @Test
    @DisplayName("AI 内容标识:每条回复带 AI 生成标识")
    void contentLabelAlwaysPresent() {
        ChatRequest request = new ChatRequest("STU20260001", null, "宿舍有没有空调", null, false);
        ResponseEntity<ApiResponse<ChatResponse>> resp = chatController.chat(request, null);

        ContentLabel label = resp.getBody().data().contentLabel();
        assertNotNull(label, "content_label 必须存在");
        assertTrue(label.isAiGenerated(), "is_ai_generated=true");
        assertNotNull(label.labelText(), "label_text 非空");
        assertFalse(label.labelText().isBlank(), "label_text 非空白");
    }
}
