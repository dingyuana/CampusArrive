package com.campusarrive.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 迎新智能助手服务入口。
 *
 * <p>规格来源：FR-01-01~18（AI 迎新智能助手）、AID-CA-2026-07。
 * 职责：MaxKB 工作流编排、DeepSeek 流式对话、RAG 知识检索、
 * MCP 工具调用（跳转环节/导航）、安全护栏与 FAQ 降级。</p>
 */
@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
