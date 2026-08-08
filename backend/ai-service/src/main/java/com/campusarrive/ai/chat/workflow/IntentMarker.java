package com.campusarrive.ai.chat.workflow;

/**
 * 跳转意图标记(AID 7.3 节点 4 输出)。
 *
 * <p>规格来源:FR-01-15(跳转报到环节)、FR-01-16(校园导航)。
 * DeepSeek 生成时在回复中嵌入结构化意图标记(如 {@code [[STEP:payment]]}、{@code [[POI:library]]}),
 * 节点 5 解析后生成跳转按钮配置随回复返回前端。</p>
 *
 * @param type    意图类型:STEP(跳转环节)/ POI(校园导航)
 * @param target  目标标识(如 payment、library)
 */
public record IntentMarker(String type, String target) {

    /** 跳转报到环节意图。 */
    public static IntentMarker step(String target) {
        return new IntentMarker("STEP", target);
    }

    /** 校园导航意图。 */
    public static IntentMarker poi(String target) {
        return new IntentMarker("POI", target);
    }
}
