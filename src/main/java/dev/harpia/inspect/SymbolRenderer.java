package dev.harpia.inspect;

import dev.harpia.model.EntityModel;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.model.UseCaseModel;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Renders every name the project declares, grouped by namespace.
 *
 * <p>This is the view a cross-file reference has to resolve against, so seeing it is the cheapest
 * way to understand why a reference did or did not resolve.
 */
final class SymbolRenderer {

    private SymbolRenderer() {
    }

    static String render(ProjectModel project) {
        StringBuilder out = new StringBuilder();
        namespace(out, "types", project.entities().stream().map(EntityModel::name).toList());
        namespace(out, "operations", project.entities().stream()
                .flatMap(entity -> entity.useCases().stream())
                .map(UseCaseModel::baseName)
                .toList());
        namespace(out, "computations", project.logics().stream()
                .map(LogicModel::name)
                .toList());
        namespace(out, "scenarios", project.scenarios().stream()
                .map(scenario -> scenario.title() + " -> " + scenario.computation())
                .toList());
        return out.toString();
    }

    private static void namespace(StringBuilder out, String name, List<String> symbols) {
        out.append("Namespace ").append(name).append('\n');
        List<String> sorted = new ArrayList<>(new TreeSet<>(symbols));
        if (sorted.isEmpty()) {
            out.append("  (empty)\n");
            return;
        }
        sorted.forEach(symbol -> out.append("  ").append(symbol).append('\n'));
    }
}
