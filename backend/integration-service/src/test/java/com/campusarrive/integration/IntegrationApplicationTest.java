package com.campusarrive.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 集成中间件服务上下文加载测试（TDD 先行）。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证 Spring 上下文可正常启动，为后续事件链、CDC 同步测试提供基座。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UT-INFRA: 集成中间件服务上下文加载")
class IntegrationApplicationTest {

    @Test
    @DisplayName("上下文加载成功且无异常")
    void contextLoads() {
        // TDD-Red: 后续迭代补充 RabbitMQ 事件链、CDC 同步、主数据映射测试
    }

    @Test
    @DisplayName("IntegrationApplication 主类存在且可实例化")
    void mainClassExists() {
        IntegrationApplication app = new IntegrationApplication();
        assertNotNull(app, "IntegrationApplication 应可实例化");
    }
}
