package com.campusarrive.gateway.filter;

import com.campusarrive.gateway.config.GatewayProperties;
import com.campusarrive.gateway.response.GatewayErrorWriter;
import com.campusarrive.gateway.security.ClaimsAccessors;
import com.campusarrive.gateway.security.JwtUtil;
import com.campusarrive.gateway.security.RevocationChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 鉴权过滤器。
 *
 * <p>规格来源：FR-04-02 — 统一鉴权与权限校验，未授权请求被拒并记日志。
 * 执行顺序 order = -200（在限流之前、RequestId 之后）。</p>
 *
 * <p>处理逻辑：
 * <ol>
 *   <li>白名单路径直接放行（/actuator/**、/api/v1/auth/login、/api/v1/parent/bind）。</li>
 *   <li>提取 Authorization: Bearer &lt;token&gt;，缺失或格式错误返回 401。</li>
 *   <li>解析并校验 JWT（exp/nbf/iss/aud），失败返回 401。</li>
 *   <li>检查 jti 是否在吊销列表（Redis/本地 Mock），已吊销返回 401。</li>
 *   <li>校验通过则将 role/student_id/scope/sub 注入请求头，传递下游。</li>
 * </ol>
 * </p>
 *
 * <p>注：越权（有效令牌但无资源权限）应返回 403，本骨架暂未实现资源级权限映射，
 * 后续迭代按路由-角色矩阵补充。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** Authorization 请求头名称。 */
    public static final String AUTH_HEADER = "Authorization";
    /** Bearer 前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** 下游传递的用户角色头。 */
    public static final String USER_ROLE_HEADER = "X-User-Role";
    /** 下游传递的学生 ID 头。 */
    public static final String STUDENT_ID_HEADER = "X-Student-Id";
    /** 下游传递的权限范围头。 */
    public static final String SCOPE_HEADER = "X-Scope";
    /** 下游传递的 subject 头。 */
    public static final String SUB_HEADER = "X-Sub";

    /** 鉴权失败业务码。 */
    private static final int CODE_UNAUTHORIZED = 40100;

    private final JwtUtil jwtUtil;
    private final GatewayProperties properties;
    private final RevocationChecker revocationChecker;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 白名单路径跳过鉴权
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 提取并校验 Authorization 头
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[{}] 鉴权失败：缺少有效的 Authorization 令牌, path={}",
                    exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER), path);
            return reject(exchange, "缺少有效的 Authorization 令牌");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtUtil.extractClaims(token);
            String jti = claims.getId();
            if (revocationChecker.isRevoked(jti)) {
                log.warn("[{}] 鉴权失败：令牌已被吊销, jti={}", requestIdOf(exchange), jti);
                return reject(exchange, "令牌已被吊销");
            }
            ServerHttpRequest mutated = injectUserContext(exchange, claims);
            log.debug("[{}] 鉴权通过: role={}, sub={}", requestIdOf(exchange),
                    ClaimsAccessors.role(claims), claims.getSubject());
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.warn("[{}] 鉴权失败：令牌无效或已过期, reason={}", requestIdOf(exchange), e.getMessage());
            return reject(exchange, "令牌无效或已过期");
        }
    }

    private ServerHttpRequest injectUserContext(ServerWebExchange exchange, Claims claims) {
        return exchange.getRequest().mutate()
                .header(USER_ROLE_HEADER, ClaimsAccessors.role(claims))
                .header(STUDENT_ID_HEADER, ClaimsAccessors.studentId(claims))
                .header(SCOPE_HEADER, ClaimsAccessors.scope(claims))
                .header(SUB_HEADER, claims.getSubject())
                .build();
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        return GatewayErrorWriter.write(exchange, objectMapper, HttpStatus.UNAUTHORIZED, CODE_UNAUTHORIZED, message);
    }

    private String requestIdOf(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
    }

    @Override
    public int getOrder() {
        // 鉴权在限流之前、RequestId 之后
        return -200;
    }
}
