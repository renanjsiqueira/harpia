package dev.harpia.target.javaspring.mapping;

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

    public static JavaTypeRef map(LogicType type) {
        Objects.requireNonNull(type, "type");
        if (type instanceof LogicType.Scalar scalar) {
            return map(ApplicationScalarType.valueOf(scalar.kind().name()));
        }
        throw new IllegalArgumentException("no Java type for " + type.display());
    }
}
