package dev.harpia.model;

import java.util.List;
import java.util.Objects;

/**
 * Who may reach an operation.
 *
 * <p>{@code AUTHENTICATED} says the request must carry an identity. {@code ROLE} says the identity
 * must also hold one of the named roles — any of them, because listing several is how you say a
 * thing is open to more than one kind of person.
 *
 * <p>How an identity is proved, and where its roles come from, is a provider's decision. What is
 * declared here is which endpoints are reachable without one.
 */
public record AccessRule(Kind kind, List<String> roles) {

    public enum Kind {
        PUBLIC,
        AUTHENTICATED,
        ROLE
    }

    public static final AccessRule PUBLIC = new AccessRule(Kind.PUBLIC, List.of());
    public static final AccessRule AUTHENTICATED = new AccessRule(Kind.AUTHENTICATED, List.of());

    public AccessRule {
        Objects.requireNonNull(kind, "kind");
        roles = List.copyOf(roles);
        if (roles.isEmpty() == (kind == Kind.ROLE)) {
            throw new IllegalArgumentException("ROLE names roles and nothing else does");
        }
    }

    public static AccessRule role(List<String> roles) {
        return new AccessRule(Kind.ROLE, roles);
    }

    /** True when the request has to carry an identity at all, whatever is asked of it. */
    public boolean requiresIdentity() {
        return kind != Kind.PUBLIC;
    }

    /** The stable form the inspect stages print; the first two read as the enum did. */
    @Override
    public String toString() {
        return kind == Kind.ROLE ? "ROLE " + String.join(" or ", roles) : kind.name();
    }
}
