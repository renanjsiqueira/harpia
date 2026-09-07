package dev.harpia.target;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a target needs from {@code harpia.yaml} that the Application IR must not carry.
 *
 * <p>{@code group} and {@code artifact} are build coordinates: Maven coordinates for
 * {@code java-spring}, and whatever the equivalent is for another target. {@code options} holds
 * values that only one target understands, such as the Spring Boot version. The core never
 * interprets them.
 */
public record TargetConfiguration(
        TargetId id,
        int languageVersion,
        String group,
        String artifact,
        Map<String, String> options) {

    public TargetConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(artifact, "artifact");
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public Optional<String> option(String key) {
        return Optional.ofNullable(options.get(key));
    }
}
