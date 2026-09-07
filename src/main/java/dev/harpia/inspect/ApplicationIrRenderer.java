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
                        .append(": ").append(field.type())
                        .append(field.required() ? " required" : "")
                        .append(field.unique() ? " unique" : "")
                        .append(field.generated() ? " generated" : "").append('\n');
            }
            for (ApplicationOperation operation : entity.operations()) {
                out.append("    Operation ").append(operation.methodName())
                        .append(" kind=").append(operation.kind())
                        .append(" transactional=").append(operation.transactional()).append('\n');
                out.append("      HttpOperation ").append(operation.endpoint().method())
                        .append(' ').append(operation.endpoint().path())
                        .append(" access=").append(operation.endpoint().access()).append('\n');
                operation.requestTypeName().ifPresent(request ->
                        out.append("      RequestModel ").append(request).append('\n'));
                out.append("      Result ").append(operation.result().status())
                        .append(' ').append(operation.result().kind())
                        .append(operation.result().responseTypeName()
                                .map(name -> " " + name).orElse(""))
                        .append('\n');
                operation.flow().forEach(instruction -> out.append("      Instruction ")
                        .append(instruction.command()).append('\n'));
                operation.failures().forEach(failure -> out.append("      Failure ")
                        .append(failure.condition()).append(" -> ").append(failure.status())
                        .append('\n'));
            }
        }
        project.logics().forEach(logic -> out.append("  Computation ")
                .append(logic.typeName())
                .append(" -> ").append(logic.returnType().display()).append('\n'));
        return out.toString();
    }
}
