package com.relivus.schema.parser;

import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.CheckType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CHECK 约束解析器。
 *
 * <p>仅解析两种语义：{@code col IN ('A','B')} 与 {@code col >= x AND col <= y}；
 * 同时兼容 PostgreSQL 将 {@code IN} 改写为 {@code col = ANY (ARRAY[...])} 的表示。
 * 涉及多列或无法识别的表达式一律标记 {@link CheckType#UNKNOWN}，绝不猜测。
 */
public final class CheckConstraintParser {

    private static final Pattern IN_PATTERN = Pattern.compile("^([a-z_]\\w*)\\s+in\\s+(.+)$");
    private static final Pattern ANY_PATTERN = Pattern.compile("^([a-z_]\\w*)\\s*=\\s*any\\s*array\\s*(.+)$");
    private static final Pattern VALUE_PATTERN =
            Pattern.compile("'((?:[^']|'')*)'|\"?(-?\\d+(?:\\.\\d+)?)\"?");

    private CheckConstraintParser() {
    }

    /**
     * 解析 CHECK 约束定义。
     *
     * @param constraintName 约束名
     * @param definition     定义文本（MySQL CHECK_CLAUSE 或 PG pg_get_constraintdef）
     * @param tableColumns   表内全部列名（小写），用于单列校验与多列判定
     * @return 解析后的约束元数据；无法解析时 {@code type=UNKNOWN}
     */
    public static CheckConstraintMetadata parse(String constraintName, String definition, Set<String> tableColumns) {
        if (definition == null || definition.isBlank()) {
            return CheckConstraintMetadata.unknown(constraintName, definition);
        }
        String normalized = normalize(definition);

        if (countKnownColumns(normalized, tableColumns) > 1) {
            return CheckConstraintMetadata.unknown(constraintName, definition);
        }

        CheckConstraintMetadata inResult = tryParseIn(constraintName, normalized, tableColumns);
        if (inResult != null) {
            return inResult;
        }
        CheckConstraintMetadata rangeResult = tryParseRange(constraintName, normalized, tableColumns);
        if (rangeResult != null) {
            return rangeResult;
        }
        return CheckConstraintMetadata.unknown(constraintName, definition);
    }

    private static CheckConstraintMetadata tryParseIn(String name, String normalized, Set<String> tableColumns) {
        Matcher matcher = IN_PATTERN.matcher(normalized);
        boolean plainIn = matcher.matches();
        String column = null;
        String valuesExpr = null;
        if (plainIn) {
            column = matcher.group(1);
            valuesExpr = matcher.group(2);
        } else {
            Matcher any = ANY_PATTERN.matcher(normalized);
            if (any.matches()) {
                column = any.group(1);
                valuesExpr = any.group(2);
            }
        }
        if (column == null || !tableColumns.contains(column)) {
            return null;
        }
        List<String> values = extractValues(valuesExpr);
        if (values.isEmpty()) {
            return null;
        }
        return new CheckConstraintMetadata(name, List.of(column), CheckType.IN, values, null, null);
    }

    private static CheckConstraintMetadata tryParseRange(String name, String normalized, Set<String> tableColumns) {
        String[] parts = normalized.split("\\s+and\\s+");
        if (parts.length != 2) {
            return null;
        }
        Comparison left = parseComparison(parts[0]);
        Comparison right = parseComparison(parts[1]);
        if (left == null || right == null || !left.column.equals(right.column)) {
            return null;
        }
        if (!tableColumns.contains(left.column)) {
            return null;
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (Comparison c : List.of(left, right)) {
            if (">=".equals(c.operator)) {
                min = c.value;
            } else if ("<=".equals(c.operator)) {
                max = c.value;
            }
        }
        if (min == null || max == null) {
            return null;
        }
        return new CheckConstraintMetadata(name, List.of(left.column), CheckType.RANGE,
                List.of(), min, max);
    }

    private static Comparison parseComparison(String expr) {
        expr = expr.trim();
        Matcher m = Pattern.compile("^([a-z_]\\w*)\\s*(>=|<=)\\s*(-?\\d+(?:\\.\\d+)?)$").matcher(expr);
        if (!m.matches()) {
            return null;
        }
        return new Comparison(m.group(1), m.group(2), new BigDecimal(m.group(3)));
    }

    /** 提取引号包裹的取值列表。 */
    static List<String> extractValues(String expr) {
        List<String> values = new ArrayList<>();
        if (expr == null) {
            return values;
        }
        Matcher m = VALUE_PATTERN.matcher(expr);
        while (m.find()) {
            String quoted = m.group(1);
            String bare = m.group(2);
            values.add(quoted != null ? quoted.replace("''", "'") : bare);
        }
        return values;
    }

    /** 归一化：小写、去标识符引号、去括号/方括号、去 PG 类型转换、去 CHECK 前缀、压缩空白。 */
    static String normalize(String definition) {
        String s = definition.toLowerCase(Locale.ROOT);
        s = s.replace("`", "").replace("\"", "");
        s = s.replace("(", " ").replace(")", " ");
        s = s.replace("[", " ").replace("]", " ");
        s = s.replaceAll("::([a-z_\\d]+)", " ");
        s = s.replaceAll("^check\\s+", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static long countKnownColumns(String normalized, Set<String> tableColumns) {
        long count = 0;
        for (String column : tableColumns) {
            if (containsWord(normalized, column)) {
                count++;
            }
        }
        return count;
    }

    static boolean containsWord(String text, String word) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(word) + "(\\s|$)").matcher(text).find();
    }

    private record Comparison(String column, String operator, BigDecimal value) {
    }
}