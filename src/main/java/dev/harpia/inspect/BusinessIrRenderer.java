package dev.harpia.inspect;

import dev.harpia.model.EntityModel;
import dev.harpia.model.FieldModel;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.UseCaseModel;

/** Renders business meaning. No word here names a language, a framework or a build tool. */
final class BusinessIrRenderer {

    private BusinessIrRenderer() {
    }

    static String render(ProjectModel project) {
        StringBuilder out = new StringBuilder();
        for (EntityModel entity : project.entities()) {
            out.append("Entity ").append(entity.name()).append('\n');
            for (FieldModel field : entity.fields()) {
                out.append("  Field ").append(field.name())
                        .append(": ").append(field.type().syntax())
                        .append(field.required() ? " required" : "")
                        .append(field.unique() ? " unique" : "")
                        .append(field.generated() ? " generated" : "")
                        .append(field.defaultValue()
                                .map(value -> " default " + value.source()).orElse(""))
                        .append('\n');
            }
            for (UseCaseModel useCase : entity.useCases()) {
                out.append("  ").append(switch (useCase.nature()) {
                    case COMMAND -> "Command";
                    case QUERY -> "Query";
                    case INFERRED -> "UseCase";
                }).append(' ').append(useCase.baseName()).append('\n');
                useCase.http().ifPresentOrElse(
                        binding -> {
                            out.append("    Http ").append(binding.method())
                                    .append(' ').append(binding.path()).append('\n');
                            out.append("    Access ").append(binding.access()).append('\n');
                        },
                        () -> out.append("    Binding none\n"));
                useCase.input().forEach(field -> out.append("    Input ")
                        .append(field.name()).append(": ").append(field.type().syntax())
                        .append(field.required() ? " required" : "").append('\n'));
                useCase.flow().steps().forEach(step -> out.append("    Step ")
                        .append(step.getClass().getSimpleName()).append('\n'));
                out.append("    Output ").append(useCase.output().status())
                        .append(' ').append(useCase.output().shape().kind())
                        .append(useCase.output().shape().entity().map(e -> " " + e).orElse(""))
                        .append('\n');
                useCase.errors().forEach(error -> out.append("    Error ")
                        .append(error.condition()).append(" -> ").append(error.status())
                        .append('\n'));
            }
        }
        project.logics().forEach(logic -> out.append("Logic ").append(logic.name())
                .append(logic.customContract().map(contract -> " custom " + contract).orElse(""))
                .append('\n'));
        return out.toString();
    }

}
