package dev.harpia.target.javaspring;

import java.util.Objects;

/** One provider-owned Spring configuration value. */
public record ConfigurationProperty(String key, String value)
        implements Comparable<ConfigurationProperty> {

    public ConfigurationProperty {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.matches("[a-z][a-z0-9.-]*")) {
            throw new IllegalArgumentException("invalid configuration property key: " + key);
        }
    }

    @Override
    public int compareTo(ConfigurationProperty other) {
        return key.compareTo(other.key);
    }
}
