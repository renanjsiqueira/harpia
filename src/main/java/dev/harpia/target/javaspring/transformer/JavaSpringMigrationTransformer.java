package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.mapping.SqlConstraintNames;
import dev.harpia.application.ApplicationProject;
import dev.harpia.application.SqlNaming;
import dev.harpia.target.javaspring.PostgresTypes;
import dev.harpia.target.javaspring.model.SqlMigrationModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Infers the deterministic PostgreSQL schema before the SQL template is invoked. */
public final class JavaSpringMigrationTransformer {

    public SqlMigrationModel transform(ApplicationProject application) {
        List<SqlMigrationModel.Table> tables = new ArrayList<>();
        for (ApplicationEntity entity : application.entities()) {
            tables.add(table(entity));
            // A collection has as many rows per owner as it has elements, so it cannot share the
            // owner's row. It gets its own table, keyed back to the owner.
            for (ApplicationField field : entity.fields()) {
                field.elementType().ifPresent(element ->
                        tables.add(collectionTable(entity, field, element)));
            }
        }
        return new SqlMigrationModel(
                tables,
                application.entities().isEmpty()
                        ? Optional.empty()
                        : Optional.of(application.entities().getFirst().where()));
    }

    private static SqlMigrationModel.Table collectionTable(
            ApplicationEntity entity,
            ApplicationField field,
            dev.harpia.application.ApplicationFieldType element) {
        String owner = entity.tableName() + "_id";
        String elementColumn = element instanceof
                dev.harpia.application.ApplicationFieldType.EnumType
                ? "varchar(64)"
                : PostgresTypes.column(element.scalarKind().orElseThrow());
        return new SqlMigrationModel.Table(
                entity.tableName() + "_" + field.columnName(),
                List.of(
                        owner + " " + PostgresTypes.column(entity.idField().scalarType())
                                + " NOT NULL",
                        field.columnName() + " " + elementColumn + " NOT NULL"),
                List.of("CONSTRAINT "
                        + SqlConstraintNames.foreignKey(
                                entity.tableName() + "_" + field.columnName(), owner)
                        + " FOREIGN KEY (" + owner + ") REFERENCES "
                        + entity.tableName() + " (" + entity.idField().columnName() + ")"));
    }

    private static String columnType(ApplicationField field) {
        if (field.enumTypeName().isPresent()) {
            return "varchar(64)";
        }
        return field.reference()
                .map(reference -> PostgresTypes.column(reference.idType()))
                .orElseGet(() -> PostgresTypes.column(field.scalarType()));
    }

    private static SqlMigrationModel.Table table(ApplicationEntity entity) {
        List<String> columns = new ArrayList<>();
        for (ApplicationField field : entity.fields()) {
            boolean identifier = field.equals(entity.idField());
            // A value has no column of its own: it is stored as the columns it groups, prefixed by
            // the field that holds it, so two values in one table cannot collide.
            // A collection lives in its own table, added beside this one.
            if (field.elementType().isPresent()) {
                continue;
            }
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
        // Referential integrity is the one thing a reference does promise, so the schema states it.
        for (ApplicationField field : entity.fields()) {
            field.reference().ifPresent(reference -> constraints.add("CONSTRAINT "
                    + SqlConstraintNames.foreignKey(entity.tableName(), field.columnName())
                    + " FOREIGN KEY (" + field.columnName() + ") REFERENCES "
                    + SqlNaming.identifier(reference.entity()) + " (id)"));
        }
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
