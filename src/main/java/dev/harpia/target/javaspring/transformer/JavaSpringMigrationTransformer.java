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
        List<SqlMigrationModel.ForeignKey> foreignKeys = new ArrayList<>();
        for (ApplicationEntity entity : application.entities()) {
            tables.add(table(entity));
            for (ApplicationField field : entity.fields()) {
                field.reference().ifPresent(reference -> foreignKeys.add(foreignKey(
                        entity.tableName(),
                        field.columnName(),
                        SqlNaming.identifier(reference.entity()),
                        "id")));
                field.relationship().ifPresent(relationship -> foreignKeys.add(foreignKey(
                        entity.tableName(),
                        field.columnName(),
                        SqlNaming.identifier(relationship.entity()),
                        "id")));
            }
            // A collection has as many rows per owner as it has elements, so it cannot share the
            // owner's row. It gets its own table, keyed back to the owner.
            for (ApplicationField field : entity.fields()) {
                field.elementType().ifPresent(element -> {
                    SqlMigrationModel.Table collection = collectionTable(entity, field, element);
                    tables.add(collection);
                    String owner = entity.tableName() + "_id";
                    foreignKeys.add(foreignKey(
                            collection.name(),
                            owner,
                            entity.tableName(),
                            entity.idField().columnName()));
                    if (element instanceof
                            dev.harpia.application.ApplicationFieldType.Relationship relationship) {
                        String targetTable = SqlNaming.identifier(relationship.entity());
                        foreignKeys.add(foreignKey(
                                collection.name(),
                                targetTable + "_id",
                                targetTable,
                                "id"));
                    }
                });
            }
        }
        List<SqlMigrationModel.Index> indexes = new ArrayList<>();
        for (ApplicationEntity entity : application.entities()) {
            for (ApplicationField field : entity.fields()) {
                if (field.indexed()) {
                    indexes.add(new SqlMigrationModel.Index(
                            SqlConstraintNames.index(entity.tableName(), field.columnName()),
                            entity.tableName(),
                            field.columnName()));
                }
            }
        }
        return new SqlMigrationModel(
                tables,
                foreignKeys,
                indexes,
                application.entities().isEmpty()
                        ? Optional.empty()
                        : Optional.of(application.entities().getFirst().where()));
    }

    private static SqlMigrationModel.Table collectionTable(
            ApplicationEntity entity,
            ApplicationField field,
            dev.harpia.application.ApplicationFieldType element) {
        if (element instanceof dev.harpia.application.ApplicationFieldType.Relationship
                relationship) {
            return relationshipTable(entity, field, relationship);
        }
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
                List.of());
    }

    private static SqlMigrationModel.Table relationshipTable(
            ApplicationEntity entity,
            ApplicationField field,
            dev.harpia.application.ApplicationFieldType.Relationship relationship) {
        String table = entity.tableName() + "_" + field.columnName();
        String owner = entity.tableName() + "_id";
        String targetTable = SqlNaming.identifier(relationship.entity());
        String target = targetTable + "_id";
        List<String> constraints = new ArrayList<>();
        constraints.add("CONSTRAINT " + SqlConstraintNames.primaryKey(table)
                + " PRIMARY KEY (" + owner + ", " + target + ")");
        if (relationship.lifecycle()
                == dev.harpia.application.ApplicationFieldType.RelationshipLifecycle.DEPENDENT) {
            constraints.add("CONSTRAINT " + SqlConstraintNames.unique(table, target)
                    + " UNIQUE (" + target + ")");
        }
        return new SqlMigrationModel.Table(
                table,
                List.of(
                        owner + " " + PostgresTypes.column(entity.idField().scalarType())
                                + " NOT NULL",
                        target + " " + PostgresTypes.column(relationship.idType())
                                + " NOT NULL"),
                constraints);
    }

    private static String columnType(ApplicationField field) {
        if (field.enumTypeName().isPresent()) {
            return "varchar(64)";
        }
        return field.reference()
                .map(reference -> PostgresTypes.column(reference.idType()))
                .or(() -> field.relationship()
                        .map(relationship -> PostgresTypes.column(relationship.idType())))
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
        entity.fields().stream()
                .filter(field -> field.unique()
                        || field.relationship()
                                .filter(relationship -> relationship.lifecycle()
                                        == dev.harpia.application.ApplicationFieldType
                                                .RelationshipLifecycle.DEPENDENT)
                                .isPresent())
                .map(ApplicationField::columnName)
                .sorted()
                .forEach(column -> constraints.add("CONSTRAINT "
                        + SqlConstraintNames.unique(entity.tableName(), column)
                        + " UNIQUE (" + column + ")"));
        return new SqlMigrationModel.Table(entity.tableName(), columns, constraints);
    }

    private static SqlMigrationModel.ForeignKey foreignKey(
            String table, String column, String targetTable, String targetColumn) {
        return new SqlMigrationModel.ForeignKey(
                SqlConstraintNames.foreignKey(table, column),
                table,
                column,
                targetTable,
                targetColumn);
    }
}
