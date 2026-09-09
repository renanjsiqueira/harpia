package dev.harpia.parse;

import dev.harpia.diag.SourceRef;

/** A named declaration contained by one {@link ModuleAst}. */
public sealed interface DeclarationAst
        permits SpecAst.EntityDeclaration,
                SpecAst.EnumDeclaration,
                SpecAst.ValueDeclaration,
                SpecAst.InvariantDeclaration,
                SpecAst.UseCaseDeclaration,
                LogicAst.Declaration,
                LogicAst.Scenario {

    DeclarationKind kind();

    String declaredName();

    SourceRef where();
}
