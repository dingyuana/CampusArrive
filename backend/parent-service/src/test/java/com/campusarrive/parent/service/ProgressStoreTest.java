package com.campusarrive.parent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-PRG-001：报到进度存储单元测试。
 *
 * <p>规格来源：FR-03-03 — 家长端查询孩子报到进度。
 * 覆盖学生注册、环节标记、到校状态计算、待办材料获取等核心逻辑。</p>
 */
@DisplayName("UT-PRG-001：报到进度存储")
class ProgressStoreTest {

    private ProgressStore store;

    @BeforeEach
    void setUp() {
        store = new ProgressStore();
    }

    @Nested
    @DisplayName("学生注册")
    class StudentRegistration {

        @Test
        @DisplayName("注册后生成 4 个未完成环节")
        void testRegisterCreatesFourIncompleteSteps() {
            store.register("STU001", "张三", List.of("身份证"));

            Optional<ProgressStore.StudentProgress> opt = store.findByStudentId("STU001");
            assertThat(opt).isPresent();

            ProgressStore.StudentProgress progress = opt.get();
            assertThat(progress.steps()).hasSize(4);
            assertThat(progress.steps()).allMatch(s -> !s.completed());
        }

        @Test
        @DisplayName("注册后待办材料列表正确")
        void testRegisterMaterials() {
            List<String> materials = List.of("身份证复印件", "体检报告", "一寸照片");
            store.register("STU001", "张三", materials);

            Optional<ProgressStore.StudentProgress> opt = store.findByStudentId("STU001");
            assertThat(opt).isPresent();
            assertThat(store.getPendingMaterials(opt.get()))
                    .containsExactlyElementsOf(materials);
        }

        @Test
        @DisplayName("注册后学生姓名和 ID 正确")
        void testRegisterStudentInfo() {
            store.register("STU001", "张三", List.of());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(progress.studentId()).isEqualTo("STU001");
            assertThat(progress.studentName()).isEqualTo("张三");
        }

        @Test
        @DisplayName("未注册学生查询返回 empty")
        void testUnregisteredStudentReturnsEmpty() {
            assertThat(store.findByStudentId("UNKNOWN")).isEmpty();
        }
    }

    @Nested
    @DisplayName("环节标记")
    class StepCompletion {

        @Test
        @DisplayName("标记环节完成后状态变为 completed")
        void testMarkStepCompleted() {
            store.register("STU001", "张三", List.of());
            Instant timestamp = Instant.now();

            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, timestamp);

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            ProgressStore.StepStatus checkinStep = progress.steps().stream()
                    .filter(s -> s.step() == ProgressStore.CheckinStep.CHECKIN_SUCCESS)
                    .findFirst()
                    .orElseThrow();

            assertThat(checkinStep.completed()).isTrue();
            assertThat(checkinStep.timestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("标记多个环节完成后各自状态正确")
        void testMarkMultipleStepsCompleted() {
            store.register("STU001", "张三", List.of());
            Instant t1 = Instant.now();
            Instant t2 = Instant.now().plusSeconds(60);

            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, t1);
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.PAYMENT_COMPLETED, t2);

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            long completedCount = progress.steps().stream()
                    .filter(ProgressStore.StepStatus::completed)
                    .count();
            assertThat(completedCount).isEqualTo(2);
        }

        @Test
        @DisplayName("标记不存在学生的环节不报错")
        void testMarkStepForNonExistentStudent() {
            store.markStepCompleted("UNKNOWN",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, Instant.now());
            // 不应抛出异常
            assertThat(store.findByStudentId("UNKNOWN")).isEmpty();
        }

        @Test
        @DisplayName("已完成的环节可以重新标记（更新时间戳）")
        void testReMarkStepCompleted() {
            store.register("STU001", "张三", List.of());
            Instant t1 = Instant.now();
            Instant t2 = t1.plusSeconds(120);

            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, t1);
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, t2);

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            ProgressStore.StepStatus step = progress.steps().stream()
                    .filter(s -> s.step() == ProgressStore.CheckinStep.CHECKIN_SUCCESS)
                    .findFirst()
                    .orElseThrow();

            assertThat(step.completed()).isTrue();
            assertThat(step.timestamp()).isEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("到校状态计算")
    class ArrivalStatusCalculation {

        @Test
        @DisplayName("无任何环节完成时状态为 NOT_ARRIVED")
        void testNoStepsCompletedReturnsNotArrived() {
            store.register("STU001", "张三", List.of());
            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();

            assertThat(store.getArrivalStatus(progress))
                    .isEqualTo(ProgressStore.ArrivalStatus.NOT_ARRIVED);
        }

        @Test
        @DisplayName("仅完成签到到校时状态为 ARRIVED")
        void testOnlyCheckinCompletedReturnsArrived() {
            store.register("STU001", "张三", List.of());
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, Instant.now());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(store.getArrivalStatus(progress))
                    .isEqualTo(ProgressStore.ArrivalStatus.ARRIVED);
        }

        @Test
        @DisplayName("签到 + 缴费完成但未全部完成时状态为 ARRIVED")
        void testPartialCompletionReturnsArrived() {
            store.register("STU001", "张三", List.of());
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.CHECKIN_SUCCESS, Instant.now());
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.PAYMENT_COMPLETED, Instant.now());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(store.getArrivalStatus(progress))
                    .isEqualTo(ProgressStore.ArrivalStatus.ARRIVED);
        }

        @Test
        @DisplayName("全部环节完成时状态为 COMPLETED")
        void testAllStepsCompletedReturnsCompleted() {
            store.register("STU001", "张三", List.of());
            for (ProgressStore.CheckinStep step : ProgressStore.CheckinStep.values()) {
                store.markStepCompleted("STU001", step, Instant.now());
            }

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(store.getArrivalStatus(progress))
                    .isEqualTo(ProgressStore.ArrivalStatus.COMPLETED);
        }

        @Test
        @DisplayName("未签到但其他环节完成时状态为 NOT_ARRIVED")
        void testOtherStepsCompletedButNoCheckinReturnsNotArrived() {
            store.register("STU001", "张三", List.of());
            // 只完成缴费，未完成签到
            store.markStepCompleted("STU001",
                    ProgressStore.CheckinStep.PAYMENT_COMPLETED, Instant.now());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(store.getArrivalStatus(progress))
                    .isEqualTo(ProgressStore.ArrivalStatus.NOT_ARRIVED);
        }
    }

    @Nested
    @DisplayName("待办材料")
    class PendingMaterials {

        @Test
        @DisplayName("待办材料列表正确返回")
        void testGetPendingMaterials() {
            List<String> materials = List.of("材料A", "材料B", "材料C");
            store.register("STU001", "张三", materials);

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            List<String> result = store.getPendingMaterials(progress);
            assertThat(result).containsExactlyElementsOf(materials);
        }

        @Test
        @DisplayName("空待办材料列表返回空列表")
        void testEmptyMaterialsReturnsEmptyList() {
            store.register("STU001", "张三", List.of());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(store.getPendingMaterials(progress)).isEmpty();
        }

        @Test
        @DisplayName("返回的待办材料列表是副本（修改不影响原数据）")
        void testReturnedMaterialsListIsCopy() {
            store.register("STU001", "张三", List.of("材料A"));
            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();

            List<String> result = store.getPendingMaterials(progress);
            result.add("新增材料");

            List<String> resultAgain = store.getPendingMaterials(progress);
            assertThat(resultAgain).hasSize(1).containsExactly("材料A");
        }
    }

    @Nested
    @DisplayName("数据清除")
    class ClearAll {

        @Test
        @DisplayName("clearAll 后所有记录被清除")
        void testClearAllRemovesAllRecords() {
            store.register("STU001", "张三", List.of());
            store.register("STU002", "李四", List.of());

            store.clearAll();

            assertThat(store.findByStudentId("STU001")).isEmpty();
            assertThat(store.findByStudentId("STU002")).isEmpty();
        }

        @Test
        @DisplayName("clearAll 后可重新注册")
        void testCanRegisterAfterClearAll() {
            store.register("STU001", "张三", List.of());
            store.clearAll();
            store.register("STU001", "李四", List.of());

            ProgressStore.StudentProgress progress = store.findByStudentId("STU001").get();
            assertThat(progress.studentName()).isEqualTo("李四");
        }
    }

    @Nested
    @DisplayName("枚举值验证")
    class EnumValues {

        @Test
        @DisplayName("CheckinStep 包含 4 个环节")
        void testCheckinStepHasFourValues() {
            assertThat(ProgressStore.CheckinStep.values()).hasSize(4);
        }

        @Test
        @DisplayName("CheckinStep 的 code 和 displayName 正确")
        void testCheckinStepCodes() {
            assertThat(ProgressStore.CheckinStep.CHECKIN_SUCCESS.getCode())
                    .isEqualTo("checkin_success");
            assertThat(ProgressStore.CheckinStep.CHECKIN_SUCCESS.getDisplayName())
                    .isEqualTo("签到到校");
            assertThat(ProgressStore.CheckinStep.PAYMENT_COMPLETED.getCode())
                    .isEqualTo("payment_completed");
            assertThat(ProgressStore.CheckinStep.VERIFIED_SUCCESS.getCode())
                    .isEqualTo("verified_success");
            assertThat(ProgressStore.CheckinStep.CHECKIN_COMPLETED.getCode())
                    .isEqualTo("checkin_completed");
        }

        @Test
        @DisplayName("ArrivalStatus 包含 3 个状态")
        void testArrivalStatusHasThreeValues() {
            assertThat(ProgressStore.ArrivalStatus.values()).hasSize(3);
        }

        @Test
        @DisplayName("ArrivalStatus 的 displayName 正确")
        void testArrivalStatusDisplayNames() {
            assertThat(ProgressStore.ArrivalStatus.NOT_ARRIVED.getDisplayName())
                    .isEqualTo("未到校");
            assertThat(ProgressStore.ArrivalStatus.ARRIVED.getDisplayName())
                    .isEqualTo("已到校");
            assertThat(ProgressStore.ArrivalStatus.COMPLETED.getDisplayName())
                    .isEqualTo("报到完成");
        }
    }
}
