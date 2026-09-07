package dev.harpia.logic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One entry of the closed built-in registry. A built-in declares its name, arity, the shape of its
 * operands and its effect. There is no reflection and no plugin system: adding a function is a
 * change set, not a runtime discovery.
 */
public record BuiltinFunction(String name, int arity, Signature signature, Effect effect) {

    public BuiltinFunction {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(effect, "effect");
        if (arity < 1) {
            throw new IllegalArgumentException("a built-in takes at least one argument");
        }
    }

    /** Computes the result type of a call, or empty when the argument types are not accepted. */
    public Optional<LogicType> resultType(List<LogicType> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.size() != arity) {
            return Optional.empty();
        }
        return signature.apply(arguments);
    }

    /** Describes which operand types a built-in accepts and what it returns. */
    public interface Signature {
        Optional<LogicType> apply(List<LogicType> arguments);
    }

    /** Every argument must be numeric; the result is the widest argument type. */
    public static Signature widestNumeric() {
        return arguments -> {
            LogicType result = null;
            for (LogicType argument : arguments) {
                if (!argument.isNumeric()) {
                    return Optional.empty();
                }
                result = result == null
                        ? argument
                        : LogicType.unify(result, argument).orElse(null);
                if (result == null) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(result);
        };
    }

    public String describe() {
        return name + "/" + arity;
    }
}
