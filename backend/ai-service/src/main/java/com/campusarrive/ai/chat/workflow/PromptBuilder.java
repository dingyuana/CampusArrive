package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;

/**
 * 节点 4 生成提示词构造器。
 *
 * <p>规格来源:AID 7.3 节点 4 提示词工程。
 * 系统提示词约束模型仅基于检索上下文作答,禁止臆造,超出范围时明确提示;
 * 提示词约定模型在涉及流程操作或地点时输出结构化意图标记。</p>
 */
public final class PromptBuilder {

    /** 系统提示词:约束模型仅基于检索上下文作答。 */
    private static final String SYSTEM_PROMPT = """
            你是大学迎新智能助手,仅基于下方检索到的知识库内容回答新生问题。
            规则:
            1. 仅使用检索上下文中的信息作答,禁止臆造库外事实。
            2. 超出知识库覆盖范围时,明确回复"建议咨询现场志愿者或辅导员"。
            3. 涉及流程环节跳转时,在回复末尾输出 [[STEP:环节标识]] 标记(如 [[STEP:payment]])。
            4. 涉及校园地点导航时,在回复末尾输出 [[POI:地点名称]] 标记(如 [[POI:第一食堂]])。
            5. 回复简洁明了,控制在 300 字以内。
            """;

    private PromptBuilder() {
    }

    /**
     * 构造完整提示词:系统提示 + 检索上下文 + 历史对话 + 当前问题。
     *
     * @param sources   检索到的知识库来源
     * @param history   会话历史(最近 5 轮)
     * @param question  当前用户问题(已脱敏)
     * @param context   对话上下文(当前环节、身份);可为 null
     * @return 完整提示词
     */
    public static String build(List<RetrievedSource> sources,
                               List<com.campusarrive.ai.chat.ConversationContext.Message> history,
                               String question,
                               com.campusarrive.ai.chat.ChatContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT);
        sb.append("\n【检索上下文】\n");
        if (sources == null || sources.isEmpty()) {
            sb.append("(无相关检索结果)\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                RetrievedSource s = sources.get(i);
                sb.append("[").append(i + 1).append("] 来源:").append(s.title())
                        .append(" / 章节:").append(s.section())
                        .append("\n内容:").append(s.snippet()).append("\n\n");
            }
        }
        if (context != null) {
            sb.append("【当前上下文】");
            if (context.currentStep() != null) {
                sb.append("环节=").append(context.currentStep());
            }
            if (context.studentType() != null) {
                sb.append(", 身份=").append(context.studentType());
            }
            sb.append("\n");
        }
        if (history != null && !history.isEmpty()) {
            sb.append("\n【历史对话】\n");
            for (com.campusarrive.ai.chat.ConversationContext.Message m : history) {
                sb.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }
        sb.append("\n【当前问题】\n").append(question);
        return sb.toString();
    }
}
