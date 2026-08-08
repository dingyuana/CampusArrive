package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.chat.ChatContext;
import com.campusarrive.ai.chat.ContentLabel;
import com.campusarrive.ai.chat.ConversationContext;
import com.campusarrive.ai.chat.TokenUsage;
import com.campusarrive.ai.chat.WorkflowResult;
import com.campusarrive.ai.knowledge.KnowledgeCategory;
import com.campusarrive.ai.knowledge.KnowledgeRetrievalService;
import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;

/**
 * 对话工作流编排器(AID 7 工作流编排)。
 *
 * <p>规格来源:FR-01-11(工作流编排)、FR-01-13(DeepSeek 生成)、FR-01-17(降级)。
 * 将一次 AI 对话拆解为五个串行节点,依次执行:</p>
 * <ol>
 *   <li>节点 1:安全过滤 — 拦截违规问题,命中走标准化拒答分支;</li>
 *   <li>节点 2:知识检索 RAG — 混合检索四类知识库,返回 Top-K 片段;</li>
 *   <li>节点 3:PII 脱敏 — 对将发送 DeepSeek 的文本脱敏,数据不出校;</li>
 *   <li>节点 4:DeepSeek 生成 — 基于脱敏上下文生成回复,含跳转意图标记;</li>
 *   <li>节点 5:内容标识 — 附加 AI 生成标识,解析意图标记生成跳转配置。</li>
 * </ol>
 *
 * <p>降级:节点 4 DeepSeek 不可用时,切换 FAQ 关键词匹配(AID 9.2),
 * 能力收敛为 FAQ 问答,标识降级模式。</p>
 *
 * <p>低置信兜底:节点 2 检索相似度均低于阈值(0.6)时,
 * 生成节点回复"建议咨询现场志愿者"(AID 7.3 节点 2)。</p>
 */
public class ChatWorkflow {

    /** 知识检索 Top-K(AID 附录 A 推荐值=5)。 */
    public static final int TOP_K = 5;
    /** 低置信相似度阈值(AID 附录 A 推荐值=0.6)。 */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.6;
    /** 回复最大长度(字符)。 */
    public static final int MAX_REPLY_LENGTH = 500;
    /** 低置信兜底话术。 */
    public static final String LOW_CONFIDENCE_REPLY =
            "抱歉,未检索到足够相关的信息。建议咨询现场志愿者或辅导员。";

    private final SafetyFilterNode safetyFilter;
    private final KnowledgeRetrievalService retrieval;
    private final PiiMasker piiMasker;
    private final LocalDeepSeekGenerator generator;
    private final FallbackFaqMatcher fallbackMatcher;

    public ChatWorkflow(SafetyFilterNode safetyFilter,
                        KnowledgeRetrievalService retrieval,
                        PiiMasker piiMasker,
                        LocalDeepSeekGenerator generator,
                        FallbackFaqMatcher fallbackMatcher) {
        this.safetyFilter = safetyFilter;
        this.retrieval = retrieval;
        this.piiMasker = piiMasker;
        this.generator = generator;
        this.fallbackMatcher = fallbackMatcher;
    }

    /**
     * 执行完整工作流。
     *
     * @param message  用户问题(原始)
     * @param context  对话上下文(含历史轮次与当前环节);可为 null
     * @return 工作流执行结果
     */
    public WorkflowResult execute(String message, ConversationContext context) {
        // ── 节点 1:安全过滤 ──
        SafetyVerdict safety = safetyFilter.filter(message);
        if (safety.blocked()) {
            return new WorkflowResult(
                    safety.rejectMessage(),
                    List.of(), List.of(), TokenUsage.zero(),
                    ContentLabel.normal(), "self_harm".equals(safety.violationCategory()),
                    false);
        }

        // ── 节点 2:知识检索 RAG ──
        // 根据问题内容选择检索分类;材料类问题按身份检索清单
        List<RetrievedSource> sources = retrieveByIntent(message, context);

        // 低置信判定
        boolean lowConfidence = sources.isEmpty()
                || sources.get(0).score() < LOW_CONFIDENCE_THRESHOLD;
        if (lowConfidence) {
            return new WorkflowResult(
                    LOW_CONFIDENCE_REPLY,
                    sources, List.of(), TokenUsage.zero(),
                    ContentLabel.normal(), false, true);
        }

        // ── 节点 3:PII 脱敏 ──
        PiiMasker.MaskResult masked = piiMasker.mask(message);

        // ── 节点 4:DeepSeek 生成(含降级) ──
        if (generator.isAvailable()) {
            return generateNormally(masked.maskedText(), sources, context);
        } else {
            return generateDegraded(masked.maskedText(), sources);
        }
    }

    /** 正常生成路径:DeepSeek 生成回复。 */
    private WorkflowResult generateNormally(String maskedQuestion,
                                            List<RetrievedSource> sources,
                                            ConversationContext context) {
        List<ConversationContext.Message> history = context == null ? List.of() : context.history();
        ChatContext ctx = context == null ? null : null; // 上下文环节从请求带入,此处简化
        String prompt = PromptBuilder.build(sources, history, maskedQuestion, ctx);
        String rawReply = generator.generateReply(sources, maskedQuestion);

        // ── 节点 5:内容标识 + 意图解析 ──
        List<IntentMarker> intents = LocalDeepSeekGenerator.extractIntents(rawReply);
        String displayReply = LocalDeepSeekGenerator.stripIntents(rawReply);
        if (displayReply.length() > MAX_REPLY_LENGTH) {
            displayReply = displayReply.substring(0, MAX_REPLY_LENGTH) + "...";
        }

        // token 估算(本地生成器无真实计量,按字符粗估)
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(rawReply);
        TokenUsage tokens = new TokenUsage(promptTokens, completionTokens,
                promptTokens + completionTokens);

        return new WorkflowResult(
                displayReply, sources, intents, tokens,
                ContentLabel.normal(), false, false);
    }

    /** 降级生成路径:FAQ 关键词匹配。 */
    private WorkflowResult generateDegraded(String maskedQuestion, List<RetrievedSource> sources) {
        FallbackFaqMatcher.FallbackResult fb = fallbackMatcher.match(maskedQuestion);
        String reply = fb.hit() ? fb.reply() : FallbackFaqMatcher.fallbackMessage();
        List<RetrievedSource> fbSources = fb.hit() ? fb.sources() : sources;
        return new WorkflowResult(
                reply, fbSources, List.of(), TokenUsage.zero(),
                ContentLabel.faqDegraded(), false, !fb.hit());
    }

    /** 根据问题意图选择检索分类。 */
    private List<RetrievedSource> retrieveByIntent(String message, ConversationContext context) {
        // 全库混合检索:材料类问题在 FAQ 与 MATERIAL 中均有覆盖,
        // 限定单分类会因清单格式 bigram 重叠度低导致低置信,故统一全库检索。
        return retrieval.retrieve(message, TOP_K, null);
    }

    /** token 粗估:中文按字符数,英文按词数(4 字符≈1 token)。 */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
