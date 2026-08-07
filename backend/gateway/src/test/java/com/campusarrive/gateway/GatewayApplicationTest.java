package com.campusarrive.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 网关服务入口类测试。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证主类可正常实例化，为后续路由/鉴权/限流过滤器测试提供基座。</p>
 *
 * <p>注：不使用 @SpringBootTest 以避免在没有 Redis/下游服务连接的环境下加载上下文失败。
 * 上下文加载测试在集成测试阶段（需完整中间件环境）执行。</p>
 */
@DisplayName("UT-INFRA: 网关服务入口类")
class GatewayApplicationTest {

    @Test
    @DisplayName("GatewayApplication 主类存在且可实例化")
    void mainClassExists() {
        GatewayApplication app = new GatewayApplication();
        assertNotNull(app, "GatewayApplication 应可实例化");
    }

    @Test
    @DisplayName("GatewayApplication.main 方法存在")
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(GatewayApplication.class.getMethod("main", String[].class),
                "GatewayApplication 应有 main 方法");
    }
}
