package dev.harpia.target.javaspring;

import dev.harpia.emit.GeneratedTree;

/** Emits one deterministic slice of a Java/Spring project into the in-memory tree. */
public interface Emitter {

    void emit(JavaSpringContext context, GeneratedTree output);
}
