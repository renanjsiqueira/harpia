package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationLogic;
import dev.harpia.application.ApplicationScenario;
import dev.harpia.application.ApplicationScalarType;
import dev.harpia.logic.LogicType;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the scenarios declared for a computation into a plain JUnit test.
 *
 * <p>No Spring context and no mock: a pure computation is a function, so its test is a call and an
 * assertion. The expected value is the one the specification declared — the compiler never invents
 * it, and never evaluates the computation to guess it.
 */
public final class JavaSpringLogicTestTransformer {

    private static final String ASSERTIONS = "org.assertj.core.api.Assertions";

    /** One test class per computation that has at least one scenario. */
    public List<JavaSourceFile> transform(JavaSpringContext context) {
        Map<String, List<ApplicationScenario>> byComputation = new LinkedHashMap<>();
        for (ApplicationScenario scenario : context.application().scenarios()) {
            byComputation
                    .computeIfAbsent(scenario.computation(), ignored -> new ArrayList<>())
                    .add(scenario);
        }

        List<JavaSourceFile> files = new ArrayList<>();
        for (ApplicationLogic logic : context.application().logics()) {
            List<ApplicationScenario> scenarios = byComputation.get(logic.typeName());
            if (scenarios != null && !scenarios.isEmpty()) {
                files.add(transform(context, logic, scenarios));
            }
        }
        return List.copyOf(files);
    }

    private JavaSourceFile transform(
            JavaSpringContext context,
            ApplicationLogic logic,
            List<ApplicationScenario> scenarios) {
        TreeSet<String> imports = new TreeSet<>(List.of(
                ASSERTIONS, "org.junit.jupiter.api.Test"));
        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationScenario scenario : scenarios) {
            methods.add(test(logic, scenario, imports));
        }

        String className = JavaLayout.testTypeName(logic.typeName());
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                context.layout().packageName(JavaLayout.LOGIC),
                className,
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                Optional.of("The scenarios declared for " + logic.typeName()
                        + ". A pure computation needs no context to be tested."),
                List.of(),
                imports.stream().map(JavaImportModel::new).toList(),
                List.of(),
                List.of(),
                List.of(),
                methods,
                Optional.of(logic.where()));
        return new JavaSourceFile(
                JavaLayout.testSourcePath(
                        context.layout().packagePath(JavaLayout.LOGIC), className),
                type,
                Optional.of(logic.where()));
    }

    private static JavaMethodModel test(
            ApplicationLogic logic, ApplicationScenario scenario, TreeSet<String> imports) {
        List<String> arguments = new ArrayList<>();
        for (ApplicationScenario.Argument argument : scenario.arguments()) {
            importFor(argument.type(), imports);
            arguments.add(value(argument.type(), argument.value()));
        }
        importFor(scenario.resultType(), imports);

        String call = logic.typeName() + ".apply(" + String.join(", ", arguments) + ")";
        List<String> statements = new ArrayList<>();
        statements.add(ASSERTIONS + ".assertThat(" + call + ")");
        statements.add("        " + assertion(scenario) + ";");

        return new JavaMethodModel(
                scenario.methodName(),
                JavaTypeRef.of("void"),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.junit.jupiter.api.Test")),
                List.of(),
                statements,
                Optional.of(scenario.where()));
    }

    /**
     * {@code BigDecimal.equals} compares scale, so {@code 20} and {@code 20.00} would not match
     * even though the specification means the same number. Decimal comparisons use the value.
     */
    private static String assertion(ApplicationScenario scenario) {
        if (scalar(scenario.resultType()) == ApplicationScalarType.DECIMAL) {
            return ".isEqualByComparingTo(\"" + scenario.expected() + "\")";
        }
        return ".isEqualTo(" + value(scenario.resultType(), scenario.expected()) + ")";
    }

    /** A declared literal as the Java expression that preserves its value. */
    private static String value(LogicType type, String literal) {
        return switch (scalar(type)) {
            case BOOLEAN, INT -> literal;
            case LONG -> literal + "L";
            case DECIMAL -> "new BigDecimal(\"" + literal + "\")";
            case STRING, TEXT, EMAIL -> literal.replace("\\/", "/");
            case UUID -> "UUID.fromString(" + literal + ")";
            case DATE -> "LocalDate.parse(" + literal + ")";
            case DATE_TIME -> "OffsetDateTime.parse(" + literal + ")";
        };
    }

    private static void importFor(LogicType type, TreeSet<String> imports) {
        JavaTypeMapper.map(type);
        switch (scalar(type)) {
            case DECIMAL -> imports.add("java.math.BigDecimal");
            case UUID -> imports.add("java.util.UUID");
            case DATE -> imports.add("java.time.LocalDate");
            case DATE_TIME -> imports.add("java.time.OffsetDateTime");
            default -> {
                // Every other scalar is java.lang and needs no import.
            }
        }
    }

    private static ApplicationScalarType scalar(LogicType type) {
        if (type instanceof LogicType.Scalar value) {
            return ApplicationScalarType.valueOf(value.kind().name());
        }
        throw new IllegalArgumentException("no Java value for " + type.display());
    }
}
