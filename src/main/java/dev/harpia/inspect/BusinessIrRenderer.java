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
                out.append("  UseCase ").append(useCase.baseName()).append('\n');
                out.append("    Http ").append(useCase.http().method())
                        .append(' ').append(useCase.http().path()).append('\n');
                out.append("    Access ").append(useCase.access()).append('\n');
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
        project.logics().forEach(logic -> out.append("Logic ").append(logic.name()).append('\n'));
        return out.toString();
    }

}
