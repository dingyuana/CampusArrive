package com.campusarrive.gateway.filter;

import com.campusarrive.gateway.config.GatewayProperties;
import com.campusarrive.gateway.ratelimit.TokenBucketRateLimiter;
import com.campusarrive.gateway.response.GatewayErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 令牌桶限流过滤器。
 *
 * <p>规格来源：FR-04-03 / API 设计文档 — 按接口与租户限流，超限返回 429。
 * 执行顺序 order = -100（鉴权之后），可读到鉴权注入的 X-Student-Id 用作限流维度。</p>
 *
 * <p>限流规则：
 * <ul>
 *   <li>AI 对话：10 次/分钟/学生（令牌桶）</li>
 *   <li>家长绑定：5 次/分钟/手机号</li>
 *   <li>全局兜底：500 次/秒（后续迭代）</li>
 * </ul>
 * 超限返回 HTTP 429，业务码 90001（学生限流），并附带 Retry-After 头。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    /** 学生限流业务码。 */
    private static final int CODE_RATE_LIMIT_STUDENT = 90001;

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    /** 按路径前缀构建的限流器实例（每条规则一个桶配置）。 */
    private final ConcurrentMap<String, TokenBucketRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    @PostConstruct
    void initRateLimiters() {
        for (GatewayProperties.RateLimitRule rule : properties.getRateLimit()) {
            rateLimiters.put(rule.getPathPrefix(),
                    new TokenBucketRateLimiter(rule.getBucketSize(), rule.getRefillRatePerMinute()));
            log.info("注册限流规则: pathPrefix={}, bucketSize={}, refillRate={}/min",
                    rule.getPathPrefix(), rule.getBucketSize(), rule.getRefillRatePerMinute());
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        GatewayProperties.RateLimitRule rule = findRule(path);
        if (rule == null) {
            // 无匹配规则，直接放行
            return chain.filter(exchange);
        }

        String key = buildKey(path, exchange, rule);
        TokenBucketRateLimiter limiter = rateLimiters.get(rule.getPathPrefix());
        if (!limiter.tryAcquire(key)) {
            long retryAfter = computeRetryAfter(rule.getRefillRatePerMinute());
            log.warn("[{}] 限流触发: path={}, key={}, retryAfter={}s",
                    exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER),
                    path, key, retryAfter);
            return rejectWithRateLimit(exchange, retryAfter);
        }
        return chain.filter(exchange);
    }

    private GatewayProperties.RateLimitRule findRule(String path) {
        return properties.getRateLimit().stream()
                .filter(rule -> matches(path, rule.getPathPrefix()))
                .findFirst()
                .orElse(null);
    }

    /** 精确匹配路径前缀，避免 /api/v1/ai 误匹配 /api/v1/airplane。 */
    private boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * 构建限流维度键：已鉴权请求按学生 ID，否则按客户端 IP。
     */
    private String buildKey(String path, ServerWebExchange exchange, GatewayProperties.RateLimitRule rule) {
        String studentId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.STUDENT_ID_HEADER);
        if (studentId != null && !studentId.isBlank()) {
            return rule.getPathPrefix() + ":student:" + studentId;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote != null ? remote.getAddress().getHostAddress() : "unknown";
        return rule.getPathPrefix() + ":ip:" + ip;
    }

    /** 计算建议重试间隔（秒）：补充 1 个令牌所需时间的上取整，至少 1 秒。 */
    private long computeRetryAfter(long refillRatePerMinute) {
        if (refillRatePerMinute <= 0) {
            return 1;
        }
        return Math.max(1, (long) Math.ceil(60.0 / refillRatePerMinute));
    }

    private Mono<Void> rejectWithRateLimit(ServerWebExchange exchange, long retryAfter) {
        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        return GatewayErrorWriter.write(exchange, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                CODE_RATE_LIMIT_STUDENT, "请求过于频繁，请稍后重试");
    }

    /**
     * 清空所有限流桶（测试用途）。
     */
    public void clearAll() {
        rateLimiters.values().forEach(TokenBucketRateLimiter::clear);
    }

    @Override
    public int getOrder() {
        // 鉴权之后执行
        return -100;
    }
}
