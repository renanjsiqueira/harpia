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
    }

    private static String yamlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
