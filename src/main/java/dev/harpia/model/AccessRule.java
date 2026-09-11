package dev.harpia.model;

/**
 * Who may reach an operation.
 *
 * <p>{@code AUTHENTICATED} says the request must carry an identity, and nothing about how it
 * proves one: which mechanism supplies the identity is a provider's decision, the way a database
 * vendor is.
 */
public enum AccessRule {
    PUBLIC,
    AUTHENTICATED
}
