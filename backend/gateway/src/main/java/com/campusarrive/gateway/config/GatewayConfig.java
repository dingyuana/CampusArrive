package com.campusarrive.gateway.config;

import com.campusarrive.gateway.security.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.HashSet;

/**
 * 网关配置类。
 *
 * <p>规格来源：FR-04-01~04 — 注册 {@link GatewayProperties} 配置属性绑定，
 * 基于 properties 构造 {@link JwtUtil}，并以 {@link RouteLocator} Bean
 * 形式定义生产环境路由（测试环境由 {@code MockRouteConfiguration} 提供）。</p>
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    /**
     * JWT 工具 Bean，使用网关配置中的密钥、签发方与受众集合构造。
     *
     * @param properties 网关配置属性
     * @return JwtUtil 实例
     */
    @Bean
    public JwtUtil jwtUtil(GatewayProperties properties) {
        return new JwtUtil(
                properties.getJwtSecret(),
                properties.getJwtIssuer(),
                new HashSet<>(properties.getJwtAudiences()));
    }

    /**
     * 生产环境路由定义（RouteLocator Bean）。
     *
     * <p>规格来源：FR-04-01 — 统一入口按路径与规则路由至后端服务。
     * 路由规则：
     * <ul>
     *   <li>/api/v1/checkin/** → checkin-service</li>
     *   <li>/api/v1/ai/** → ai-service</li>
     *   <li>/api/v1/parent/** → parent-service</li>
     *   <li>/api/v1/integration/** → integration-service</li>
     *   <li>/api/v1/auth/** → checkin-service（认证相关）</li>
     * </ul>
     * 使用 {@code lb://} 协议配合服务发现（Nacos/Eureka）做负载均衡。</p>
     *
     * <p>仅在非 test profile 下激活；测试环境路由由
     * {@code MockRouteConfiguration} 以 Bean 形式指向 Mock 后端。</p>
     *
     * @param builder Spring Cloud Gateway 路由构建器
     * @return 路由定位器
     */
    @Bean
    @Profile("!test")
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("checkin-service", r -> r.path("/api/v1/checkin/**")
                        .uri("lb://checkin-service"))
                .route("ai-service", r -> r.path("/api/v1/ai/**")
                        .uri("lb://ai-service"))
                .route("parent-service", r -> r.path("/api/v1/parent/**")
                        .uri("lb://parent-service"))
                .route("integration-service", r -> r.path("/api/v1/integration/**")
                        .uri("lb://integration-service"))
                .route("auth-service", r -> r.path("/api/v1/auth/**")
                        .uri("lb://checkin-service"))
                .build();
    }
}
