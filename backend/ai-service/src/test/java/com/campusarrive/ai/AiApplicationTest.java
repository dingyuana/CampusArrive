package com.campusarrive.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AI 助手服务上下文加载测试（TDD 先行）。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证 Spring 上下文可正常启动，为后续对话接口、工作流编排测试提供基座。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UT-INFRA: AI 助手服务上下文加载")
class AiApplicationTest {

    @Test
    @DisplayName("上下文加载成功且无异常")
    void contextLoads() {
        // TDD-Red: 后续迭代补充对话接口、RAG 检索、MCP 工具调用测试
    }

    @Test
    @DisplayName("AiApplication 主类存在且可实例化")
    void mainClassExists() {
        AiApplication app = new AiApplication();
        assertNotNull(app, "AiApplication 应可实例化");
    }
}
