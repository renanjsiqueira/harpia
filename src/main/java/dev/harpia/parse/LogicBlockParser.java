package dev.harpia.parse;

import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.LogicAst.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Offside-rule parser for the body of a {@code logic} block. Indentation is exactly four spaces per
 * level, tabs are rejected and a statement always occupies one line: there is no line continuation,
 * because an expression that does not fit on a line should be named instead.
 */
public final class LogicBlockParser {

    private static final int INDENT = 4;
    private static final int MAX_DEPTH = 3;

    private final List<Line> lines;
    private final DiagnosticCollector diagnostics;
    private int cursor;
    private boolean failed;

    private LogicBlockParser(List<Line> lines, DiagnosticCollector diagnostics) {
        this.lines = lines;
        this.diagnostics = diagnostics;
    }

    public static Optional<List<Statement>> parse(
            List<RawSpan> bodyLines, SourceRef blockWhere, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(bodyLines, "bodyLines");
        Objects.requireNonNull(blockWhere, "blockWhere");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<Line> lines = new ArrayList<>();
        boolean valid = true;
        for (RawSpan span : bodyLines) {
            String text = span.text().stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            int tab = text.indexOf('\t');
            if (tab >= 0) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_LOGIC_INDENT,
                        "tabs are not allowed inside a logic block; indent with four spaces",
                        LineSyntax.at(span.where(), text, tab));
                valid = false;
                continue;
            }
            int indent = 0;
            while (indent < text.length() && text.charAt(indent) == ' ') {
                indent++;
            }
            if (indent % INDENT != 0) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_LOGIC_INDENT,
                        "indentation must be a multiple of four spaces but was " + indent,
                        span.where());
                valid = false;
                continue;
            }
            lines.add(new Line(indent / INDENT, text.substring(indent), span, indent));
        }
        if (!valid) {
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                    "a logic block must contain at least one statement",
                    blockWhere);
            return Optional.empty();
        }
        if (lines.getFirst().level() != 0) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_LOGIC_INDENT,
                    "the first statement of a logic block must not be indented",
                    lines.getFirst().where());
            return Optional.empty();
        }

        LogicBlockParser parser = new LogicBlockParser(lines, diagnostics);
        List<Statement> statements = parser.block(0, 0);
        if (parser.failed) {
            return Optional.empty();
        }
        if (parser.cursor < lines.size()) {
            Line line = lines.get(parser.cursor);
            parser.error(
                    ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                    line.isElse()
                            ? "'else' has no matching 'if'"
                            : "unexpected indentation",
                    line.where());
            return Optional.empty();
        }
        return Optional.of(statements);
    }

    private List<Statement> block(int level, int depth) {
        List<Statement> statements = new ArrayList<>();
        while (cursor < lines.size() && !failed) {
            Line line = lines.get(cursor);
            if (line.level() < level || line.isElse() && line.level() == level) {
                break;
            }
            if (line.level() > level) {
                error(ErrorCodes.SYNTAX_LOGIC_INDENT, "unexpected indentation", line.where());
                break;
            }
            Optional<Statement> statement = statement(line, level, depth);
            if (statement.isEmpty()) {
                break;
            }
            statements.add(statement.orElseThrow());
        }
        return statements;
    }

    private Optional<Statement> statement(Line line, int level, int depth) {
        Optional<List<LogicToken>> lexed =
                LogicLexer.tokenize(line.text(), line.contentWhere(), diagnostics);
        if (lexed.isEmpty()) {
            failed = true;
            return Optional.empty();
        }
        List<LogicToken> tokens = lexed.orElseThrow();
        LogicToken first = tokens.getFirst();

        if (first.is(LogicToken.Kind.EFFECT)) {
            error(
                    ErrorCodes.SEMANTIC_LOGIC_SIDE_EFFECT,
                    LogicExpressionParser.sideEffectMessage(first),
                    first.where());
            return Optional.empty();
        }
        if (first.is(LogicToken.Kind.ELSE)) {
            error(ErrorCodes.SYNTAX_LOGIC_STATEMENT, "'else' has no matching 'if'", first.where());
            return Optional.empty();
        }
        if (first.is(LogicToken.Kind.IF)) {
            return conditional(tokens, line, level, depth);
        }
        if (first.is(LogicToken.Kind.RETURN)) {
            cursor++;
            return expression(tokens.subList(1, tokens.size()), first, "return")
                    .map(value -> new LogicAst.Return(value, first.where()));
        }
        if (first.is(LogicToken.Kind.IDENTIFIER)
                && tokens.size() > 1
                && tokens.get(1).is(LogicToken.Kind.ASSIGN)) {
            cursor++;
            return expression(tokens.subList(2, tokens.size()), tokens.get(1), "=")
                    .map(value -> new LogicAst.Assignment(first.text(), value, first.where()));
        }

        error(
                ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                "expected an assignment, 'if' or 'return' but found '" + first.text() + "'",
                first.where());
        return Optional.empty();
    }

    private Optional<Statement> conditional(
            List<LogicToken> tokens, Line line, int level, int depth) {
        if (depth + 1 > MAX_DEPTH) {
            error(
                    ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                    "logic nesting is limited to " + MAX_DEPTH
                            + " levels; extract a Logic or use a Decision",
                    tokens.getFirst().where());
            return Optional.empty();
        }
        Optional<LogicAst.Expression> condition =
                expression(tokens.subList(1, tokens.size()), tokens.getFirst(), "if");
        if (condition.isEmpty()) {
            return Optional.empty();
        }
        cursor++;

        List<Statement> thenBranch = branch(level, depth, line, "if");
        if (failed) {
            return Optional.empty();
        }

        Optional<List<Statement>> elseBranch = Optional.empty();
        if (cursor < lines.size()
                && lines.get(cursor).level() == level
                && lines.get(cursor).isElse()) {
            Line elseLine = lines.get(cursor);
            if (!elseLine.text().equals("else")) {
                error(
                        ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                        "'else' must be alone on its line",
                        elseLine.where());
                return Optional.empty();
            }
            cursor++;
            List<Statement> statements = branch(level, depth, elseLine, "else");
            if (failed) {
                return Optional.empty();
            }
            elseBranch = Optional.of(statements);
        }
        return Optional.of(new LogicAst.Conditional(
                condition.orElseThrow(), thenBranch, elseBranch, tokens.getFirst().where()));
    }

    private List<Statement> branch(int level, int depth, Line owner, String keyword) {
        if (cursor >= lines.size() || lines.get(cursor).level() != level + 1) {
            error(
                    ErrorCodes.SYNTAX_LOGIC_INDENT,
                    "'" + keyword + "' must be followed by statements indented by four spaces",
                    owner.where());
            return List.of();
        }
        List<Statement> statements = block(level + 1, depth + 1);
        if (!failed && statements.isEmpty()) {
            error(
                    ErrorCodes.SYNTAX_LOGIC_STATEMENT,
                    "'" + keyword + "' must not have an empty body",
                    owner.where());
        }
        return statements;
    }

    private Optional<LogicAst.Expression> expression(
            List<LogicToken> tokens, LogicToken keyword, String after) {
        if (tokens.size() <= 1) {
            error(
                    ErrorCodes.SYNTAX_LOGIC_EXPRESSION,
                    "expected an expression after '" + after + "'",
                    keyword.where());
            return Optional.empty();
        }
        Optional<LogicAst.Expression> value =
                LogicExpressionParser.parse(tokens, diagnostics);
        if (value.isEmpty()) {
            failed = true;
        }
        return value;
    }

    private void error(String code, String message, SourceRef where) {
        if (failed) {
            return;
        }
        failed = true;
        diagnostics.error(code, message, where);
    }

    private record Line(int level, String text, RawSpan span, int indent) {

        private SourceRef where() {
            return contentWhere();
        }

        private SourceRef contentWhere() {
            return LineSyntax.at(span.where(), span.text(), indent);
        }

        private boolean isElse() {
            return text.equals("else") || text.startsWith("else ");
        }
    }
}
