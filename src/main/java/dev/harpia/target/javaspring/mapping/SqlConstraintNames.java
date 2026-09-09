package dev.harpia.target.javaspring.mapping;

import java.util.Objects;

/**
 * Names of the constraints this target creates.
 *
 * <p>The schema declares them and the error handler recognises them, so the name is decided in one
 * place. If the two ever disagreed, a duplicate would surface as a 500 instead of the declared 409,
 * and nothing would fail until production.
 */
public final class SqlConstraintNames {

    private SqlConstraintNames() {
    }

    public static String primaryKey(String tableName) {
        Objects.requireNonNull(tableName, "tableName");
        return "pk_" + tableName;
    }

    public static String foreignKey(String tableName, String columnName) {
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(columnName, "columnName");
        return "fk_" + tableName + "_" + columnName;
    }

    public static String unique(String tableName, String columnName) {
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(columnName, "columnName");
        return "uq_" + tableName + "_" + columnName;
    }
}
