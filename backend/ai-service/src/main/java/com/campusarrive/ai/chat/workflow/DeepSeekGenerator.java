package com.campusarrive.ai.chat.workflow;

/**
 * 节点 4:DeepSeek 生成(抽象接口)。
 *
 * <p>规格来源:FR-01-13(DeepSeek 生成)、AID 6.3(模型路由 Flash/Pro)。
 * 将脱敏后的上下文 + 用户问题发送 DeepSeek 生成回复,采用流式生成降低首字等待感。</p>
 *
 * <p>当前提供本地模拟实现(基于检索片段拼接),真实 HTTP 客户端实现
 * 在 INFRA-1.3 DeepSeek 环境就绪后接入(OpenAI 兼容协议)。</p>
 */
public interface DeepSeekGenerator {

    /**
     * 生成回复。
     *
     * @param prompt    构造好的提示词(系统提示 + 检索上下文 + 用户问题,已脱敏)
     * @param maxLength 回复最大长度(字符)
     * @return 生成的回复正文
     */
    String generate(String prompt, int maxLength);

    /** DeepSeek 服务是否可用(健康检查)。 */
    boolean isAvailable();
}
