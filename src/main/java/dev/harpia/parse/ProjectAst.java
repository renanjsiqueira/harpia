package dev.harpia.parse;

import dev.harpia.LanguageVersion;
import dev.harpia.binding.BindingAst;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete project syntax tree in deterministic source-path order. */
public record ProjectAst(
        LanguageVersion languageVersion,
        List<ModuleAst> modules,
        List<BindingAst> bindingFiles) {

    public ProjectAst {
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(bindingFiles, "bindingFiles");
        Set<String> files = new HashSet<>();
        for (ModuleAst module : modules) {
            Objects.requireNonNull(module, "module");
            if (!files.add(module.file())) {
                throw new IllegalArgumentException(
                        "project syntax tree repeats source file " + module.file());
            }
        }
        modules = modules.stream()
                .sorted(java.util.Comparator.comparing(ModuleAst::file))
                .toList();
        Set<String> bindingPaths = new HashSet<>();
        for (BindingAst bindingFile : bindingFiles) {
            Objects.requireNonNull(bindingFile, "bindingFile");
            if (!bindingPaths.add(bindingFile.file())) {
                throw new IllegalArgumentException(
                        "project syntax tree repeats binding file " + bindingFile.file());
            }
        }
        bindingFiles = bindingFiles.stream()
                .sorted(java.util.Comparator.comparing(BindingAst::file))
                .toList();
    }

    public ProjectAst(LanguageVersion languageVersion, List<ModuleAst> modules) {
        this(languageVersion, modules, List.of());
    }

    public static ProjectAst empty() {
        return empty(LanguageVersion.CURRENT);
    }

    public static ProjectAst empty(LanguageVersion languageVersion) {
        return new ProjectAst(languageVersion, List.of(), List.of());
    }
}
