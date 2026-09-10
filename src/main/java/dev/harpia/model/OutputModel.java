package dev.harpia.model;

import java.util.Objects;
import java.util.Optional;

public record OutputModel(int status, Shape shape) {
    public OutputModel {
        Objects.requireNonNull(shape, "shape");
    }

    public record Shape(Kind kind, Optional<String> entity) {
        public Shape {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entity, "entity");
            if ((kind == Kind.NOTHING) == entity.isPresent()) {
                throw new IllegalArgumentException("only entity and list shapes name an entity");
            }
        }
    }

    public enum Kind {
        ENTITY,
        LIST,
        PAGE,
        NOTHING
    }
}
