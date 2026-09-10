package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Renderer-ready SQL migration model; schema inference has already happened. */
public record SqlMigrationModel(
        List<Table> tables,
        List<ForeignKey> foreignKeys,
        Optional<SourceRef> source) {

    public SqlMigrationModel {
        tables = tables.stream().sorted(java.util.Comparator.comparing(Table::name)).toList();
        foreignKeys = foreignKeys.stream()
                .sorted(java.util.Comparator.comparing(ForeignKey::table)
                        .thenComparing(ForeignKey::column))
                .toList();
        Objects.requireNonNull(source, "source");
    }

    public record Table(String name, List<String> columns, List<String> constraints) {
        public Table {
            Objects.requireNonNull(name, "name");
            columns = List.copyOf(columns);
            constraints = List.copyOf(constraints);
        }
    }

    /**
     * A foreign key emitted after every table exists.
     *
     * <p>Keeping it outside {@link Table} supports reverse declaration order, self references and
     * relationship cycles without trying to topologically sort an inherently cyclic graph.
     */
    public record ForeignKey(
            String name,
            String table,
            String column,
            String targetTable,
            String targetColumn) {
        public ForeignKey {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(column, "column");
            Objects.requireNonNull(targetTable, "targetTable");
            Objects.requireNonNull(targetColumn, "targetColumn");
        }
    }
}
