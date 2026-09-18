package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The S0 artifacts of Harpia Core V1, checked against the things they claim about.
 *
 * <p>A planning document is the easiest kind of file to let drift: nothing executes it, so a status
 * that stopped being true, a test selector that was renamed and a refusal that no longer happens
 * all survive indefinitely. These tests take the three artifacts of S0 and confront each claim with
 * the source it came from — the backlog for status, the test tree for evidence, and the compiler
 * itself for every refusal the spike recorded.
 *
 * <p>What they deliberately do not do is judge the architecture. Whether the contracts in the RFC
 * are the right ones is a review by a person; what is checked here is that each contract actually
 * carries the five parts it promises, and that no code it reserves collides with one already in
 * use.
 */
class CoreV1PlanningTest {

    private static final Path BACKLOG = Path.of("BACKLOG.md");
    private static final Path INVENTORY = Path.of(".design/mvp-core-v1-inventory.md");
    private static final Path SPIKE = Path.of(".design/mvp-core-v1-spike.md");
    private static final Path CONTRACTS = Path.of(".design/mvp-core-v1-contracts.md");
    private static final Path TESTS = Path.of("src/test/java");

    /** The fourteen open records the plan is accountable for, in the plan's own order. */
    private static final List<String> OPEN_RECORDS = List.of(
            "CORE-010", "FLOW-014", "FLOW-015", "FLOW-020", "JAVA-004", "CMD-008", "PERSIST-008",
            "RELY-001", "EVENT-003", "EVENT-005", "SPRING-008", "CUSTOM-002", "CUSTOM-003",
            "GREEN-003");

    /** The four cases TG-01 asked the spike to run. Extra cases are welcome; these are required. */
    private static final List<String> REQUIRED_SPIKE_CASES =
            List.of("input", "member-access", "command-result", "owned");

    private static final Pattern SELECTOR = Pattern.compile("`([A-Z][A-Za-z0-9]*Test)#([a-z][A-Za-z0-9]*)`");

    @TempDir
    Path projectRoot;

    /**
     * C1 — the inventory separates what the backlog says from what the tree already proves.
     *
     * <p>The interesting column is the middle one. Four of these records describe as missing
     * something that is implemented and tested today, and a builder who trusts the description
     * rebuilds it. So every selector cited as existing evidence has to exist, and every canonical
     * status has to be the one the backlog actually carries — otherwise the document would be free
     * to invent a reconciliation nobody can check.
     */
    @Test
    void inventoryDistinguishesCanonicalStatusFromExistingEvidence() {
        Map<String, Row> rows = inventoryRows();

        assertThat(rows.keySet())
                .as("the inventory covers the fourteen open records, no more and no fewer")
                .containsExactlyInAnyOrderElementsOf(OPEN_RECORDS);

        String backlog = read(BACKLOG);
        for (String id : OPEN_RECORDS) {
            Row row = rows.get(id);
            assertThat(row.status())
                    .as("%s: no record is declared DONE by a planning document", id)
                    .isIn("PARTIAL", "TODO");
            assertThat(canonicalStatus(backlog, id))
                    .as("%s: the inventory copies the canonical status rather than restating it", id)
                    .isEqualTo(row.status());
            assertThat(backlogEntry(backlog, id))
                    .as("%s: an open record keeps its unchecked box in the backlog", id)
                    .startsWith("- [ ] ");
            assertThat(row.remaining())
                    .as("%s: an open record says what is effectively left", id)
                    .isNotBlank();
            assertThat(row.slices())
                    .as("%s: every open record is owned by at least one slice", id)
                    .isNotEmpty()
                    .allSatisfy(slice -> assertThat(slice).matches("S[0-8]"));
        }

        List<String> cited = rows.values().stream()
                .flatMap(row -> selectors(row.evidence()).stream())
                .distinct()
                .toList();
        assertThat(cited)
                .as("the reconciliation rests on evidence that exists, so it has to cite some")
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(cited).allSatisfy(CoreV1PlanningTest::assertSelectorExists);
    }

    /**
     * C2 — every case in the spike report is recompiled, and the report's own table is the answer
     * key.
     *
     * <p>A spike is worth exactly what its observations are worth. Here the fixture is inside the
     * document, so a refusal that stops happening — because the gap was closed, or because someone
     * wrote down a code the compiler never produced — fails this test instead of quietly becoming
     * folklore that the next slice builds on.
     */
    @Test
    void spikeRecordsInputsObservedResultsAndOwningSlices() throws IOException {
        List<Case> cases = spikeCases();

        assertThat(cases).extracting(Case::id)
                .as("the four cases TG-01 asked for are all present")
                .containsAll(REQUIRED_SPIKE_CASES);

        Map<String, TableRow> table = spikeTable();
        assertThat(table.keySet())
                .as("the required cases are described in the report's table, not only fenced")
                .containsAll(REQUIRED_SPIKE_CASES);
        for (String id : REQUIRED_SPIKE_CASES) {
            assertThat(table.get(id).slice())
                    .as("%s: the case names the slice that owns the gap", id)
                    .matches("S[0-8].*");
        }

        for (Case fixture : cases) {
            write(fixture);
            List<String> observed = new HarpiaCompiler()
                    .compile(new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE))
                    .diagnostics().stream()
                    .map(Diagnostic::code)
                    .toList();
            if (fixture.expected().equals("ok")) {
                assertThat(observed)
                        .as("spike case '%s' is recorded as accepted today", fixture.id())
                        .isEmpty();
            } else {
                assertThat(observed)
                        .as("spike case '%s' produces what the report says it produces today",
                                fixture.id())
                        .contains(fixture.expected());
            }
            TableRow row = table.get(fixture.id());
            if (row == null) {
                continue;
            }
            if (row.open()) {
                assertThat(row.observed())
                        .as("case '%s' is recorded as open, so the S0 refusal still happens",
                                fixture.id())
                        .isEqualTo(fixture.expected());
                assertThat(fixture.open())
                        .as("case '%s': the table and the fixture agree the gap is open",
                                fixture.id())
                        .isTrue();
            } else {
                assertThat(row.observed())
                        .as("case '%s' was closed, so what it produces now differs from S0",
                                fixture.id())
                        .isNotEqualTo(fixture.expected());
                assertThat(row.state())
                        .as("a closed gap names the slice that closed it and what replaced the "
                                + "refusal")
                        .matches(".*S[1-8].*(HRP[1-7][0-9]{3}|aceito).*");
                assertThat(fixture.open())
                        .as("case '%s': the table and the fixture agree the gap is closed",
                                fixture.id())
                        .isFalse();
            }
        }
    }

    /**
     * C3 — each new contract carries a literal form, both examples, a mechanism and the alternative
     * it rejected.
     *
     * <p>The rejected alternative is the part that only exists while the choice is still open, so
     * it is checked like the rest. The codes get the same treatment from the other direction: a
     * code the RFC reuses must already mean something, and a code it reserves must be free —
     * reserving one that is already taken would silently redefine a published refusal.
     */
    @Test
    void everyNewContractHasLiteralExamplesAndMechanism() {
        String document = read(CONTRACTS);
        List<String> contracts = sections(document, "## Contrato ");
        assertThat(contracts)
                .as("Flow, transação, falha outbound e evento — os quatro contratos de AC 3")
                .hasSize(4);

        List<String> parts = List.of(
                "### Forma literal",
                "### Exemplo válido",
                "### Exemplo recusado",
                "### Mecanismo",
                "### Alternativa rejeitada");
        for (String contract : contracts) {
            String title = contract.lines().findFirst().orElse("");
            for (String part : parts) {
                assertThat(body(contract, part))
                        .as("%s: '%s' is present and says something", title, part)
                        .isNotBlank();
            }
            assertThat(body(contract, "### Forma literal"))
                    .as("%s: the literal form is shown, not described", title)
                    .contains("```");
            assertThat(body(contract, "### Exemplo válido"))
                    .as("%s: the valid example is a block someone can copy", title)
                    .contains("```");
            assertThat(body(contract, "### Exemplo recusado"))
                    .as("%s: a refusal names the code it produces", title)
                    .containsPattern("HRP[1-7][0-9]{3}");
        }

        Set<String> declared = declaredCodes();
        Set<String> reserved = reservedCodes(document);
        assertThat(reserved)
                .as("the RFC reserves the codes its refusals need")
                .isNotEmpty();
        assertThat(declared)
                .as("a reserved code is a free number; reserving a taken one redefines a refusal")
                .doesNotContainAnyElementsOf(reserved);
        for (String code : codesIn(document)) {
            if (reserved.contains(code)) {
                continue;
            }
            assertThat(declared)
                    .as("%s is reused by the RFC, so it already has to mean something", code)
                    .contains(code);
        }
    }

    /**
     * C4 — the local event provider does not drag Next into the Core through an old dependency.
     *
     * <p>Two Core records depended on items that live upstream. Nobody decided that; it was
     * inherited. The correction belongs in the backlog before any work of those dependencies runs,
     * so this test reads the backlog rather than the plan: what the Core depends on is what the
     * canonical record says it depends on.
     */
    @Test
    void localEventsDoNotPromoteUpstreamDependencies() {
        String backlog = read(BACKLOG);

        assertThat(dependencies(backlog, "EVENT-005"))
                .as("local delivery needs the emission, not the declarative consumer")
                .contains("EVENT-003")
                .doesNotContain("EVENT-004");
        assertThat(dependencies(backlog, "GREEN-003"))
                .as("the integrated baseline needs the local event, not distributed messaging")
                .contains("EVENT-005")
                .doesNotContain("MSG-001");

        int next = backlog.indexOf("\n# Harpia Next / Upstream");
        assertThat(next).as("the backlog still has a Next horizon to keep things in").isPositive();
        String inventory = read(INVENTORY);
        for (String id : List.of("DOM-015", "DOM-016", "EVENT-004", "MSG-001")) {
            assertThat(backlog.indexOf("- [ ] `" + id + "`"))
                    .as("%s is declared after the Core horizon ends", id)
                    .isGreaterThan(next);
            assertThat(inventory)
                    .as("%s is named as staying out of the Core", id)
                    .contains("`" + id + "`");
        }
    }

    // --- inventory ---------------------------------------------------------------------------

    /** One row of the inventory table, keyed by the record it reconciles. */
    private record Row(String status, String evidence, String remaining, List<String> slices) {
    }

    private static Map<String, Row> inventoryRows() {
        Map<String, Row> rows = new LinkedHashMap<>();
        Pattern id = Pattern.compile("^\\| `([A-Z]+-[0-9]+)` \\|");
        for (String line : read(INVENTORY).split("\n")) {
            Matcher matcher = id.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 6) {
                continue;
            }
            rows.put(
                    matcher.group(1),
                    new Row(
                            columns[2].replace("`", "").strip(),
                            columns[3].strip(),
                            columns[4].strip(),
                            Stream.of(columns[5].split(","))
                                    .map(String::strip)
                                    .filter(slice -> !slice.isEmpty())
                                    .toList()));
        }
        return rows;
    }

    private static List<String> selectors(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = SELECTOR.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1) + "#" + matcher.group(2));
        }
        return found;
    }

    /** A cited proof has to be findable by the name it was cited under. */
    private static void assertSelectorExists(String selector) {
        String type = selector.substring(0, selector.indexOf('#'));
        String method = selector.substring(selector.indexOf('#') + 1);
        Optional<Path> file = find(type + ".java");
        assertThat(file).as("the test class %s exists", type).isPresent();
        assertThat(read(file.orElseThrow()))
                .as("%s declares %s", type, method)
                .containsPattern("\\b" + Pattern.quote(method) + "\\s*\\(");
    }

    private static Optional<Path> find(String fileName) {
        try (Stream<Path> tree = Files.walk(TESTS)) {
            return tree.filter(path -> path.getFileName().toString().equals(fileName)).findFirst();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // --- backlog -----------------------------------------------------------------------------

    private static String backlogEntry(String backlog, String id) {
        return backlog.lines()
                .filter(line -> line.startsWith("- [") && line.contains("`" + id + "`"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no backlog entry for " + id));
    }

    /** The status token of a record, which is the first backticked word after its title. */
    private static String canonicalStatus(String backlog, String id) {
        Matcher matcher = Pattern.compile("`(DONE|PARTIAL|TODO|BLOCKED|RESEARCH)`")
                .matcher(backlogEntry(backlog, id));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String dependencies(String backlog, String id) {
        List<String> lines = backlog.lines().toList();
        int start = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith("- [") && lines.get(index).contains("`" + id + "`")) {
                start = index;
                break;
            }
        }
        assertThat(start).as("no backlog entry for %s", id).isNotNegative();
        for (int index = start + 1; index < lines.size() && lines.get(index).startsWith("  "); index++) {
            if (lines.get(index).strip().startsWith("- Depends on:")) {
                return lines.get(index);
            }
        }
        throw new AssertionError(id + " declares no dependencies to reconcile");
    }

    // --- spike -------------------------------------------------------------------------------

    /** One executable case: its id, its whole project, what it produces now, and whether the gap is open. */
    private record Case(String id, String spec, String expected, boolean open) {
    }

    /** The row of the report's table for one case, which has to agree with the fixture. */
    private record TableRow(String observed, String state, String slice) {

        boolean open() {
            return state.startsWith("aberto");
        }
    }

    private static List<Case> spikeCases() {
        List<Case> cases = new ArrayList<>();
        List<String> spec = null;
        String id = "";
        String expected = "";
        boolean open = true;
        for (String line : read(SPIKE).split("\n", -1)) {
            if (spec == null) {
                if (line.startsWith("````harpia")) {
                    id = attribute(line, "case");
                    expected = attribute(line, "expect");
                    open = !attribute(line, "state").equals("closed");
                    spec = new ArrayList<>();
                }
                continue;
            }
            if (line.startsWith("````")) {
                cases.add(new Case(id, String.join("\n", spec) + "\n", expected, open));
                spec = null;
                continue;
            }
            spec.add(line);
        }
        if (spec != null) {
            throw new AssertionError("unterminated fixture in " + SPIKE + " under '" + id + "'");
        }
        return cases;
    }

    private static String attribute(String info, String name) {
        Matcher matcher = Pattern.compile(name + "=([^\\s]+)").matcher(info);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** Reads the table under the TG-01 heading, where each row is `| case | ... | code | slice |`. */
    private static Map<String, TableRow> spikeTable() {
        Map<String, TableRow> rows = new LinkedHashMap<>();
        String section = body(read(SPIKE), "## Casos exigidos por TG-01");
        for (String line : section.split("\n")) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 6) {
                continue;
            }
            Matcher code = Pattern.compile("HRP[1-7][0-9]{3}").matcher(columns[3]);
            Matcher id = Pattern.compile("`([a-z-]+)`").matcher(columns[1]);
            if (!id.find()) {
                continue;
            }
            rows.put(
                    id.group(1),
                    new TableRow(
                            code.find() ? code.group() : "",
                            columns[4].strip(),
                            columns[6].strip()));
        }
        return rows;
    }

    /**
     * Writes a case as a project.
     *
     * <p>Two of the four cases need a second entity to be about, so an HTML comment naming a
     * project-relative path splits the fixture — the same convention the catalogue uses, invisible
     * when the report is read and unambiguous when it is compiled.
     */
    private void write(Case fixture) throws IOException {
        try (Stream<Path> tree = Files.walk(projectRoot.resolve("specs"))) {
            for (Path path : tree.filter(Files::isRegularFile).toList()) {
                Files.delete(path);
            }
        } catch (IOException ignored) {
            Files.createDirectories(projectRoot.resolve("specs"));
        }
        Files.createDirectories(projectRoot.resolve("specs"));
        String name = "specs/case.harpia.md";
        StringBuilder content = new StringBuilder();
        for (String line : fixture.spec().split("\n", -1)) {
            if (line.startsWith("<!-- ") && line.endsWith(".harpia.md -->")) {
                writeFile(name, content.toString());
                content.setLength(0);
                name = line.substring("<!-- ".length(), line.length() - " -->".length());
                continue;
            }
            content.append(line).append('\n');
        }
        writeFile(name, content.toString());
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: commerce-service
                  group: com.example
                  artifact: commerce-service
                  package: com.example.commerce

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.6"

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """, StandardCharsets.UTF_8);
    }

    private void writeFile(String path, String content) throws IOException {
        Path file = projectRoot.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // --- contracts ---------------------------------------------------------------------------

    /** Every code the catalogue of diagnostics already publishes, read from the constants. */
    private static Set<String> declaredCodes() {
        Set<String> codes = new TreeSet<>();
        for (Field field : ErrorCodes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(exception);
                }
            }
        }
        return codes;
    }

    /** The codes the RFC reserves, read from its reservation table rather than from prose. */
    private static Set<String> reservedCodes(String document) {
        Set<String> codes = new TreeSet<>();
        Matcher matcher = Pattern.compile("^\\| `(HRP[1-7][0-9]{3})` \\| `([A-Z_]+)` \\|",
                Pattern.MULTILINE).matcher(document);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static Set<String> codesIn(String text) {
        Set<String> codes = new TreeSet<>();
        Matcher matcher = Pattern.compile("HRP[1-7][0-9]{3}").matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group());
        }
        return codes;
    }

    // --- markdown ----------------------------------------------------------------------------

    /**
     * The chunks that start with the given heading prefix, each up to the next heading of its
     * level.
     *
     * <p>A heading inside a fenced block is an example of a heading, not one. These documents show
     * whole Markdown declarations — {@code ## Command CreateOrder} lives inside more than one
     * example — so a splitter that reads those as structure cuts a contract in half and then
     * reports the half as missing its parts.
     */
    private static List<String> sections(String document, String prefix) {
        String level = prefix.strip().split(" ")[0] + " ";
        List<String> sections = new ArrayList<>();
        StringBuilder current = null;
        boolean fenced = false;
        for (String line : document.split("\n", -1)) {
            if (line.startsWith("```")) {
                fenced = !fenced;
            } else if (!fenced) {
                if (line.startsWith(prefix)) {
                    if (current != null) {
                        sections.add(current.toString());
                    }
                    current = new StringBuilder();
                } else if (current != null && line.startsWith(level)) {
                    sections.add(current.toString());
                    current = null;
                }
            }
            if (current != null) {
                current.append(line).append('\n');
            }
        }
        if (current != null) {
            sections.add(current.toString());
        }
        return sections;
    }

    /** What a heading says, up to the next heading of the same or a higher level outside a fence. */
    private static String body(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int level = heading.indexOf(' ');
        String[] lines = document.substring(start + heading.length()).split("\n", -1);
        StringBuilder body = new StringBuilder();
        boolean fenced = false;
        for (String line : lines) {
            if (line.startsWith("```")) {
                fenced = !fenced;
            }
            if (!fenced) {
                int hashes = 0;
                while (hashes < line.length() && line.charAt(hashes) == '#') {
                    hashes++;
                }
                if (hashes > 0 && hashes <= level && line.length() > hashes
                        && line.charAt(hashes) == ' ') {
                    break;
                }
            }
            body.append(line).append('\n');
        }
        return body.toString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
