package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Call;
import dev.harpia.parse.SpecAst.CreateFrom;
import dev.harpia.parse.SpecAst.Delete;
import dev.harpia.parse.SpecAst.Fail;
import dev.harpia.parse.SpecAst.FindBy;
import dev.harpia.parse.SpecAst.FlowStatement;
import dev.harpia.parse.SpecAst.ListAll;
import dev.harpia.parse.SpecAst.ListBy;
import dev.harpia.parse.SpecAst.LoadById;
import dev.harpia.parse.SpecAst.Require;
import dev.harpia.parse.SpecAst.Return;
import dev.harpia.parse.SpecAst.Save;
import dev.harpia.parse.SpecAst.SetField;
import dev.harpia.parse.SpecAst.UpdateFrom;
import dev.harpia.parse.SpecAst.ValidateInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed line grammar for Flow commands. */
public final class FlowLineParser {

    private static final String VARIABLE = "([a-z][A-Za-z0-9]*)";
    private static final String ENTITY = "([A-Z][A-Za-z0-9]*)";
    private static final Pattern CREATE = Pattern.compile(
            "^" + VARIABLE + " +\\= +create +" + ENTITY + " +from +input$");
    private static final Pattern LOAD = Pattern.compile(
            "^" + VARIABLE + " +\\= +load +" + ENTITY + " +by +id$");
    private static final Pattern FIND = Pattern.compile(
            "^" + VARIABLE + " +\\= +find +" + ENTITY + " +by +([a-z][A-Za-z0-9]*)$");
    private static final Pattern UPDATE = Pattern.compile("^update +" + VARIABLE + " +from +input$");
    private static final String SORT = "(?: +sorted +by +([a-z][A-Za-z0-9]*(?: +(?:asc|desc))?"
            + "(?: +and +[a-z][A-Za-z0-9]*(?: +(?:asc|desc))?)*))?";
    private static final String PAGED = "( +paged)?";
    private static final Pattern LIST =
            Pattern.compile("^" + VARIABLE + " +\\= +list +" + ENTITY + SORT + PAGED + "$");
    private static final Pattern LIST_BY = Pattern.compile(
            "^" + VARIABLE + " +\\= +list +" + ENTITY
                    + " +by +([a-z][A-Za-z0-9]*(?: +and +[a-z][A-Za-z0-9]*)*)" + SORT + PAGED + "$");
    private static final Pattern SET = Pattern.compile(
            "^set +" + VARIABLE + "\\.([a-z][A-Za-z0-9]*) += +(\\S.*)$");
    private static final Pattern ADD = Pattern.compile(
            "^add +(\\S.*?) +to +" + VARIABLE + "\\.([a-z][A-Za-z0-9]*)$");
    private static final Pattern REMOVE = Pattern.compile(
            "^remove +(\\S.*?) +from +" + VARIABLE + "\\.([a-z][A-Za-z0-9]*)$");
    private static final Pattern SAVE = Pattern.compile("^save +" + VARIABLE + "$");
    private static final Pattern DELETE = Pattern.compile("^delete +" + VARIABLE + "$");
    private static final Pattern RETURN = Pattern.compile("^return +(nothing|[a-z][A-Za-z0-9]*)$");
    private static final Pattern FAIL = Pattern.compile(
            "^fail +([a-z]+(?: [a-z]+)*?) +when +(\\S.*)$");
    private static final Pattern REQUIRE = Pattern.compile(
            "^require +(\\S.*?) +otherwise +([a-z]+(?: [a-z]+)*)$");
    private static final Pattern CALL = Pattern.compile(
            "^(?:" + VARIABLE + " +\\= +)?call +" + ENTITY
                    + "(?:\\." + ENTITY + ")?\\((.*)\\)$");
    private static final Pattern CALL_ARGUMENT = Pattern.compile(
            "^([a-z][A-Za-z0-9]*) += +(\\S.*)$");

    private FlowLineParser() {
    }

    /**
     * Where an expression written inside a flow line begins.
     *
     * <p>The tokens of an expression carry their position, and a diagnostic about a name points at
     * the token. Handing the lexer the line's own position would make every name in the line
     * report at its first column, so a reader is told the line and then has to find the word.
     */
    private static SourceRef fragment(SourceRef where, String line, int offset) {
        return LineSyntax.at(where, line, offset);
    }

    private static Optional<FlowStatement> collectionChange(
            SpecAst.CollectionChange change,
            Matcher matcher,
            String line,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        String text = matcher.group(1).strip();
        String variable = matcher.group(2);
        String field = matcher.group(3);
        SourceRef at = fragment(where, line, matcher.start(1));
        return LogicLexer.tokenize(text, at, diagnostics)
                .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics))
                .map(element -> new SpecAst.ChangeCollection(
                        change, variable, field, text, element, where));
    }

    /** {@code sorted by priority desc and name} in declaration order; ascending when unsaid. */
    private static List<SpecAst.SortOrder> sortOrders(String clause) {
        if (clause == null) {
            return List.of();
        }
        List<SpecAst.SortOrder> orders = new java.util.ArrayList<>();
        for (String term : clause.split(" +and +")) {
            String[] parts = term.strip().split(" +");
            orders.add(new SpecAst.SortOrder(
                    parts[0], parts.length > 1 && parts[1].equals("desc")));
        }
        return List.copyOf(orders);
    }

    public static Optional<FlowStatement> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        String line = raw.strip();
        if (line.equals("validate input")) {
            return Optional.of(new ValidateInput(where));
        }
        Matcher matcher = CALL.matcher(line);
        if (matcher.matches()) {
            // `matcher` is reassigned below, so the groups are read before the lambda captures.
            Optional<String> target = Optional.ofNullable(matcher.group(1));
            String name = matcher.group(2);
            Optional<String> qualifier = Optional.ofNullable(matcher.group(3));
            Optional<List<LogicAst.NamedArgument>> arguments = callArguments(
                    matcher.group(4),
                    fragment(where, line, matcher.start(4)),
                    where,
                    diagnostics);
            return arguments.map(values -> new Call(target, name, qualifier, values, where));
        }
        matcher = CREATE.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new CreateFrom(matcher.group(1), matcher.group(2), where));
        }
        matcher = LOAD.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new LoadById(matcher.group(1), matcher.group(2), where));
        }
        matcher = FIND.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new FindBy(
                    matcher.group(1), matcher.group(2), matcher.group(3), where));
        }
        matcher = UPDATE.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new UpdateFrom(matcher.group(1), where));
        }
        matcher = LIST.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new ListAll(
                    matcher.group(1),
                    matcher.group(2),
                    sortOrders(matcher.group(3)),
                    matcher.group(4) != null,
                    where));
        }
        matcher = SET.matcher(line);
        if (matcher.matches()) {
            String variable = matcher.group(1);
            String field = matcher.group(2);
            String text = matcher.group(3).strip();
            SourceRef at = fragment(where, line, matcher.start(3));
            return LogicLexer.tokenize(text, at, diagnostics)
                    .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics))
                    .map(value -> new SetField(variable, field, text, value, where));
        }
        matcher = ADD.matcher(line);
        if (matcher.matches()) {
            return collectionChange(
                    SpecAst.CollectionChange.ADD, matcher, line, where, diagnostics);
        }
        matcher = REMOVE.matcher(line);
        if (matcher.matches()) {
            return collectionChange(
                    SpecAst.CollectionChange.REMOVE, matcher, line, where, diagnostics);
        }
        matcher = LIST_BY.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new ListBy(
                    matcher.group(1),
                    matcher.group(2),
                    List.of(matcher.group(3).split(" +and +")),
                    sortOrders(matcher.group(4)),
                    matcher.group(5) != null,
                    where));
        }
        matcher = SAVE.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new Save(matcher.group(1), where));
        }
        matcher = DELETE.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new Delete(matcher.group(1), where));
        }
        matcher = FAIL.matcher(line);
        if (matcher.matches()) {
            String error = matcher.group(1);
            String condition = matcher.group(2).strip();
            SourceRef at = fragment(where, line, matcher.start(2));
            Optional<LogicAst.Expression> parsed =
                    LogicLexer.tokenize(condition, at, diagnostics)
                            .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            return parsed.map(expression -> new Fail(error, condition, expression, where));
        }
        matcher = REQUIRE.matcher(line);
        if (matcher.matches()) {
            String condition = matcher.group(1).strip();
            String error = matcher.group(2);
            SourceRef at = fragment(where, line, matcher.start(1));
            Optional<LogicAst.Expression> parsed =
                    LogicLexer.tokenize(condition, at, diagnostics)
                            .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            return parsed.map(expression -> new Require(condition, expression, error, where));
        }
        matcher = RETURN.matcher(line);
        if (matcher.matches()) {
            return Optional.of(new Return(
                    matcher.group(1).equals("nothing")
                            ? Optional.empty()
                            : Optional.of(matcher.group(1)),
                    where));
        }

        diagnostics.error(
                ErrorCodes.SYNTAX_FLOW_COMMAND,
                "unknown flow command '" + raw
                        + "'; expected validate, create, load, update, set, add, remove, list, "
                        + "call, require, fail, save, delete, or return",
                where);
        return Optional.empty();
    }

    private static Optional<List<LogicAst.NamedArgument>> callArguments(
            String source, SourceRef start, SourceRef where, DiagnosticCollector diagnostics) {
        if (source.isBlank()) {
            return Optional.of(List.of());
        }
        List<LogicAst.NamedArgument> arguments = new ArrayList<>();
        int offset = 0;
        for (String raw : splitArguments(source)) {
            int leading = raw.length() - raw.stripLeading().length();
            String argument = raw.strip();
            Matcher matcher = CALL_ARGUMENT.matcher(argument);
            if (!matcher.matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_FLOW_COMMAND,
                        "call arguments must be named as '<name> = <expression>'",
                        LineSyntax.at(start, source, offset + leading));
                return Optional.empty();
            }
            String expression = matcher.group(2).strip();
            SourceRef at = LineSyntax.at(
                    start, source, offset + leading + matcher.start(2));
            Optional<LogicAst.Expression> value = LogicLexer.tokenize(
                            expression, at, diagnostics)
                    .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            if (value.isEmpty()) {
                return Optional.empty();
            }
            arguments.add(new LogicAst.NamedArgument(
                    matcher.group(1), value.orElseThrow(), at));
            // The separating comma is not part of either argument.
            offset += raw.length() + 1;
        }
        return Optional.of(List.copyOf(arguments));
    }

    /** Splits only commas at the call's own depth, preserving nested Logic calls and strings. */
    private static List<String> splitArguments(String source) {
        List<String> arguments = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                arguments.add(source.substring(start, index));
                start = index + 1;
            }
        }
        arguments.add(source.substring(start));
        return List.copyOf(arguments);
    }
}
