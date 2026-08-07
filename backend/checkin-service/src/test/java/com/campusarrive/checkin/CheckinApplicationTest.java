package com.campusarrive.checkin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报到服务上下文加载测试（TDD 先行）。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证 Spring 上下文可正常启动，为后续报到流程控制器测试提供基座。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UT-INFRA: 报到服务上下文加载")
class CheckinApplicationTest {

    @Test
    @DisplayName("上下文加载成功且无异常")
    void contextLoads() {
        // TDD-Red: 后续迭代补充报到流程、进度追踪等控制器测试
    }

    @Test
    @DisplayName("CheckinApplication 主类存在且可实例化")
    void mainClassExists() {
        CheckinApplication app = new CheckinApplication();
        assertNotNull(app, "CheckinApplication 应可实例化");
    }
}
