package com.campusarrive.gateway.security.pii;

/**
 * PII（个人身份信息）类型枚举。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.1 节数据分类分级 —
 * L3 核心敏感数据包括身份证号、手机号、家庭住址、人脸照片等，
 * 需按字段类型执行差异化脱敏策略。</p>
 */
public enum PiiType {
    /** 身份证号（18 位）：显示前 3 后 4。 */
    ID_CARD,
    /** 手机号（11 位）：显示前 3 后 4。 */
    PHONE,
    /** 姓名：首尾保留，中间掩码。 */
    NAME,
    /** 邮箱：本地名首字符保留，其余掩码。 */
    EMAIL,
    /** 家庭住址：仅显示到区县级。 */
    ADDRESS,
    /** 银行卡号：显示前 4 后 4。 */
    BANK_CARD
}
