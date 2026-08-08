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
        store.put(phone, new PreRegistration(phone, studentId, studentName));
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
     * 按学生 ID 反查预登记信息（PARENT-4.3 消息推送用）。
     *
     * @param studentId 学生 ID
     * @return 预登记信息（含家长手机号与学生姓名），未找到返回 empty
     */
    public Optional<PreRegistration> findByStudentId(String studentId) {
        return store.values().stream()
                .filter(reg -> reg.studentId().equals(studentId))
                .findFirst();
    }

    /**
     * 按学生 ID 反查家长手机号（便捷方法）。
     *
     * @param studentId 学生 ID
     * @return 家长手机号，未找到返回 empty
     */
    public Optional<String> findPhoneByStudentId(String studentId) {
        return findByStudentId(studentId)
                .map(PreRegistration::phone);
    }

    /**
     * 预登记信息记录。
     *
     * @param phone        家长手机号
     * @param studentId    学生 ID
     * @param studentName  学生姓名
     */
    public record PreRegistration(String phone, String studentId, String studentName) {
    }
}
