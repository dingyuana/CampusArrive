package com.campusarrive.ai.chat.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PiiMasker} 单元测试。
 *
 * <p>规格来源:AID 8.5 PII 脱敏、FR-05-09(数据不出校)。
 * 验证身份证号、手机号、学号、银行卡号的检测与替换。</p>
 */
@DisplayName("UT-AI: PII 脱敏节点")
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    @DisplayName("身份证号脱敏为 [ID]")
    void idCardMasked() {
        PiiMasker.MaskResult r = masker.mask("我的身份证是110101199003071234");
        assertTrue(r.maskedText().contains("[ID]"), "应替换为 [ID]");
        assertFalse(r.maskedText().contains("110101199003071234"), "不应含明文身份证");
        assertTrue(r.mapping().containsKey("110101199003071234"), "映射表应记录原值");
    }

    @Test
    @DisplayName("手机号脱敏为 [PHONE]")
    void phoneMasked() {
        PiiMasker.MaskResult r = masker.mask("联系电话13800138000");
        assertTrue(r.maskedText().contains("[PHONE]"), "应替换为 [PHONE]");
        assertFalse(r.maskedText().contains("13800138000"), "不应含明文手机号");
    }

    @Test
    @DisplayName("学号脱敏为 [STUDENT_NO]")
    void studentNoMasked() {
        PiiMasker.MaskResult r = masker.mask("学号STU20260001");
        assertTrue(r.maskedText().contains("[STUDENT_NO]"), "应替换为 [STUDENT_NO]");
        assertFalse(r.maskedText().contains("STU20260001"), "不应含明文学号");
    }

    @Test
    @DisplayName("8 位纯数字学号脱敏")
    void numericStudentNoMasked() {
        PiiMasker.MaskResult r = masker.mask("我的学号是20260001");
        assertTrue(r.maskedText().contains("[STUDENT_NO]"), "8 位学号应脱敏");
    }

    @Test
    @DisplayName("银行卡号脱敏为 [CARD]")
    void bankCardMasked() {
        PiiMasker.MaskResult r = masker.mask("卡号6222021234567890123");
        assertTrue(r.maskedText().contains("[CARD]"), "应替换为 [CARD]");
    }

    @Test
    @DisplayName("多类型 PII 同时脱敏")
    void multiplePiiMasked() {
        PiiMasker.MaskResult r = masker.mask("身份证110101199003071234电话13800138000");
        assertTrue(r.maskedText().contains("[ID]"), "身份证应脱敏");
        assertTrue(r.maskedText().contains("[PHONE]"), "手机号应脱敏");
        assertTrue(r.mapping().size() >= 2, "映射表应记录多个 PII");
    }

    @Test
    @DisplayName("无 PII 文本不变")
    void noPiiUnchanged() {
        PiiMasker.MaskResult r = masker.mask("报到需要带什么材料");
        assertEquals("报到需要带什么材料", r.maskedText(), "无 PII 文本应不变");
        assertTrue(r.mapping().isEmpty(), "映射表应为空");
    }

    @Test
    @DisplayName("空文本安全处理")
    void emptyTextSafe() {
        PiiMasker.MaskResult r = masker.mask("");
        assertEquals("", r.maskedText());
        PiiMasker.MaskResult r2 = masker.mask(null);
        assertEquals(null, r2.maskedText());
    }
}
