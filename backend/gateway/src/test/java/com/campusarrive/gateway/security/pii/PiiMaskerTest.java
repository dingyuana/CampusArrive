package com.campusarrive.gateway.security.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-SEC-001：PII 脱敏规则单元测试。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.2 节 PII 五层防护第 4 层展示层 —
 * 身份证号显示前 3 后 4、手机号显示前 3 后 4、家庭住址仅显示到区县级。
 * 中间四位掩码，不可逆。</p>
 */
@DisplayName("UT-SEC-001：PII 脱敏规则")
class PiiMaskerTest {

    // ─── 身份证号脱敏 ────────────────────────────────────────────

    @Nested
    @DisplayName("身份证号脱敏")
    class IdCardMasking {

        @Test
        @DisplayName("18 位身份证号保留前 3 后 4")
        void testMaskIdCard18Digits() {
            assertThat(PiiMasker.maskIdCard("110101199001011234"))
                    .isEqualTo("110***********1234");
        }

        @Test
        @DisplayName("末位为 X 的身份证号正确脱敏")
        void testMaskIdCardEndingWithX() {
            assertThat(PiiMasker.maskIdCard("11010119900101123X"))
                    .isEqualTo("110***********123X");
        }

        @Test
        @DisplayName("过短身份证号全部掩码")
        void testMaskIdCardShort() {
            assertThat(PiiMasker.maskIdCard("12345")).isEqualTo("*****");
        }

        @Test
        @DisplayName("null 身份证号返回 null")
        void testMaskIdCardNull() {
            assertThat(PiiMasker.maskIdCard(null)).isNull();
        }

        @Test
        @DisplayName("恰好 8 位的值全部掩码")
        void testMaskIdCardExactly8() {
            assertThat(PiiMasker.maskIdCard("12345678")).isEqualTo("********");
        }

        @Test
        @DisplayName("9 位值保留前 3 后 4")
        void testMaskIdCard9Digits() {
            assertThat(PiiMasker.maskIdCard("123456789")).isEqualTo("123**6789");
        }
    }

    // ─── 手机号脱敏 ──────────────────────────────────────────────

    @Nested
    @DisplayName("手机号脱敏")
    class PhoneMasking {

        @Test
        @DisplayName("11 位手机号保留前 3 后 4")
        void testMaskPhone11Digits() {
            assertThat(PiiMasker.maskPhone("13812345678"))
                    .isEqualTo("138****5678");
        }

        @Test
        @DisplayName("过短手机号全部掩码")
        void testMaskPhoneShort() {
            assertThat(PiiMasker.maskPhone("123456")).isEqualTo("******");
        }

        @Test
        @DisplayName("null 手机号返回 null")
        void testMaskPhoneNull() {
            assertThat(PiiMasker.maskPhone(null)).isNull();
        }

        @Test
        @DisplayName("恰好 8 位的手机号全部掩码")
        void testMaskPhoneExactly8() {
            assertThat(PiiMasker.maskPhone("12345678")).isEqualTo("********");
        }
    }

    // ─── 姓名脱敏 ────────────────────────────────────────────────

    @Nested
    @DisplayName("姓名脱敏")
    class NameMasking {

        @Test
        @DisplayName("两字姓名保留首字掩码末字")
        void testMaskName2Chars() {
            assertThat(PiiMasker.maskName("张三")).isEqualTo("张*");
        }

        @Test
        @DisplayName("三字姓名保留首尾中间掩码")
        void testMaskName3Chars() {
            assertThat(PiiMasker.maskName("张三丰")).isEqualTo("张*丰");
        }

        @Test
        @DisplayName("四字姓名保留首尾中间双掩码")
        void testMaskName4Chars() {
            assertThat(PiiMasker.maskName("欧阳修文")).isEqualTo("欧**文");
        }

        @Test
        @DisplayName("单字姓名全部掩码")
        void testMaskName1Char() {
            assertThat(PiiMasker.maskName("李")).isEqualTo("*");
        }

        @Test
        @DisplayName("null 姓名返回 null")
        void testMaskNameNull() {
            assertThat(PiiMasker.maskName(null)).isNull();
        }

        @Test
        @DisplayName("空字符串姓名返回空字符串")
        void testMaskNameEmpty() {
            assertThat(PiiMasker.maskName("")).isEmpty();
        }
    }

    // ─── 邮箱脱敏 ────────────────────────────────────────────────

    @Nested
    @DisplayName("邮箱脱敏")
    class EmailMasking {

        @Test
        @DisplayName("标准邮箱本地名保留首字符")
        void testMaskEmailStandard() {
            assertThat(PiiMasker.maskEmail("zhangsan@example.com"))
                    .isEqualTo("z***@example.com");
        }

        @Test
        @DisplayName("单字符本地名全部掩码")
        void testMaskEmailSingleCharLocal() {
            assertThat(PiiMasker.maskEmail("a@test.com"))
                    .isEqualTo("*@test.com");
        }

        @Test
        @DisplayName("无 @ 符号的字符串全部掩码")
        void testMaskEmailNoAtSign() {
            assertThat(PiiMasker.maskEmail("notanemail")).isEqualTo("**********");
        }

        @Test
        @DisplayName("@ 在首位时全部掩码")
        void testMaskEmailAtStart() {
            assertThat(PiiMasker.maskEmail("@example.com")).isEqualTo("************");
        }

        @Test
        @DisplayName("null 邮箱返回 null")
        void testMaskEmailNull() {
            assertThat(PiiMasker.maskEmail(null)).isNull();
        }

        @Test
        @DisplayName("空字符串邮箱返回空字符串")
        void testMaskEmailEmpty() {
            assertThat(PiiMasker.maskEmail("")).isEmpty();
        }

        @Test
        @DisplayName("空白字符串邮箱原样返回")
        void testMaskEmailBlank() {
            assertThat(PiiMasker.maskEmail("   ")).isEqualTo("   ");
        }
    }

    // ─── 地址脱敏 ────────────────────────────────────────────────

    @Nested
    @DisplayName("家庭住址脱敏")
    class AddressMasking {

        @Test
        @DisplayName("含区级后缀的地址保留到区级")
        void testMaskAddressWithDistrict() {
            assertThat(PiiMasker.maskAddress("北京市海淀区中关村南大街5号"))
                    .isEqualTo("北京市海淀区***");
        }

        @Test
        @DisplayName("含县级后缀的地址保留到县级")
        void testMaskAddressWithCounty() {
            assertThat(PiiMasker.maskAddress("河北省石家庄市正定县府西街12号"))
                    .isEqualTo("河北省石家庄市正定县***");
        }

        @Test
        @DisplayName("含市级后缀的地址保留到区级")
        void testMaskAddressWithCity() {
            assertThat(PiiMasker.maskAddress("深圳市南山区科技园路1号"))
                    .isEqualTo("深圳市南山区***");
        }

        @Test
        @DisplayName("无行政区划后缀的长地址保留前 6 字符")
        void testMaskAddressNoSuffix() {
            String address = "某地某路某号某室";
            assertThat(PiiMasker.maskAddress(address))
                    .isEqualTo("某地某路某号***");
        }

        @Test
        @DisplayName("短地址全部掩码")
        void testMaskAddressShort() {
            assertThat(PiiMasker.maskAddress("北京")).isEqualTo("**");
        }

        @Test
        @DisplayName("null 地址返回 null")
        void testMaskAddressNull() {
            assertThat(PiiMasker.maskAddress(null)).isNull();
        }

        @Test
        @DisplayName("空字符串地址返回空字符串")
        void testMaskAddressEmpty() {
            assertThat(PiiMasker.maskAddress("")).isEmpty();
        }
    }

    // ─── 银行卡号脱敏 ────────────────────────────────────────────

    @Nested
    @DisplayName("银行卡号脱敏")
    class BankCardMasking {

        @Test
        @DisplayName("19 位银行卡号保留前 4 后 4")
        void testMaskBankCard19Digits() {
            assertThat(PiiMasker.maskBankCard("6222021234567890123"))
                    .isEqualTo("6222***********0123");
        }

        @Test
        @DisplayName("16 位银行卡号保留前 4 后 4")
        void testMaskBankCard16Digits() {
            assertThat(PiiMasker.maskBankCard("6222020123456789"))
                    .isEqualTo("6222********6789");
        }

        @Test
        @DisplayName("过短银行卡号全部掩码")
        void testMaskBankCardShort() {
            assertThat(PiiMasker.maskBankCard("12345678")).isEqualTo("********");
        }

        @Test
        @DisplayName("null 银行卡号返回 null")
        void testMaskBankCardNull() {
            assertThat(PiiMasker.maskBankCard(null)).isNull();
        }
    }

    // ─── 统一脱敏入口 mask(String, PiiType) ─────────────────────

    @Nested
    @DisplayName("统一脱敏入口")
    class DispatchMask {

        @ParameterizedTest
        @CsvSource({
                "110101199001011234, ID_CARD, 110***********1234",
                "13812345678, PHONE, 138****5678",
                "张三丰, NAME, 张*丰",
                "zhangsan@example.com, EMAIL, z***@example.com",
                "6222021234567890123, BANK_CARD, 6222***********0123"
        })
        @DisplayName("按 PII 类型分发脱敏")
        void testMaskByType(String input, String typeName, String expected) {
            PiiType type = PiiType.valueOf(typeName);
            assertThat(PiiMasker.mask(input, type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("null 值统一返回 null")
        void testMaskNullValue() {
            for (PiiType type : PiiType.values()) {
                assertThat(PiiMasker.mask(null, type)).isNull();
            }
        }

        @Test
        @DisplayName("空白值统一原样返回")
        void testMaskBlankValue() {
            for (PiiType type : PiiType.values()) {
                assertThat(PiiMasker.mask("  ", type)).isEqualTo("  ");
            }
        }
    }
}
