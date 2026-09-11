package dev.harpia.target.javaspring;

import dev.harpia.emit.GeneratedHeader;
import dev.harpia.emit.GeneratedFile;
import dev.harpia.emit.GeneratedFileType;
import dev.harpia.emit.GeneratedTree;
import dev.harpia.emit.OutputNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Emits deterministic Spring configuration using environment-backed secrets. */
public final class AppConfigEmitter implements Emitter {

    private final JavaSpringTemplates templates;

    public AppConfigEmitter(JavaSpringTemplates templates) {
        this.templates = templates;
    }

    @Override
    public void emit(JavaSpringContext context, GeneratedTree output) {
        TreeMap<String, ConfigurationProperty> properties = new TreeMap<>();
        String applicationName = context.application().settings().name();
        properties.put(
                "spring.application.name",
                new ConfigurationProperty("spring.application.name", applicationName));
        for (ConfigurationProperty property : context.contributions().properties()) {
            ConfigurationProperty previous = properties.putIfAbsent(property.key(), property);
            if (previous != null && !previous.equals(property)) {
                throw new IllegalArgumentException(
                        "provider conflicts with application property '" + property.key() + "'");
            }
        }
        // The project states the rest. A key a provider already owns is refused rather than
        // overwritten: the provider chose `ddl-auto: validate` to match the migrations Harpia
        // generated, and a project quietly flipping it would make those migrations a lie.
        for (Map.Entry<String, String> declared
                : context.configuration().properties().entrySet()) {
            ConfigurationProperty previous = properties.putIfAbsent(
                    declared.getKey(),
                    new ConfigurationProperty(declared.getKey(), declared.getValue()));
            if (previous != null) {
                throw new dev.harpia.target.TargetGenerationException(
                        dev.harpia.diag.ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                        "target.properties cannot set '" + declared.getKey()
                                + "': the generated project already sets it to '"
                                + previous.value() + "'",
                        dev.harpia.diag.SourceRef.file("harpia.yaml"),
                        null);
            }
        }

        List<Map<String, String>> propertyViews = new ArrayList<>();
        for (ConfigurationProperty property : properties.values()) {
            propertyViews.add(Map.of("key", property.key(), "value", yamlString(property.value())));
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("header", GeneratedHeader.yamlComment());
        view.put("properties", propertyViews);
        output.put(new GeneratedFile(
                "src/main/resources/application.yaml",
                OutputNormalizer.normalize(templates.render("application.yaml.mustache", view)),
                GeneratedFileType.CONFIGURATION,
                java.util.Optional.of(dev.harpia.diag.SourceRef.file("harpia.yaml"))));

        // A test has no deployment to fill a placeholder in, so whatever the context reads on the
        // way up gets a value here instead of a fabricated default in what everyone ships.
        if (context.contributions().testProperties().isEmpty()
                || !context.application().settings().generation().tests()) {
            return;
        }
        List<Map<String, String>> testViews = new ArrayList<>();
        for (ConfigurationProperty property : context.contributions().testProperties()) {
            testViews.add(Map.of("key", property.key(), "value", yamlString(property.value())));
        }
        Map<String, Object> testView = new LinkedHashMap<>();
        testView.put("header", GeneratedHeader.yamlComment());
        testView.put("properties", testViews);
        output.put(new GeneratedFile(
                "src/test/resources/application.yaml",
                OutputNormalizer.normalize(
                        templates.render("application.yaml.mustache", testView)),
                GeneratedFileType.CONFIGURATION,
                java.util.Optional.of(dev.harpia.diag.SourceRef.file("harpia.yaml"))));
    }

    private static String yamlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
