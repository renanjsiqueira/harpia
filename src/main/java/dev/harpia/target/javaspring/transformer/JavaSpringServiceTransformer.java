package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationRule;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaLogicWriter;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Translates the Harpia flows of one entity into an application service.
 *
 * <p>Each method is one flow, statement for statement, keeping the variable names the
 * specification used. The transaction boundary follows the operation rather than the code: a flow
 * that writes is transactional, a flow that only reads is read-only.
 *
 * <p>{@code validate input} produces a method-validation boundary through {@code @Validated} and
 * {@code @Valid}. An HTTP adapter also validates at deserialization time, but internal operations
 * keep the declared validation when invoked through the Spring service bean.
 */
public final class JavaSpringServiceTransformer {

    private static final String REPOSITORY_FIELD = "repository";
    private static final String REQUEST_PARAMETER = "request";
    private static final String ID_PARAMETER = "id";

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        Names names = Names.of(context, entity);
        TreeSet<String> explicitImports = new TreeSet<>();
        boolean validatesInput = entity.operations().stream().anyMatch(
                JavaSpringServiceTransformer::validates);

        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationOperation operation : entity.operations()) {
            methods.add(operation(names, entity, operation, explicitImports));
        }
        methods.add(toResponse(names, entity));

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                names.servicePackage(),
                names.serviceName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Application service generated from the Harpia use cases of "
                        + entity.typeName() + "."),
                validatesInput
                        ? List.of(
                                JavaAnnotationModel.marker(
                                        "org.springframework.stereotype.Service"),
                                JavaAnnotationModel.marker(
                                        "org.springframework.validation.annotation.Validated"))
                        : List.of(JavaAnnotationModel.marker(
                                "org.springframework.stereotype.Service")),
                explicitImports.stream().map(JavaImportModel::new).toList(),
                List.of(),
                List.of(new JavaFieldModel(
                        REPOSITORY_FIELD,
                        names.repositoryType(),
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.FINAL),
                        List.of(),
                        Optional.empty(),
                        Optional.of(entity.where()))),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(new JavaParameterModel(REPOSITORY_FIELD, names.repositoryType())),
                        List.of("this." + REPOSITORY_FIELD + " = " + REPOSITORY_FIELD + ";"),
                        Optional.of(entity.where()))),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(names.servicePath(), names.serviceName()),
                type,
                Optional.of(entity.where()));
    }

    private JavaMethodModel operation(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> explicitImports) {
        List<JavaParameterModel> parameters = new ArrayList<>();
        if (operation.requiresId()) {
            parameters.add(new JavaParameterModel(
                    ID_PARAMETER, JavaTypeMapper.map(entity.idField().scalarType())));
        }
        operation.requestTypeName().ifPresent(request -> parameters.add(new JavaParameterModel(
                REQUEST_PARAMETER,
                JavaTypeRef.of(names.dtoPackage() + "." + request),
                validates(operation)
                        ? List.of(JavaAnnotationModel.marker("jakarta.validation.Valid"))
                        : List.of())));

        return new JavaMethodModel(
                operation.methodName(),
                returnType(names, operation),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(transactional(operation)),
                parameters,
                statements(names, entity, operation, explicitImports),
                List.of(),
                documentation(operation),
                Optional.of(operation.where()));
    }

    /**
     * What the specification called this operation. A stated intent should survive into the code a
     * person reads, rather than being erased once it has served the compiler.
     */
    private static Optional<String> documentation(ApplicationOperation operation) {
        return switch (operation.nature()) {
            case COMMAND -> Optional.of("Command " + operation.title() + ".");
            case QUERY -> Optional.of("Query " + operation.title() + ".");
            case INFERRED -> Optional.empty();
        };
    }

    private static JavaAnnotationModel transactional(ApplicationOperation operation) {
        String annotation = "org.springframework.transaction.annotation.Transactional";
        return operation.transactional()
                ? JavaAnnotationModel.marker(annotation)
                : JavaAnnotationModel.of(
                        annotation, new JavaAnnotationModel.Attribute("readOnly", "true"));
    }

    private static boolean validates(ApplicationOperation operation) {
        return operation.flow().stream().anyMatch(instruction ->
                instruction.command() == ApplicationOperation.FlowCommand.VALIDATE_INPUT);
    }

    private static JavaTypeRef returnType(Names names, ApplicationOperation operation) {
        return switch (operation.result().kind()) {
            case ENTITY -> names.responseType();
            case LIST -> JavaTypeRef.parameterized("java.util.List", names.responseType());
            case NOTHING -> JavaTypeRef.of("void");
        };
    }

    private List<String> statements(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> explicitImports) {
        List<String> statements = new ArrayList<>();
        String entityName = entity.typeName();
        for (ApplicationOperation.FlowInstruction instruction : operation.flow()) {
            switch (instruction.command()) {
                case VALIDATE_INPUT -> {
                    // Field constraints are enforced by Bean Validation at the method boundary.
                    // A rule is the part of validating the input that no annotation can express,
                    // so it is checked here, where the specification says validation happens.
                    for (ApplicationRule rule : operation.rules()) {
                        explicitImports.add(names.ruleViolationException());
                        JavaLogicWriter.Result condition = JavaLogicWriter.condition(
                                rule.condition(),
                                operation.methodName(),
                                name -> REQUEST_PARAMETER + "." + name + "()");
                        explicitImports.addAll(condition.imports());
                        statements.add("if (!(" + condition.body() + ")) {");
                        statements.add("    throw new RuleViolationException(\""
                                + rule.text().replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\");");
                        statements.add("}");
                    }
                }
                case CREATE_FROM -> {
                    String variable = instruction.variable().orElseThrow();
                    statements.add(entityName + " " + variable + " = new " + entityName + "();");
                    copyInput(statements, variable, operation);
                }
                case LOAD_BY_ID -> {
                    explicitImports.add(names.notFoundException());
                    String variable = instruction.variable().orElseThrow();
                    statements.add(entityName + " " + variable + " = " + REPOSITORY_FIELD
                            + ".findById(" + ID_PARAMETER + ")");
                    statements.add("        .orElseThrow(() -> new NotFoundException(\""
                            + entityName + "\", " + ID_PARAMETER + "));");
                }
                case UPDATE_FROM ->
                        copyInput(statements, instruction.variable().orElseThrow(), operation);
                case LIST_ALL -> {
                    explicitImports.add("org.springframework.data.domain.Sort");
                    String variable = instruction.variable().orElseThrow();
                    statements.add("List<" + entityName + "> " + variable + " = "
                            + REPOSITORY_FIELD + ".findAll(Sort.by(\""
                            + entity.idField().name() + "\"));");
                }
                case SAVE -> {
                    String variable = instruction.variable().orElseThrow();
                    statements.add(variable + " = " + REPOSITORY_FIELD
                            + ".save(" + variable + ");");
                }
                case DELETE -> statements.add(REPOSITORY_FIELD + ".delete("
                        + instruction.variable().orElseThrow() + ");");
                case RETURN -> instruction.variable().ifPresent(variable -> statements.add(
                        operation.result().kind() == ApplicationOperation.ResultKind.LIST
                                ? "return " + variable + ".stream().map("
                                        + names.serviceName() + "::toResponse).toList();"
                                : "return toResponse(" + variable + ");"));
            }
        }
        return statements;
    }

    private static void copyInput(
            List<String> statements, String variable, ApplicationOperation operation) {
        for (ApplicationField field : operation.input()) {
            statements.add(variable + "." + JavaLayout.accessor("set", field.name())
                    + "(" + REQUEST_PARAMETER + "." + field.name() + "());");
        }
    }

    private static JavaMethodModel toResponse(Names names, ApplicationEntity entity) {
        List<String> arguments = entity.fields().stream()
                .map(field -> "entity." + JavaLayout.accessor("get", field.name()) + "()")
                .toList();
        List<String> statements = new ArrayList<>();
        if (arguments.size() == 1) {
            statements.add("return new " + entity.responseTypeName()
                    + "(" + arguments.getFirst() + ");");
        } else {
            statements.add("return new " + entity.responseTypeName() + "(");
            for (int index = 0; index < arguments.size(); index++) {
                statements.add("        " + arguments.get(index)
                        + (index == arguments.size() - 1 ? ");" : ","));
            }
        }
        return new JavaMethodModel(
                "toResponse",
                names.responseType(),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC),
                List.of(),
                List.of(new JavaParameterModel("entity", names.entityType())),
                statements,
                Optional.of(entity.where()));
    }

    /** Every Java name this transformer needs, resolved once. */
    private record Names(
            String servicePackage,
            String servicePath,
            String serviceName,
            String dtoPackage,
            String errorPackage,
            JavaTypeRef entityType,
            JavaTypeRef responseType,
            JavaTypeRef repositoryType) {

        private static Names of(JavaSpringContext context, ApplicationEntity entity) {
            JavaLayout layout = context.layout();
            return new Names(
                    layout.packageName(JavaLayout.SERVICE),
                    layout.packagePath(JavaLayout.SERVICE),
                    JavaLayout.serviceTypeName(entity.typeName()),
                    layout.packageName(JavaLayout.DTO),
                    layout.packageName(JavaLayout.ERROR),
                    JavaTypeRef.of(
                            layout.packageName(JavaLayout.DOMAIN) + "." + entity.typeName()),
                    JavaTypeRef.of(
                            layout.packageName(JavaLayout.DTO) + "." + entity.responseTypeName()),
                    JavaTypeRef.of(layout.packageName(JavaLayout.REPOSITORY) + "."
                            + JavaLayout.repositoryTypeName(entity.typeName())));
        }

        private String notFoundException() {
            return errorPackage + ".NotFoundException";
        }

        private String ruleViolationException() {
            return errorPackage + ".RuleViolationException";
        }
    }
}
