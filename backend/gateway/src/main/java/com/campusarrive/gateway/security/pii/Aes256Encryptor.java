package com.campusarrive.gateway.security.pii;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * AES-256-GCM 字段级加密器。
 *
 * <p>规格来源：SCS-CA-2026-09 第 3.3 节加密策略 —
 * L3 核心敏感数据（身份证号、手机号、家庭住址、人脸照片）使用
 * AES-256-GCM 算法加密存储，带认证标签防篡改，密钥由校园 KMS 统一管理。
 * 第 3.4 节密钥管理：支持密钥轮换，轮换期间双密钥并行，保证存量密文可解密。</p>
 *
 * <p>密文格式：Base64( IV(12B) ‖ Ciphertext ‖ Tag(16B) )</p>
 */
public class Aes256Encryptor {

    /** AES-GCM 算法标识。 */
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 推荐 IV 长度：12 字节。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度：128 位 = 16 字节。 */
    private static final int TAG_LENGTH_BITS = 128;

    /** AES-256 密钥长度：32 字节。 */
    private static final int KEY_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 当前活跃密钥列表（索引 0 为最新密钥，用于加密；其余为历史密钥，仅用于解密）。 */
    private final List<SecretKeySpec> keys;

    /**
     * 使用单个密钥构造加密器。
     *
     * @param keyBase64 Base64 编码的 256 位密钥
     * @throws IllegalArgumentException 密钥长度不足 32 字节
     */
    public Aes256Encryptor(String keyBase64) {
        this(List.of(keyBase64));
    }

    /**
     * 使用多个密钥构造加密器（支持密钥轮换）。
     *
     * <p>列表中第一个密钥为当前活跃密钥（用于加密），其余为历史密钥（仅用于解密）。
     * 解密时依次尝试所有密钥，任一成功即返回。</p>
     *
     * @param keysBase64 Base64 编码的密钥列表，至少包含一个密钥
     * @throws IllegalArgumentException 密钥列表为空或密钥长度不足 32 字节
     */
    public Aes256Encryptor(List<String> keysBase64) {
        if (keysBase64 == null || keysBase64.isEmpty()) {
            throw new IllegalArgumentException("密钥列表不能为空");
        }
        List<SecretKeySpec> parsed = new ArrayList<>(keysBase64.size());
        for (String keyBase64 : keysBase64) {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            if (keyBytes.length != KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "AES-256 密钥长度必须为 " + KEY_LENGTH + " 字节，实际: " + keyBytes.length);
            }
            parsed.add(new SecretKeySpec(keyBytes, ALGORITHM));
        }
        this.keys = Collections.unmodifiableList(parsed);
    }

    /**
     * 加密明文。
     *
     * <p>使用当前活跃密钥（列表第一个）和随机 IV 执行 AES-256-GCM 加密，
     * 返回 Base64 编码的 {@code IV ‖ Ciphertext ‖ Tag} 拼接结果。</p>
     *
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(0), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM 加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * <p>依次尝试所有密钥（当前 + 历史），任一成功即返回明文。
     * 支持密钥轮换场景下旧密文的解密。</p>
     *
     * @param ciphertextBase64 Base64 编码的密文
     * @return 明文
     * @throws IllegalStateException 所有密钥均无法解密
     */
    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null) {
            return null;
        }
        byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
        if (combined.length < IV_LENGTH + 1) {
            throw new IllegalStateException("密文长度不足，无法解密");
        }

        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        byte[] ciphertextWithTag = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, IV_LENGTH, ciphertextWithTag, 0, ciphertextWithTag.length);

        for (SecretKeySpec key : keys) {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
                byte[] plaintext = cipher.doFinal(ciphertextWithTag);
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // 当前密钥解密失败，尝试下一个密钥
            }
        }
        throw new IllegalStateException("所有密钥均无法解密密文，可能密钥不匹配或数据已被篡改");
    }

    /**
     * 获取当前活跃密钥数量（用于密钥轮换状态检查）。
     *
     * @return 密钥数量
     */
    public int getKeyCount() {
        return keys.size();
    }
}
