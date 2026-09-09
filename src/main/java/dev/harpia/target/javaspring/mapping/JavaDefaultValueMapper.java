package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationField;
import java.util.Optional;

/** Maps a validated Harpia literal to the Java initializer that preserves its value. */
public final class JavaDefaultValueMapper {

    public Optional<String> map(ApplicationField field) {
        if (field.enumTypeName().isPresent()) {
            return field.defaultValue()
                    .map(literal -> field.enumTypeName().orElseThrow() + "."
                            + JavaTypeMapper.enumConstant(literal));
        }
        return field.defaultValue().map(literal -> switch (field.scalarType()) {
            case BOOLEAN, INT -> literal;
            case LONG -> literal + "L";
            case DECIMAL -> "new BigDecimal(" + quoted(literal) + ")";
            case STRING, TEXT, EMAIL -> literal.replace("\\/", "/");
            case UUID -> "UUID.fromString(" + quoted(literal) + ")";
            case DATE -> "LocalDate.parse(" + quoted(literal) + ")";
            case DATE_TIME -> "OffsetDateTime.parse(" + quoted(literal) + ")";
        });
    }

    private static String quoted(String literal) {
        return "\"" + literal + "\"";
    }
}
