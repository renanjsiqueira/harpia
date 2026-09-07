package dev.harpia.inspect;

import dev.harpia.parse.LogicAst;
import dev.harpia.parse.SpecAst;
import java.util.List;

/** Renders the Harpia syntax tree. It shows what was written, before any name is resolved. */
final class AstRenderer {

    private AstRenderer() {
    }

    static String render(List<SpecAst> modules) {
        StringBuilder out = new StringBuilder();
        for (SpecAst module : modules) {
            out.append("Module ").append(module.moduleName())
                    .append(" (").append(module.file()).append(")\n");
            if (module.declaresEntity()) {
                out.append("  Data\n");
                module.fields().forEach(field -> out.append("    Field ").append(field.name())
                        .append(": ").append(field.type())
                        .append(field.required() ? " required" : "")
                        .append(field.unique() ? " unique" : "")
                        .append(field.generated() ? " generated" : "")
                        .append(field.defaultValue().map(value -> " default " + value).orElse(""))
                        .append('\n'));
            }
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                out.append("  UseCase ").append(useCase.title()).append('\n');
                out.append("    Endpoint ").append(useCase.endpoint().method())
                        .append(' ').append(useCase.endpoint().path()).append('\n');
                useCase.flow().forEach(statement -> out.append("    Flow ")
                        .append(statement.getClass().getSimpleName()).append('\n'));
            }
            for (LogicAst.Declaration logic : module.logics()) {
                out.append("  Logic ").append(logic.name()).append('\n');
                logic.parameters().forEach(parameter -> out.append("    Input ")
                        .append(parameter.name()).append(": ").append(parameter.type())
                        .append('\n'));
                out.append("    Output ").append(logic.returnType()).append('\n');
                logic.body().forEach(statement -> out.append("    Statement ")
                        .append(statement.getClass().getSimpleName()).append('\n'));
            }
            for (LogicAst.Scenario scenario : module.scenarios()) {
                out.append("  Scenario ").append(scenario.title())
                        .append(" of ").append(scenario.computation()).append('\n');
                scenario.given().forEach(binding -> out.append("    Given ")
                        .append(binding.name()).append(": ").append(binding.literal()).append('\n'));
                out.append("    Then result: ").append(scenario.expected().literal()).append('\n');
            }
        }
        return out.toString();
    }
}
