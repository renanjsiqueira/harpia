package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** Syntax tree of one {@code *.harpia.md} source module. */
public record ModuleAst(
        String file,
        String name,
        List<DeclarationAst> declarations,
        SourceRef where) {

    public ModuleAst {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(name, "name");
        declarations = List.copyOf(declarations);
        Objects.requireNonNull(where, "where");
    }

    public <T extends DeclarationAst> List<T> declarations(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return declarations.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public List<SpecAst.EntityDeclaration> entities() {
        return declarations(SpecAst.EntityDeclaration.class);
    }

    public List<SpecAst.EnumDeclaration> enums() {
        return declarations(SpecAst.EnumDeclaration.class);
    }

    public List<SpecAst.ValueDeclaration> values() {
        return declarations(SpecAst.ValueDeclaration.class);
    }

    public List<SpecAst.UseCaseDeclaration> useCases() {
        return declarations(SpecAst.UseCaseDeclaration.class);
    }

    public List<LogicAst.Declaration> logics() {
        return declarations(LogicAst.Declaration.class);
    }

    public List<LogicAst.Scenario> scenarios() {
        return declarations(LogicAst.Scenario.class);
    }

    /** Compatibility view for the V0 form where {@code ## Data} declares one primary entity. */
    public boolean declaresEntity() {
        return !entities().isEmpty();
    }

    /** The V0 primary entity declared by {@code ## Data}. */
    public SpecAst.EntityDeclaration entity() {
        if (entities().size() != 1) {
            throw new IllegalStateException(
                    "module " + name + " does not declare exactly one V0 entity");
        }
        return entities().getFirst();
    }
}
