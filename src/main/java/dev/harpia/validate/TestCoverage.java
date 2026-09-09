package dev.harpia.validate;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.ScenarioModel;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports where a request for tests produces none.
 *
 * <p>A pure computation has no behaviour the compiler can derive an expectation from, so it gets a
 * generated test only when a scenario says what the right answer is. Saying that out loud is the
 * point: {@code generation.tests: true} silently producing nothing is worse than producing nothing.
 */
public final class TestCoverage {

    private TestCoverage() {
    }

    public static void report(
            ProjectModel project, boolean generatesTests, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (!generatesTests) {
            return;
        }
        Set<String> covered = project.scenarios().stream()
                .map(ScenarioModel::computation)
                .collect(Collectors.toSet());
        for (LogicModel logic : project.logics()) {
            // Harpia generates no test for an implementation it does not own, so its absence is
            // not a gap it can report.
            if (logic.customContract().isPresent()) {
                continue;
            }
            if (!covered.contains(logic.name())) {
                diagnostics.warning(
                        ErrorCodes.SEMANTIC_SCENARIO_MISSING,
                        "no test was generated for Logic '" + logic.name()
                                + "' because it declares no scenario",
                        logic.where(),
                        "add '## Scenario <title>' with '### Given', '### When " + logic.name()
                                + "' and '### Then'");
            }
        }
    }
}
