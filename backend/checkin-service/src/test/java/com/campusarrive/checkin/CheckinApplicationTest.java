package com.campusarrive.checkin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报到服务入口类测试。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证主类可正常实例化，为后续报到流程控制器测试提供基座。</p>
 *
 * <p>注：不使用 @SpringBootTest 以避免在没有 RabbitMQ 连接的环境下加载上下文失败。
 * 上下文加载测试在集成测试阶段（需中间件环境）执行。</p>
 */
@DisplayName("UT-INFRA: 报到服务入口类")
class CheckinApplicationTest {

    @Test
    @DisplayName("CheckinApplication 主类存在且可实例化")
    void mainClassExists() {
        CheckinApplication app = new CheckinApplication();
        assertNotNull(app, "CheckinApplication 应可实例化");
    }

    @Test
    @DisplayName("CheckinApplication.main 方法存在")
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(CheckinApplication.class.getMethod("main", String[].class),
                "CheckinApplication 应有 main 方法");
    }
}
