package dev.harpia.inspect;

import dev.harpia.parse.LogicAst;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;

/** Renders the Harpia syntax tree. It shows what was written, before any name is resolved. */
final class AstRenderer {

    private AstRenderer() {
    }

    static String render(ProjectAst project) {
        StringBuilder out = new StringBuilder();
        out.append("Project languageVersion=")
                .append(project.languageVersion())
                .append('\n');
        for (ModuleAst module : project.modules()) {
            out.append("Module ").append(module.name())
                    .append(" (").append(module.file()).append(")\n");
            if (module.declaresEntity()) {
                out.append("  Data\n");
                module.entity().fields().forEach(field -> out.append("    Field ").append(field.name())
                        .append(": ").append(field.type())
                        .append(field.required() ? " required" : "")
                        .append(field.unique() ? " unique" : "")
                        .append(field.generated() ? " generated" : "")
                        .append(field.owned() ? " owned" : "")
                        .append(field.defaultValue().map(value -> " default " + value).orElse(""))
                        .append('\n'));
            }
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                out.append("  ").append(switch (useCase.declaredKind()) {
                    case COMMAND -> "Command";
                    case QUERY -> "Query";
                    default -> "UseCase";
                }).append(' ').append(useCase.title()).append('\n');
                useCase.endpoint().ifPresentOrElse(
                        endpoint -> out.append("    Endpoint ").append(endpoint.method())
                                .append(' ').append(endpoint.path()).append('\n'),
                        () -> out.append("    Binding none\n"));
                useCase.flow().forEach(statement -> out.append("    Flow ")
                        .append(statement.getClass().getSimpleName()).append('\n'));
            }
            for (dev.harpia.parse.IntegrationAst.Declaration integration
                    : module.integrations()) {
                out.append("  Integration ").append(integration.name()).append('\n');
                integration.operations().forEach(operation -> out.append("    Operation ")
                        .append(operation.name()).append('\n'));
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
        project.bindingFiles().forEach(file -> {
            out.append("BindingFile ").append(file.file()).append('\n');
            file.httpBindings().forEach(binding -> out.append("  Http ")
                    .append(binding.operation()).append(' ')
                    .append(binding.endpoint().method()).append(' ')
                    .append(binding.endpoint().path()).append(" access=")
                    .append(binding.access()).append('\n'));
        });
        return out.toString();
    }
}
