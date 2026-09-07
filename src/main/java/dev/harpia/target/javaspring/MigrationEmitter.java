package dev.harpia.target.javaspring;

import dev.harpia.emit.GeneratedHeader;
import dev.harpia.emit.GeneratedFile;
import dev.harpia.emit.GeneratedFileType;
import dev.harpia.emit.GeneratedTree;
import dev.harpia.emit.OutputNormalizer;
import dev.harpia.target.javaspring.model.SqlMigrationModel;
import dev.harpia.target.javaspring.transformer.JavaSpringMigrationTransformer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits the initial Flyway migration.
 *
 * <p>The schema is derived from the same Application IR the entity comes from, so the two cannot
 * drift: {@code ddl-auto: validate} in the generated configuration turns any disagreement into a
 * startup failure rather than a silent mismatch.
 */
public final class MigrationEmitter implements Emitter {

    public static final String PATH = "src/main/resources/db/migration/V1__init.sql";

    private final JavaSpringTemplates templates;
    private final JavaSpringMigrationTransformer transformer;

    public MigrationEmitter(JavaSpringTemplates templates) {
        this.templates = templates;
        this.transformer = new JavaSpringMigrationTransformer();
    }

    @Override
    public void emit(JavaSpringContext context, GeneratedTree output) {
        if (!context.application().settings().generation().migrations()
                || context.application().entities().isEmpty()) {
            return;
        }
        SqlMigrationModel migration = transformer.transform(context.application());
        List<Map<String, Object>> tables = migration.tables().stream()
                .map(MigrationEmitter::tableView)
                .toList();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("header", GeneratedHeader.sqlComment());
        view.put("tables", tables);
        output.put(new GeneratedFile(
                PATH,
                OutputNormalizer.normalize(templates.render("migration.sql.mustache", view)),
                GeneratedFileType.MIGRATION,
                migration.source()));
    }

    private static Map<String, Object> tableView(SqlMigrationModel.Table table) {
        List<Map<String, Object>> columns = new ArrayList<>();
        table.columns().forEach(column -> columns.add(Map.of("definition", column)));

        List<Map<String, Object>> constraintViews = new ArrayList<>();
        for (int index = 0; index < table.constraints().size(); index++) {
            constraintViews.add(Map.of(
                    "definition", table.constraints().get(index),
                    "last", index == table.constraints().size() - 1));
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("tableName", table.name());
        view.put("columns", columns);
        view.put("constraints", constraintViews);
        return view;
    }
}
