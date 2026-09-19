package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 哈希：输出十六进制，用于不可逆但需确定性一致的场景。
 *
 * <p>密钥为系统 HMAC_KEY（Base64 解码后的原始字节）；keyVersion 参与派生，
 * 升级密钥版本即可让历史值全部失效重算。
 */
public class HmacHash implements MaskingAlgorithm {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] baseKey;

    public HmacHash(byte[] baseKey) {
        this.baseKey = baseKey;
    }

    @Override
    public String name() {
        return "hmac";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        byte[] key = deriveKey(config.keyVersion());
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(original.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", e);
        }
    }

    /** 版本派生：SHA-256(baseKey + ":" + keyVersion)，避免同密钥跨版本复用。 */
    private byte[] deriveKey(int keyVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(baseKey);
            digest.update((byte) ':');
            digest.update(String.valueOf(keyVersion).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}