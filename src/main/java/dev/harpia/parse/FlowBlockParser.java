package dev.harpia.parse;

import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.FlowStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a {@code flow} block, which is a sequence of statements with optional branches.
 *
 * <p>Indentation is read by {@link IndentedLines}, the same way a Logic block reads it. What
 * differs is what a statement means: a flow has no return analysis and no expressions of its own,
 * so the branch semantics live here rather than being shared.
 */
final class FlowBlockParser {

    private static final int MAX_DEPTH = 2;

    private final List<IndentedLines.Line> lines;
    private final boolean v1Flow;
    private final DiagnosticCollector diagnostics;
    private int cursor;
    private boolean failed;

    private FlowBlockParser(
            List<IndentedLines.Line> lines,
            boolean v1Flow,
            DiagnosticCollector diagnostics) {
        this.lines = lines;
        this.v1Flow = v1Flow;
        this.diagnostics = diagnostics;
    }

    static Optional<List<FlowStatement>> parse(
            List<RawSpan> bodyLines,
            SourceRef blockWhere,
            boolean v1Flow,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(bodyLines, "bodyLines");
        Objects.requireNonNull(blockWhere, "blockWhere");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Optional<List<IndentedLines.Line>> scanned = IndentedLines.scan(
                bodyLines,
                blockWhere,
                ErrorCodes.SYNTAX_LOGIC_INDENT,
                ErrorCodes.SYNTAX_FLOW_COMMAND,
                "a flow block must contain at least one command",
                "the first command of a flow must not be indented",
                "tabs are not allowed inside a flow block; indent with four spaces",
                diagnostics);
        if (scanned.isEmpty()) {
            return Optional.empty();
        }

        FlowBlockParser parser =
                new FlowBlockParser(scanned.orElseThrow(), v1Flow, diagnostics);
        List<FlowStatement> flow = parser.block(0, 0);
        if (parser.failed) {
            return Optional.empty();
        }
        if (parser.cursor < parser.lines.size()) {
            IndentedLines.Line line = parser.lines.get(parser.cursor);
            parser.error(
                    isElse(line) ? "'else' has no matching 'if'" : "unexpected indentation",
                    line.where());
            return Optional.empty();
        }
        return Optional.of(flow);
    }

    private int loops;

    private List<FlowStatement> block(int level, int depth) {
        List<FlowStatement> statements = new ArrayList<>();
        while (cursor < lines.size() && !failed) {
            IndentedLines.Line line = lines.get(cursor);
            if (line.level() < level || isElse(line) && line.level() == level) {
                break;
            }
            if (line.level() > level) {
                error("unexpected indentation", line.where());
                break;
            }
            Optional<FlowStatement> statement = statement(line, level, depth);
            if (statement.isEmpty()) {
                break;
            }
            statements.add(statement.orElseThrow());
        }
        return statements;
    }

    private static final java.util.regex.Pattern FOR_EACH = java.util.regex.Pattern.compile(
            "^for each +([a-z][A-Za-z0-9]*) +in +(\\S.*)$");

    /**
     * {@code for each <item> in <collection>} and its body.
     *
     * <p>One level, and the refusals say so rather than reporting an unknown command: nesting,
     * {@code break}, {@code continue} and an asynchronous form are all things someone will write,
     * and each of them has a reason to be outside this slice that is worth reading.
     */
    private Optional<FlowStatement> forEach(IndentedLines.Line line, int level, int depth) {
        if (!v1Flow) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                    "'for each' in a flow needs harpia.languageVersion 1",
                    line.contentWhere());
            failed = true;
            return Optional.empty();
        }
        if (loops > 0) {
            iterationRefused(
                    "a flow iterates one level; a nested loop describes an algorithm, which "
                            + "belongs in Logic",
                    line);
            return Optional.empty();
        }
        java.util.regex.Matcher matcher = FOR_EACH.matcher(line.text());
        if (!matcher.matches()) {
            error("'for each' must read 'for each <item> in <collection>'", line.where());
            failed = true;
            return Optional.empty();
        }
        String variable = matcher.group(1);
        String text = matcher.group(2).strip();
        if (text.endsWith(" async") || text.equals("async")) {
            iterationRefused(
                    "asynchronous iteration is not in this version; the body runs in the order "
                            + "the collection has",
                    line);
            return Optional.empty();
        }
        Optional<LogicAst.Expression> collection =
                LogicLexer.tokenize(text, LineSyntax.at(line.contentWhere(), line.text(),
                                matcher.start(2)), diagnostics)
                        .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
        if (collection.isEmpty()) {
            failed = true;
            return Optional.empty();
        }
        cursor++;
        loops++;
        List<FlowStatement> body = block(level + 1, depth + 1);
        loops--;
        if (failed) {
            return Optional.empty();
        }
        if (body.isEmpty()) {
            error("'for each' must be followed by commands indented by four spaces", line.where());
            failed = true;
            return Optional.empty();
        }
        return Optional.of(new SpecAst.ForEach(
                variable, text, collection.orElseThrow(), body, line.contentWhere()));
    }

    private void iterationRefused(String message, IndentedLines.Line line) {
        diagnostics.error(ErrorCodes.SEMANTIC_ITERATION, message, line.contentWhere());
        failed = true;
    }

    private Optional<FlowStatement> statement(IndentedLines.Line line, int level, int depth) {
        if (line.text().startsWith("for each")) {
            return forEach(line, level, depth);
        }
        if (loops > 0 && (line.text().equals("break") || line.text().equals("continue"))) {
            iterationRefused(
                    "'" + line.text() + "' is not in this version; a loop body runs for every "
                            + "item of the collection",
                    line);
            return Optional.empty();
        }
        if (!v1Flow && isCall(line)) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                    "'call' in a flow needs harpia.languageVersion 1",
                    line.contentWhere());
            failed = true;
            return Optional.empty();
        }
        for (String guard : List.of("require", "fail")) {
            if (!v1Flow && line.text().startsWith(guard + " ")) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                        "'" + guard + "' in a flow needs harpia.languageVersion 1",
                        line.contentWhere());
                failed = true;
                return Optional.empty();
            }
        }
        if (!line.text().startsWith("if ")) {
            if (isCall(line) && !line.text().endsWith(")")) {
                return multilineCall(line, level);
            }
            cursor++;
            Optional<FlowStatement> parsed =
                    FlowLineParser.parse(line.text(), line.contentWhere(), diagnostics);
            if (parsed.isEmpty()) {
                failed = true;
            }
            return parsed;
        }

        if (!v1Flow) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                    "'if' in a flow needs harpia.languageVersion 1",
                    line.contentWhere());
            failed = true;
            return Optional.empty();
        }

        if (depth >= MAX_DEPTH) {
            // A flow that needs deeper branching is describing an algorithm, and an algorithm
            // belongs in Logic where it can be named, typed and tested on its own.
            error("a flow nests at most " + MAX_DEPTH + " levels of 'if'", line.where());
            failed = true;
            return Optional.empty();
        }

        String text = line.text().substring("if ".length()).strip();
        Optional<LogicAst.Expression> condition =
                LogicLexer.tokenize(text, line.contentWhere(), diagnostics)
                        .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
        if (condition.isEmpty()) {
            failed = true;
            return Optional.empty();
        }
        cursor++;
        List<FlowStatement> whenTrue = block(level + 1, depth + 1);
        if (failed) {
            return Optional.empty();
        }
        if (whenTrue.isEmpty()) {
            error("'if' must be followed by commands indented by four spaces", line.where());
            return Optional.empty();
        }

        List<FlowStatement> whenFalse = List.of();
        if (cursor < lines.size() && isElse(lines.get(cursor))
                && lines.get(cursor).level() == level) {
            IndentedLines.Line elseLine = lines.get(cursor);
            cursor++;
            whenFalse = block(level + 1, depth + 1);
            if (failed) {
                return Optional.empty();
            }
            if (whenFalse.isEmpty()) {
                error("'else' must be followed by commands indented by four spaces",
                        elseLine.where());
                return Optional.empty();
            }
        }
        return Optional.of(new SpecAst.Conditional(
                text, condition.orElseThrow(), whenTrue, whenFalse, line.contentWhere()));
    }

    /** Collapses the readable multi-line call form into the same closed line grammar. */
    private Optional<FlowStatement> multilineCall(IndentedLines.Line header, int level) {
        StringBuilder source = new StringBuilder(header.text());
        cursor++;
        while (cursor < lines.size()) {
            IndentedLines.Line line = lines.get(cursor);
            if (line.level() == level && line.text().equals(")")) {
                source.append(')');
                cursor++;
                Optional<FlowStatement> parsed = FlowLineParser.parse(
                        source.toString(), header.contentWhere(), diagnostics);
                if (parsed.isEmpty()) {
                    failed = true;
                }
                return parsed;
            }
            if (line.level() != level + 1) {
                error(
                        "call arguments must be indented by four spaces and ')' must align with "
                                + "the call",
                        line.where());
                return Optional.empty();
            }
            source.append(' ').append(line.text());
            cursor++;
        }
        error("call is missing its closing ')'", header.where());
        return Optional.empty();
    }

    private static boolean isCall(IndentedLines.Line line) {
        return line.text().startsWith("call ") || line.text().matches(
                "[a-z][A-Za-z0-9]* += +call +.*");
    }

    private static boolean isElse(IndentedLines.Line line) {
        return line.text().equals("else");
    }

    private void error(String message, SourceRef where) {
        if (failed) {
            return;
        }
        failed = true;
        diagnostics.error(ErrorCodes.SYNTAX_FLOW_COMMAND, message, where);
    }
}
