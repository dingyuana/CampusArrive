package com.campusarrive.gateway.security;

/**
 * 令牌吊销检查器。
 *
 * <p>规格来源：FR-04-02 — jti 存入 Redis 吊销列表，网关校验时拒绝已吊销令牌。
 * 测试环境无 Redis，暂用本地实现；生产环境替换为 Redis 实现即可（依赖倒置）。</p>
 */
public interface RevocationChecker {

    /**
     * 判断指定 jti 是否已被吊销。
     *
     * @param jti 令牌唯一标识
     * @return 已吊销返回 true
     */
    boolean isRevoked(String jti);
}
