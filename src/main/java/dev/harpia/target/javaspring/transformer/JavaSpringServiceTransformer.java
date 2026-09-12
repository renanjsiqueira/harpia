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
import java.util.LinkedHashSet;
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

        List<String> usedIntegrations = usedIntegrations(entity);
        List<String> usedCustomContracts = usedCustomContracts(entity);
        List<String> usedOperationEntities = usedOperationEntities(entity);
        List<JavaFieldModel> fields = new ArrayList<>();
        fields.add(new JavaFieldModel(
                REPOSITORY_FIELD,
                names.repositoryType(),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.FINAL),
                List.of(),
                Optional.empty(),
                Optional.of(entity.where())));
        List<JavaParameterModel> constructorParameters = new ArrayList<>();
        constructorParameters.add(new JavaParameterModel(REPOSITORY_FIELD, names.repositoryType()));
        List<String> assignments = new ArrayList<>();
        assignments.add("this." + REPOSITORY_FIELD + " = " + REPOSITORY_FIELD + ";");
        for (String contract : usedCustomContracts) {
            String fieldName = lowerFirst(contract);
            JavaTypeRef contractType = JavaTypeRef.of(names.logicPackage() + "." + contract);
            fields.add(dependencyField(fieldName, contractType, entity));
            constructorParameters.add(new JavaParameterModel(fieldName, contractType));
            assignments.add("this." + fieldName + " = " + fieldName + ";");
        }
        for (String owner : usedOperationEntities) {
            String fieldName = lowerFirst(owner) + "Service";
            JavaTypeRef serviceType = JavaTypeRef.of(names.servicePackage() + "."
                    + JavaLayout.serviceTypeName(owner));
            fields.add(dependencyField(fieldName, serviceType, entity));
            constructorParameters.add(new JavaParameterModel(fieldName, serviceType));
            assignments.add("this." + fieldName + " = " + fieldName + ";");
        }
        for (String integration : usedIntegrations) {
            String fieldName = JavaSpringIntegrationClientTransformer.clientFieldName(integration);
            JavaTypeRef clientType = JavaTypeRef.of(names.integrationPackage() + "."
                    + JavaSpringIntegrationClientTransformer.clientTypeName(integration));
            fields.add(new JavaFieldModel(
                    fieldName,
                    clientType,
                    JavaVisibility.PRIVATE,
                    Set.of(JavaModifier.FINAL),
                    List.of(),
                    Optional.empty(),
                    Optional.of(entity.where())));
            constructorParameters.add(new JavaParameterModel(fieldName, clientType));
            assignments.add("this." + fieldName + " = " + fieldName + ";");
        }

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
                fields,
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        constructorParameters,
                        assignments,
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
        return operation.allInstructions().stream().anyMatch(instruction ->
                instruction.command() == ApplicationOperation.FlowCommand.VALIDATE_INPUT);
    }

    private static JavaTypeRef returnType(Names names, ApplicationOperation operation) {
        return switch (operation.result().kind()) {
            case ENTITY -> names.responseType();
            case LIST -> JavaTypeRef.parameterized("java.util.List", names.responseType());
            case PAGE -> JavaTypeRef.parameterized(
                    names.dtoPackage() + ".PageResponse", names.responseType());
            case NOTHING -> JavaTypeRef.of("void");
        };
    }

    private List<String> statements(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> explicitImports) {
        List<String> statements = new ArrayList<>();
        emit(names, entity, operation, operation.flow(), statements, explicitImports,
                new LinkedHashSet<>());
        return statements;
    }

    /** Renders one level of a flow; a conditional renders its branches by calling back in. */
    private void emit(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            List<ApplicationOperation.FlowInstruction> flow,
            List<String> statements,
            TreeSet<String> explicitImports,
            Set<String> localValues) {
        String entityName = entity.typeName();
        for (ApplicationOperation.FlowInstruction instruction : flow) {
            switch (instruction.command()) {
                case IF -> {
                    ApplicationOperation.FlowInstruction.TypedValue condition =
                            instruction.value().orElseThrow();
                    JavaLogicWriter.Result written = JavaLogicWriter.condition(
                            condition.expression(),
                            operation.methodName(),
                            field -> javaValue(field, localValues));
                    explicitImports.addAll(written.imports());
                    statements.add("if (" + written.body() + ") {");
                    List<String> branch = new ArrayList<>();
                    emit(names, entity, operation, instruction.whenTrue(), branch,
                            explicitImports, new LinkedHashSet<>(localValues));
                    branch.forEach(line -> statements.add("    " + line));
                    if (instruction.whenFalse().isEmpty()) {
                        statements.add("}");
                    } else {
                        statements.add("} else {");
                        List<String> otherwise = new ArrayList<>();
                        emit(names, entity, operation, instruction.whenFalse(), otherwise,
                                explicitImports, new LinkedHashSet<>(localValues));
                        otherwise.forEach(line -> statements.add("    " + line));
                        statements.add("}");
                    }
                }
                case VALIDATE_INPUT -> {
                    // Field constraints are enforced by Bean Validation at the method boundary.
                    // A rule is the part of validating the input that no annotation can express,
                    // so it is checked here, where the specification says validation happens.
                    for (ApplicationRule rule : operation.rules()) {
                        explicitImports.add(names.ruleViolationException());
                        JavaLogicWriter.Result condition = JavaLogicWriter.condition(
                                rule.condition(),
                                operation.methodName(),
                                name -> javaValue(name, localValues));
                        explicitImports.addAll(condition.imports());
                        statements.add("if (!(" + condition.body() + ")) {");
                        statements.add("    throw new RuleViolationException(\""
                                + rule.text().replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\");");
                        statements.add("}");
                    }
                }
                case FAIL, REQUIRE -> {
                    ApplicationOperation.FlowInstruction.TypedValue raised =
                            instruction.value().orElseThrow();
                    String exception = dev.harpia.model.Naming.errorSymbol(raised.name())
                            + "Exception";
                    explicitImports.add(names.errorPackage() + "." + exception);
                    JavaLogicWriter.Result condition = JavaLogicWriter.condition(
                            raised.expression(),
                            operation.methodName(),
                            field -> javaValue(field, localValues));
                    explicitImports.addAll(condition.imports());
                    String failureCondition = instruction.command()
                                    == ApplicationOperation.FlowCommand.REQUIRE
                            ? "!(" + condition.body() + ")"
                            : condition.body();
                    statements.add("if (" + failureCondition + ") {");
                    statements.add("    throw new " + exception + "(\""
                            + raised.text().replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\");");
                    statements.add("}");
                }
                case CALL_LOGIC -> {
                    ApplicationOperation.FlowInstruction.Invocation invocation =
                            instruction.invocation().orElseThrow();
                    List<String> arguments = new ArrayList<>();
                    for (ApplicationOperation.FlowInstruction.Invocation.Argument argument
                            : invocation.arguments()) {
                        JavaLogicWriter.Result written = JavaLogicWriter.expression(
                                argument.value(),
                                argument.parameterType(),
                                JavaSpringLogicTransformer.METHOD_NAME,
                                field -> javaValue(field, localValues));
                        explicitImports.addAll(written.imports());
                        arguments.add(written.body());
                    }
                    dev.harpia.target.javaspring.model.JavaTypeRef resultType =
                            JavaTypeMapper.map(invocation.resultType());
                    if (resultType.canonicalName().contains(".")
                            && !resultType.canonicalName().startsWith("java.lang.")) {
                        explicitImports.add(resultType.canonicalName());
                    }
                    String receiver;
                    if (invocation.customContract().isPresent()) {
                        receiver = lowerFirst(invocation.customContract().orElseThrow());
                    } else {
                        explicitImports.add(names.logicPackage() + "." + invocation.target());
                        receiver = invocation.target();
                    }
                    statements.add(resultType.simpleName() + " "
                            + instruction.variable().orElseThrow() + " = "
                            + receiver + "."
                            + JavaSpringLogicTransformer.METHOD_NAME + "("
                            + String.join(", ", arguments) + ");");
                    localValues.add(instruction.variable().orElseThrow());
                }
                case CALL_INTEGRATION -> {
                    ApplicationOperation.FlowInstruction.IntegrationInvocation invocation =
                            instruction.integrationInvocation().orElseThrow();
                    List<String> arguments = new ArrayList<>();
                    for (ApplicationOperation.FlowInstruction.IntegrationInvocation.Argument argument
                            : invocation.arguments()) {
                        JavaLogicWriter.Result written = JavaLogicWriter.expression(
                                argument.value(),
                                argument.parameterType(),
                                JavaSpringLogicTransformer.METHOD_NAME,
                                field -> javaValue(field, localValues));
                        explicitImports.addAll(written.imports());
                        arguments.add(written.body());
                    }
                    String call = JavaSpringIntegrationClientTransformer.clientFieldName(
                                    invocation.integration())
                            + "."
                            + JavaSpringIntegrationClientTransformer.operationMethodName(
                                    invocation.operation())
                            + "(" + String.join(", ", arguments) + ");";
                    if (invocation.resultType().isEmpty()) {
                        statements.add(call);
                    } else {
                        JavaTypeRef resultType = JavaTypeMapper.map(
                                invocation.resultType().orElseThrow(), names.domainPackage());
                        resultType.flattened()
                                .map(JavaTypeRef::canonicalName)
                                .filter(name -> name.contains(".")
                                        && !name.startsWith("java.lang."))
                                .forEach(explicitImports::add);
                        statements.add(resultType.sourceName() + " "
                                + instruction.variable().orElseThrow() + " = " + call);
                        localValues.add(instruction.variable().orElseThrow());
                    }
                }
                case CALL_OPERATION -> {
                    ApplicationOperation.FlowInstruction.OperationInvocation invocation =
                            instruction.operationInvocation().orElseThrow();
                    List<String> identifierArguments = new ArrayList<>();
                    List<String> requestArguments = new ArrayList<>();
                    for (ApplicationOperation.FlowInstruction.OperationInvocation.Argument argument
                            : invocation.arguments()) {
                        JavaLogicWriter.Result written = JavaLogicWriter.expression(
                                argument.value(),
                                argument.parameterType(),
                                JavaSpringLogicTransformer.METHOD_NAME,
                                field -> javaValue(field, localValues));
                        explicitImports.addAll(written.imports());
                        (argument.identifier() ? identifierArguments : requestArguments)
                                .add(written.body());
                    }
                    List<String> arguments = new ArrayList<>(identifierArguments);
                    if (!requestArguments.isEmpty()) {
                        String requestType = invocation.operation() + "Request";
                        explicitImports.add(names.dtoPackage() + "." + requestType);
                        arguments.add("new " + requestType + "("
                                + String.join(", ", requestArguments) + ")");
                    }
                    String receiver = invocation.entity().equals(entity.typeName())
                            ? "this"
                            : lowerFirst(invocation.entity()) + "Service";
                    String call = receiver + "." + lowerFirst(invocation.operation())
                            + "(" + String.join(", ", arguments) + ");";
                    if (invocation.resultKind() == ApplicationOperation.ResultKind.NOTHING) {
                        statements.add(call);
                    } else {
                        JavaTypeRef response = JavaTypeRef.of(names.dtoPackage() + "."
                                + invocation.entity() + "Response");
                        JavaTypeRef resultType = switch (invocation.resultKind()) {
                            case ENTITY -> response;
                            case LIST -> JavaTypeRef.parameterized("java.util.List", response);
                            case PAGE -> JavaTypeRef.parameterized(
                                    names.dtoPackage() + ".PageResponse", response);
                            case NOTHING -> throw new IllegalStateException();
                        };
                        resultType.flattened()
                                .map(JavaTypeRef::canonicalName)
                                .filter(name -> name.contains(".")
                                        && !name.startsWith("java.lang."))
                                .forEach(explicitImports::add);
                        statements.add(resultType.sourceName() + " "
                                + instruction.variable().orElseThrow() + " = " + call);
                    }
                }
                case CREATE_FROM -> {
                    String variable = instruction.variable().orElseThrow();
                    statements.add(entityName + " " + variable + " = new " + entityName + "();");
                    copyInput(statements, variable, operation, false);
                }
                case LOAD_BY_ID -> {
                    explicitImports.add(names.notFoundException());
                    String variable = instruction.variable().orElseThrow();
                    statements.add(entityName + " " + variable + " = " + REPOSITORY_FIELD
                            + ".findById(" + ID_PARAMETER + ")");
                    statements.add("        .orElseThrow(() -> new NotFoundException(\""
                            + entityName + "\", " + ID_PARAMETER + "));");
                }
                case FIND_BY -> {
                    explicitImports.add(names.notFoundException());
                    String variable = instruction.variable().orElseThrow();
                    String field = instruction.fields().getFirst();
                    String argument = REQUEST_PARAMETER + "." + field + "()";
                    statements.add(entityName + " " + variable + " = " + REPOSITORY_FIELD
                            + "." + finderName(instruction) + "(" + argument + ")");
                    statements.add("        .orElseThrow(() -> new NotFoundException(\""
                            + entityName + "\", " + argument + "));");
                }
                case LIST_BY -> {
                    explicitImports.add("org.springframework.data.domain.Sort");
                    if (instruction.paged()) {
                        explicitImports.add("org.springframework.data.domain.PageRequest");
                    }
                    if (reportsPage(operation)) {
                        explicitImports.add("org.springframework.data.domain.Page");
                        explicitImports.add(names.dtoPackage() + ".PageResponse");
                    }
                    String variable = instruction.variable().orElseThrow();
                    String arguments = instruction.fields().stream()
                            .map(field -> REQUEST_PARAMETER + "." + field + "()")
                            .collect(java.util.stream.Collectors.joining(", "));
                    statements.add(listingType(operation, entityName) + " " + variable + " = "
                            + REPOSITORY_FIELD + "." + finderName(instruction) + "("
                            + arguments + ", " + pageable(entity, instruction) + ");");
                }
                case SET_FIELD -> {
                    ApplicationOperation.FlowInstruction.TypedValue assigned =
                            instruction.value().orElseThrow();
                    String variable = instruction.variable().orElseThrow();
                    JavaLogicWriter.Result written = JavaLogicWriter.condition(
                            assigned.expression(),
                            operation.methodName(),
                            field -> javaValue(field, localValues));
                    explicitImports.addAll(written.imports());
                    statements.add(variable + "."
                            + JavaLayout.accessor("set", assigned.name())
                            + "(" + written.body() + ");");
                }
                case ADD_TO, REMOVE_FROM -> {
                    ApplicationOperation.FlowInstruction.TypedValue changed =
                            instruction.value().orElseThrow();
                    String variable = instruction.variable().orElseThrow();
                    JavaLogicWriter.Result element = JavaLogicWriter.condition(
                            changed.expression(),
                            operation.methodName(),
                            field -> javaValue(field, localValues));
                    explicitImports.addAll(element.imports());
                    statements.add(variable + "."
                            + JavaLayout.accessor("get", changed.name()) + "()."
                            + (instruction.command() == ApplicationOperation.FlowCommand.ADD_TO
                                    ? "add"
                                    : "remove")
                            + "(" + element.body() + ");");
                }
                case UPDATE_FROM ->
                        copyInput(statements, instruction.variable().orElseThrow(), operation,
                                operation.partialUpdate());
                case LIST_ALL -> {
                    explicitImports.add("org.springframework.data.domain.Sort");
                    if (instruction.paged()) {
                        explicitImports.add("org.springframework.data.domain.PageRequest");
                    }
                    if (reportsPage(operation)) {
                        explicitImports.add("org.springframework.data.domain.Page");
                        explicitImports.add(names.dtoPackage() + ".PageResponse");
                    }
                    String variable = instruction.variable().orElseThrow();
                    statements.add(listingType(operation, entityName) + " " + variable + " = "
                            + REPOSITORY_FIELD + ".findAll("
                            + pageable(entity, instruction) + ")"
                            + (instruction.paged() && !reportsPage(operation)
                                    ? ".getContent();"
                                    : ";"));
                }
                case SAVE -> {
                    String variable = instruction.variable().orElseThrow();
                    // An invariant is about what the entity is allowed to be, so it is checked
                    // where the entity is about to become durable — whichever operation got here.
                    for (ApplicationRule invariant : entity.invariants()) {
                        explicitImports.add(names.invariantViolationException());
                        JavaLogicWriter.Result condition = JavaLogicWriter.condition(
                                invariant.condition(),
                                operation.methodName(),
                                field -> variable + "." + JavaLayout.accessor("get", field) + "()");
                        explicitImports.addAll(condition.imports());
                        statements.add("if (!(" + condition.body() + ")) {");
                        statements.add("    throw new InvariantViolationException(\""
                                + entityName + "\", \""
                                + invariant.text().replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\");");
                        statements.add("}");
                    }
                    statements.add(variable + " = " + REPOSITORY_FIELD
                            + ".save(" + variable + ");");
                }
                case DELETE -> statements.add(REPOSITORY_FIELD + ".delete("
                        + instruction.variable().orElseThrow() + ");");
                case RETURN -> instruction.variable().ifPresent(variable -> statements.addAll(
                        returned(names, operation, variable)));
            }
        }
    }

    private static boolean reportsPage(ApplicationOperation operation) {
        return operation.result().kind() == ApplicationOperation.ResultKind.PAGE;
    }

    private static List<String> usedIntegrations(ApplicationEntity entity) {
        return entity.operations().stream()
                .flatMap(operation -> operation.allInstructions().stream())
                .filter(instruction -> instruction.command()
                        == ApplicationOperation.FlowCommand.CALL_INTEGRATION)
                .map(instruction -> instruction.integrationInvocation()
                        .orElseThrow().integration())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> usedCustomContracts(ApplicationEntity entity) {
        return entity.operations().stream()
                .flatMap(operation -> operation.allInstructions().stream())
                .filter(instruction -> instruction.command()
                        == ApplicationOperation.FlowCommand.CALL_LOGIC)
                .flatMap(instruction -> instruction.invocation().orElseThrow()
                        .customContract().stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> usedOperationEntities(ApplicationEntity entity) {
        return entity.operations().stream()
                .flatMap(operation -> operation.allInstructions().stream())
                .filter(instruction -> instruction.command()
                        == ApplicationOperation.FlowCommand.CALL_OPERATION)
                .map(instruction -> instruction.operationInvocation().orElseThrow().entity())
                .filter(owner -> !owner.equals(entity.typeName()))
                .distinct()
                .sorted()
                .toList();
    }

    private static JavaFieldModel dependencyField(
            String name, JavaTypeRef type, ApplicationEntity entity) {
        return new JavaFieldModel(
                name,
                type,
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.FINAL),
                List.of(),
                Optional.empty(),
                Optional.of(entity.where()));
    }

    private static String javaValue(String name, Set<String> localValues) {
        return localValues.contains(name) ? name : REQUEST_PARAMETER + "." + name + "()";
    }

    private static String lowerFirst(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String listingType(ApplicationOperation operation, String entityName) {
        return reportsPage(operation) ? "Page<" + entityName + ">" : "List<" + entityName + ">";
    }

    /**
     * What the method hands back.
     *
     * <p>A page reports which page it is, so the service keeps the page the query returned rather
     * than throwing the metadata away and returning only the rows.
     */
    private static List<String> returned(
            Names names, ApplicationOperation operation, String variable) {
        return switch (operation.result().kind()) {
            case LIST -> List.of("return " + variable + ".stream().map("
                    + names.serviceName() + "::toResponse).toList();");
            case PAGE -> List.of(
                    "return new PageResponse<>(",
                    "        " + variable + ".getContent().stream()",
                    "                .map(" + names.serviceName() + "::toResponse).toList(),",
                    "        " + variable + ".getNumber(),",
                    "        " + variable + ".getSize(),",
                    "        " + variable + ".getTotalElements(),",
                    "        " + variable + ".getTotalPages());");
            case ENTITY, NOTHING -> List.of("return toResponse(" + variable + ");");
        };
    }

    /**
     * The order a listing returns rows in.
     *
     * <p>A declared sort by a field many rows share leaves ties in whatever order the database
     * felt like, and Harpia promises the same bytes for the same input. The id goes last as the
     * tiebreaker, so a declared order refines the stable one instead of replacing it.
     */
    /**
     * What the listing passes to the repository: an order, or a page of that order.
     *
     * <p>A page without an order is a page of nothing in particular, so paging is layered on top of
     * the same stable order rather than replacing it.
     */
    private static String pageable(
            ApplicationEntity entity, ApplicationOperation.FlowInstruction instruction) {
        String order = sort(entity, instruction);
        return instruction.paged()
                ? "PageRequest.of(" + REQUEST_PARAMETER + ".page(), " + REQUEST_PARAMETER
                        + ".size(), " + order + ")"
                : order;
    }

    private static String sort(
            ApplicationEntity entity, ApplicationOperation.FlowInstruction instruction) {
        if (instruction.sort().isEmpty()) {
            return "Sort.by(\"" + entity.idField().name() + "\")";
        }
        StringBuilder orders = new StringBuilder("Sort.by(");
        for (ApplicationOperation.FlowInstruction.SortOrder order : instruction.sort()) {
            orders.append("Sort.Order.")
                    .append(order.descending() ? "desc" : "asc")
                    .append("(\"").append(order.field()).append("\"), ");
        }
        return orders.append("Sort.Order.asc(\"")
                .append(entity.idField().name())
                .append("\"))")
                .toString();
    }

    /** The derived finder name Spring Data reads: the name is the query. */
    private static String finderName(ApplicationOperation.FlowInstruction instruction) {
        StringBuilder name = new StringBuilder("findBy");
        for (int index = 0; index < instruction.fields().size(); index++) {
            if (index > 0) {
                name.append("And");
            }
            name.append(JavaLayout.accessor("", instruction.fields().get(index)));
        }
        return name.toString();
    }

    /**
     * Copies the request onto the entity.
     *
     * <p>A partial update copies only what arrived: a field the request omitted is not a change,
     * so the stored value has to survive the copy. A full update copies everything, because there
     * the omission is itself the statement that the field holds nothing.
     */
    private static void copyInput(
            List<String> statements,
            String variable,
            ApplicationOperation operation,
            boolean partial) {
        for (ApplicationField field : operation.input()) {
            String assignment = variable + "." + JavaLayout.accessor("set", field.name())
                    + "(" + REQUEST_PARAMETER + "." + field.name() + "());";
            if (partial) {
                statements.add("if (" + REQUEST_PARAMETER + "." + field.name() + "() != null) {");
                statements.add("    " + assignment);
                statements.add("}");
            } else {
                statements.add(assignment);
            }
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
            String logicPackage,
            String integrationPackage,
            String domainPackage,
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
                    layout.packageName(JavaLayout.LOGIC),
                    layout.packageName(JavaLayout.INTEGRATION),
                    layout.packageName(JavaLayout.DOMAIN),
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

        private String invariantViolationException() {
            return errorPackage + ".InvariantViolationException";
        }
    }
}
