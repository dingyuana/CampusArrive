package com.campusarrive.integration.consumer;

import com.campusarrive.integration.event.EventConstants;
import com.campusarrive.integration.event.EventEnvelope;
import com.campusarrive.integration.event.EventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件链追踪器 — 记录事件消费顺序，用于事件链顺序验证。
 *
 * <p>规格来源：CT-MW-006 事件链顺序验证。
 * 记录每个 studentId 的事件消费顺序，验证签到→缴费→核验→完成四条事件链按序触发。</p>
 */
@Component
public class EventChainTracker {

    /** studentId → 已消费事件列表（按消费顺序） */
    private final ConcurrentMap<String, CopyOnWriteArrayList<String>> chainRecords = new ConcurrentHashMap<>();

    /** studentId → 已完成的事件链步骤集合 */
    private final ConcurrentMap<String, CopyOnWriteArrayList<String>> completedSteps = new ConcurrentHashMap<>();

    /**
     * 记录事件消费。
     *
     * @param studentId 学生 ID
     * @param eventType 事件类型
     */
    public void record(String studentId, String eventType) {
        chainRecords.computeIfAbsent(studentId, k -> new CopyOnWriteArrayList<>()).add(eventType);
        completedSteps.computeIfAbsent(studentId, k -> new CopyOnWriteArrayList<>()).add(eventType);
    }

    /**
     * 获取指定学生的事件消费顺序。
     */
    public List<String> getChain(String studentId) {
        CopyOnWriteArrayList<String> list = chainRecords.get(studentId);
        return list != null ? list : List.of();
    }

    /**
     * 检查事件链步骤是否按序完成。
     *
     * <p>验证四条核心事件链的触发顺序：
     * 1. student.checkin.success（签到成功）
     * 2. student.payment.completed（缴费完成）
     * 3. student.verified.success（核验通过）
     * 4. student.checkin.completed（报到完成）
     *
     * 签到必须先于其他事件；报到完成必须在签到、缴费、核验之后。</p>
     *
     * @param studentId 学生 ID
     * @return true=顺序正确, false=顺序异常或缺环
     */
    public boolean isChainOrdered(String studentId) {
        List<String> chain = getChain(studentId);
        if (chain.isEmpty()) {
            return false;
        }

        int checkinIdx = chain.indexOf(EventType.CHECKIN_SUCCESS.routingKey());
        int paymentIdx = chain.indexOf(EventType.PAYMENT_COMPLETED.routingKey());
        int verifiedIdx = chain.indexOf(EventType.VERIFIED_SUCCESS.routingKey());
        int completedIdx = chain.indexOf(EventType.CHECKIN_COMPLETED.routingKey());

        // 签到必须是第一个事件
        if (checkinIdx == -1 || checkinIdx != 0) {
            return false;
        }

        // 报到完成必须在签到之后
        if (completedIdx != -1 && completedIdx <= checkinIdx) {
            return false;
        }

        // 如果有缴费，必须在签到之后
        if (paymentIdx != -1 && paymentIdx <= checkinIdx) {
            return false;
        }

        // 如果有核验，必须在签到之后
        if (verifiedIdx != -1 && verifiedIdx <= checkinIdx) {
            return false;
        }

        // 报到完成必须在缴费和核验之后
        if (completedIdx != -1) {
            if (paymentIdx != -1 && completedIdx <= paymentIdx) return false;
            if (verifiedIdx != -1 && completedIdx <= verifiedIdx) return false;
        }

        return true;
    }

    /**
     * 检查指定学生的报到完成事件是否满足前置条件。
     *
     * <p>报到完成（student.checkin.completed）要求签到、缴费、核验均已发生。</p>
     */
    public boolean isCompletionPrerequisitesMet(String studentId) {
        CopyOnWriteArrayList<String> steps = completedSteps.get(studentId);
        List<String> stepList = steps != null ? steps : List.of();
        return stepList.contains(EventType.CHECKIN_SUCCESS.routingKey())
                && stepList.contains(EventType.PAYMENT_COMPLETED.routingKey())
                && stepList.contains(EventType.VERIFIED_SUCCESS.routingKey());
    }

    /** 重置所有记录（测试用） */
    public void reset() {
        chainRecords.clear();
        completedSteps.clear();
    }
}
