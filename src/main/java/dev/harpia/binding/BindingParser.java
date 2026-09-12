package dev.harpia.binding;

import dev.harpia.LanguageVersion;
import dev.harpia.ast.BlockNode;
import dev.harpia.ast.MarkdownStructure;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.AccessParser;
import dev.harpia.parse.EndpointParser;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the first external binding grammar without interpreting Markdown prose. */
public final class BindingParser {

    private static final String FILE_HEADING = "HTTP Bindings";
    private static final String BASE_URL_HEADING = "Base URL";
    private static final String AUTH_HEADING = "Auth";
    private static final String DECLARATION_PREFIX = "Bind ";
    private static final Pattern OPERATION = Pattern.compile(
            "[A-Z][A-Za-z0-9]*(?:\\.[A-Z][A-Za-z0-9]*)?");
    private static final Pattern BASE_URL = Pattern.compile(
            "^(?:https?://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9._~-]*)*"
                    + "|/(?:[a-z][a-z0-9-]*(?:/[a-z][a-z0-9-]*)*)?)$");
    private static final Pattern REQUEST = Pattern.compile(
            "^([a-z][A-Za-z0-9]*): +(body|path|query|header)(?: +([^ ]+))?$");
    private static final Pattern AUTH = Pattern.compile(
            "^(?:bearer|api key ([A-Za-z][A-Za-z0-9-]*))$");
    private static final Set<String> SECTIONS = Set.of(
            "Endpoint", "Access", "Request", "Response");
    private static final Set<String> FILE_SECTIONS = Set.of(BASE_URL_HEADING, AUTH_HEADING);

    private BindingParser() {
    }

    public static Optional<BindingAst> parse(
            SourceFile source,
            LanguageVersion languageVersion,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(diagnostics, "diagnostics");
        MarkdownStructure markdown = MarkdownStructure.parse(source);

        List<BlockNode> h1 = headings(markdown.blocks(), 1);
        SourceRef fileWhere = h1.isEmpty()
                ? SourceRef.file(source.relativePath())
                : h1.getFirst().raw().where();
        if (languageVersion != LanguageVersion.V1) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                    "external bindings need harpia.languageVersion 1",
                    fileWhere,
                    "set harpia.languageVersion to 1");
            return Optional.empty();
        }

        boolean valid = true;
        if (h1.size() != 1 || !h1.getFirst().headingText().equals(FILE_HEADING)) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_FILE,
                    "expected exactly one '# " + FILE_HEADING + "' heading",
                    h1.size() > 1 ? h1.get(1).raw().where() : fileWhere);
            valid = false;
        }

        List<Section> topLevel = sections(markdown.blocks(), 2);
        List<Section> baseUrls = topLevel.stream()
                .filter(section -> section.heading().headingText().equals(BASE_URL_HEADING))
                .toList();
        Optional<BindingAst.BaseUrl> baseUrl = Optional.empty();
        if (baseUrls.size() > 1) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_FILE,
                    "binding file repeats '## " + BASE_URL_HEADING + "'",
                    baseUrls.get(1).heading().raw().where());
            valid = false;
        }
        if (!baseUrls.isEmpty()) {
            Optional<dev.harpia.ast.RawSpan> value =
                    paragraph(baseUrls.getFirst(), BASE_URL_HEADING, diagnostics);
            if (value.isEmpty() || !BASE_URL.matcher(value.orElseThrow().text().strip()).matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "'## Base URL' requires '/api/v1' or an absolute URL such as "
                                + "'https://service.example'",
                        value.map(dev.harpia.ast.RawSpan::where)
                                .orElse(baseUrls.getFirst().heading().raw().where()));
                valid = false;
            } else {
                dev.harpia.ast.RawSpan raw = value.orElseThrow();
                baseUrl = Optional.of(new BindingAst.BaseUrl(raw.text().strip(), raw.where()));
            }
        }

        List<Section> auths = topLevel.stream()
                .filter(section -> section.heading().headingText().equals(AUTH_HEADING))
                .toList();
        Optional<BindingAst.Auth> auth = Optional.empty();
        if (auths.size() > 1) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_FILE,
                    "binding file repeats '## " + AUTH_HEADING + "'",
                    auths.get(1).heading().raw().where());
            valid = false;
        }
        if (!auths.isEmpty()) {
            Optional<dev.harpia.ast.RawSpan> value =
                    paragraph(auths.getFirst(), AUTH_HEADING, diagnostics);
            java.util.regex.Matcher matcher = value
                    .map(raw -> AUTH.matcher(raw.text().strip()))
                    .filter(java.util.regex.Matcher::matches)
                    .orElse(null);
            if (matcher == null) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "'## Auth' requires 'bearer' or 'api key <Header-Name>'",
                        value.map(dev.harpia.ast.RawSpan::where)
                                .orElse(auths.getFirst().heading().raw().where()));
                valid = false;
            } else {
                dev.harpia.ast.RawSpan raw = value.orElseThrow();
                auth = Optional.of(matcher.group(1) == null
                        ? new BindingAst.Auth(
                                BindingAst.Auth.Kind.BEARER, "Authorization", raw.where())
                        : new BindingAst.Auth(
                                BindingAst.Auth.Kind.API_KEY, matcher.group(1), raw.where()));
            }
        }

        List<Section> declarations = topLevel.stream()
                .filter(section -> !FILE_SECTIONS.contains(section.heading().headingText()))
                .toList();
        if (declarations.isEmpty()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_FILE,
                    "binding file declares nothing; add '## Bind <Operation>'",
                    fileWhere);
            valid = false;
        }

        List<BindingAst.Declaration> bindings = new ArrayList<>();
        for (Section declaration : declarations) {
            Optional<BindingAst.Declaration> binding = parseBinding(declaration, diagnostics);
            if (binding.isPresent()) {
                bindings.add(binding.orElseThrow());
            } else {
                valid = false;
            }
        }
        return valid
                ? Optional.of(new BindingAst(
                        source.relativePath(), baseUrl, auth, bindings, fileWhere))
                : Optional.empty();
    }

    private static Optional<BindingAst.Declaration> parseBinding(
            Section declaration, DiagnosticCollector diagnostics) {
        boolean valid = true;
        String heading = declaration.heading().headingText();
        String operation = heading.startsWith(DECLARATION_PREFIX)
                ? heading.substring(DECLARATION_PREFIX.length()).strip()
                : "";
        if (!OPERATION.matcher(operation).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_FILE,
                    "invalid binding declaration '## " + heading
                            + "'; expected '## Bind <OperationSymbol>'",
                    declaration.heading().raw().where());
            valid = false;
        }
        boolean outbound = operation.contains(".");

        Map<String, List<Section>> byName = new LinkedHashMap<>();
        for (Section section : sections(declaration.content(), 3)) {
            if (!SECTIONS.contains(section.heading().headingText())) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "unknown HTTP binding section '### "
                                + section.heading().headingText() + "'",
                        section.heading().raw().where());
                valid = false;
                continue;
            }
            byName.computeIfAbsent(section.heading().headingText(), ignored -> new ArrayList<>())
                    .add(section);
        }
        List<String> requiredSections = outbound
                ? List.of("Endpoint", "Request", "Response")
                : List.of("Endpoint", "Access", "Request", "Response");
        for (String required : requiredSections) {
            List<Section> found = byName.getOrDefault(required, List.of());
            if (found.isEmpty()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "binding for '" + operation + "' is missing '### " + required + "'",
                        declaration.heading().raw().where());
                valid = false;
            } else if (found.size() > 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "binding for '" + operation + "' repeats '### " + required + "'",
                        found.get(1).heading().raw().where());
                valid = false;
            }
        }
        if (outbound && byName.containsKey("Access")) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_SECTION,
                    "outbound Integration binding '" + operation
                            + "' cannot declare '### Access'",
                    byName.get("Access").getFirst().heading().raw().where(),
                    "Access controls inbound callers; how this file's calls prove who they "
                            + "are is '## Auth'");
            valid = false;
        }

        Optional<dev.harpia.parse.SpecAst.Endpoint> endpoint = first(byName, "Endpoint")
                .flatMap(section -> paragraph(section, "Endpoint", diagnostics))
                .flatMap(raw -> EndpointParser.parse(raw.text(), raw.where(), diagnostics));
        Optional<dev.harpia.parse.SpecAst.Access> access = outbound
                ? Optional.empty()
                : first(byName, "Access")
                        .flatMap(section -> paragraph(section, "Access", diagnostics))
                        .flatMap(raw -> AccessParser.parse(raw.text(), raw.where(), diagnostics));
        ParseMappings request = first(byName, "Request")
                .map(section -> request(section, diagnostics))
                .orElseGet(() -> new ParseMappings(List.of(), false));
        Optional<BindingAst.ResponseMapping> response = first(byName, "Response")
                .flatMap(section -> response(section, diagnostics));
        valid &= endpoint.isPresent();
        valid &= outbound || access.isPresent();
        valid &= request.valid();
        valid &= response.isPresent();
        if (!valid) {
            return Optional.empty();
        }
        dev.harpia.parse.SpecAst.Endpoint parsed = endpoint.orElseThrow();
        if (outbound) {
            String[] target = operation.split("\\.", 2);
            return Optional.of(new BindingAst.IntegrationHttp(
                    target[0],
                    target[1],
                    new BindingAst.Endpoint(parsed.method(), parsed.path(), parsed.where()),
                    request.mappings(),
                    response.orElseThrow(),
                    declaration.heading().raw().where()));
        }
        return Optional.of(new BindingAst.Http(
                operation,
                new BindingAst.Endpoint(parsed.method(), parsed.path(), parsed.where()),
                new BindingAst.Access(
                        BindingAst.Access.Kind.valueOf(access.orElseThrow().kind().name()),
                        access.orElseThrow().roles()),
                request.mappings(),
                response.orElseThrow(),
                declaration.heading().raw().where()));
    }

    private static ParseMappings request(Section section, DiagnosticCollector diagnostics) {
        List<BlockNode> content = section.content().stream()
                .filter(block -> block.kind() != BlockNode.Kind.HEADING)
                .toList();
        if (content.size() == 1
                && content.getFirst().kind() == BlockNode.Kind.PARAGRAPH
                && content.getFirst().raw().text().strip().equals("none")) {
            return new ParseMappings(List.of(), true);
        }

        List<BindingAst.RequestMapping> mappings = new ArrayList<>();
        boolean valid = !content.isEmpty();
        for (BlockNode block : content) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "'### Request' requires direct mapping list items or 'none'",
                        block.raw().where());
                valid = false;
                continue;
            }
            java.util.regex.Matcher matcher = REQUEST.matcher(block.listItemText().strip());
            if (!matcher.matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "invalid request mapping '" + block.listItemText().strip() + "'",
                        block.raw().where(),
                        "use '<input>: path|query|header <name>' or '<input>: body'");
                valid = false;
                continue;
            }
            String input = matcher.group(1);
            String kind = matcher.group(2);
            String externalName = matcher.group(3);
            if (kind.equals("body") != (externalName == null)) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        kind.equals("body")
                                ? "body mapping does not accept an external name"
                                : kind + " mapping requires an external name",
                        block.raw().where());
                valid = false;
                continue;
            }
            mappings.add(switch (kind) {
                case "path" -> new BindingAst.Path(input, externalName, block.raw().where());
                case "query" -> new BindingAst.Query(input, externalName, block.raw().where());
                case "header" -> new BindingAst.Header(input, externalName, block.raw().where());
                case "body" -> new BindingAst.Body(input, block.raw().where());
                default -> throw new IllegalStateException("unknown request mapping " + kind);
            });
        }
        return new ParseMappings(mappings, valid);
    }

    private static Optional<BindingAst.ResponseMapping> response(
            Section section, DiagnosticCollector diagnostics) {
        Optional<dev.harpia.ast.RawSpan> value = paragraph(section, "Response", diagnostics);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        dev.harpia.ast.RawSpan raw = value.orElseThrow();
        return switch (raw.text().strip()) {
            case "output: body" -> Optional.of(new BindingAst.ResponseBody("output", raw.where()));
            case "none" -> Optional.of(new BindingAst.NoResponse(raw.where()));
            default -> {
                diagnostics.error(
                        ErrorCodes.SYNTAX_BINDING_SECTION,
                        "'### Response' must be 'output: body' or 'none'",
                        raw.where());
                yield Optional.empty();
            }
        };
    }

    private static Optional<dev.harpia.ast.RawSpan> paragraph(
            Section section, String name, DiagnosticCollector diagnostics) {
        List<BlockNode> content = section.content().stream()
                .filter(block -> block.kind() != BlockNode.Kind.HEADING)
                .toList();
        if (content.size() != 1 || content.getFirst().kind() != BlockNode.Kind.PARAGRAPH) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_BINDING_SECTION,
                    "'### " + name + "' requires exactly one paragraph",
                    section.heading().raw().where());
            return Optional.empty();
        }
        return Optional.of(content.getFirst().raw());
    }

    private static Optional<Section> first(Map<String, List<Section>> sections, String name) {
        List<Section> found = sections.getOrDefault(name, List.of());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    private static List<BlockNode> headings(List<BlockNode> blocks, int level) {
        return blocks.stream()
                .filter(block -> block.kind() == BlockNode.Kind.HEADING)
                .filter(block -> block.headingLevel() == level)
                .toList();
    }

    private static List<Section> sections(List<BlockNode> blocks, int level) {
        List<Section> result = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            BlockNode block = blocks.get(index);
            if (block.kind() != BlockNode.Kind.HEADING || block.headingLevel() != level) {
                continue;
            }
            int end = index + 1;
            while (end < blocks.size()) {
                BlockNode candidate = blocks.get(end);
                if (candidate.kind() == BlockNode.Kind.HEADING
                        && candidate.headingLevel() <= level) {
                    break;
                }
                end++;
            }
            result.add(new Section(block, blocks.subList(index + 1, end)));
            index = end - 1;
        }
        return List.copyOf(result);
    }

    private record Section(BlockNode heading, List<BlockNode> content) {
        private Section {
            content = List.copyOf(content);
        }
    }

    private record ParseMappings(List<BindingAst.RequestMapping> mappings, boolean valid) {
        private ParseMappings {
            mappings = List.copyOf(mappings);
        }
    }
}
