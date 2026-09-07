package dev.harpia.target.javaspring;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationLogic;
import dev.harpia.application.ApplicationProject;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.target.HarpiaTarget;
import dev.harpia.target.TargetCatalog;
import dev.harpia.target.TargetConfiguration;
import dev.harpia.target.TargetDescriptor;
import dev.harpia.target.TargetGenerationResult;

/**
 * The only target this compiler can generate today: Java 21 and Spring Boot, built with Maven.
 *
 * <p>Everything Java or Spring specific — type mapping, package layout, reserved words, build file,
 * framework dependencies, configuration keys — is reachable only from here.
 */
public final class JavaSpringTarget implements HarpiaTarget {

    public static final String SPRING_BOOT_VERSION_OPTION = "springBootVersion";

    private final EmitterPipeline pipeline;

    public JavaSpringTarget() {
        this(EmitterPipeline.standard());
    }

    public JavaSpringTarget(EmitterPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public TargetDescriptor descriptor() {
        return TargetCatalog.find(TargetCatalog.JAVA_SPRING).orElseThrow();
    }

    @Override
    public void validate(
            ApplicationProject application,
            TargetConfiguration configuration,
            DiagnosticCollector diagnostics) {
        javaPackage("project.group", configuration.group(), diagnostics);
        javaPackage("project.package", application.settings().namespace(), diagnostics);
        if (configuration.option(SPRING_BOOT_VERSION_OPTION).isEmpty()) {
            diagnostics.error(
                    ErrorCodes.TARGET_OPTION,
                    "target `java-spring` requires the option '" + SPRING_BOOT_VERSION_OPTION + "'",
                    dev.harpia.diag.SourceRef.file("harpia.yaml"));
        }
        for (ApplicationEntity entity : application.entities()) {
            for (ApplicationField field : entity.fields()) {
                reserved(field.name(), "field", field.where(), diagnostics);
            }
        }
        for (ApplicationLogic logic : application.logics()) {
            for (ApplicationLogic.Parameter parameter : logic.parameters()) {
                reserved(parameter.name(), "parameter", logic.where(), diagnostics);
            }
        }
    }

    private static void javaPackage(
            String key, String value, DiagnosticCollector diagnostics) {
        if (!JavaNames.isPackageName(value)) {
            diagnostics.error(
                    ErrorCodes.CONFIG_INVALID_PACKAGE,
                    key + " is not a valid Java 21 package name: '" + value + "'",
                    dev.harpia.diag.SourceRef.file("harpia.yaml"));
        }
    }

    @Override
    public TargetGenerationResult generate(
            ApplicationProject application, TargetConfiguration configuration) {
        return pipeline.emit(JavaSpringContext.of(application, configuration));
    }

    private static void reserved(
            String name,
            String kind,
            dev.harpia.diag.SourceRef where,
            DiagnosticCollector diagnostics) {
        if (JavaReservedWords.ALL.contains(name)) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_JAVA_RESERVED,
                    kind + " name '" + name + "' is a reserved Java word",
                    where);
        }
    }
}
