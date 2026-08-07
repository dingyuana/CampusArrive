package com.campusarrive.parent.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌吊销存储（内存实现）。
 *
 * <p>规格来源：FR-03-02 — 令牌可吊销，已吊销令牌访问被拒返回 401。
 * 生产环境应使用 Redis 实现分布式吊销列表。</p>
 */
public class TokenRevocationStore {

    /** 已吊销的 jti 集合。 */
    private final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();

    /** 手机号 → 已吊销 jti 集合（用于按手机号批量吊销）。 */
    private final Map<String, Set<String>> revokedByPhone = new ConcurrentHashMap<>();

    /**
     * 吊销指定令牌。
     *
     * @param jti   令牌唯一标识
     * @param phone 关联手机号
     */
    public void revoke(String jti, String phone) {
        revokedJtis.add(jti);
        revokedByPhone.computeIfAbsent(phone, k -> ConcurrentHashMap.newKeySet()).add(jti);
    }

    /**
     * 检查令牌是否已吊销。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已吊销
     */
    public boolean isRevoked(String jti) {
        return revokedJtis.contains(jti);
    }

    /**
     * 批量吊销指定手机号下所有令牌。
     *
     * @param phone 家长手机号
     * @return 吊销的令牌数量
     */
    public int revokeAllByPhone(String phone) {
        Set<String> jtis = revokedByPhone.get(phone);
        if (jtis == null) {
            return 0;
        }
        revokedJtis.addAll(jtis);
        return jtis.size();
    }

    /**
     * 清除所有吊销记录（测试间隔离用）。
     */
    public void clearAll() {
        revokedJtis.clear();
        revokedByPhone.clear();
    }
}
