package com.relivus.service;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 连接密码加解密（DOC-10）。
 *
 * <p>AES-GCM 256，密钥来自环境变量 {@code RELIVUS_AES_KEY}（Base64 32 字节）。
 * 密文格式：{@code Base64(IV(12B) || cipherText || tag)}。
 * 解密结果仅在内存中短暂存在，禁止日志输出、禁止持久化明文。
 */
@Component
public class CryptoService {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoService.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final ThreadLocal<Cipher> cipherHolder = new ThreadLocal<>();

    public CryptoService(com.relivus.config.RelivusProperties props) {
        byte[] key = Base64.getDecoder().decode(props.getCrypto().getAesKey());
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    /** 加密：返回 Base64(IV || cipherText || tag)。 */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = cipherHolder.get();
            if (cipher == null) {
                cipher = Cipher.getInstance(TRANSFORMATION);
                cipherHolder.set(cipher);
            }
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, payload, IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            LOG.error("AES-GCM encryption failed", e);
            throw new RelivusException(ErrorCode.CRYPTO_FAILED, "Password encryption failed");
        }
    }

    /** 解密：输入 {@link #encrypt(String)} 的输出。不可逆缺陷视为内部错误，不泄漏细节。 */
    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null) {
            throw new IllegalArgumentException("cipherText must not be null");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherTextBase64);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("cipher text too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = cipherHolder.get();
            if (cipher == null) {
                cipher = Cipher.getInstance(TRANSFORMATION);
                cipherHolder.set(cipher);
            }
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("AES-GCM decryption failed", e);
            throw new RelivusException(ErrorCode.CRYPTO_FAILED, "Password decryption failed");
        }
    }
}