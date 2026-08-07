package com.campusarrive.parent.service;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务：生成、存储、校验、限频防刷。
 *
 * <p>规格来源：FR-03-01 —
 * 验证码 6 位数字、5 分钟有效、限频防刷（同一手机号 60 秒内仅允许 1 次请求）、
 * 连续错误 5 次锁定 30 分钟。</p>
 */
@Slf4j
public class VerificationCodeService {

    private final SecureRandom secureRandom = new SecureRandom();

    private final int codeLength;
    private final long codeExpirySeconds;
    private final int maxErrorCount;
    private final long lockDurationSeconds;
    private final long rateLimitWindowSeconds;
    private final int rateLimitMaxRequests;

    /** 手机号 → 验证码记录。 */
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    /** 手机号 → 错误次数。 */
    private final Map<String, Integer> errorCount = new ConcurrentHashMap<>();

    /** 手机号 → 锁定截止时间戳。 */
    private final Map<String, Instant> lockUntil = new ConcurrentHashMap<>();

    /** 手机号 → 请求时间戳列表（限频窗口）。 */
    private final Map<String, Instant[]> requestTimestamps = new ConcurrentHashMap<>();

    /**
     * @param codeLength             验证码长度
     * @param codeExpirySeconds      验证码有效期（秒）
     * @param maxErrorCount          最大错误次数
     * @param lockDurationSeconds    锁定时长（秒）
     * @param rateLimitWindowSeconds 限频窗口（秒）
     * @param rateLimitMaxRequests   窗口内最大请求数
     */
    public VerificationCodeService(int codeLength, long codeExpirySeconds,
                                   int maxErrorCount, long lockDurationSeconds,
                                   long rateLimitWindowSeconds, int rateLimitMaxRequests) {
        this.codeLength = codeLength;
        this.codeExpirySeconds = codeExpirySeconds;
        this.maxErrorCount = maxErrorCount;
        this.lockDurationSeconds = lockDurationSeconds;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    /**
     * 生成 6 位数字验证码。
     *
     * @return 验证码字符串（如 "836201"）
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 检查限频：同一手机号在窗口内是否超过最大请求数。
     *
     * @param phone 家长手机号
     * @return true 表示已被限频（应拒绝请求）
     */
    public boolean isRateLimited(String phone) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(rateLimitWindowSeconds);

        Instant[] timestamps = requestTimestamps.get(phone);
        if (timestamps == null) {
            return false;
        }
        int count = 0;
        for (Instant ts : timestamps) {
            if (ts != null && ts.isAfter(cutoff)) {
                count++;
            }
        }
        return count >= rateLimitMaxRequests;
    }

    /**
     * 记录请求时间戳（用于限频计数）。
     *
     * @param phone 家长手机号
     * @param now   请求时间
     */
    public void recordRequest(String phone, Instant now) {
        Instant cutoff = now.minusSeconds(rateLimitWindowSeconds);
        Instant[] timestamps = requestTimestamps.computeIfAbsent(phone, k -> new Instant[rateLimitMaxRequests + 1]);

        // 清理过期时间戳并记录新的
        int writeIdx = 0;
        for (Instant ts : timestamps) {
            if (ts != null && ts.isAfter(cutoff)) {
                timestamps[writeIdx++] = ts;
            }
        }
        if (writeIdx < timestamps.length) {
            timestamps[writeIdx] = now;
        } else {
            // 数组已满，覆盖最早的一个
            timestamps[0] = now;
        }
    }

    /**
     * 生成并存储验证码。
     *
     * <p>调用前应先检查 {@link #isRateLimited} 和 {@link #isLocked}。</p>
     *
     * @param phone 家长手机号
     * @return 生成的验证码
     */
    public String generateAndStore(String phone) {
        String code = generateCode();
        codeStore.put(phone, new CodeEntry(code, Instant.now().plusSeconds(codeExpirySeconds)));
        log.info("验证码已生成: phone={}", phone);
        return code;
    }

    /**
     * 校验验证码。
     *
     * @param phone 家长手机号
     * @param code  待校验的验证码
     * @return 校验结果
     */
    public VerifyResult verify(String phone, String code) {
        // 检查是否被锁定
        if (isLocked(phone)) {
            return VerifyResult.locked();
        }

        CodeEntry entry = codeStore.get(phone);
        if (entry == null) {
            incrementError(phone);
            return VerifyResult.notFound();
        }

        // 检查是否过期
        if (Instant.now().isAfter(entry.expiry())) {
            codeStore.remove(phone);
            incrementError(phone);
            return VerifyResult.expired();
        }

        // 校验验证码
        if (!entry.code().equals(code)) {
            incrementError(phone);
            return VerifyResult.wrong();
        }

        // 校验成功，清除记录
        codeStore.remove(phone);
        errorCount.remove(phone);
        lockUntil.remove(phone);
        return VerifyResult.success();
    }

    /**
     * 检查手机号是否因错误次数过多被锁定。
     *
     * @param phone 家长手机号
     * @return true 表示已被锁定
     */
    public boolean isLocked(String phone) {
        Instant until = lockUntil.get(phone);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            // 锁定已过期，清理
            lockUntil.remove(phone);
            errorCount.remove(phone);
            return false;
        }
        return true;
    }

    /**
     * 获取剩余锁定时间（秒）。
     *
     * @param phone 家长手机号
     * @return 剩余锁定秒数，未锁定返回 0
     */
    public long getRemainingLockSeconds(String phone) {
        Instant until = lockUntil.get(phone);
        if (until == null || Instant.now().isAfter(until)) {
            return 0;
        }
        return until.getEpochSecond() - Instant.now().getEpochSecond();
    }

    /**
     * 增加错误次数计数，超限则锁定。
     */
    private void incrementError(String phone) {
        int count = errorCount.merge(phone, 1, Integer::sum);
        if (count >= maxErrorCount) {
            lockUntil.put(phone, Instant.now().plusSeconds(lockDurationSeconds));
            log.warn("验证码错误次数超限，手机号已锁定: phone={}, count={}", phone, count);
        }
    }

    /**
     * 清除所有内部状态（验证码、错误计数、锁定、限频）。
     *
     * <p>用于测试间状态隔离。生产环境不应调用。</p>
     */
    public void clearAll() {
        codeStore.clear();
        errorCount.clear();
        lockUntil.clear();
        requestTimestamps.clear();
    }

    // ─── 内部类型 ──────────────────────────────────────────────

    /** 验证码存储记录。 */
    private record CodeEntry(String code, Instant expiry) {
    }

    /** 验证结果。 */
    public sealed interface VerifyResult permits VerifyResult.Success, VerifyResult.Expired,
            VerifyResult.Wrong, VerifyResult.NotFound, VerifyResult.Locked {

        record Success() implements VerifyResult {
        }

        record Expired() implements VerifyResult {
        }

        record Wrong() implements VerifyResult {
        }

        record NotFound() implements VerifyResult {
        }

        record Locked() implements VerifyResult {
        }

        static VerifyResult success() {
            return new Success();
        }

        static VerifyResult expired() {
            return new Expired();
        }

        static VerifyResult wrong() {
            return new Wrong();
        }

        static VerifyResult notFound() {
            return new NotFound();
        }

        static VerifyResult locked() {
            return new Locked();
        }
    }
}
