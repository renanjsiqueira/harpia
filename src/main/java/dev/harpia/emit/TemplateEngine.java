package dev.harpia.emit;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Small classpath-only Mustache adapter. Templates contain presentation, never business logic. */
public final class TemplateEngine {

    public String render(String templateName, Object context) {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(context, "context");
        String resourceName = templateName;
        try (InputStream input = TemplateEngine.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("template not found: " + resourceName);
            }
            Mustache template = new DefaultMustacheFactory().compile(
                    new InputStreamReader(input, StandardCharsets.UTF_8), resourceName);
            StringWriter output = new StringWriter();
            template.execute(output, context).flush();
            return output.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to render template " + resourceName, exception);
        }
    }
}
