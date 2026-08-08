package com.campusarrive.ai.chat;

/**
 * 对话上下文(API 5.1.4 context 字段)。
 *
 * <p>规格来源:FR-01-06(多轮上下文)、AID 7.4(上下文感知 —
 * 对话发起时自动将当前所处环节作为隐式上下文传入)。</p>
 *
 * @param currentStep  当前报到环节标识(checkin/payment/verification/dorm_assign/material_upload)
 * @param studentType  学生身份类别(undergraduate/postgraduate/international)
 * @param stepIndex    当前环节序号
 */
public record ChatContext(
        String currentStep,
        String studentType,
        Integer stepIndex
) {
}
