package com.campusarrive.ai.chat;

import com.campusarrive.ai.chat.workflow.ChatWorkflow;
import com.campusarrive.ai.knowledge.RetrievedSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 对话服务。
 *
 * <p>规格来源:FR-01-01(对话入口)、FR-01-06(多轮上下文)。
 * 协调限流、会话上下文、工作流编排,产出 {@link ChatResponse}。</p>
 */
@Service
public class ChatService {

    private final ChatWorkflow workflow;
    private final ConversationContextManager contextManager;
    private final ChatRateLimiter rateLimiter;

    public ChatService(ChatWorkflow workflow,
                       ConversationContextManager contextManager,
                       ChatRateLimiter rateLimiter) {
        this.workflow = workflow;
        this.contextManager = contextManager;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 处理对话请求。
     *
     * @param request 对话请求(已校验)
     * @return 对话响应
     */
    public ChatResult handle(ChatRequest request) {
        // 限流检查
        ChatRateLimiter.RateLimitResult rl = rateLimiter.tryAcquire(request.studentId());
        if (!rl.allowed()) {
            return ChatResult.rateLimited(rl);
        }
        // 会话上下文
        ConversationContext ctx = contextManager.getOrCreate(request.sessionId(), request.studentId());
        // 执行工作流
        WorkflowResult result = workflow.execute(request.message(), ctx);
        // 追加历史
        contextManager.appendRound(ctx.sessionId(), request.studentId(), request.message(), result.reply());
        // 构造响应
        ChatResponse response = toResponse(ctx.sessionId(), result);
        return ChatResult.success(response, rl);
    }

    private ChatResponse toResponse(String sessionId, WorkflowResult result) {
        List<ToolInvocation> tools = result.intents() == null ? List.of() :
                result.intents().stream().map(i -> new ToolInvocation(
                        "STEP".equals(i.type()) ? "navigate_to_step" : "start_navigation",
                        UUID.randomUUID().toString(),
                        i.target(),
                        "跳转至 " + i.target(),
                        true
                )).collect(Collectors.toList());
        return new ChatResponse(
                sessionId,
                "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                result.reply(),
                result.sources() == null ? List.of() : result.sources(),
                result.contentLabel(),
                tools,
                result.transferToHuman(),
                result.tokens()
        );
    }

    /**
     * 服务处理结果(含限流信息,供控制器设置响应头)。
     *
     * @param response 对话响应(限流时为 null)
     * @param rateLimit 限流结果
     * @param rateLimited 是否被限流
     */
    public record ChatResult(ChatResponse response, ChatRateLimiter.RateLimitResult rateLimit, boolean rateLimited) {

        public static ChatResult success(ChatResponse response, ChatRateLimiter.RateLimitResult rl) {
            return new ChatResult(response, rl, false);
        }

        public static ChatResult rateLimited(ChatRateLimiter.RateLimitResult rl) {
            return new ChatResult(null, rl, true);
        }
    }
}
