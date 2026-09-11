package dev.harpia.target;

import dev.harpia.capability.Capability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in target metadata.
 *
 * <p>The catalogue exists so an agent or a person can discover which targets are real before
 * proposing one. Listing an identifier here is documentation of intent, never a promise: every
 * entry except {@code java-spring} is {@link TargetStatus#NOT_SUPPORTED} and has no generator.
 *
 * <p>Identifiers of targets that are not yet supported may still change.
 */
public final class TargetCatalog {

    public static final TargetId JAVA_SPRING = TargetId.of("java-spring");

    private static final Map<TargetId, TargetDescriptor> DESCRIPTORS = descriptors();

    private TargetCatalog() {
    }

    private static Map<TargetId, TargetDescriptor> descriptors() {
        Map<TargetId, TargetDescriptor> catalogue = new LinkedHashMap<>();
        put(catalogue, new TargetDescriptor(
                JAVA_SPRING,
                "Java + Spring Boot",
                "java",
                "spring-boot",
                TargetStatus.SUPPORTED,
                "Java >= 21",
                21,
                Set.of(Capability.HTTP, Capability.PERSISTENCE),
                "1",
                "default",
                1,
                // `package` runs the tests and then builds the artifact, so the two gates overlap
                // on purpose: one says the code is right, the other that it can be started.
                List.of(
                        new TargetDescriptor.Gate("test", "mvn -f {output} test"),
                        new TargetDescriptor.Gate("package", "mvn -f {output} package"))));
        put(catalogue, TargetDescriptor.planned(
                "kotlin-spring", "Kotlin + Spring Boot", "kotlin", "spring"));
        put(catalogue, TargetDescriptor.planned(
                "csharp-aspnet", "C# + ASP.NET Core", "csharp", "aspnet"));
        put(catalogue, TargetDescriptor.planned(
                "typescript-nestjs", "TypeScript + NestJS", "typescript", "nestjs"));
        put(catalogue, TargetDescriptor.planned(
                "python-fastapi", "Python + FastAPI", "python", "fastapi"));
        put(catalogue, TargetDescriptor.planned("go", "Go", "go", "net/http"));
        put(catalogue, TargetDescriptor.planned(
                "clojure-jvm", "Clojure on the JVM", "clojure", "ring"));
        put(catalogue, TargetDescriptor.planned(
                "php-laravel", "PHP + Laravel", "php", "laravel"));
        put(catalogue, TargetDescriptor.planned("rust", "Rust", "rust", "axum"));
        put(catalogue, TargetDescriptor.planned(
                "elixir-phoenix", "Elixir + Phoenix", "elixir", "phoenix"));
        put(catalogue, TargetDescriptor.planned(
                "ruby-rails", "Ruby + Rails", "ruby", "rails"));
        return Map.copyOf(catalogue);
    }

    private static void put(Map<TargetId, TargetDescriptor> catalogue, TargetDescriptor descriptor) {
        if (catalogue.putIfAbsent(descriptor.id(), descriptor) != null) {
            throw new IllegalStateException("duplicate target id: " + descriptor.id());
        }
    }

    public static Optional<TargetDescriptor> find(TargetId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(DESCRIPTORS.get(id));
    }

    /** Every catalogued descriptor, supported first and then by identifier. */
    public static List<TargetDescriptor> all() {
        return DESCRIPTORS.values().stream()
                .sorted(java.util.Comparator
                        .comparing((TargetDescriptor descriptor) -> !descriptor.status().canGenerate())
                        .thenComparing(TargetDescriptor::id))
                .toList();
    }

    public static List<TargetId> supported() {
        return all().stream()
                .filter(descriptor -> descriptor.status().canGenerate())
                .map(TargetDescriptor::id)
                .toList();
    }

    public static List<TargetId> planned() {
        return all().stream()
                .filter(descriptor -> !descriptor.status().canGenerate())
                .map(TargetDescriptor::id)
                .toList();
    }

    /** Stable discovery view shared by future adapters without exposing generator internals. */
    public static List<TargetInfo> info() {
        return all().stream().map(TargetInfo::from).toList();
    }
}
