package dev.harpia.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The built-in catalogue of Harpia Logic. It is closed and deterministic.
 *
 * <p>A function is only added together with the type that justifies it: {@code round} arrives with
 * {@code Money}, the aggregates arrive with {@code List<T>} and the date helpers arrive with
 * {@code Duration}. Adding a function before its type produces dead API.
 */
public final class BuiltinRegistry {

    private static final Map<String, BuiltinFunction> FUNCTIONS = functions();

    private BuiltinRegistry() {
    }

    private static Map<String, BuiltinFunction> functions() {
        Map<String, BuiltinFunction> registry = new LinkedHashMap<>();
        register(registry, new BuiltinFunction(
                "min", 2, BuiltinFunction.widestNumeric(), Effect.PURE));
        register(registry, new BuiltinFunction(
                "max", 2, BuiltinFunction.widestNumeric(), Effect.PURE));
        return Map.copyOf(registry);
    }

    private static void register(Map<String, BuiltinFunction> registry, BuiltinFunction function) {
        if (registry.putIfAbsent(function.name(), function) != null) {
            throw new IllegalStateException("duplicate built-in: " + function.name());
        }
    }

    public static Optional<BuiltinFunction> lookup(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(FUNCTIONS.get(name));
    }

    /** Sorted for stable diagnostics. */
    public static Set<String> names() {
        return new java.util.TreeSet<>(FUNCTIONS.keySet());
    }
}
