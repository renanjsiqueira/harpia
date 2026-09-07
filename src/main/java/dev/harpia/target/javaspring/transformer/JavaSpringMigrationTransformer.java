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

    private static SqlMigrationModel.Table table(ApplicationEntity entity) {
        List<String> columns = new ArrayList<>();
        for (ApplicationField field : entity.fields()) {
            boolean identifier = field.equals(entity.idField());
            columns.add(field.columnName()
                    + " " + PostgresTypes.column(field.type())
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
