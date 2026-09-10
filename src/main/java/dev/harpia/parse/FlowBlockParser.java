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
    private final boolean conditionalFlow;
    private final DiagnosticCollector diagnostics;
    private int cursor;
    private boolean failed;

    private FlowBlockParser(
            List<IndentedLines.Line> lines,
            boolean conditionalFlow,
            DiagnosticCollector diagnostics) {
        this.lines = lines;
        this.conditionalFlow = conditionalFlow;
        this.diagnostics = diagnostics;
    }

    static Optional<List<FlowStatement>> parse(
            List<RawSpan> bodyLines,
            SourceRef blockWhere,
            boolean conditionalFlow,
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
                new FlowBlockParser(scanned.orElseThrow(), conditionalFlow, diagnostics);
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

    private Optional<FlowStatement> statement(IndentedLines.Line line, int level, int depth) {
        if (!line.text().startsWith("if ")) {
            cursor++;
            Optional<FlowStatement> parsed =
                    FlowLineParser.parse(line.text(), line.contentWhere(), diagnostics);
            if (parsed.isEmpty()) {
                failed = true;
            }
            return parsed;
        }

        if (!conditionalFlow) {
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
