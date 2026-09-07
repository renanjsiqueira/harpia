package dev.harpia.target.javaspring.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** A Java type name, including generic arguments and array dimensions. */
public record JavaTypeRef(
        String canonicalName,
        List<JavaTypeRef> arguments,
        int arrayDimensions) {

    public JavaTypeRef {
        Objects.requireNonNull(canonicalName, "canonicalName");
        arguments = List.copyOf(arguments);
        if (canonicalName.isBlank() || arrayDimensions < 0) {
            throw new IllegalArgumentException("invalid Java type: " + canonicalName);
        }
    }

    public static JavaTypeRef of(String canonicalName) {
        return new JavaTypeRef(canonicalName, List.of(), 0);
    }

    public static JavaTypeRef parameterized(String canonicalName, JavaTypeRef... arguments) {
        return new JavaTypeRef(canonicalName, List.of(arguments), 0);
    }

    public JavaTypeRef array() {
        return new JavaTypeRef(canonicalName, arguments, arrayDimensions + 1);
    }

    public String simpleName() {
        int separator = canonicalName.lastIndexOf('.');
        return separator < 0 ? canonicalName : canonicalName.substring(separator + 1);
    }

    public String sourceName() {
        String generic = arguments.isEmpty()
                ? ""
                : "<" + arguments.stream().map(JavaTypeRef::sourceName)
                        .collect(java.util.stream.Collectors.joining(", ")) + ">";
        return simpleName() + generic + "[]".repeat(arrayDimensions);
    }

    public Stream<JavaTypeRef> flattened() {
        return Stream.concat(Stream.of(this), arguments.stream().flatMap(JavaTypeRef::flattened));
    }
}
