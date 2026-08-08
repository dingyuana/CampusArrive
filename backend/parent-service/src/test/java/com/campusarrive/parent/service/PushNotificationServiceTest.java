package com.campusarrive.parent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-PUSH-001：家长消息推送服务单元测试。
 *
 * <p>规格来源：FR-03-06 — 学生签到后推送家长端通知，
 * 推送内容仅含到校提示不含敏感信息，未绑定家长静默丢弃。</p>
 */
@DisplayName("UT-PUSH-001：家长消息推送服务")
class PushNotificationServiceTest {

    private PreRegistrationStore preRegistrationStore;
    private PushNotificationService pushNotificationService;

    @BeforeEach
    void setUp() {
        preRegistrationStore = new PreRegistrationStore();
        pushNotificationService = new PushNotificationService(preRegistrationStore);
    }

    @Nested
    @DisplayName("签到通知推送")
    class NotifyParent {

        @Test
        @DisplayName("已绑定家长的学生签到后推送成功")
        void testBoundStudentPushSuccess() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            boolean result = pushNotificationService.notifyParent("STU20260001");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("未绑定家长的学生签到后静默丢弃")
        void testUnboundStudentSilentDiscard() {
            boolean result = pushNotificationService.notifyParent("UNKNOWN_STUDENT");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("不同学生分别推送各自家长")
        void testDifferentStudentsPushDifferentParents() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");
            preRegistrationStore.register("13987654321", "STU20260002", "李四");

            boolean r1 = pushNotificationService.notifyParent("STU20260001");
            boolean r2 = pushNotificationService.notifyParent("STU20260002");

            assertThat(r1).isTrue();
            assertThat(r2).isTrue();
        }

        @Test
        @DisplayName("同一学生重复调用仍可推送（非幂等控制范围）")
        void testRepeatCallStillPushes() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            assertThat(pushNotificationService.notifyParent("STU20260001")).isTrue();
            assertThat(pushNotificationService.notifyParent("STU20260001")).isTrue();
        }

        @Test
        @DisplayName("studentId 为 null 时静默丢弃")
        void testNullStudentIdSilentDiscard() {
            boolean result = pushNotificationService.notifyParent(null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("studentId 为空字符串时静默丢弃")
        void testEmptyStudentIdSilentDiscard() {
            boolean result = pushNotificationService.notifyParent("");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("推送内容脱敏")
    class ContentDesensitization {

        @Test
        @DisplayName("推送内容包含到校提示")
        void testContentContainsArrivalNotice() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).contains("到校");
        }

        @Test
        @DisplayName("推送内容不含学生姓名")
        void testContentNoStudentName() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("张三");
            assertThat(content).doesNotContain("李四");
            assertThat(content).doesNotContain("姓名");
        }

        @Test
        @DisplayName("推送内容不含身份证号")
        void testContentNoIdCard() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("身份证");
            assertThat(content).doesNotContain("id_card");
        }

        @Test
        @DisplayName("推送内容不含手机号")
        void testContentNoPhone() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("138");
            assertThat(content).doesNotContain("phone");
        }

        @Test
        @DisplayName("推送内容不含签到地点")
        void testContentNoCheckinPoint() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("南门");
            assertThat(content).doesNotContain("签到点");
        }

        @Test
        @DisplayName("推送内容不含签到时间")
        void testContentNoCheckinTime() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).doesNotContain("签到时间");
            assertThat(content).doesNotContain("checkinTime");
        }

        @Test
        @DisplayName("推送内容为固定安全文案")
        void testContentIsFixedSafeText() {
            String content = pushNotificationService.buildNotificationContent();

            assertThat(content).isEqualTo("您的孩子已到校，请放心。");
        }
    }

    @Nested
    @DisplayName("PreRegistrationStore 反查")
    class PreRegistrationReverseLookup {

        @Test
        @DisplayName("按 studentId 可查到家长手机号")
        void testFindByStudentIdReturnsPhone() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            var opt = preRegistrationStore.findByStudentId("STU20260001");

            assertThat(opt).isPresent();
            assertThat(opt.get().phone()).isEqualTo("13812345678");
            assertThat(opt.get().studentName()).isEqualTo("张三丰");
        }

        @Test
        @DisplayName("未注册的 studentId 查询返回 empty")
        void testUnregisteredStudentIdReturnsEmpty() {
            assertThat(preRegistrationStore.findByStudentId("UNKNOWN")).isEmpty();
        }

        @Test
        @DisplayName("findPhoneByStudentId 返回手机号")
        void testFindPhoneByStudentId() {
            preRegistrationStore.register("13987654321", "STU20260002", "李四");

            assertThat(preRegistrationStore.findPhoneByStudentId("STU20260002"))
                    .contains("13987654321");
        }

        @Test
        @DisplayName("findByPhone 仍正常工作")
        void testFindByPhoneStillWorks() {
            preRegistrationStore.register("13812345678", "STU20260001", "张三丰");

            var opt = preRegistrationStore.findByPhone("13812345678");

            assertThat(opt).isPresent();
            assertThat(opt.get().studentId()).isEqualTo("STU20260001");
            assertThat(opt.get().phone()).isEqualTo("13812345678");
        }
    }
}
