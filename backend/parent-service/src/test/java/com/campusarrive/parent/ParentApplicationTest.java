package com.campusarrive.parent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 家长端服务入口类测试。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证主类可正常实例化，为后续绑定鉴权、进度展示测试提供基座。</p>
 *
 * <p>注：不使用 @SpringBootTest 以避免在没有数据库/Redis 连接的环境下加载上下文失败。
 * 上下文加载测试在集成测试阶段（需完整中间件环境）执行。</p>
 */
@DisplayName("UT-INFRA: 家长端服务入口类")
class ParentApplicationTest {

    @Test
    @DisplayName("ParentApplication 主类存在且可实例化")
    void mainClassExists() {
        ParentApplication app = new ParentApplication();
        assertNotNull(app, "ParentApplication 应可实例化");
    }

    @Test
    @DisplayName("ParentApplication.main 方法存在")
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(ParentApplication.class.getMethod("main", String[].class),
                "ParentApplication 应有 main 方法");
    }
}
