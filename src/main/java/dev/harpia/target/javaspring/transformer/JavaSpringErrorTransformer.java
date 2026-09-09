package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.diag.SourceRef;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.SqlConstraintNames;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the failures the specification declares into HTTP responses.
 *
 * <p>Only conditions some use case actually declares are handled, so no project carries a handler
 * for an error it cannot produce.
 *
 * <p>A duplicate is recognised from the unique constraint rather than pre-checked. A pre-check
 * races with the insert, and with JPA the insert flushes at commit — after the service method has
 * already returned — so the constraint is the only place that can decide a conflict correctly.
 */
public final class JavaSpringErrorTransformer {

    private static final String NOT_FOUND = "NotFoundException";
    private static final String RULE_VIOLATION = "RuleViolationException";
    private static final String API_ERROR = "ApiError";
    private static final String HANDLER = "ApiExceptionHandler";
    private static final int DEFAULT_NOT_FOUND_STATUS = 404;

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        Failures failures = Failures.of(context);
        Optional<SourceRef> notFoundWhere = context.application().entities().stream()
                .flatMap(entity -> entity.operations().stream())
                .filter(ApplicationOperation::requiresId)
                .map(ApplicationOperation::where)
                .findFirst();
        if (notFoundWhere.isEmpty() && failures.notFound()) {
            notFoundWhere = Optional.of(failures.where());
        }
        if (notFoundWhere.isEmpty() && !failures.any()) {
            return List.of();
        }
        List<JavaSourceFile> files = new ArrayList<>();
        SourceRef where = failures.where();
        if (notFoundWhere.isPresent()) {
            files.add(notFoundException(context, notFoundWhere.orElseThrow()));
        }
        if (failures.rules()) {
            files.add(ruleViolationException(context, where));
        }
        for (Map.Entry<String, DomainError> domain : failures.domains().entrySet()) {
            files.add(domainException(context, domain.getKey(), domain.getValue()));
        }
        if (failures.any()) {
            files.add(apiError(context, where));
            files.add(handler(context, failures, where));
        }
        return List.copyOf(files);
    }

    private static JavaSourceFile notFoundException(JavaSpringContext context, SourceRef where) {
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                errorPackage(context),
                NOT_FOUND,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Raised when a Harpia `load ... by id` finds nothing."),
                List.of(),
                List.of(),
                List.of(JavaTypeRef.of("java.lang.RuntimeException")),
                List.of(new JavaFieldModel(
                        "serialVersionUID",
                        JavaTypeRef.of("long"),
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                        List.of(),
                        Optional.of("1L"),
                        Optional.of(where))),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(
                                new JavaParameterModel("type", JavaTypeRef.of("java.lang.String")),
                                new JavaParameterModel("id", JavaTypeRef.of("java.lang.Object"))),
                        List.of("super(type + \" \" + id + \" was not found\");"),
                        Optional.of(where))),
                List.of(),
                Optional.of(where));
        return file(context, NOT_FOUND, type, where);
    }

    /**
     * One class per domain error the specification names.
     *
     * <p>Nothing in a V0 flow raises it yet — {@code fail} is a separate item. What the type buys
     * today is the contract: the name exists, the handler maps it to the declared status, and code
     * written by hand beside the generated service can throw it and get that status.
     */
    private static JavaSourceFile domainException(
            JavaSpringContext context, String typeName, DomainError error) {
        SourceRef where = error.where();
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                errorPackage(context),
                typeName,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Raised for the declared domain error `" + error.condition()
                        + "`, which answers " + error.status() + "."),
                List.of(),
                List.of(),
                List.of(JavaTypeRef.of("java.lang.RuntimeException")),
                List.of(new JavaFieldModel(
                        "serialVersionUID",
                        JavaTypeRef.of("long"),
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                        List.of(),
                        Optional.of("1L"),
                        Optional.of(where))),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(new JavaParameterModel(
                                "message", JavaTypeRef.of("java.lang.String"))),
                        List.of("super(message);"),
                        Optional.of(where))),
                List.of(),
                Optional.of(where));
        return file(context, typeName, type, where);
    }

    /**
     * Raised when a declared rule does not hold.
     *
     * <p>A rule is input validation that no field annotation can express, so it fails the same way
     * an invalid field does and answers the status the specification declared for invalid input.
     */
    private static JavaSourceFile ruleViolationException(
            JavaSpringContext context, SourceRef where) {
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                errorPackage(context),
                RULE_VIOLATION,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Raised when a rule declared for an operation does not hold."),
                List.of(),
                List.of(),
                List.of(JavaTypeRef.of("java.lang.RuntimeException")),
                List.of(new JavaFieldModel(
                        "serialVersionUID",
                        JavaTypeRef.of("long"),
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                        List.of(),
                        Optional.of("1L"),
                        Optional.of(where))),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(new JavaParameterModel(
                                "rule", JavaTypeRef.of("java.lang.String"))),
                        List.of("super(\"rule does not hold: \" + rule);"),
                        Optional.of(where))),
                List.of(),
                Optional.of(where));
        return file(context, RULE_VIOLATION, type, where);
    }

    private static JavaMethodModel ruleHandler(Failures failures, SourceRef where) {
        int status = failures.invalidInputStatus();
        return handlerMethod(
                "ruleViolation",
                RULE_VIOLATION,
                List.of("return ResponseEntity.status(" + status + ")",
                        "        .body(new " + API_ERROR + "(" + status
                                + ", \"invalid input\", exception.getMessage()));"),
                where);
    }

    private static JavaSourceFile apiError(JavaSpringContext context, SourceRef where) {
        List<JavaFieldModel> components = List.of(
                component("status", "int", where),
                component("error", "java.lang.String", where),
                component("message", "java.lang.String", where));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.RECORD,
                errorPackage(context),
                API_ERROR,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Error body shared by every failure the specification declares."),
                List.of(),
                List.of(),
                List.of(),
                components,
                List.of(),
                List.of(),
                Optional.of(where));
        return file(context, API_ERROR, type, where);
    }

    private static JavaSourceFile handler(
            JavaSpringContext context, Failures failures, SourceRef where) {
        List<JavaMethodModel> methods = new ArrayList<>();
        List<JavaImportModel> imports = new ArrayList<>();
        if (failures.notFound()) {
            methods.add(notFoundHandler(failures, where));
        }
        if (failures.invalidInput()) {
            imports.add(new JavaImportModel("java.util.stream.Collectors"));
            methods.add(invalidInputHandler(failures, where));
            methods.add(constraintViolationHandler(failures, where));
        }
        if (failures.rules()) {
            methods.add(ruleHandler(failures, where));
        }
        if (!failures.duplicates().isEmpty()) {
            methods.add(duplicateHandler(failures, where));
        }
        failures.domains().forEach((typeName, error) ->
                methods.add(domainHandler(typeName, error)));
        if (failures.invalidInput()) {
            methods.add(fieldName(where));
        }

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                errorPackage(context),
                HANDLER,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Maps the failures declared in the specification to their declared "
                        + "HTTP status."),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.web.bind.annotation.RestControllerAdvice")),
                imports,
                List.of(),
                List.of(),
                List.of(),
                methods,
                Optional.of(where));
        return file(context, HANDLER, type, where);
    }

    private static JavaMethodModel notFoundHandler(Failures failures, SourceRef where) {
        int status = failures.notFoundStatus();
        return handlerMethod(
                "notFound",
                NOT_FOUND,
                List.of("return ResponseEntity.status(" + status + ")",
                        "        .body(new " + API_ERROR + "(" + status
                                + ", \"not found\", exception.getMessage()));"),
                where);
    }

    private static JavaMethodModel invalidInputHandler(Failures failures, SourceRef where) {
        int status = failures.invalidInputStatus();
        return handlerMethod(
                "invalidInput",
                "org.springframework.web.bind.MethodArgumentNotValidException",
                List.of(
                        "String message = exception.getBindingResult().getFieldErrors().stream()",
                        "        .map(error -> error.getField() + \" \""
                                + " + error.getDefaultMessage())",
                        "        .sorted()",
                        "        .collect(Collectors.joining(\", \"));",
                        "return ResponseEntity.status(" + status + ")",
                        "        .body(new " + API_ERROR + "(" + status
                                + ", \"invalid input\", message));"),
                where);
    }

    /**
     * The same declared status when the invalid value did not arrive in the body.
     *
     * <p>A body is validated by the controller and fails as a binding error. A value bound from a
     * path, query or header reaches the service as a normal argument, where method validation
     * rejects it — a different exception, and without this handler a declared 400 would surface as
     * a 500. The specification declared one status for invalid input, not one per transport.
     */
    private static JavaMethodModel constraintViolationHandler(Failures failures, SourceRef where) {
        int status = failures.invalidInputStatus();
        return handlerMethod(
                "invalidParameter",
                "jakarta.validation.ConstraintViolationException",
                List.of(
                        "String message = exception.getConstraintViolations().stream()",
                        "        .map(violation -> field(violation) + \" \""
                                + " + violation.getMessage())",
                        "        .sorted()",
                        "        .collect(Collectors.joining(\", \"));",
                        "return ResponseEntity.status(" + status + ")",
                        "        .body(new " + API_ERROR + "(" + status
                                + ", \"invalid input\", message));"),
                where);
    }

    /** The violated property, without the method path the validator prefixes it with. */
    private static JavaMethodModel fieldName(SourceRef where) {
        return new JavaMethodModel(
                "field",
                JavaTypeRef.of("java.lang.String"),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC),
                List.of(),
                List.of(new JavaParameterModel(
                        "violation",
                        JavaTypeRef.parameterized(
                                "jakarta.validation.ConstraintViolation",
                                JavaTypeRef.of("?")))),
                List.of(
                        "String path = violation.getPropertyPath().toString();",
                        "int last = path.lastIndexOf('.');",
                        "return last < 0 ? path : path.substring(last + 1);"),
                Optional.of(where));
    }

    private static JavaMethodModel duplicateHandler(Failures failures, SourceRef where) {
        int status = failures.duplicateStatus();
        List<String> statements = new ArrayList<>();
        statements.add("String cause = exception.getMostSpecificCause().getMessage();");
        statements.add("String field = \"resource\";");
        statements.add("if (cause != null) {");
        failures.duplicates().forEach((constraint, field) -> {
            statements.add("    if (cause.contains(\"" + constraint + "\")) {");
            statements.add("        field = \"" + field + "\";");
            statements.add("    }");
        });
        statements.add("}");
        statements.add("return ResponseEntity.status(" + status + ")");
        statements.add("        .body(new " + API_ERROR + "(" + status
                + ", \"duplicate\", field + \" already exists\"));");
        return handlerMethod(
                "duplicate",
                "org.springframework.dao.DataIntegrityViolationException",
                statements,
                where);
    }

    private static JavaMethodModel domainHandler(String typeName, DomainError error) {
        int status = error.status();
        return handlerMethod(
                lowerFirst(typeName),
                typeName,
                List.of("return ResponseEntity.status(" + status + ")",
                        "        .body(new " + API_ERROR + "(" + status + ", \""
                                + error.condition() + "\", exception.getMessage()));"),
                error.where());
    }

    private static String lowerFirst(String value) {
        return value.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + value.substring(1);
    }

    private static JavaMethodModel handlerMethod(
            String name, String exceptionType, List<String> statements, SourceRef where) {
        JavaTypeRef exception = JavaTypeRef.of(exceptionType);
        return new JavaMethodModel(
                name,
                JavaTypeRef.parameterized(
                        "org.springframework.http.ResponseEntity", JavaTypeRef.of(API_ERROR)),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(JavaAnnotationModel.of(
                        "org.springframework.web.bind.annotation.ExceptionHandler",
                        new JavaAnnotationModel.Attribute(
                                "value", exception.simpleName() + ".class"))),
                List.of(new JavaParameterModel("exception", exception)),
                statements,
                Optional.of(where));
    }

    private static JavaFieldModel component(String name, String type, SourceRef where) {
        return new JavaFieldModel(
                name,
                JavaTypeRef.of(type),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(),
                Optional.empty(),
                Optional.of(where));
    }

    private static JavaSourceFile file(
            JavaSpringContext context, String className, JavaTypeModel type, SourceRef where) {
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.ERROR), className),
                type,
                Optional.of(where));
    }

    private static String errorPackage(JavaSpringContext context) {
        return context.layout().packageName(JavaLayout.ERROR);
    }

    /** Which failures this project can actually produce, and with which declared status. */
    /** A domain error as the target needs it: the declared phrase, its status and its origin. */
    private record DomainError(String condition, int status, SourceRef where) {
    }

    private record Failures(
            boolean notFound,
            int notFoundStatus,
            boolean invalidInput,
            int invalidInputStatus,
            Map<String, String> duplicates,
            int duplicateStatus,
            Map<String, DomainError> domains,
            boolean rules,
            SourceRef where) {

        private static Failures of(JavaSpringContext context) {
            boolean notFound = false;
            int notFoundStatus = DEFAULT_NOT_FOUND_STATUS;
            boolean invalidInput = false;
            int invalidInputStatus = 400;
            int duplicateStatus = 409;
            Map<String, String> duplicates = new LinkedHashMap<>();
            Map<String, DomainError> domains = new java.util.TreeMap<>();
            boolean rules = false;
            SourceRef where = SourceRef.file("harpia.yaml");

            for (ApplicationEntity entity : context.application().entities()) {
                for (ApplicationOperation operation : entity.operations()) {
                    if (operation.endpoint().isEmpty()) {
                        continue;
                    }
                    // A load that can miss must be mapped, whether or not the spec spelled it out.
                    if (operation.requiresId()) {
                        notFound = true;
                        where = entity.where();
                    }
                    if (!operation.rules().isEmpty()) {
                        rules = true;
                        where = entity.where();
                    }
                    for (ApplicationOperation.Failure failure : operation.failures()) {
                        where = entity.where();
                        switch (failure.condition()) {
                            case NOT_FOUND -> {
                                notFound = true;
                                notFoundStatus = failure.status();
                            }
                            case INVALID_INPUT -> {
                                invalidInput = true;
                                invalidInputStatus = failure.status();
                            }
                            case DOMAIN -> {
                                String condition = failure.name().orElseThrow();
                                domains.putIfAbsent(
                                        dev.harpia.model.Naming.errorSymbol(condition)
                                                + "Exception",
                                        new DomainError(
                                                condition, failure.status(), failure.where()));
                            }
                            case DUPLICATE -> {
                                duplicateStatus = failure.status();
                                failure.field()
                                        .flatMap(name -> column(entity, name))
                                        .ifPresent(column -> duplicates.putIfAbsent(
                                                SqlConstraintNames.unique(
                                                        entity.tableName(), column),
                                                failure.field().orElseThrow()));
                            }
                        }
                    }
                }
            }
            return new Failures(
                    notFound,
                    notFoundStatus,
                    invalidInput,
                    invalidInputStatus,
                    Map.copyOf(duplicates).isEmpty() ? Map.of() : new LinkedHashMap<>(duplicates),
                    duplicateStatus,
                    Map.copyOf(domains).isEmpty() ? Map.of() : new java.util.TreeMap<>(domains),
                    rules,
                    where);
        }

        private static Optional<String> column(ApplicationEntity entity, String fieldName) {
            return entity.fields().stream()
                    .filter(field -> field.name().equals(fieldName))
                    .map(ApplicationField::columnName)
                    .findFirst();
        }

        private boolean any() {
            return notFound || invalidInput || !duplicates.isEmpty() || !domains.isEmpty() || rules;
        }
    }
}
