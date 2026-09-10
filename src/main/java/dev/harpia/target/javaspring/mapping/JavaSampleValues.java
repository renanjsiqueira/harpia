package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationFieldType;
import dev.harpia.application.ApplicationScalarType;
import java.util.Objects;
import java.util.Optional;

/**
 * Values a generated test uses to stand in for real data.
 *
 * <p>They are derived from the field, never random: a test that changes between two runs cannot
 * tell a regression from noise. Text carries the field name so a failure message says which field
 * it was about, and an {@code Email} stays a valid address so the generated validation does not
 * reject the happy path by accident.
 */
public final class JavaSampleValues {

    private static final String UUID_VALUE = "00000000-0000-0000-0000-000000000001";
    private static final String DATE_VALUE = "2024-01-01";
    private static final String DATE_TIME_VALUE = "2024-01-01T00:00:00Z";

    private JavaSampleValues() {
    }

    private static String jsonOf(ApplicationScalarType type) {
        return switch (type) {
            case STRING, TEXT -> "\\\"value\\\"";
            case EMAIL -> "\\\"value@example.com\\\"";
            case INT, LONG -> "1";
            case DECIMAL -> "1.00";
            case BOOLEAN -> "true";
            case UUID -> "\\\"" + UUID_VALUE + "\\\"";
            case DATE -> "\\\"" + DATE_VALUE + "\\\"";
            case DATE_TIME -> "\\\"" + DATE_TIME_VALUE + "\\\"";
        };
    }

    /** A sample of a bare scalar, with no field to take a name from. */
    private static String sampleOf(ApplicationScalarType type) {
        return switch (type) {
            case STRING, TEXT -> "\"value\"";
            case EMAIL -> "\"value@example.com\"";
            case INT -> "1";
            case LONG -> "1L";
            case DECIMAL -> "new BigDecimal(\"1.00\")";
            case BOOLEAN -> "true";
            case UUID -> "UUID.fromString(\"" + UUID_VALUE + "\")";
            case DATE -> "LocalDate.parse(\"" + DATE_VALUE + "\")";
            case DATE_TIME -> "OffsetDateTime.parse(\"" + DATE_TIME_VALUE + "\")";
        };
    }

    /** The value as a Java expression. */
    public static String java(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        if (field.optionalType().isPresent()) {
            return "Optional.of(" + java(present(field)) + ")";
        }
        if (field.reference().isPresent()) {
            return sampleOf(field.reference().orElseThrow().idType());
        }
        if (field.relationship().isPresent()) {
            return "new " + field.relationship().orElseThrow().entity() + "()";
        }
        Optional<String> declared = field.enumTypeName();
        if (declared.isPresent()) {
            return declared.orElseThrow() + "." + enumSample(field);
        }
        if (field.elementType().isPresent()) {
            // A flow may add to this collection, and an immutable one would refuse.
            return "new ArrayList<>(List.of(" + element(field) + "))";
        }
        if (field.valueType().isPresent()) {
            ApplicationFieldType.ValueType value = field.valueType().orElseThrow();
            return "new " + value.name() + "("
                    + value.components().stream()
                            .map(JavaSampleValues::java)
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("")
                    + ")";
        }
        return switch (field.scalarType()) {
            case STRING, TEXT -> "\"" + text(field) + "\"";
            case EMAIL -> "\"" + text(field) + "\"";
            case INT -> "1";
            case LONG -> "1L";
            case DECIMAL -> "new BigDecimal(\"1.00\")";
            case BOOLEAN -> "true";
            case UUID -> "UUID.fromString(\"" + UUID_VALUE + "\")";
            case DATE -> "LocalDate.parse(\"" + DATE_VALUE + "\")";
            case DATE_TIME -> "OffsetDateTime.parse(\"" + DATE_TIME_VALUE + "\")";
        };
    }

    /** The same value as a JSON literal, for a request body. */
    public static String json(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        if (field.elementType().isPresent()) {
            return "[" + json(sample(field)) + "]";
        }
        if (field.valueType().isPresent()) {
            ApplicationFieldType.ValueType value = field.valueType().orElseThrow();
            return "{" + value.components().stream()
                    .map(component -> "\\\"" + component.name() + "\\\":" + json(component))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + "}";
        }
        if (field.reference().isPresent()) {
            return jsonOf(field.reference().orElseThrow().idType());
        }
        if (field.relationship().isPresent()) {
            return "{}";
        }
        if (field.enumTypeName().isPresent()) {
            return "\\\"" + enumSample(field) + "\\\"";
        }
        return switch (field.scalarType()) {
            case STRING, TEXT, EMAIL -> "\\\"" + text(field) + "\\\"";
            case INT, LONG -> "1";
            case DECIMAL -> "1.00";
            case BOOLEAN -> "true";
            case UUID -> "\\\"" + UUID_VALUE + "\\\"";
            case DATE -> "\\\"" + DATE_VALUE + "\\\"";
            case DATE_TIME -> "\\\"" + DATE_TIME_VALUE + "\\\"";
        };
    }

    /**
     * The same value as plain request text, for a query parameter or a header.
     *
     * <p>A query parameter is not JSON: quoting it as a JSON literal would send the quotes as part
     * of the value, and an {@code Email} constraint would then reject the happy path.
     */
    public static String plain(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        if (field.relationship().isPresent()) {
            return plainOf(field.relationship().orElseThrow().idType());
        }
        if (field.reference().isPresent()) {
            return plainOf(field.reference().orElseThrow().idType());
        }
        return switch (field.scalarType()) {
            case STRING, TEXT, EMAIL -> text(field);
            case INT, LONG -> "1";
            case DECIMAL -> "1.00";
            case BOOLEAN -> "true";
            case UUID -> UUID_VALUE;
            case DATE -> DATE_VALUE;
            case DATE_TIME -> DATE_TIME_VALUE;
        };
    }

    /**
     * The import a field's sample value needs, when it needs one.
     *
     * <p>A declared type lives in the domain package, which a scalar never does, so the field is
     * asked rather than its scalar kind.
     */
    public static java.util.List<String> requiredImports(
            ApplicationField field, String domainPackage) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(domainPackage, "domainPackage");
        java.util.List<String> imports = new java.util.ArrayList<>();
        field.declaredType().ifPresent(name -> imports.add(domainPackage + "." + name));
        field.optionalType().ifPresent(inner -> {
            imports.add("java.util.Optional");
            imports.addAll(requiredImports(present(field), domainPackage));
        });
        field.elementType().ifPresent(element -> {
            imports.add("java.util.ArrayList");
            imports.add("java.util.List");
            imports.addAll(requiredImports(sample(field), domainPackage));
        });
        // Building a value's sample means naming its components' types too.
        field.valueType().ifPresent(value -> value.components()
                .forEach(component -> imports.addAll(requiredImports(component, domainPackage))));
        field.reference().ifPresent(reference ->
                requiredImport(reference.idType()).ifPresent(imports::add));
        field.relationship().ifPresent(relationship ->
                imports.add(domainPackage + "." + relationship.entity()));
        if (field.declaredType().isEmpty()
                && field.elementType().isEmpty()
                && field.optionalType().isEmpty()
                && field.reference().isEmpty()
                && field.relationship().isEmpty()) {
            requiredImport(field.scalarType()).ifPresent(imports::add);
        }
        return java.util.List.copyOf(imports);
    }

    /** The import a Java sample value needs, when it needs one. */
    public static Optional<String> requiredImport(ApplicationScalarType type) {
        return switch (type) {
            case DECIMAL -> Optional.of("java.math.BigDecimal");
            case UUID -> Optional.of("java.util.UUID");
            case DATE -> Optional.of("java.time.LocalDate");
            case DATE_TIME -> Optional.of("java.time.OffsetDateTime");
            default -> Optional.empty();
        };
    }

    /**
     * The value as the field is stored, which is what a setter takes.
     *
     * <p>A setter takes the type JPA holds, not the type callers are handed back, so a sample that
     * feeds one is not the same expression as a sample that fills the other.
     */
    public static String stored(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        return java(present(field));
    }

    /** The same field as if the value were always there, for building the value itself. */
    private static ApplicationField present(ApplicationField field) {
        return new ApplicationField(
                field.name(),
                field.columnName(),
                field.present(),
                true,
                field.unique(),
                field.generated(),
                field.indexed(),
                field.defaultValue(),
                field.where());
    }

    /** One element of a collection, standing in as a field of the element's own type. */
    private static ApplicationField sample(ApplicationField field) {
        return new ApplicationField(
                field.name(),
                field.columnName(),
                field.elementType().orElseThrow(),
                true,
                false,
                false,
                false,
                java.util.Optional.empty(),
                field.where());
    }

    private static String element(ApplicationField field) {
        return java(sample(field));
    }

    /** The sample constant of a declared enum, which the field itself cannot know. */
    private static String enumSample(ApplicationField field) {
        return field.enumValues().stream()
                .findFirst()
                .map(JavaTypeMapper::enumConstant)
                .orElseThrow(() -> new IllegalStateException(
                        "enum field '" + field.name() + "' has no values"));
    }

    private static String text(ApplicationField field) {
        return field.scalarType() == ApplicationScalarType.EMAIL
                ? field.name() + "@example.com"
                : field.name();
    }

    private static String plainOf(ApplicationScalarType type) {
        return switch (type) {
            case STRING, TEXT, EMAIL -> "value";
            case INT, LONG -> "1";
            case DECIMAL -> "1.00";
            case BOOLEAN -> "true";
            case UUID -> UUID_VALUE;
            case DATE -> DATE_VALUE;
            case DATE_TIME -> DATE_TIME_VALUE;
        };
    }
}
