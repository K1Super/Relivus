package com.relivus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 启动核心配置校验（"启动快速失败机制"）。
 *
 * <p>校验元数据库 URL、API Token、AES 密钥、HMAC 密钥等核心必填配置；关键缺失时直接抛出异常
 * 终止启动，拒绝带病运行。校验过程不打印任何密钥明文，仅输出掩码后的风险提示。
 */
@Component
public class StartupConfigValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private final RelivusProperties props;
    private final Environment environment;

    public StartupConfigValidator(RelivusProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String url = props.getMeta().getUrl();
        if (url == null || url.isBlank()) {
            fail("RELIVUS_META_URL is missing, refuse to start");
        }
        if (props.getSecurity().getToken().isBlank()) {
            fail("RELIVUS_TOKEN is missing, refuse to start");
        }
        if (props.getCrypto().getAesKey().isBlank()) {
            fail("RELIVUS_AES_KEY is missing, refuse to start");
        }
        if (props.getMasking().getHmacKey().isBlank()) {
            fail("RELIVUS_HMAC_KEY is missing, refuse to start");
        }
        validateAesKey(props.getCrypto().getAesKey());
        validateHmacKey(props.getMasking().getHmacKey());

        String profile = environment != null && environment.getActiveProfiles().length > 0
                ? String.join(",", environment.getActiveProfiles()) : "default";
        log.info("Startup config validation passed, active profiles={}, meta url host={}",
                profile, maskUrl(url));
    }

    /** AES 密钥必须为 Base64 后 32 字节（AES-GCM 256）。 */
    static void validateAesKey(String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            if (key.length != 32) {
                fail("RELIVUS_AES_KEY must decode to 32 bytes (AES-256), got " + key.length);
            }
        } catch (IllegalArgumentException e) {
            fail("RELIVUS_AES_KEY is not valid Base64");
        }
    }

    /** HMAC 密钥必须为 Base64 32 字节。 */
    static void validateHmacKey(String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            if (key.length != 32) {
                fail("RELIVUS_HMAC_KEY must decode to 32 bytes, got " + key.length);
            }
        } catch (IllegalArgumentException e) {
            fail("RELIVUS_HMAC_KEY is not valid Base64");
        }
    }

    /** 日志脱敏：遮蔽内嵌凭据与 query 中的 password 参数（企业规范 §4.3，禁止日志打印凭据）。 */
    static String maskUrl(String url) {
        String masked = url.replaceAll("(?i)(password=)[^&]*", "$1***");
        int at = masked.indexOf('@');
        if (at < 0) {
            return masked;
        }
        return masked.substring(0, masked.indexOf("//") + 3) + "***@" + masked.substring(at + 1);
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}