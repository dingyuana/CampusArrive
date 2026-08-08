package com.campusarrive.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 对话请求(API 5.1.4 请求体契约)。
 *
 * <p>规格来源:FR-01-01(对话入口)、FR-01-06(多轮上下文)。
 * 字段映射 API 设计文档 §5.1.4 请求体字段表。</p>
 *
 * @param studentId 学生 ID,需与 JWT 中 student_id 一致
 * @param sessionId 会话 ID,未提供时由服务端新建(FR-01-06)
 * @param message   用户提问,1-500 字符
 * @param context   上下文(当前环节、身份类别);可为 {@code null}
 * @param stream    是否流式响应,默认 false
 */
public record ChatRequest(
        @NotBlank String studentId,
        String sessionId,
        @NotBlank @Size(min = 1, max = 500) String message,
        ChatContext context,
        Boolean stream
) {

    /** stream 默认值:false(普通 JSON 响应)。 */
    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }

    /** 取当前报到环节标识;context 为 null 时返回 null。 */
    public String currentStep() {
        return context == null ? null : context.currentStep();
    }

    /** 取学生身份类别;context 为 null 时返回 null。 */
    public String studentType() {
        return context == null ? null : context.studentType();
    }
}
