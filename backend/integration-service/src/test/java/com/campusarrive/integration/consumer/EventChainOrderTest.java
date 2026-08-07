package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventType;
import com.campusarrive.integration.testsupport.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-MW-006：事件链顺序测试。
 *
 * <p>规格来源：FR-04-06 事件链顺序、SIM-CA-2026-08 第 5.3 节核心事件链。
 * 验证签到→缴费→核验→完成四条事件链按序触发，缺环可检测。</p>
 *
 * <p>TDD 类型：CT（契约测试）</p>
 */
@DisplayName("CT-MW-006: 事件链顺序")
class EventChainOrderTest {

    private EventChainTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new EventChainTracker();
    }

    @Nested
    @DisplayName("正常事件链顺序")
    class NormalChain {

        @Test
        @DisplayName("完整事件链顺序正确：签到→缴费→核验→完成")
        void fullChainInOrder() {
            String studentId = "20260001";

            // 按序记录事件
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());
            tracker.record(studentId, EventType.CHECKIN_COMPLETED.routingKey());

            List<String> chain = tracker.getChain(studentId);
            assertEquals(4, chain.size());
            assertEquals(EventType.CHECKIN_SUCCESS.routingKey(), chain.get(0));
            assertEquals(EventType.PAYMENT_COMPLETED.routingKey(), chain.get(1));
            assertEquals(EventType.VERIFIED_SUCCESS.routingKey(), chain.get(2));
            assertEquals(EventType.CHECKIN_COMPLETED.routingKey(), chain.get(3));

            assertTrue(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("仅签到事件时链顺序正确")
        void onlyCheckin() {
            String studentId = "20260002";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());

            assertTrue(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("签到→缴费→核验（未完成）顺序正确")
        void partialChain() {
            String studentId = "20260003";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            assertTrue(tracker.isChainOrdered(studentId));
        }
    }

    @Nested
    @DisplayName("异常事件链顺序")
    class AbnormalChain {

        @Test
        @DisplayName("签到非第一个事件 → 顺序异常")
        void checkinNotFirst() {
            String studentId = "20260004";
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());

            assertFalse(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("报到完成在签到之前 → 顺序异常")
        void completedBeforeCheckin() {
            String studentId = "20260005";
            tracker.record(studentId, EventType.CHECKIN_COMPLETED.routingKey());
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());

            assertFalse(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("报到完成在缴费之前 → 顺序异常")
        void completedBeforePayment() {
            String studentId = "20260006";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.CHECKIN_COMPLETED.routingKey());
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());

            assertFalse(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("报到完成在核验之前 → 顺序异常")
        void completedBeforeVerified() {
            String studentId = "20260007";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());
            tracker.record(studentId, EventType.CHECKIN_COMPLETED.routingKey());

            // 实际上报到完成在核验之后是正确的
            // 让我们测试报到完成在核验之前
            tracker.reset();
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.CHECKIN_COMPLETED.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            assertFalse(tracker.isChainOrdered(studentId));
        }

        @Test
        @DisplayName("无任何事件 → 顺序异常")
        void emptyChain() {
            assertFalse(tracker.isChainOrdered("20260099"));
        }
    }

    @Nested
    @DisplayName("前置条件检查")
    class Prerequisites {

        @Test
        @DisplayName("报到完成需签到+缴费+核验均已完成")
        void prerequisitesMet() {
            String studentId = "20260008";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            assertTrue(tracker.isCompletionPrerequisitesMet(studentId));
        }

        @Test
        @DisplayName("缺少缴费时前置条件不满足")
        void missingPayment() {
            String studentId = "20260009";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            assertFalse(tracker.isCompletionPrerequisitesMet(studentId));
        }

        @Test
        @DisplayName("缺少核验时前置条件不满足")
        void missingVerified() {
            String studentId = "20260010";
            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());

            assertFalse(tracker.isCompletionPrerequisitesMet(studentId));
        }

        @Test
        @DisplayName("缺少签到时前置条件不满足")
        void missingCheckin() {
            String studentId = "20260011";
            tracker.record(studentId, EventType.PAYMENT_COMPLETED.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            assertFalse(tracker.isCompletionPrerequisitesMet(studentId));
        }
    }

    @Nested
    @DisplayName("事件链端到端模拟")
    class EndToEnd {

        @Test
        @DisplayName("完整报到流程事件链端到端验证")
        void fullCheckinFlow() {
            String studentId = "20260012";

            // 1. 签到成功
            EventEnvelope checkin = TestEventFactory.checkinSuccess(studentId);
            tracker.record(studentId, checkin.getEventType());

            // 2. 缴费完成
            EventEnvelope payment = TestEventFactory.paymentCompleted(studentId);
            tracker.record(studentId, payment.getEventType());

            // 3. 核验通过
            EventEnvelope verified = TestEventFactory.verifiedSuccess(studentId);
            tracker.record(studentId, verified.getEventType());

            // 验证前置条件
            assertTrue(tracker.isCompletionPrerequisitesMet(studentId));

            // 4. 报到完成
            EventEnvelope completed = TestEventFactory.checkinCompleted(studentId);
            tracker.record(studentId, completed.getEventType());

            // 验证完整链顺序
            assertTrue(tracker.isChainOrdered(studentId));

            List<String> chain = tracker.getChain(studentId);
            assertEquals(4, chain.size());
        }

        @Test
        @DisplayName("缺环检测：缺少缴费直接报到完成 → 前置条件不满足")
        void missingLinkDetected() {
            String studentId = "20260013";

            tracker.record(studentId, EventType.CHECKIN_SUCCESS.routingKey());
            tracker.record(studentId, EventType.VERIFIED_SUCCESS.routingKey());

            // 尝试报到完成，但缺少缴费
            assertFalse(tracker.isCompletionPrerequisitesMet(studentId));
        }
    }
}
