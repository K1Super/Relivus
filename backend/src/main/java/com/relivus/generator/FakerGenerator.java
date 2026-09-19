package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;
import net.datafaker.Faker;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * DataFaker 假数据生成器。
 *
 * <p>按列名启发式选择 provider：email/name/address/company/city 等；支持参数 {@code provider}
 * 显式指定。Faker 实例按引擎运行创建，避免跨线程共享。
 */
public class FakerGenerator implements ValueGenerator {

    private static final Set<String> EMAIL_COLUMNS = Set.of("email", "mail");
    private static final Set<String> NAME_COLUMNS = Set.of("name", "username", "username_name", "nickname", "full_name");
    private static final Set<String> ADDRESS_COLUMNS = Set.of("address", "addr", "street", "street_address", "city", "province", "region");
    private static final Set<String> COMPANY_COLUMNS = Set.of("company", "company_name", "org", "organization");
    private static final Set<String> PHONE_COLUMNS = Set.of("phone", "mobile", "tel", "telephone");

    private final Faker faker = new Faker(Locale.CHINA);

    @Override
    public String name() {
        return "faker";
    }

    @Override
    public Object generate(GenerationContext context) {
        String provider = context.param("provider", infer(context.column().columnName()));
        Function<Faker, Object> fn = resolve(provider);
        if (fn == null) {
            throw new IllegalArgumentException("faker: unsupported provider '" + provider + "'");
        }
        return fn.apply(faker);
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        String lower = column.columnName().toLowerCase(Locale.ROOT);
        if (EMAIL_COLUMNS.contains(lower) || NAME_COLUMNS.contains(lower)
                || ADDRESS_COLUMNS.contains(lower) || COMPANY_COLUMNS.contains(lower)
                || PHONE_COLUMNS.contains(lower)) {
            return true;
        }
        String t = column.dataType().toUpperCase();
        return t.contains("VARCHAR") || t.contains("TEXT") || t.contains("CHAR");
    }

    /** 列名启发式推断 provider。 */
    static String infer(String columnName) {
        String lower = columnName.toLowerCase(Locale.ROOT);
        if (EMAIL_COLUMNS.contains(lower)) {
            return "email";
        }
        if (PHONE_COLUMNS.contains(lower)) {
            return "phone";
        }
        if (NAME_COLUMNS.contains(lower)) {
            return "name";
        }
        if (ADDRESS_COLUMNS.contains(lower)) {
            return "address";
        }
        if (COMPANY_COLUMNS.contains(lower)) {
            return "company";
        }
        return "name";
    }

    private static Function<Faker, Object> resolve(String provider) {
        return switch (provider) {
            // 数据质量整改：中文邮箱含汉字非纯 ASCII，改为拼音 ASCII 邮箱；姓名剔除古风复姓/生僻字
            case "email" -> f -> ChinesePersonData.randomEmail();
            case "name" -> f -> ChinesePersonData.randomName();
            case "first_name" -> f -> f.name().firstName();
            case "last_name" -> f -> f.name().lastName();
            case "address" -> f -> f.address().fullAddress();
            case "city" -> f -> f.address().city();
            case "company" -> f -> f.company().name();
            case "phone" -> f -> f.phoneNumber().cellPhone();
            case "job" -> f -> f.job().title();
            case "text" -> f -> f.lorem().sentence(8);
            case "uuid" -> f -> f.internet().uuid();
            case "boolean" -> f -> f.bool().bool();
            case "date" -> f -> f.date().birthday().toLocalDateTime().toString();
            default -> null;
        };
    }
}