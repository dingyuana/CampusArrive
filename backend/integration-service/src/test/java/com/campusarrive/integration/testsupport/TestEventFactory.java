package com.campusarrive.integration.testsupport;

import com.campusarrive.integration.event.*;

import java.time.OffsetDateTime;

/**
 * 测试事件工厂 — 构建标准测试事件信封。
 *
 * <p>规格来源：UT-MW-003 / UT-MW-004 / CT-MW-005 / CT-MW-006 / CT-MW-007。
 * 避免在测试中硬编码魔法值，统一通过工厂方法创建测试事件。</p>
 */
public final class TestEventFactory {

    private TestEventFactory() {
    }

    /** 创建报到成功事件 */
    public static EventEnvelope checkinSuccess(String studentId) {
        return EventEnvelope.builder(EventType.CHECKIN_SUCCESS)
                .eventId("evt-test-checkin-" + studentId)
                .source("checkin-service")
                .traceId("trace-" + studentId)
                .eventTime(OffsetDateTime.parse("2026-08-28T09:30:15+08:00"))
                .payload(new CheckinSuccessPayload(
                        studentId,
                        "330***********1234",
                        "张三",
                        "2026-08-28T09:30:00+08:00",
                        "主楼一层大厅"
                ))
                .build();
    }

    /** 创建缴费完成事件 */
    public static EventEnvelope paymentCompleted(String studentId) {
        PaymentCompletedPayload payload = new PaymentCompletedPayload();
        payload.setStudentId(studentId);
        payload.setIdCard("330***********1234");
        payload.setName("张三");
        payload.setGender("M");
        payload.setCollegeCode("01");
        payload.setDormBuilding("3号楼");
        payload.setRoomNo("301");
        payload.setBedNo("2");
        payload.setPayOrderNo("PAY20260828001");
        payload.setPayAmount(5800.00);
        payload.setPayMethod("WECHAT");

        return EventEnvelope.builder(EventType.PAYMENT_COMPLETED)
                .eventId("evt-test-payment-" + studentId)
                .source("checkin-service")
                .traceId("trace-" + studentId)
                .eventTime(OffsetDateTime.parse("2026-08-28T10:00:00+08:00"))
                .payload(payload)
                .build();
    }

    /** 创建核验通过事件 */
    public static EventEnvelope verifiedSuccess(String studentId) {
        VerifiedSuccessPayload payload = new VerifiedSuccessPayload();
        payload.setStudentId(studentId);
        payload.setIdCard("330***********1234");
        payload.setName("张三");
        payload.setCollegeCode("01");
        payload.setPhotoUrl("https://oss.xxx.edu.cn/photo/" + studentId + ".jpg");
        payload.setIssueType("NEW");

        return EventEnvelope.builder(EventType.VERIFIED_SUCCESS)
                .eventId("evt-test-verified-" + studentId)
                .source("checkin-service")
                .traceId("trace-" + studentId)
                .eventTime(OffsetDateTime.parse("2026-08-28T10:15:00+08:00"))
                .payload(payload)
                .build();
    }

    /** 创建报到完成事件 */
    public static EventEnvelope checkinCompleted(String studentId) {
        CheckinCompletedPayload payload = new CheckinCompletedPayload();
        payload.setStudentId(studentId);
        payload.setIdCard("330***********1234");
        payload.setName("张三");
        payload.setCollegeCode("01");
        payload.setClassNo("计科2401");
        payload.setCounselorId("T20100001");
        payload.setCompletedTime("2026-08-28T11:00:00+08:00");

        return EventEnvelope.builder(EventType.CHECKIN_COMPLETED)
                .eventId("evt-test-completed-" + studentId)
                .source("checkin-service")
                .traceId("trace-" + studentId)
                .eventTime(OffsetDateTime.parse("2026-08-28T11:00:00+08:00"))
                .payload(payload)
                .build();
    }

    /** 创建指定重试次数的事件 */
    public static EventEnvelope withRetryCount(EventEnvelope envelope, int retryCount) {
        return EventEnvelope.builder(EventType.fromRoutingKey(envelope.getEventType()))
                .eventId(envelope.getEventId())
                .source(envelope.getSource())
                .traceId(envelope.getTraceId())
                .eventTime(envelope.getEventTime())
                .payload(envelope.getPayload())
                .retryCount(retryCount)
                .build();
    }
}
