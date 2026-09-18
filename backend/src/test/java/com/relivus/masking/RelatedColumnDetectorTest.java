package com.relivus.masking;

import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 关联列识别测试（DOC-04：外键分组、同名列分组、复合键）。
 */
class RelatedColumnDetectorTest {

    private static ColumnMetadata col(String name, String type) {
        return new ColumnMetadata(name, type, true, null, false, List.of());
    }

    private static TableMetadata table(String name, List<ColumnMetadata> columns, List<ForeignKeyMetadata> fks) {
        return new TableMetadata(name, columns, fks, List.of(), List.of(), "id");
    }

    @Test
    void groupsForeignKeyColumnsWithParentReference() {
        TableMetadata user = table("user", List.of(col("id", "BIGINT")), List.of());
        TableMetadata order = table("order",
                List.of(col("id", "BIGINT"), col("user_id", "BIGINT"), col("email", "VARCHAR")),
                List.of(new ForeignKeyMetadata("fk_user", "user_id", "user", "id", true)));

        Map<String, List<RelatedColumnDetector.ColumnRef>> groups =
                new RelatedColumnDetector().detect(List.of(user, order));

        List<RelatedColumnDetector.ColumnRef> fkGroup = groups.get("fk:user.id");
        assertThat(fkGroup).contains(
                new RelatedColumnDetector.ColumnRef("order", "user_id"),
                new RelatedColumnDetector.ColumnRef("user", "id"));
    }

    @Test
    void groupsSameNameSameTypeColumns() {
        TableMetadata user = table("user", List.of(col("id", "BIGINT"), col("email", "VARCHAR")), List.of());
        TableMetadata account = table("account", List.of(col("id", "BIGINT"), col("email", "VARCHAR")), List.of());

        Map<String, List<RelatedColumnDetector.ColumnRef>> groups =
                new RelatedColumnDetector().detect(List.of(user, account));

        assertThat(groups.get("name:email:VARCHAR")).contains(
                new RelatedColumnDetector.ColumnRef("user", "email"),
                new RelatedColumnDetector.ColumnRef("account", "email"));
    }

    @Test
    void fkColumnsAreExcludedFromNameGroups() {
        TableMetadata user = table("user", List.of(col("id", "BIGINT")), List.of());
        TableMetadata order = table("order",
                List.of(col("id", "BIGINT"), col("user_id", "BIGINT"), col("email", "VARCHAR")),
                List.of(new ForeignKeyMetadata("fk", "user_id", "user", "id", true)));

        Map<String, List<RelatedColumnDetector.ColumnRef>> groups =
                new RelatedColumnDetector().detect(List.of(user, order));

        Map<String, List<RelatedColumnDetector.ColumnRef>> nameGroups = new java.util.HashMap<>(groups);
        nameGroups.remove("fk:user.id");
        assertThat(nameGroups.values().stream().flatMap(List::stream))
                .noneMatch(ref -> ref.column().equals("user_id"));
    }

    @Test
    void findForeignKeyGroupResolvesOrReturnsNull() {
        TableMetadata user = table("user", List.of(col("id", "BIGINT")), List.of());
        TableMetadata order = table("order",
                List.of(col("id", "BIGINT"), col("user_id", "BIGINT")),
                List.of(new ForeignKeyMetadata("fk", "user_id", "user", "id", true)));
        Map<String, List<RelatedColumnDetector.ColumnRef>> groups =
                new RelatedColumnDetector().detect(List.of(user, order));

        assertThat(RelatedColumnDetector.findForeignKeyGroup(groups, "order", "user_id"))
                .isEqualTo("fk:user.id");
        assertThat(RelatedColumnDetector.findForeignKeyGroup(groups, "user", "user_id")).isNull();
        assertThat(RelatedColumnDetector.findForeignKeyGroup(groups, "order", "id")).isNull();
        assertThat(RelatedColumnDetector.findForeignKeyGroup(Map.of(), "order", "user_id")).isNull();
    }
}