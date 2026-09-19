package com.relivus.dialect;

/**
 * 分页语法。
 *
 * <ul>
 *   <li>MySQL：{@code LIMIT offset, size}</li>
 *   <li>PostgreSQL：{@code LIMIT size OFFSET offset}</li>
 * </ul>
 */
public enum PaginationSyntax {

    /** MySQL 风格：LIMIT offset, size。 */
    MYSQL {
        @Override
        public String apply(String sql, long offset, int size) {
            return sql + " LIMIT " + offset + ", " + size;
        }
    },
    /** PostgreSQL 风格：LIMIT size OFFSET offset。 */
    POSTGRESQL {
        @Override
        public String apply(String sql, long offset, int size) {
            return sql + " LIMIT " + size + " OFFSET " + offset;
        }
    };

    /** 在 SQL 末尾追加分页子句。 */
    public abstract String apply(String sql, long offset, int size);
}