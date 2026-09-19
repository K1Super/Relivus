package com.relivus.masking;

import com.relivus.dto.MaskingConfig;
import net.datafaker.Faker;

import java.util.Locale;

/**
 * DataFaker 假值替换：生成同类型假数据。
 * 参数：provider（email / phone / name / address / company / city / uuid / text，默认 name）。
 * Faker 实例为线程安全单例。
 */
public class FakerReplace implements MaskingAlgorithm {

    private static final Faker FAKER = new Faker(Locale.CHINA);

    @Override
    public String name() {
        return "faker";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        String provider = config.params() == null ? null : config.params().get("provider");
        if (provider == null || provider.isBlank()) {
            provider = "name";
        }
        return switch (provider) {
            case "email" -> FAKER.internet().emailAddress();
            case "phone" -> FAKER.phoneNumber().cellPhone();
            case "name" -> FAKER.name().fullName();
            case "address" -> FAKER.address().fullAddress();
            case "company" -> FAKER.company().name();
            case "city" -> FAKER.address().city();
            case "uuid" -> FAKER.internet().uuid();
            case "text" -> FAKER.lorem().sentence(8);
            default -> throw new IllegalArgumentException("faker: unsupported provider '" + provider + "'");
        };
    }
}