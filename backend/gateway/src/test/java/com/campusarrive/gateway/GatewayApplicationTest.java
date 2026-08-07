package com.campusarrive.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 网关服务上下文加载测试（TDD 先行）。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证 Spring 上下文可正常启动，为后续路由/鉴权/限流过滤器测试提供基座。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UT-INFRA: 网关服务上下文加载")
class GatewayApplicationTest {

    @Test
    @DisplayName("上下文加载成功且无异常")
    void contextLoads() {
        // TDD-Red: 初始骨架阶段此测试验证上下文可加载
        // 后续迭代将补充路由过滤器、鉴权过滤器的单元测试
    }

    @Test
    @DisplayName("GatewayApplication 主类存在且可实例化")
    void mainClassExists() {
        GatewayApplication app = new GatewayApplication();
        assertNotNull(app, "GatewayApplication 应可实例化");
    }
}
