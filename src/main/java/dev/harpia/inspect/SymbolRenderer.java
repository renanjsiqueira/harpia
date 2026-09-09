package dev.harpia.inspect;

import dev.harpia.symbol.Namespace;
import dev.harpia.symbol.Symbol;
import dev.harpia.symbol.SymbolTable;
import java.util.List;

/**
 * Renders the symbol table.
 *
 * <p>This is the view a cross-module reference resolves against, so seeing it is the cheapest way
 * to understand why a reference did or did not resolve. Each symbol shows the module that declares
 * it, which is what a diagnostic points at when two modules claim the same name.
 */
final class SymbolRenderer {

    private SymbolRenderer() {
    }

    static String render(SymbolTable symbols) {
        StringBuilder out = new StringBuilder();
        for (Namespace namespace : Namespace.values()) {
            out.append("Namespace ").append(namespace.id()).append('\n');
            List<Symbol> declared = symbols.in(namespace);
            if (declared.isEmpty()) {
                out.append("  (empty)\n");
                continue;
            }
            for (Symbol symbol : declared) {
                out.append("  ").append(symbol.name())
                        .append(describe(symbol))
                        .append("  [").append(symbol.module()).append("]\n");
            }
        }
        return out.toString();
    }

    private static String describe(Symbol symbol) {
        if (symbol instanceof Symbol.Computation computation) {
            String parameters = computation.parameters().stream()
                    .map(parameter -> parameter.name() + ": " + parameter.type().display())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return "(" + parameters + ") -> " + computation.returnType().display();
        }
        if (symbol instanceof Symbol.EnumType declared) {
            return " enum(" + String.join(", ", declared.values()) + ")";
        }
        if (symbol instanceof Symbol.ValueType declared) {
            return " value(" + declared.fields().stream()
                    .map(field -> field.name() + ": " + field.type())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("") + ")";
        }
        if (symbol instanceof Symbol.Scenario scenario) {
            return " -> " + scenario.computation();
        }
        return "";
    }
}
