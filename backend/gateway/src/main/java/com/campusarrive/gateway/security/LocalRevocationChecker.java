package com.campusarrive.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存版令牌吊销检查器（Mock 实现）。
 *
 * <p>规格来源：FR-04-02 — jti 吊销列表。测试环境无 Redis，使用 {@link ConcurrentHashMap}
 * 模拟吊销集合。通过 {@link RevocationChecker} 接口抽象，后续可无缝替换为 Redis 实现。</p>
 */
@Slf4j
@Component
public class LocalRevocationChecker implements RevocationChecker {

    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        return revoked.contains(jti);
    }

    /**
     * 吊销指定令牌（测试与运维用途）。
     *
     * @param jti 令牌唯一标识
     */
    public void revoke(String jti) {
        if (jti != null) {
            revoked.add(jti);
            log.info("令牌已加入吊销列表: jti={}", jti);
        }
    }

    /**
     * 清空吊销列表（测试用途）。
     */
    public void clear() {
        revoked.clear();
    }
}
