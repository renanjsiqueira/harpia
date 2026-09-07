package dev.harpia.target.javaspring;

import dev.harpia.emit.TemplateEngine;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.target.TargetGenerationException;
import java.util.Objects;

/** Template lookup scoped to this target, so each target owns its own template directory. */
public final class JavaSpringTemplates {

    private static final String DIRECTORY = "targets/java-spring/templates/";

    private final TemplateEngine engine = new TemplateEngine();

    public String render(String templateName, Object context) {
        Objects.requireNonNull(templateName, "templateName");
        try {
            return engine.render(DIRECTORY + templateName, context);
        } catch (RuntimeException exception) {
            throw new TargetGenerationException(
                    ErrorCodes.TARGET_TEMPLATE_FAILURE,
                    "failed to render Java/Spring target template `" + templateName + "`",
                    SourceRef.file("harpia.yaml"),
                    exception);
        }
    }
}
