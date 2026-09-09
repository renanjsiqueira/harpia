package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.mapping.SqlConstraintNames;
import dev.harpia.application.ApplicationProject;
import dev.harpia.target.javaspring.PostgresTypes;
import dev.harpia.target.javaspring.model.SqlMigrationModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Infers the deterministic PostgreSQL schema before the SQL template is invoked. */
public final class JavaSpringMigrationTransformer {

    public SqlMigrationModel transform(ApplicationProject application) {
        List<SqlMigrationModel.Table> tables = application.entities().stream()
                .map(JavaSpringMigrationTransformer::table)
                .toList();
        return new SqlMigrationModel(
                tables,
                application.entities().isEmpty()
                        ? Optional.empty()
                        : Optional.of(application.entities().getFirst().where()));
    }

    private static String columnType(ApplicationField field) {
        return field.enumTypeName().isPresent()
                ? "varchar(64)"
                : PostgresTypes.column(field.scalarType());
    }

    private static SqlMigrationModel.Table table(ApplicationEntity entity) {
        List<String> columns = new ArrayList<>();
        for (ApplicationField field : entity.fields()) {
            boolean identifier = field.equals(entity.idField());
            // A value has no column of its own: it is stored as the columns it groups, prefixed by
            // the field that holds it, so two values in one table cannot collide.
            if (field.valueType().isPresent()) {
                for (ApplicationField component : field.valueType().orElseThrow().components()) {
                    columns.add(field.columnName() + "_" + component.columnName()
                            + " " + columnType(component)
                            + (field.required() && component.required() ? " NOT NULL" : ""));
                }
                continue;
            }
            columns.add(field.columnName()
                    + " " + columnType(field)
                    + (field.required() || identifier ? " NOT NULL" : ""));
        }

        List<String> constraints = new ArrayList<>();
        constraints.add("CONSTRAINT "
                + SqlConstraintNames.primaryKey(entity.tableName())
                + " PRIMARY KEY (" + entity.idField().columnName() + ")");
        entity.fields().stream()
                .filter(ApplicationField::unique)
                .map(ApplicationField::columnName)
                .sorted()
                .forEach(column -> constraints.add("CONSTRAINT "
                        + SqlConstraintNames.unique(entity.tableName(), column)
                        + " UNIQUE (" + column + ")"));
        return new SqlMigrationModel.Table(entity.tableName(), columns, constraints);
    }
}
