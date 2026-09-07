package dev.harpia.application;

import dev.harpia.capability.Capability;
import dev.harpia.capability.ProviderId;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Constraints imposed by a resolved provider rather than by the Harpia language.
 *
 * <p>PostgreSQL reserving a word is a property of PostgreSQL, not of Harpia and not of Java: any
 * target generating against this provider hits the same constraint, so the check belongs here and
 * runs only when the provider is actually selected.
 */
public final class ProviderValidation {

    private static final ProviderId POSTGRESQL = new ProviderId("postgresql");
    private static final Set<String> POSTGRES_RESERVED = load("/reserved-postgres.txt");

    private ProviderValidation() {
    }

    public static void validate(ApplicationProject project, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(diagnostics, "diagnostics");
        boolean postgres = project.capabilities().get(Capability.PERSISTENCE)
                .flatMap(resolved -> resolved.provider())
                .filter(POSTGRESQL::equals)
                .isPresent();
        if (!postgres) {
            return;
        }
        for (ApplicationEntity entity : project.entities()) {
            if (POSTGRES_RESERVED.contains(entity.tableName())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_POSTGRES_RESERVED,
                        "table name '" + entity.tableName()
                                + "' is reserved by PostgreSQL; rename the entity",
                        entity.where());
            }
            for (ApplicationField field : entity.fields()) {
                if (POSTGRES_RESERVED.contains(field.columnName())) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_POSTGRES_RESERVED,
                            "column name '" + field.columnName()
                                    + "' is reserved by PostgreSQL; rename the field",
                            field.where());
                }
            }
        }
    }

    private static Set<String> load(String resource) {
        InputStream stream = ProviderValidation.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new ExceptionInInitializerError("missing resource " + resource);
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(words::add);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Set.copyOf(words);
    }
}
