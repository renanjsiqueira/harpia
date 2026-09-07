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

/** Emits the Spring Boot Maven build from the target configuration and its contributions. */
public final class PomEmitter implements Emitter {

    private final JavaSpringTemplates templates;

    public PomEmitter(JavaSpringTemplates templates) {
        this.templates = templates;
    }

    @Override
    public void emit(JavaSpringContext context, GeneratedTree output) {
        List<Map<String, Object>> dependencyViews = new ArrayList<>();
        for (MavenDependency dependency : context.contributions().dependencies()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("groupId", dependency.groupId());
            view.put("artifactId", dependency.artifactId());
            view.put("hasVersion", dependency.version().isPresent());
            view.put("version", dependency.version().orElse(""));
            view.put("hasScope", dependency.scope().isPresent());
            view.put("scope", dependency.scope().orElse(""));
            dependencyViews.add(view);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("header", GeneratedHeader.xmlComment());
        view.put("groupId", context.configuration().group());
        view.put("artifactId", context.configuration().artifact());
        view.put("name", context.application().settings().name());
        view.put("javaVersion", context.configuration().languageVersion());
        view.put(
                "springBootVersion",
                context.configuration()
                        .option(JavaSpringTarget.SPRING_BOOT_VERSION_OPTION)
                        .orElseThrow());
        view.put("dependencies", dependencyViews);
        output.put(new GeneratedFile(
                "pom.xml",
                OutputNormalizer.normalize(templates.render("pom.xml.mustache", view)),
                GeneratedFileType.BUILD,
                java.util.Optional.of(dev.harpia.diag.SourceRef.file("harpia.yaml"))));
    }
}
