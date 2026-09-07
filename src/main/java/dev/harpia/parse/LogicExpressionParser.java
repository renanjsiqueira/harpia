package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.LogicAst.Expression;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Recursive descent over the tokens of a single line. Precedence, from lowest to highest:
 * {@code or}, {@code and}, {@code not}, comparison, {@code + -}, {@code * /}, unary {@code -},
 * member access and call. Comparison is non-associative on purpose: {@code a < b < c} is a
 * business-level mistake, not a chained comparison.
 */
public final class LogicExpressionParser {

    private final List<LogicToken> tokens;
    private final DiagnosticCollector diagnostics;
    private int index;
    private boolean failed;

    private LogicExpressionParser(List<LogicToken> tokens, DiagnosticCollector diagnostics) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    /** Parses the tokens as one complete expression. Trailing tokens are a diagnostic. */
    public static Optional<Expression> parse(
            List<LogicToken> tokens, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(diagnostics, "diagnostics");
        LogicExpressionParser parser = new LogicExpressionParser(tokens, diagnostics);
        Optional<Expression> expression = parser.expression();
        if (expression.isEmpty()) {
            return Optional.empty();
        }
        if (!parser.peek().is(LogicToken.Kind.END_OF_LINE)) {
            parser.error(parser.peek(), "unexpected '" + parser.peek().text() + "'");
            return Optional.empty();
        }
        return expression;
    }

    private Optional<Expression> expression() {
        return or();
    }

    private Optional<Expression> or() {
        return leftAssociative(this::and, LogicToken.Kind.OR);
    }

    private Optional<Expression> and() {
        return leftAssociative(this::not, LogicToken.Kind.AND);
    }

    private Optional<Expression> not() {
        if (peek().is(LogicToken.Kind.NOT)) {
            LogicToken operator = advance();
            return not().map(operand ->
                    new LogicAst.Unary(operator.text(), operand, operator.where()));
        }
        return comparison();
    }

    private Optional<Expression> comparison() {
        Optional<Expression> left = additive();
        if (left.isEmpty() || !isComparison(peek().kind())) {
            return left;
        }
        LogicToken operator = advance();
        Optional<Expression> right = additive();
        if (right.isEmpty()) {
            return Optional.empty();
        }
        if (isComparison(peek().kind())) {
            error(peek(), "comparison is not associative; use 'and' to combine two comparisons");
            return Optional.empty();
        }
        return Optional.of(new LogicAst.Binary(
                operator.text(), left.orElseThrow(), right.orElseThrow(), operator.where()));
    }

    private Optional<Expression> additive() {
        return leftAssociative(
                this::multiplicative, LogicToken.Kind.PLUS, LogicToken.Kind.MINUS);
    }

    private Optional<Expression> multiplicative() {
        return leftAssociative(this::unary, LogicToken.Kind.STAR, LogicToken.Kind.SLASH);
    }

    private Optional<Expression> unary() {
        if (peek().is(LogicToken.Kind.MINUS)) {
            LogicToken operator = advance();
            return unary().map(operand ->
                    new LogicAst.Unary(operator.text(), operand, operator.where()));
        }
        return postfix();
    }

    private Optional<Expression> postfix() {
        Optional<Expression> value = primary();
        while (value.isPresent() && peek().is(LogicToken.Kind.DOT)) {
            LogicToken dot = advance();
            if (!peek().is(LogicToken.Kind.IDENTIFIER)) {
                error(peek(), "expected a field name after '.'");
                return Optional.empty();
            }
            LogicToken member = advance();
            value = Optional.of(new LogicAst.MemberAccess(
                    value.orElseThrow(), member.text(), dot.where()));
        }
        return value;
    }

    private Optional<Expression> primary() {
        LogicToken token = peek();
        switch (token.kind()) {
            case INTEGER -> {
                advance();
                return Optional.of(new LogicAst.Literal(
                        LogicAst.LiteralKind.INTEGER, token.text(), token.where()));
            }
            case DECIMAL -> {
                advance();
                return Optional.of(new LogicAst.Literal(
                        LogicAst.LiteralKind.DECIMAL, token.text(), token.where()));
            }
            case STRING -> {
                advance();
                return Optional.of(new LogicAst.Literal(
                        LogicAst.LiteralKind.STRING, token.text(), token.where()));
            }
            case TRUE, FALSE -> {
                advance();
                return Optional.of(new LogicAst.Literal(
                        LogicAst.LiteralKind.BOOLEAN, token.text(), token.where()));
            }
            case IDENTIFIER -> {
                advance();
                if (peek().is(LogicToken.Kind.LEFT_PAREN)) {
                    return builtinCall(token);
                }
                return Optional.of(new LogicAst.Reference(token.text(), token.where()));
            }
            case TYPE_NAME -> {
                advance();
                if (!peek().is(LogicToken.Kind.LEFT_PAREN)) {
                    error(token, "'" + token.text()
                            + "' is a declaration name; only a call may appear in an expression");
                    return Optional.empty();
                }
                return logicCall(token);
            }
            case LEFT_PAREN -> {
                advance();
                Optional<Expression> inner = expression();
                if (inner.isEmpty()) {
                    return Optional.empty();
                }
                if (!peek().is(LogicToken.Kind.RIGHT_PAREN)) {
                    error(peek(), "expected ')'");
                    return Optional.empty();
                }
                advance();
                return inner;
            }
            case EFFECT -> {
                error(ErrorCodes.SEMANTIC_LOGIC_SIDE_EFFECT, token, sideEffectMessage(token));
                return Optional.empty();
            }
            case RESERVED -> {
                error(token, "'" + token.text()
                        + "' is reserved for a planned Harpia Logic feature");
                return Optional.empty();
            }
            case END_OF_LINE -> {
                error(token, "expected an expression");
                return Optional.empty();
            }
            default -> {
                error(token, "unexpected '" + token.text() + "' in an expression");
                return Optional.empty();
            }
        }
    }

    private Optional<Expression> logicCall(LogicToken name) {
        advance();
        List<LogicAst.NamedArgument> arguments = new ArrayList<>();
        if (!peek().is(LogicToken.Kind.RIGHT_PAREN)) {
            while (true) {
                if (!peek().is(LogicToken.Kind.IDENTIFIER)) {
                    error(peek(), "arguments of " + name.text()
                            + " must be named, as in 'total = order.total'");
                    return Optional.empty();
                }
                LogicToken argument = advance();
                if (!peek().is(LogicToken.Kind.ASSIGN)) {
                    error(peek(), "arguments of " + name.text()
                            + " must be named, as in '" + argument.text() + " = value'");
                    return Optional.empty();
                }
                advance();
                Optional<Expression> value = expression();
                if (value.isEmpty()) {
                    return Optional.empty();
                }
                arguments.add(new LogicAst.NamedArgument(
                        argument.text(), value.orElseThrow(), argument.where()));
                if (!peek().is(LogicToken.Kind.COMMA)) {
                    break;
                }
                advance();
            }
        }
        if (!peek().is(LogicToken.Kind.RIGHT_PAREN)) {
            error(peek(), "expected ')' to close the call to " + name.text());
            return Optional.empty();
        }
        advance();
        return Optional.of(new LogicAst.Call(name.text(), arguments, name.where()));
    }

    private Optional<Expression> builtinCall(LogicToken name) {
        advance();
        List<Expression> arguments = new ArrayList<>();
        if (!peek().is(LogicToken.Kind.RIGHT_PAREN)) {
            while (true) {
                Optional<Expression> value = expression();
                if (value.isEmpty()) {
                    return Optional.empty();
                }
                arguments.add(value.orElseThrow());
                if (!peek().is(LogicToken.Kind.COMMA)) {
                    break;
                }
                advance();
            }
        }
        if (!peek().is(LogicToken.Kind.RIGHT_PAREN)) {
            error(peek(), "expected ')' to close the call to " + name.text());
            return Optional.empty();
        }
        advance();
        return Optional.of(new LogicAst.BuiltinCall(name.text(), arguments, name.where()));
    }

    private Optional<Expression> leftAssociative(
            java.util.function.Supplier<Optional<Expression>> operand,
            LogicToken.Kind... operators) {
        Optional<Expression> left = operand.get();
        while (left.isPresent() && isAny(peek().kind(), operators)) {
            LogicToken operator = advance();
            Optional<Expression> right = operand.get();
            if (right.isEmpty()) {
                return Optional.empty();
            }
            left = Optional.of(new LogicAst.Binary(
                    operator.text(), left.orElseThrow(), right.orElseThrow(), operator.where()));
        }
        return left;
    }

    private static boolean isAny(LogicToken.Kind kind, LogicToken.Kind... candidates) {
        for (LogicToken.Kind candidate : candidates) {
            if (kind == candidate) {
                return true;
            }
        }
        return false;
    }

    private static boolean isComparison(LogicToken.Kind kind) {
        return kind == LogicToken.Kind.EQUAL_EQUAL
                || kind == LogicToken.Kind.NOT_EQUAL
                || kind == LogicToken.Kind.LESS
                || kind == LogicToken.Kind.LESS_OR_EQUAL
                || kind == LogicToken.Kind.GREATER
                || kind == LogicToken.Kind.GREATER_OR_EQUAL;
    }

    static String sideEffectMessage(LogicToken token) {
        return "operation '" + token.text() + "' is not allowed inside Logic; "
                + "Logic must be pure, so move side-effecting operations to Flow";
    }

    private LogicToken peek() {
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    private LogicToken advance() {
        LogicToken token = peek();
        if (index < tokens.size() - 1) {
            index++;
        }
        return token;
    }

    private void error(LogicToken token, String message) {
        error(ErrorCodes.SYNTAX_LOGIC_EXPRESSION, token, message);
    }

    private void error(String code, LogicToken token, String message) {
        if (failed) {
            return;
        }
        failed = true;
        SourceRef where = token.where();
        diagnostics.error(code, message, where);
    }
}
