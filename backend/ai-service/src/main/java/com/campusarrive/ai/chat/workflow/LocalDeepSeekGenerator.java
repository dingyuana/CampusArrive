package com.campusarrive.ai.chat.workflow;

import com.campusarrive.ai.knowledge.RetrievedSource;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 本地生成实现(降级/开发环境占位)。
 *
 * <p>规格来源:FR-01-13(DeepSeek 生成)、AID 9.2(FAQ 降级)、AID 6.3(模型路由)。
 * 真实环境由 DeepSeek V4(OpenAI 兼容协议)生成,INFRA-1.3 环境就绪后替换为 HTTP 客户端。</p>
 *
 * <p>本实现基于检索片段合成回复:取 Top-1 来源的 snippet 作为回复主体,
 * 并根据问题与来源类型在末尾附加结构化意图标记([[STEP:xxx]] / [[POI:xxx]]),
 * 模拟 DeepSeek 的跳转意图输出能力(AID 7.3 节点 4)。
 * 使 AI-3.2 工作流可在无 DeepSeek 环境下完成逻辑联调与降级。</p>
 */
public class LocalDeepSeekGenerator implements DeepSeekGenerator {

    /** 意图标记提取正则。 */
    private static final Pattern INTENT_PATTERN = Pattern.compile("\\[\\[(STEP|POI):([^\\]]+)\\]\\]");

    /** 默认可用(本地生成始终可用,用于降级)。 */
    private volatile boolean available = true;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String generate(String prompt, int maxLength) {
        // 本地生成器不解析 prompt 生成回复,而是由工作流直接基于检索结果合成。
        // 此方法保留接口契约,真实 DeepSeek 接入后替换为 HTTP 调用。
        // 工作流调用 generateReply 合成回复。
        return "";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 基于检索来源合成回复(含意图标记)。
     *
     * <p>取 Top-1 来源 snippet 作为回复主体,补充来源溯源信息,
     * 并根据来源分类与问题内容附加跳转意图标记。</p>
     *
     * @param sources 检索来源
     * @param question 用户问题(已脱敏)
     * @return 合成回复(含意图标记)
     */
    public String generateReply(List<RetrievedSource> sources, String question) {
        if (sources == null || sources.isEmpty()) {
            return "抱歉,未检索到相关信息。建议咨询现场志愿者或辅导员。";
        }
        RetrievedSource top = sources.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(top.snippet());
        // 补充更多来源的要点
        if (sources.size() > 1) {
            sb.append("\n\n补充信息:");
            for (int i = 1; i < Math.min(sources.size(), 3); i++) {
                RetrievedSource s = sources.get(i);
                sb.append("\n").append(s.section()).append(":").append(s.snippet());
            }
        }
        // 附加意图标记:流程类问题附加 STEP,POI 类附加 POI
        String intent = detectIntent(question, top);
        if (intent != null) {
            sb.append("\n").append(intent);
        }
        return sb.toString();
    }

    /** 根据问题与来源检测跳转意图,返回意图标记字符串(如 [[STEP:payment]]);无意图返回 null。 */
    private String detectIntent(String question, RetrievedSource top) {
        if (question == null) {
            return null;
        }
        String q = question.toLowerCase();
        // 流程环节跳转
        if (q.contains("缴费") || q.contains("学费") || top.section().contains("缴纳")) {
            return "[[STEP:payment]]";
        }
        if (q.contains("宿舍") || q.contains("入住") || top.section().contains("宿舍")) {
            return "[[STEP:dorm_assign]]";
        }
        if (q.contains("材料") || q.contains("提交") || top.section().contains("材料")) {
            return "[[STEP:material_upload]]";
        }
        if (q.contains("核验") || q.contains("身份") || top.section().contains("核验")) {
            return "[[STEP:verification]]";
        }
        if (q.contains("体检") || top.section().contains("体检")) {
            return "[[STEP:checkin]]";
        }
        // POI 导航
        if (q.contains("在哪") || q.contains("怎么走") || q.contains("位置")
                || top.title().contains("POI") || top.section().contains("食堂")
                || top.section().contains("图书馆") || top.section().contains("医院")) {
            return "[[POI:" + top.section() + "]]";
        }
        return null;
    }

    /** 从回复文本中提取所有意图标记。 */
    public static List<IntentMarker> extractIntents(String reply) {
        List<IntentMarker> intents = new java.util.ArrayList<>();
        if (reply == null) {
            return intents;
        }
        Matcher m = INTENT_PATTERN.matcher(reply);
        while (m.find()) {
            intents.add(new IntentMarker(m.group(1), m.group(2).trim()));
        }
        return intents;
    }

    /** 从回复文本中移除意图标记,得到纯展示文本。 */
    public static String stripIntents(String reply) {
        if (reply == null) {
            return "";
        }
        return INTENT_PATTERN.matcher(reply).replaceAll("").trim();
    }
}
