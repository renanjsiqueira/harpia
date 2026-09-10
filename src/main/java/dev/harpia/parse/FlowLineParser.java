package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.CreateFrom;
import dev.harpia.parse.SpecAst.Delete;
import dev.harpia.parse.SpecAst.Fail;
import dev.harpia.parse.SpecAst.FindBy;
import dev.harpia.parse.SpecAst.FlowStatement;
import dev.harpia.parse.SpecAst.ListAll;
import dev.harpia.parse.SpecAst.LoadById;
import dev.harpia.parse.SpecAst.Return;
import dev.harpia.parse.SpecAst.Save;
import dev.harpia.parse.SpecAst.UpdateFrom;
import dev.harpia.parse.SpecAst.ValidateInput;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed grammar for the eight V0 flow commands. */
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
    private static final Pattern LIST = Pattern.compile("^" + VARIABLE + " +\\= +list +" + ENTITY + "$");
    private static final Pattern SAVE = Pattern.compile("^save +" + VARIABLE + "$");
    private static final Pattern DELETE = Pattern.compile("^delete +" + VARIABLE + "$");
    private static final Pattern RETURN = Pattern.compile("^return +(nothing|[a-z][A-Za-z0-9]*)$");
    private static final Pattern FAIL = Pattern.compile(
            "^fail +([a-z]+(?: [a-z]+)*?) +when +(\\S.*)$");

    private FlowLineParser() {
    }

    public static Optional<FlowStatement> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        String line = raw.strip();
        if (line.equals("validate input")) {
            return Optional.of(new ValidateInput(where));
        }
        Matcher matcher = CREATE.matcher(line);
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
            return Optional.of(new ListAll(matcher.group(1), matcher.group(2), where));
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
            Optional<LogicAst.Expression> parsed =
                    LogicLexer.tokenize(condition, where, diagnostics)
                            .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            return parsed.map(expression -> new Fail(error, condition, expression, where));
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
                        + "'; expected validate, create, load, update, list, save, delete, or return",
                where);
        return Optional.empty();
    }
}
