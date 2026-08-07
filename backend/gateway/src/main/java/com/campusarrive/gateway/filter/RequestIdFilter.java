package com.campusarrive.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪请求 ID 过滤器。
 *
 * <p>规格来源：FR-04-01 — 请求头 X-Request-Id，未提供时网关自动生成 UUID，
 * 并将 request_id 传递到下游服务。该过滤器最先执行（order = -300），
 * 确保后续鉴权/限流过滤器及错误响应均可附带 request_id。</p>
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    /** 链路追踪请求头名称。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Arrange：读取已有 X-Request-Id，缺失则生成 UUID
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        final String finalRequestId = requestId;

        // Act：将 request_id 写入请求头（覆盖/补全），传递下游
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, finalRequestId))
                .build();

        log.debug("[{}] 请求进入网关: {} {}",
                finalRequestId, exchange.getRequest().getMethod(), exchange.getRequest().getPath());

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // 最先执行
        return -300;
    }
}
