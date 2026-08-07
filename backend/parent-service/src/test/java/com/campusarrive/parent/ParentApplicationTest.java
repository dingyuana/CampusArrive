package com.campusarrive.parent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 家长端服务上下文加载测试（TDD 先行）。
 *
 * <p>规格来源：INFRA-1.1 — 项目骨架冒烟测试。
 * 验证 Spring 上下文可正常启动，为后续绑定鉴权、进度展示测试提供基座。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UT-INFRA: 家长端服务上下文加载")
class ParentApplicationTest {

    @Test
    @DisplayName("上下文加载成功且无异常")
    void contextLoads() {
        // TDD-Red: 后续迭代补充绑定接口、JWT 签发、脱敏展示测试
    }

    @Test
    @DisplayName("ParentApplication 主类存在且可实例化")
    void mainClassExists() {
        ParentApplication app = new ParentApplication();
        assertNotNull(app, "ParentApplication 应可实例化");
    }
}
