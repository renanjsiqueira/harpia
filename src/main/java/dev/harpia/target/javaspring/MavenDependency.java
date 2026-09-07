package dev.harpia.target.javaspring;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Maven dependency requested by a resolved provider, before XML rendering. */
public record MavenDependency(
        String groupId,
        String artifactId,
        Optional<String> version,
        Optional<String> scope) implements Comparable<MavenDependency> {

    private static final Comparator<MavenDependency> ORDER = Comparator
            .comparing(MavenDependency::groupId)
            .thenComparing(MavenDependency::artifactId)
            .thenComparing(item -> item.version().orElse(""))
            .thenComparing(item -> item.scope().orElse(""));

    public MavenDependency {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(scope, "scope");
        if (groupId.isBlank() || artifactId.isBlank()) {
            throw new IllegalArgumentException("Maven coordinates must not be blank");
        }
    }

    public static MavenDependency managed(String groupId, String artifactId) {
        return new MavenDependency(groupId, artifactId, Optional.empty(), Optional.empty());
    }

    public static MavenDependency managed(
            String groupId, String artifactId, String scope) {
        return new MavenDependency(
                groupId, artifactId, Optional.empty(), Optional.of(scope));
    }

    @Override
    public int compareTo(MavenDependency other) {
        return ORDER.compare(this, other);
    }
}
