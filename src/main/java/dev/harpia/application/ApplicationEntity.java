package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/**
 * Application-level persistent entity and the operations that act on it.
 *
 * <p>It names the entity, its table and its response model, and stops there. Which components
 * implement the operations is an architecture decision, and architecture belongs to the target.
 */
public record ApplicationEntity(
        String typeName,
        String tableName,
        String responseTypeName,
        List<ApplicationField> fields,
        ApplicationField idField,
        List<ApplicationOperation> operations,
        SourceRef where) {

    public ApplicationEntity {
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(responseTypeName, "responseTypeName");
        fields = List.copyOf(fields);
        Objects.requireNonNull(idField, "idField");
        operations = List.copyOf(operations);
        Objects.requireNonNull(where, "where");
        if (fields.stream().noneMatch(idField::equals)) {
            throw new IllegalArgumentException("id field must belong to the entity");
        }
    }
}
