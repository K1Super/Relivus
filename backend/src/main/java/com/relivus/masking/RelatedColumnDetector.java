package com.relivus.masking;

import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 关联列识别。
 *
 * <p>分组规则：
 * <ul>
 *   <li>外键关联：外键列与父表被引用的列归为一组（键 {@code fk:<refTable>.<refColumn>}）；</li>
 *   <li>同名列：列名与类型相同归为一组（键 {@code name:<column>}），排除已归入外键组的列。</li>
 * </ul>
 * 用户显式指定的 columnGroup 优先级最高，在引擎内直接短路本识别结果。
 */
public class RelatedColumnDetector {

    /** 列引用。 */
    public record ColumnRef(String table, String column) {
    }

    /**
     * 识别目标库全部关联列分组。
     *
     * @return 分组名 → 组内列引用（保序）
     */
    public Map<String, List<ColumnRef>> detect(List<TableMetadata> tables) {
        Map<String, List<ColumnRef>> groups = new LinkedHashMap<>();
        Map<String, List<ColumnRef>> nameGroups = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            for (ForeignKeyMetadata fk : table.foreignKeys()) {
                String group = "fk:" + fk.refTable() + "." + fk.refColumn();
                groups.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new ColumnRef(table.tableName(), fk.columnName()));
                // 父表被引用列（通常是主键）加入同组，保证跨表外键脱敏一致
                groups.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new ColumnRef(fk.refTable(), fk.refColumn()));
            }
        }
        // 同名列：剔除已入外键组的列避免重复处理
        for (TableMetadata table : tables) {
            for (var column : table.columns()) {
                if (isInForeignKeyGroup(groups, table.tableName(), column.columnName())) {
                    continue;
                }
                String group = "name:" + column.columnName().toLowerCase(Locale.ROOT)
                        + ":" + column.dataType().toUpperCase(Locale.ROOT);
                nameGroups.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new ColumnRef(table.tableName(), column.columnName()));
            }
        }
        groups.putAll(nameGroups);
        return groups;
    }

    /** 查找列所属的外键分组名，未命中返回 null。 */
    public static String findForeignKeyGroup(Map<String, List<ColumnRef>> groups, String table, String column) {
        for (Map.Entry<String, List<ColumnRef>> entry : groups.entrySet()) {
            if (!entry.getKey().startsWith("fk:")) {
                continue;
            }
            for (ColumnRef ref : entry.getValue()) {
                if (ref.table().equals(table) && ref.column().equals(column)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static boolean isInForeignKeyGroup(Map<String, List<ColumnRef>> groups, String table, String column) {
        for (List<ColumnRef> refs : groups.values()) {
            for (ColumnRef ref : refs) {
                if (ref.table().equals(table) && ref.column().equals(column)) {
                    return true;
                }
            }
        }
        return false;
    }
}