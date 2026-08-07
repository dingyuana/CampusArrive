package com.campusarrive.gateway.security.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT-SEC-002：AES-256-GCM 加密单元测试。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.3 节加密策略 —
 * L3 核心敏感数据使用 AES-256-GCM 加密存储，带认证标签防篡改。
 * 第 3.4 节密钥管理：支持密钥轮换，轮换期间双密钥并行，保证存量密文可解密。</p>
 */
@DisplayName("UT-SEC-002：AES-256-GCM 加密")
class Aes256EncryptorTest {

    /** 测试用 256 位密钥（32 字节全 0x42）。 */
    private static final String TEST_KEY_1 = Base64.getEncoder()
            .encodeToString(fillBytes(new byte[32], (byte) 0x42));

    /** 测试用第二密钥（32 字节全 0x55），用于密钥轮换测试。 */
    private static final String TEST_KEY_2 = Base64.getEncoder()
            .encodeToString(fillBytes(new byte[32], (byte) 0x55));

    /** 无效短密钥（16 字节）。 */
    private static final String SHORT_KEY = Base64.getEncoder()
            .encodeToString(fillBytes(new byte[16], (byte) 0x01));

    private Aes256Encryptor encryptor;

    private static byte[] fillBytes(byte[] bytes, byte value) {
        Arrays.fill(bytes, value);
        return bytes;
    }

    @BeforeEach
    void setUp() {
        encryptor = new Aes256Encryptor(TEST_KEY_1);
    }

    // ─── 加密/解密往返 ──────────────────────────────────────────

    @Nested
    @DisplayName("加密/解密往返")
    class EncryptDecryptRoundTrip {

        @Test
        @DisplayName("身份证号加密后可正确解密")
        void testRoundTripIdCard() {
            String plaintext = "110101199001011234";
            String ciphertext = encryptor.encrypt(plaintext);

            assertThat(ciphertext).isNotEqualTo(plaintext);
            assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("手机号加密后可正确解密")
        void testRoundTripPhone() {
            String plaintext = "13812345678";
            String ciphertext = encryptor.encrypt(plaintext);

            assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("中文地址加密后可正确解密")
        void testRoundTripChineseAddress() {
            String plaintext = "北京市海淀区中关村南大街5号";
            String ciphertext = encryptor.encrypt(plaintext);

            assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("空字符串加密后可正确解密")
        void testRoundTripEmptyString() {
            String ciphertext = encryptor.encrypt("");

            assertThat(encryptor.decrypt(ciphertext)).isEmpty();
        }

        @Test
        @DisplayName("长文本加密后可正确解密")
        void testRoundTripLongText() {
            String plaintext = "A".repeat(10000);
            String ciphertext = encryptor.encrypt(plaintext);

            assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
        }
    }

    // ─── 密文特性 ──────────────────────────────────────────────

    @Nested
    @DisplayName("密文特性")
    class CiphertextProperties {

        @Test
        @DisplayName("密文为 Base64 格式")
        void testCiphertextIsBase64() {
            String ciphertext = encryptor.encrypt("test");

            assertThat(ciphertext).matches("[A-Za-z0-9+/]+={0,2}");
        }

        @Test
        @DisplayName("同一明文多次加密产生不同密文（随机 IV）")
        void testRandomIvProducesDifferentCiphertexts() {
            String plaintext = "110101199001011234";
            String ciphertext1 = encryptor.encrypt(plaintext);
            String ciphertext2 = encryptor.encrypt(plaintext);

            assertThat(ciphertext1).isNotEqualTo(ciphertext2);
            // 但两者都能正确解密
            assertThat(encryptor.decrypt(ciphertext1)).isEqualTo(plaintext);
            assertThat(encryptor.decrypt(ciphertext2)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("密文不可逆推明文")
        void testCiphertextNotReversibleWithoutKey() {
            String plaintext = "13812345678";
            String ciphertext = encryptor.encrypt(plaintext);

            // 密文中不包含明文
            assertThat(ciphertext).doesNotContain(plaintext);
            // 密文不等于明文的 Base64 编码
            String plainBase64 = Base64.getEncoder()
                    .encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
            assertThat(ciphertext).isNotEqualTo(plainBase64);
        }
    }

    // ─── 密钥轮换 ──────────────────────────────────────────────

    @Nested
    @DisplayName("密钥轮换")
    class KeyRotation {

        @Test
        @DisplayName("旧密钥加密的密文可用新密钥列表解密")
        void testDecryptOldCiphertextWithRotatedKeys() {
            // 用旧密钥加密
            Aes256Encryptor oldEncryptor = new Aes256Encryptor(TEST_KEY_1);
            String plaintext = "110101199001011234";
            String ciphertext = oldEncryptor.encrypt(plaintext);

            // 轮换后使用新密钥列表（新密钥在前，旧密钥在后）
            Aes256Encryptor rotatedEncryptor = new Aes256Encryptor(
                    List.of(TEST_KEY_2, TEST_KEY_1));

            // 旧密文仍可解密
            assertThat(rotatedEncryptor.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("轮换后新加密使用新密钥")
        void testNewEncryptionUsesNewKey() {
            Aes256Encryptor rotatedEncryptor = new Aes256Encryptor(
                    List.of(TEST_KEY_2, TEST_KEY_1));

            String plaintext = "13812345678";
            String ciphertext = rotatedEncryptor.encrypt(plaintext);

            // 仅用旧密钥的加密器无法解密
            Aes256Encryptor oldOnlyEncryptor = new Aes256Encryptor(TEST_KEY_1);
            assertThatThrownBy(() -> oldOnlyEncryptor.decrypt(ciphertext))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("密钥数量正确反映")
        void testKeyCount() {
            Aes256Encryptor single = new Aes256Encryptor(TEST_KEY_1);
            assertThat(single.getKeyCount()).isEqualTo(1);

            Aes256Encryptor rotated = new Aes256Encryptor(List.of(TEST_KEY_2, TEST_KEY_1));
            assertThat(rotated.getKeyCount()).isEqualTo(2);
        }
    }

    // ─── 异常处理 ──────────────────────────────────────────────

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("加密 null 返回 null")
        void testEncryptNull() {
            assertThat(encryptor.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("解密 null 返回 null")
        void testDecryptNull() {
            assertThat(encryptor.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("错误密钥解密抛出异常")
        void testDecryptWithWrongKey() {
            String ciphertext = encryptor.encrypt("secret");

            Aes256Encryptor wrongKeyEncryptor = new Aes256Encryptor(TEST_KEY_2);
            assertThatThrownBy(() -> wrongKeyEncryptor.decrypt(ciphertext))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("所有密钥均无法解密");
        }

        @Test
        @DisplayName("密钥长度不足 32 字节抛出异常")
        void testShortKeyThrows() {
            assertThatThrownBy(() -> new Aes256Encryptor(SHORT_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密钥长度");
        }

        @Test
        @DisplayName("空密钥列表抛出异常")
        void testEmptyKeyListThrows() {
            assertThatThrownBy(() -> new Aes256Encryptor(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密钥列表不能为空");
        }

        @Test
        @DisplayName("null 密钥列表抛出异常")
        void testNullKeyListThrows() {
            assertThatThrownBy(() -> new Aes256Encryptor((List<String>) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("过短密文抛出异常")
        void testShortCiphertextThrows() {
            // 构造一个只有 5 字节的密文（不足 IV 长度 + 1）
            String shortCiphertext = Base64.getEncoder()
                    .encodeToString(new byte[5]);

            assertThatThrownBy(() -> encryptor.decrypt(shortCiphertext))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("密文长度不足");
        }

        @Test
        @DisplayName("篡改密文后解密失败")
        void testTamperedCiphertextThrows() {
            String ciphertext = encryptor.encrypt("secret");
            // 篡改密文中部字符（避开 Base64 padding 区域，确保仍为合法 Base64）
            int tamperIndex = ciphertext.length() / 2;
            char original = ciphertext.charAt(tamperIndex);
            char tampered = original == 'A' ? 'B' : 'A';
            String tamperedCiphertext = ciphertext.substring(0, tamperIndex)
                    + tampered
                    + ciphertext.substring(tamperIndex + 1);

            assertThatThrownBy(() -> encryptor.decrypt(tamperedCiphertext))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
