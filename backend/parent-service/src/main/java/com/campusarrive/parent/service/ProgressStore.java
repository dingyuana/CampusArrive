package com.campusarrive.parent.service;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报到进度存储（内存实现）。
 *
 * <p>规格来源：FR-03-03 — 家长端查询孩子报到进度。
 * 存储学生的报到环节完成状态、到校状态、待办材料清单。
 * 生产环境应替换为数据库查询，此处为开发/测试提供内存实现。</p>
 *
 * <p>报到环节（4 步）：
 * <ol>
 *   <li>签到到校（checkin_success）</li>
 *   <li>缴费确认（payment_completed）</li>
 *   <li>资格核验（verified_success）</li>
 *   <li>报到完成（checkin_completed）</li>
 * </ol></p>
 */
@Slf4j
public class ProgressStore {

    /** 学生 ID → 进度记录。 */
    private final Map<String, StudentProgress> store = new ConcurrentHashMap<>();

    /**
     * 报到环节定义。
     */
    public enum CheckinStep {
        CHECKIN_SUCCESS("签到到校", "checkin_success"),
        PAYMENT_COMPLETED("缴费确认", "payment_completed"),
        VERIFIED_SUCCESS("资格核验", "verified_success"),
        CHECKIN_COMPLETED("报到完成", "checkin_completed");

        private final String displayName;
        private final String code;

        CheckinStep(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 到校状态。
     */
    public enum ArrivalStatus {
        NOT_ARRIVED("未到校"),
        ARRIVED("已到校"),
        COMPLETED("报到完成");

        private final String displayName;

        ArrivalStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 注册学生进度记录（初始状态：未到校，所有环节未完成）。
     *
     * @param studentId    学生 ID
     * @param studentName  学生姓名
     * @param materials    待办材料名称列表
     */
    public void register(String studentId, String studentName, List<String> materials) {
        List<StepStatus> steps = new ArrayList<>();
        for (CheckinStep step : CheckinStep.values()) {
            steps.add(new StepStatus(step, false, null));
        }
        store.put(studentId, new StudentProgress(studentId, studentName, steps, materials));
    }

    /**
     * 标记某环节完成。
     *
     * @param studentId 学生 ID
     * @param step      报到环节
     * @param timestamp 完成时间
     */
    public void markStepCompleted(String studentId, CheckinStep step, Instant timestamp) {
        StudentProgress progress = store.get(studentId);
        if (progress == null) {
            log.warn("学生进度记录不存在: studentId={}", studentId);
            return;
        }
        for (StepStatus status : progress.steps()) {
            if (status.step() == step) {
                progress.steps().set(progress.steps().indexOf(status),
                        new StepStatus(step, true, timestamp));
                break;
            }
        }
    }

    /**
     * 查询学生报到进度。
     *
     * @param studentId 学生 ID
     * @return 进度记录，不存在返回 empty
     */
    public Optional<StudentProgress> findByStudentId(String studentId) {
        return Optional.ofNullable(store.get(studentId));
    }

    /**
     * 根据进度计算到校状态。
     *
     * @param progress 学生进度
     * @return 到校状态
     */
    public ArrivalStatus getArrivalStatus(StudentProgress progress) {
        boolean checkinDone = progress.steps().stream()
                .filter(s -> s.step() == CheckinStep.CHECKIN_SUCCESS)
                .findFirst()
                .map(StepStatus::completed)
                .orElse(false);

        boolean allDone = progress.steps().stream()
                .allMatch(StepStatus::completed);

        if (allDone) {
            return ArrivalStatus.COMPLETED;
        }
        if (checkinDone) {
            return ArrivalStatus.ARRIVED;
        }
        return ArrivalStatus.NOT_ARRIVED;
    }

    /**
     * 获取待办材料列表（仅名称）。
     *
     * @param progress 学生进度
     * @return 材料名称列表
     */
    public List<String> getPendingMaterials(StudentProgress progress) {
        return new ArrayList<>(progress.materials());
    }

    /**
     * 清除所有记录（测试间隔离用）。
     */
    public void clearAll() {
        store.clear();
    }

    // ─── 内部类型 ──────────────────────────────────────────────

    /** 学生进度记录。 */
    public record StudentProgress(String studentId, String studentName,
                                   List<StepStatus> steps, List<String> materials) {
    }

    /** 环节完成状态。 */
    public record StepStatus(CheckinStep step, boolean completed, Instant timestamp) {
    }
}
