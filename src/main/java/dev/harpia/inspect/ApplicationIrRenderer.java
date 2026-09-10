package dev.harpia.inspect;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationProject;
import dev.harpia.capability.Capability;
import dev.harpia.capability.ResolvedCapability;

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
                out.append("    Column ").append(field.columnName())
                        .append(": ").append(field.type().syntax())
                        .append(field.required() ? " required" : "")
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
                operation.flow().forEach(instruction -> out.append("      Instruction ")
                        .append(instruction.command())
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
                        .append(instruction.guard()
                                .map(guard -> " " + guard.error() + " when " + guard.text())
                                .orElse(""))
                        .append('\n'));
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
        return out.toString();
    }
}
