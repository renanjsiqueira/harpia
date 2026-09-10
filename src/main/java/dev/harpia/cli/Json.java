package dev.harpia.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The smallest JSON writer that can express a CLI report.
 *
 * <p>Harpia writes JSON and never reads it, so a library would bring a parser, a reflection-based
 * mapper and a field order decided by a class layout. Here the order is the order of the calls,
 * which is what makes two identical runs produce identical bytes — the same promise the generated
 * code already makes.
 */
final class Json {

    private Json() {
    }

    /** An object whose members keep the order they were added in. */
    static final class Object {

        private final List<String> members = new ArrayList<>();

        Object put(String key, String value) {
            members.add(quote(key) + ":" + quote(value));
            return this;
        }

        Object put(String key, int value) {
            members.add(quote(key) + ":" + value);
            return this;
        }

        Object put(String key, boolean value) {
            members.add(quote(key) + ":" + value);
            return this;
        }

        Object put(String key, Object value) {
            members.add(quote(key) + ":" + value.render());
            return this;
        }

        /** Omits the member entirely: absent and null are different answers. */
        Object putIfPresent(String key, java.util.Optional<String> value) {
            value.ifPresent(present -> put(key, present));
            return this;
        }

        Object putObjects(String key, List<Object> values) {
            members.add(quote(key) + ":[" + values.stream()
                    .map(Object::render)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + "]");
            return this;
        }

        Object putStrings(String key, List<String> values) {
            members.add(quote(key) + ":[" + values.stream()
                    .map(Json::quote)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + "]");
            return this;
        }

        String render() {
            return "{" + String.join(",", members) + "}";
        }
    }

    static String quote(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    // Everything below space has no literal spelling, and U+2028/U+2029 are line
                    // terminators to a JavaScript parser even though JSON allows them raw.
                    // Everything below space has no literal spelling. U+2028 and U+2029 are
                    // written as code points because a Java source file cannot hold either one:
                    // both are line terminators to the compiler's own lexer.
                    if (character < 0x20 || character == 0x2028 || character == 0x2029) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
