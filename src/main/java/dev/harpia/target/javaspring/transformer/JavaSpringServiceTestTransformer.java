package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationOperation.FlowCommand;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaSampleValues;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates a unit test per flow, so the translation from Harpia to Java is observed and not only
 * compiled.
 *
 * <p>The repository is mocked: what is under test is the flow, not Spring Data. Every value is
 * derived from the specification, so two runs of the generator produce the same assertions.
 */
public final class JavaSpringServiceTestTransformer {

    // Statements use simple names; the qualified names below are what the file imports.
    private static final String MOCKITO = "Mockito";
    private static final String MATCHERS = "ArgumentMatchers";
    private static final String ASSERTIONS = "Assertions";
    private static final String CAPTOR = "ArgumentCaptor";
    private static final List<String> FIXED_IMPORTS = List.of(
            "java.util.Optional",
            "org.assertj.core.api.Assertions",
            "org.junit.jupiter.api.Test",
            "org.mockito.ArgumentCaptor",
            "org.mockito.ArgumentMatchers",
            "org.mockito.InjectMocks",
            "org.mockito.Mock",
            "org.mockito.Mockito",
            "org.mockito.junit.jupiter.MockitoExtension");

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        Names names = Names.of(context, entity);
        TreeSet<String> imports = new TreeSet<>(FIXED_IMPORTS);
        imports.add(names.responseImport());
        entity.fields().forEach(field ->
                imports.addAll(
                        JavaSampleValues.requiredImports(field, names.domainPackage())));
        for (ApplicationOperation operation : entity.operations()) {
            operation.requestTypeName().ifPresent(
                    request -> imports.add(names.dtoPackage() + "." + request));
            if (has(operation, FlowCommand.LOAD_BY_ID)) {
                imports.add(names.notFoundImport());
            }
        }

        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationOperation operation : entity.operations()) {
            methods.add(happyPath(names, entity, operation, imports));
            if (has(operation, FlowCommand.LOAD_BY_ID)) {
                methods.add(missing(names, entity, operation));
            }
        }
        methods.add(sampleEntity(names, entity));

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                names.servicePackage(),
                JavaLayout.testTypeName(names.serviceName()),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                Optional.of("Every Harpia flow of " + entity.typeName()
                        + ", observed against a mocked repository."),
                List.of(JavaAnnotationModel.of(
                        "org.junit.jupiter.api.extension.ExtendWith",
                        new JavaAnnotationModel.Attribute("value", "MockitoExtension.class"))),
                imports.stream().map(JavaImportModel::new).toList(),
                List.of(),
                List.of(identifier(names, entity), mock(names), underTest(names)),
                List.of(),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.testSourcePath(names.servicePath(), JavaLayout.testTypeName(
                        names.serviceName())),
                type,
                Optional.of(entity.where()));
    }

    private static JavaFieldModel identifier(Names names, ApplicationEntity entity) {
        return new JavaFieldModel(
                "ID",
                JavaTypeMapper.map(entity.idField().scalarType()),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                List.of(),
                Optional.of(JavaSampleValues.java(entity.idField())),
                Optional.of(entity.where()));
    }

    private static JavaFieldModel mock(Names names) {
        return new JavaFieldModel(
                "repository",
                names.repositoryType(),
                JavaVisibility.PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.mockito.Mock")),
                Optional.empty(),
                Optional.empty());
    }

    private static JavaFieldModel underTest(Names names) {
        return new JavaFieldModel(
                "service",
                names.serviceType(),
                JavaVisibility.PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.mockito.InjectMocks")),
                Optional.empty(),
                Optional.empty());
    }

    /** The field a {@code find} in this operation searches by, when there is one. */
    private static Optional<String> finder(ApplicationOperation operation) {
        return operation.flow().stream()
                .filter(instruction -> instruction.command() == FlowCommand.FIND_BY)
                .findFirst()
                .flatMap(ApplicationOperation.FlowInstruction::field);
    }

    private JavaMethodModel happyPath(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> imports) {
        String entityName = entity.typeName();
        boolean loads = has(operation, FlowCommand.LOAD_BY_ID);
        Optional<String> finds = finder(operation);
        boolean lists = has(operation, FlowCommand.LIST_ALL);
        boolean creates = has(operation, FlowCommand.CREATE_FROM);
        boolean updates = has(operation, FlowCommand.UPDATE_FROM);
        boolean saves = has(operation, FlowCommand.SAVE);
        boolean deletes = has(operation, FlowCommand.DELETE);

        List<String> statements = new ArrayList<>();
        if (loads || lists || finds.isPresent()) {
            statements.add(entityName + " entity = sampleEntity();");
        }
        if (loads) {
            statements.add(MOCKITO + ".when(repository.findById(ID))"
                    + ".thenReturn(Optional.of(entity));");
        }
        // A find searches by the field the flow named, so the stub has to answer that call and
        // not the one an id lookup would have made.
        finds.ifPresent(field -> statements.add(MOCKITO + ".when(repository.findBy"
                + JavaLayout.accessor("", field) + "(" + MATCHERS + ".any()))"
                + ".thenReturn(Optional.of(entity));"));
        if (lists) {
            imports.add("java.util.List");
            imports.add("org.springframework.data.domain.Sort");
            statements.add(MOCKITO + ".when(repository.findAll(" + MATCHERS
                    + ".any(Sort.class)))");
            statements.add("        .thenReturn(List.of(entity));");
        }
        if (saves) {
            statements.add(MOCKITO + ".when(repository.save(" + MATCHERS + ".any("
                    + entityName + ".class)))");
            statements.add("        .thenAnswer(invocation -> invocation.getArgument(0));");
        }

        String request = null;
        if (operation.requestTypeName().isPresent()) {
            request = "request";
            statements.add("");
            statements.add(operation.requestTypeName().orElseThrow() + " request = new "
                    + operation.requestTypeName().orElseThrow() + "("
                    + arguments(operation.input()) + ");");
        }
        statements.add("");
        statements.add(call(names, operation, request));
        statements.add("");

        if (creates && saves) {
            statements.add(CAPTOR + "<" + entityName + "> saved = " + CAPTOR + ".forClass("
                    + entityName + ".class);");
            statements.add(MOCKITO + ".verify(repository).save(saved.capture());");
            for (ApplicationField field : operation.input()) {
                statements.add(ASSERTIONS + ".assertThat(saved.getValue()."
                        + JavaLayout.accessor("get", field.name()) + "())"
                        + ".isEqualTo(request." + field.name() + "());");
            }
            operation.input().stream().findFirst().ifPresent(field -> statements.add(
                    ASSERTIONS + ".assertThat(response." + field.name() + "())"
                            + ".isEqualTo(request." + field.name() + "());"));
        }
        if (updates) {
            for (ApplicationField field : operation.input()) {
                statements.add(ASSERTIONS + ".assertThat(entity."
                        + JavaLayout.accessor("get", field.name()) + "())"
                        + ".isEqualTo(request." + field.name() + "());");
            }
            if (saves) {
                statements.add(MOCKITO + ".verify(repository).save(entity);");
            }
        }
        if (deletes) {
            statements.add(MOCKITO + ".verify(repository).delete(entity);");
        }
        if (lists) {
            statements.add(ASSERTIONS + ".assertThat(response).hasSize(1);");
            statements.add(CAPTOR + "<Sort> order = " + CAPTOR + ".forClass(Sort.class);");
            statements.add(MOCKITO + ".verify(repository).findAll(order.capture());");
            statements.add(ASSERTIONS + ".assertThat(order.getValue())"
                    + ".as(\"Harpia requires a stable order\").isEqualTo(Sort.by(\""
                    + entity.idField().name() + "\"));");
        } else if (loads
                && operation.result().kind() == ApplicationOperation.ResultKind.ENTITY
                && !updates) {
            statements.add(ASSERTIONS + ".assertThat(response." + entity.idField().name()
                    + "()).isEqualTo(entity." + JavaLayout.accessor("get",
                            entity.idField().name()) + "());");
        }

        return new JavaMethodModel(
                methodName(operation, describe(creates, updates, deletes, lists)),
                JavaTypeRef.of("void"),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.junit.jupiter.api.Test")),
                List.of(),
                statements,
                Optional.of(operation.where()));
    }

    private JavaMethodModel missing(
            Names names, ApplicationEntity entity, ApplicationOperation operation) {
        List<String> statements = new ArrayList<>();
        Optional<String> finds = finder(operation);
        statements.add(finds
                .map(field -> MOCKITO + ".when(repository.findBy" + JavaLayout.accessor("", field)
                        + "(" + MATCHERS + ".any())).thenReturn(Optional.empty());")
                .orElse(MOCKITO + ".when(repository.findById(ID)).thenReturn(Optional.empty());"));
        statements.add("");
        String request = null;
        if (operation.requestTypeName().isPresent()) {
            request = "request";
            statements.add(operation.requestTypeName().orElseThrow() + " request = new "
                    + operation.requestTypeName().orElseThrow() + "("
                    + arguments(operation.input()) + ");");
        }
        statements.add(ASSERTIONS + ".assertThatThrownBy(() -> service."
                + operation.methodName() + "(" + callArguments(operation, request) + "))");
        statements.add("        .isInstanceOf(NotFoundException.class);");

        return new JavaMethodModel(
                methodName(operation, "FailsWhenNothingIsStored"),
                JavaTypeRef.of("void"),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.junit.jupiter.api.Test")),
                List.of(),
                statements,
                Optional.of(operation.where()));
    }

    private static JavaMethodModel sampleEntity(Names names, ApplicationEntity entity) {
        List<String> statements = new ArrayList<>();
        statements.add(entity.typeName() + " entity = new " + entity.typeName() + "();");
        for (ApplicationField field : entity.fields()) {
            statements.add("entity." + JavaLayout.accessor("set", field.name()) + "("
                    + JavaSampleValues.stored(field) + ");");
        }
        statements.add("return entity;");
        return new JavaMethodModel(
                "sampleEntity",
                names.entityType(),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC),
                List.of(),
                List.of(),
                statements,
                Optional.of(entity.where()));
    }

    private static String call(Names names, ApplicationOperation operation, String request) {
        String invocation = "service." + operation.methodName()
                + "(" + callArguments(operation, request) + ");";
        if (operation.result().kind() == ApplicationOperation.ResultKind.NOTHING) {
            return invocation;
        }
        String type = operation.result().kind() == ApplicationOperation.ResultKind.LIST
                ? "List<" + names.responseName() + ">"
                : names.responseName();
        return type + " response = " + invocation;
    }

    private static String callArguments(ApplicationOperation operation, String request) {
        List<String> arguments = new ArrayList<>();
        if (operation.requiresId()) {
            arguments.add("ID");
        }
        if (request != null) {
            arguments.add(request);
        }
        return String.join(", ", arguments);
    }

    private static String arguments(List<ApplicationField> fields) {
        return fields.stream().map(JavaSampleValues::java).reduce(
                (left, right) -> left + ", " + right).orElse("");
    }

    private static String methodName(ApplicationOperation operation, String suffix) {
        return operation.methodName() + suffix;
    }

    /** Names the test after what it actually observes. */
    private static String describe(
            boolean creates, boolean updates, boolean deletes, boolean lists) {
        if (creates) {
            return "PersistsWhatTheRequestCarries";
        }
        if (updates) {
            return "CopiesTheRequestOntoTheStoredRecord";
        }
        if (deletes) {
            return "RemovesTheStoredRecord";
        }
        if (lists) {
            return "ReturnsEveryRecordInDeclaredOrder";
        }
        return "ReturnsTheStoredRecord";
    }

    private static boolean has(ApplicationOperation operation, FlowCommand command) {
        return operation.flow().stream()
                .anyMatch(instruction -> instruction.command() == command);
    }

    /** Java names this transformer needs, resolved once. */
    private record Names(
            String servicePackage,
            String servicePath,
            String serviceName,
            String responseName,
            String dtoPackage,
            String errorPackage,
            JavaTypeRef entityType,
            JavaTypeRef serviceType,
            JavaTypeRef repositoryType) {

        private static Names of(JavaSpringContext context, ApplicationEntity entity) {
            JavaLayout layout = context.layout();
            String serviceName = JavaLayout.serviceTypeName(entity.typeName());
            return new Names(
                    layout.packageName(JavaLayout.SERVICE),
                    layout.packagePath(JavaLayout.SERVICE),
                    serviceName,
                    entity.responseTypeName(),
                    layout.packageName(JavaLayout.DTO),
                    layout.packageName(JavaLayout.ERROR),
                    JavaTypeRef.of(
                            layout.packageName(JavaLayout.DOMAIN) + "." + entity.typeName()),
                    JavaTypeRef.of(layout.packageName(JavaLayout.SERVICE) + "." + serviceName),
                    JavaTypeRef.of(layout.packageName(JavaLayout.REPOSITORY) + "."
                            + JavaLayout.repositoryTypeName(entity.typeName())));
        }

        private String domainPackage() {
            String qualified = entityType.canonicalName();
            return qualified.substring(0, qualified.lastIndexOf('.'));
        }

        private String responseImport() {
            return dtoPackage + "." + responseName;
        }

        private String notFoundImport() {
            return errorPackage + ".NotFoundException";
        }
    }
}
