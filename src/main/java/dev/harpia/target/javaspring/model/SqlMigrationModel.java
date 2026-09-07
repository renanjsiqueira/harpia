package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Renderer-ready SQL migration model; schema inference has already happened. */
public record SqlMigrationModel(List<Table> tables, Optional<SourceRef> source) {

    public SqlMigrationModel {
        tables = tables.stream().sorted(java.util.Comparator.comparing(Table::name)).toList();
        Objects.requireNonNull(source, "source");
    }

    public record Table(String name, List<String> columns, List<String> constraints) {
        public Table {
            Objects.requireNonNull(name, "name");
            columns = List.copyOf(columns);
            constraints = List.copyOf(constraints);
        }
    }
}
