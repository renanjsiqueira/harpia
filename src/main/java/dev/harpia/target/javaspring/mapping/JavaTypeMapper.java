package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationFieldType;
import dev.harpia.application.ApplicationScalarType;
import dev.harpia.logic.LogicType;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import java.util.Objects;

/** The single mapping from target-independent scalar types to Java types. */
public final class JavaTypeMapper {

    private JavaTypeMapper() {
    }

    public static JavaTypeRef map(ApplicationScalarType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case STRING, TEXT, EMAIL -> JavaTypeRef.of("java.lang.String");
            case INT -> JavaTypeRef.of("java.lang.Integer");
            case LONG -> JavaTypeRef.of("java.lang.Long");
            case DECIMAL -> JavaTypeRef.of("java.math.BigDecimal");
            case BOOLEAN -> JavaTypeRef.of("java.lang.Boolean");
            case UUID -> JavaTypeRef.of("java.util.UUID");
            case DATE -> JavaTypeRef.of("java.time.LocalDate");
            case DATE_TIME -> JavaTypeRef.of("java.time.OffsetDateTime");
        };
    }

    /**
     * The Java type of a field, which for a declared type is the one the enum transformer emits.
     *
     * @param domainPackage where declared types are materialised
     */
    public static JavaTypeRef map(ApplicationFieldType type, String domainPackage) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(domainPackage, "domainPackage");
        if (type instanceof ApplicationFieldType.EnumType declared) {
            return JavaTypeRef.of(domainPackage + "." + declared.name());
        }
        if (type instanceof ApplicationFieldType.ValueType declared) {
            return JavaTypeRef.of(domainPackage + "." + declared.name());
        }
        return map(type.scalarKind().orElseThrow());
    }

    /** {@code awaiting_payment} names the constant {@code AWAITING_PAYMENT}. */
    public static String enumConstant(String value) {
        Objects.requireNonNull(value, "value");
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    public static JavaTypeRef map(LogicType type) {
        Objects.requireNonNull(type, "type");
        if (type instanceof LogicType.Scalar scalar) {
            return map(ApplicationScalarType.valueOf(scalar.kind().name()));
        }
        throw new IllegalArgumentException("no Java type for " + type.display());
    }
}
