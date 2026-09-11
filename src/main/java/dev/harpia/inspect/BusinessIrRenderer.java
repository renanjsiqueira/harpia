package dev.harpia.inspect;

import dev.harpia.model.EntityModel;
import dev.harpia.model.FieldModel;
import dev.harpia.model.FlowStep;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.UseCaseModel;
import java.util.List;

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
                        .append(owned(field) ? " owned" : "")
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
                renderFlow(out, useCase.flow().steps(), "    ");
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
        project.events().forEach(event -> {
            out.append("Event ").append(event.name()).append('\n');
            event.payload().forEach(field -> out.append("  Payload ")
                    .append(field.name()).append(": ").append(field.type().syntax())
                    .append(field.required() ? " required" : "").append('\n'));
        });
        project.integrations().forEach(integration -> {
            out.append("Integration ").append(integration.name()).append('\n');
            integration.operations().forEach(operation -> {
                out.append("  Port ").append(operation.name()).append('\n');
                operation.input().forEach(parameter -> out.append("    Input ")
                        .append(parameter.name()).append(": ").append(parameter.type().syntax())
                        .append(parameter.required() ? " required" : "").append('\n'));
                out.append("    Output ")
                        .append(operation.output().type()
                                .map(dev.harpia.model.FieldType::syntax)
                                .orElse("nothing"))
                        .append('\n');
                operation.errors().forEach(error -> out.append("    Error ")
                        .append(error.name()).append('\n'));
            });
        });
        return out.toString();
    }

    private static boolean owned(FieldModel field) {
        dev.harpia.model.FieldType type = field.type();
        if (type instanceof dev.harpia.model.FieldType.Container container) {
            type = container.element();
        }
        return type instanceof dev.harpia.model.FieldType.Relationship relationship
                && relationship.lifecycle()
                        == dev.harpia.model.FieldType.RelationshipLifecycle.DEPENDENT;
    }

    private static void renderFlow(StringBuilder out, List<FlowStep> flow, String indent) {
        for (FlowStep step : flow) {
            out.append(indent).append("Step ").append(step.getClass().getSimpleName());
            if (step instanceof FlowStep.Conditional conditional) {
                out.append(' ').append(conditional.text());
            } else if (step instanceof FlowStep.Fail fail) {
                out.append(' ').append(fail.error()).append(" when ").append(fail.text());
            } else if (step instanceof FlowStep.Require require) {
                out.append(' ').append(require.text())
                        .append(" otherwise ").append(require.error());
            } else if (step instanceof FlowStep.Call call) {
                out.append(' ').append(call.variable()).append(" = ")
                        .append(call.logic()).append('(')
                        .append(call.arguments().stream()
                                .map(argument -> argument.name() + ": "
                                        + argument.parameterType().display())
                                .reduce((left, right) -> left + ", " + right)
                                .orElse(""))
                        .append(')');
            } else if (step instanceof FlowStep.IntegrationCall call) {
                call.variable().ifPresent(variable -> out.append(' ')
                        .append(variable).append(" ="));
                out.append(' ').append(call.integration()).append('.')
                        .append(call.operation()).append('(')
                        .append(call.arguments().stream()
                                .map(argument -> argument.name() + ": "
                                        + argument.parameterType().display())
                                .reduce((left, right) -> left + ", " + right)
                                .orElse(""))
                        .append(')');
                call.resultType().ifPresent(type -> out.append(" -> ").append(type.display()));
            }
            out.append('\n');
            if (step instanceof FlowStep.Conditional conditional) {
                out.append(indent).append("  Then\n");
                renderFlow(out, conditional.whenTrue(), indent + "    ");
                if (!conditional.whenFalse().isEmpty()) {
                    out.append(indent).append("  Else\n");
                    renderFlow(out, conditional.whenFalse(), indent + "    ");
                }
            }
        }
    }

}
