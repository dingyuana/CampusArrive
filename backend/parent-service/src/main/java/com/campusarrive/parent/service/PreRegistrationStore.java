package com.campusarrive.parent.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预登记手机号存储（内存实现）。
 *
 * <p>规格来源：FR-03-01 — 仅预登记手机号可发起绑定。
 * 生产环境应替换为数据库查询，此处为开发/测试提供内存实现。</p>
 */
public class PreRegistrationStore {

    /** 手机号 → 预登记信息（studentId, studentName）。 */
    private final Map<String, PreRegistration> store = new ConcurrentHashMap<>();

    /**
     * 注册预登记手机号。
     *
     * @param phone        家长手机号
     * @param studentId    关联学生 ID
     * @param studentName  学生姓名（用于脱敏展示）
     */
    public void register(String phone, String studentId, String studentName) {
        store.put(phone, new PreRegistration(studentId, studentName));
    }

    /**
     * 检查手机号是否已预登记。
     *
     * @param phone 家长手机号
     * @return 预登记信息（含学生 ID 与姓名），未登记返回 empty
     */
    public Optional<PreRegistration> findByPhone(String phone) {
        return Optional.ofNullable(store.get(phone));
    }

    /**
     * 预登记信息记录。
     *
     * @param studentId    学生 ID
     * @param studentName  学生姓名
     */
    public record PreRegistration(String studentId, String studentName) {
    }
}
