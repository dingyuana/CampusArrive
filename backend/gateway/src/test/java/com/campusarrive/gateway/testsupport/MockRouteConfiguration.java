package com.campusarrive.gateway.testsupport;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测试用路由配置：将所有服务路由指向 {@link MockBackendSupport} 启动的 Mock 后端。
 *
 * <p>规格来源：CT-MW-001~003 — 契约测试需要下游服务接收转发请求。
 * 以 {@link RouteLocator} Bean 形式定义路由，规避 application.yml 中 lb:// 路由
 * 在无注册中心环境下的绑定问题（测试 profile 已通过 routes: [] 清空生产路由）。</p>
 *
 * <p>各集成测试通过 {@code @Import(MockRouteConfiguration.class)} 引入。</p>
 */
@Configuration
public class MockRouteConfiguration {

    @Bean
    public RouteLocator mockRouteLocator(RouteLocatorBuilder builder) {
        String uri = "http://127.0.0.1:" + MockBackendSupport.PORT;
        return builder.routes()
                .route("checkin-service", r -> r.path("/api/v1/checkin/**").uri(uri))
                .route("ai-service", r -> r.path("/api/v1/ai/**").uri(uri))
                .route("parent-service", r -> r.path("/api/v1/parent/**").uri(uri))
                .route("integration-service", r -> r.path("/api/v1/integration/**").uri(uri))
                .route("auth-service", r -> r.path("/api/v1/auth/**").uri(uri))
                .build();
    }
}
