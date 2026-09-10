package dev.harpia.inspect;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationProject;
import dev.harpia.capability.Capability;
import dev.harpia.capability.ResolvedCapability;
import java.util.List;

/**
 * Renders application behaviour: persistent entities, operations, request and response models and
 * transaction boundaries. Which components implement them is the target's decision, so no component
 * appears here.
 */
final class ApplicationIrRenderer {

    private ApplicationIrRenderer() {
    }

    static String render(ApplicationProject project) {
        StringBuilder out = new StringBuilder();
        out.append("Application ").append(project.settings().name()).append('\n');
        out.append("  Namespace ").append(project.settings().namespace()).append('\n');
        out.append("  Generation migrations=")
                .append(project.settings().generation().migrations())
                .append(" tests=").append(project.settings().generation().tests()).append('\n');
        for (Capability capability : Capability.values()) {
            ResolvedCapability resolved = project.capabilities().asMap().get(capability);
            if (resolved != null) {
                out.append("  Capability ").append(capability.id())
                        .append(" provider=")
                        .append(resolved.provider().map(provider -> provider.value()).orElse("<target>"))
                        .append('\n');
            }
        }
        for (ApplicationEntity entity : project.entities()) {
            out.append("  PersistentEntity ").append(entity.typeName())
                    .append(" table=").append(entity.tableName())
                    .append(" response=").append(entity.responseTypeName()).append('\n');
            for (ApplicationField field : entity.fields()) {
                if (field.isRelationship()) {
                    dev.harpia.application.ApplicationFieldType.Relationship relationship =
                            field.relationship().or(() -> field.relationshipElement())
                                    .orElseThrow();
                    out.append("    Relationship ").append(field.name())
                            .append(": ").append(field.type().syntax())
                            .append(" cardinality=")
                            .append(field.relationship().isPresent() ? "ONE" : "MANY")
                            .append(" loading=").append(relationship.loading())
                            .append(" lifecycle=").append(relationship.lifecycle());
                } else {
                    out.append("    Column ").append(field.columnName())
                            .append(": ").append(field.type().syntax());
                }
                out.append(field.required() ? " required" : "")
                        .append(field.unique() ? " unique" : "")
                        .append(field.generated() ? " generated" : "").append('\n');
            }
            entity.invariants().forEach(invariant -> out.append("    Invariant ")
                    .append(invariant.text()).append('\n'));
            for (ApplicationOperation operation : entity.operations()) {
                out.append("    Operation ").append(operation.methodName())
                        .append(" nature=").append(operation.nature())
                        .append(" kind=").append(operation.kind())
                        .append(" transactional=").append(operation.transactional()).append('\n');
                operation.endpoint().ifPresentOrElse(
                        endpoint -> out.append("      HttpOperation ").append(endpoint.method())
                                .append(' ').append(endpoint.path())
                                .append(" access=").append(endpoint.access()).append('\n'),
                        () -> out.append("      Binding none\n"));
                operation.requestTypeName().ifPresent(request ->
                        out.append("      RequestModel ").append(request).append('\n'));
                out.append("      Result ").append(operation.result().status())
                        .append(' ').append(operation.result().kind())
                        .append(operation.result().responseTypeName()
                                .map(name -> " " + name).orElse(""))
                        .append('\n');
                operation.rules().forEach(rule -> out.append("      Rule ")
                        .append(rule.text()).append('\n'));
                renderFlow(out, operation.flow(), "      ");
                operation.failures().forEach(failure -> out.append("      Failure ")
                        .append(failure.condition())
                        .append(failure.name().map(name -> " '" + name + "'").orElse(""))
                        .append(" -> ").append(failure.status())
                        .append('\n'));
            }
        }
        project.enums().forEach(declared -> out.append("  Enum ")
                .append(declared.typeName())
                .append('(').append(String.join(", ", declared.values())).append(")\n"));
        project.values().forEach(declared -> out.append("  Value ")
                .append(declared.typeName())
                .append('(')
                .append(declared.components().stream()
                        .map(component -> component.name() + ": " + component.type().syntax())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse(""))
                .append(")\n"));
        project.logics().forEach(logic -> out.append("  Computation ")
                .append(logic.typeName())
                .append(" -> ").append(logic.returnType().display())
                .append(logic.customContract().map(contract -> " custom " + contract).orElse(""))
                .append('\n'));
        project.integrations().forEach(integration -> {
            out.append("  OutboundPort ").append(integration.name()).append('\n');
            integration.operations().forEach(operation -> out.append("    Operation ")
                    .append(operation.name()).append('\n'));
        });
        return out.toString();
    }

    private static void renderFlow(
            StringBuilder out,
            List<ApplicationOperation.FlowInstruction> flow,
        String indent) {
        for (ApplicationOperation.FlowInstruction instruction : flow) {
            out.append(indent).append("Instruction ").append(instruction.command())
                    .append(instruction.fields().isEmpty()
                            ? ""
                            : " by " + String.join(" and ", instruction.fields()))
                    .append(instruction.sort().isEmpty()
                            ? ""
                            : " sorted by " + instruction.sort().stream()
                                    .map(order -> order.field()
                                            + (order.descending() ? " desc" : " asc"))
                                    .reduce((left, right) -> left + " and " + right)
                                    .orElse(""))
                    .append(instruction.paged() ? " paged" : "")
                    // The shape is shared; how it reads is not. Fail raises when a condition
                    // holds, require raises unless it holds, and set assigns a value.
                    .append(instruction.value()
                            .map(typed -> switch (instruction.command()) {
                                case FAIL -> " " + typed.name() + " when " + typed.text();
                                case REQUIRE ->
                                        " " + typed.text() + " otherwise " + typed.name();
                                case IF -> " " + typed.text();
                                case ADD_TO -> " " + typed.text() + " to " + typed.name();
                                case REMOVE_FROM ->
                                        " " + typed.text() + " from " + typed.name();
                                default -> " " + typed.name() + " = " + typed.text();
                            })
                            .orElse(""))
                    .append('\n');
            if (instruction.command() == ApplicationOperation.FlowCommand.IF) {
                out.append(indent).append("  Then\n");
                renderFlow(out, instruction.whenTrue(), indent + "    ");
                if (!instruction.whenFalse().isEmpty()) {
                    out.append(indent).append("  Else\n");
                    renderFlow(out, instruction.whenFalse(), indent + "    ");
                }
            }
        }
    }
}
