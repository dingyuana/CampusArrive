package com.campusarrive.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AI 助手服务入口类测试。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证主类可正常实例化，为后续对话接口、工作流编排测试提供基座。</p>
 *
 * <p>注：不使用 @SpringBootTest 以避免在没有 MaxKB/DeepSeek 连接的环境下加载上下文失败。
 * 上下文加载测试在集成测试阶段（需 AI 中间件环境）执行。</p>
 */
@DisplayName("UT-INFRA: AI 助手服务入口类")
class AiApplicationTest {

    @Test
    @DisplayName("AiApplication 主类存在且可实例化")
    void mainClassExists() {
        AiApplication app = new AiApplication();
        assertNotNull(app, "AiApplication 应可实例化");
    }

    @Test
    @DisplayName("AiApplication.main 方法存在")
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(AiApplication.class.getMethod("main", String[].class),
                "AiApplication 应有 main 方法");
    }
}
