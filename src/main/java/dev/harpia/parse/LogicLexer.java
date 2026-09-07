package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns one statement line of a {@code logic} block into tokens. The grammar is closed: any
 * character outside the catalogue is a diagnostic, never an escape into another language.
 */
public final class LogicLexer {

    private static final Map<String, LogicToken.Kind> KEYWORDS = Map.of(
            "if", LogicToken.Kind.IF,
            "else", LogicToken.Kind.ELSE,
            "return", LogicToken.Kind.RETURN,
            "and", LogicToken.Kind.AND,
            "or", LogicToken.Kind.OR,
            "not", LogicToken.Kind.NOT,
            "true", LogicToken.Kind.TRUE,
            "false", LogicToken.Kind.FALSE);

    /** Words held for planned constructs so they never become an identifier by accident. */
    static final Set<String> RESERVED = Set.of(
            "when", "otherwise", "match", "for", "each", "in", "where",
            "exists", "null", "let", "const", "case", "then", "elif");

    /** Words that name a side effect. They exist in Flow and are rejected inside Logic. */
    static final Set<String> EFFECTS = Set.of(
            "save", "delete", "update", "create", "load", "find", "list",
            "emit", "send", "call", "store", "transaction", "validate");

    private LogicLexer() {
    }

    /**
     * Tokenizes {@code line}, whose first character is at {@code where}. Returns empty when the
     * line contains a lexical error; every error is already reported.
     */
    public static Optional<List<LogicToken>> tokenize(
            String line, SourceRef where, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<LogicToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            char character = line.charAt(index);
            if (character == ' ') {
                index++;
                continue;
            }
            SourceRef position = LineSyntax.at(where, line, index);
            if (character == '"') {
                int end = closingQuote(line, index);
                if (end < 0) {
                    error(diagnostics, position, "unterminated string literal");
                    return Optional.empty();
                }
                tokens.add(new LogicToken(
                        LogicToken.Kind.STRING, line.substring(index, end + 1), position));
                index = end + 1;
                continue;
            }
            if (Character.isDigit(character)) {
                int end = index;
                while (end < line.length() && Character.isDigit(line.charAt(end))) {
                    end++;
                }
                boolean decimal = end + 1 < line.length()
                        && line.charAt(end) == '.'
                        && Character.isDigit(line.charAt(end + 1));
                if (decimal) {
                    end++;
                    while (end < line.length() && Character.isDigit(line.charAt(end))) {
                        end++;
                    }
                }
                if (end < line.length() && line.charAt(end) == '.') {
                    error(diagnostics, position,
                            "a decimal literal requires digits on both sides of the point");
                    return Optional.empty();
                }
                tokens.add(new LogicToken(
                        decimal ? LogicToken.Kind.DECIMAL : LogicToken.Kind.INTEGER,
                        line.substring(index, end),
                        position));
                index = end;
                continue;
            }
            if (Character.isLetter(character)) {
                int end = index;
                while (end < line.length() && Character.isLetterOrDigit(line.charAt(end))) {
                    end++;
                }
                String word = line.substring(index, end);
                tokens.add(new LogicToken(wordKind(word), word, position));
                index = end;
                continue;
            }

            Optional<LogicToken> operator = operator(line, index, position, diagnostics);
            if (operator.isEmpty()) {
                return Optional.empty();
            }
            tokens.add(operator.orElseThrow());
            index += operator.orElseThrow().text().length();
        }
        tokens.add(new LogicToken(
                LogicToken.Kind.END_OF_LINE, "", LineSyntax.at(where, line, line.length())));
        return Optional.of(tokens);
    }

    private static LogicToken.Kind wordKind(String word) {
        LogicToken.Kind keyword = KEYWORDS.get(word);
        if (keyword != null) {
            return keyword;
        }
        if (RESERVED.contains(word)) {
            return LogicToken.Kind.RESERVED;
        }
        if (EFFECTS.contains(word)) {
            return LogicToken.Kind.EFFECT;
        }
        return Character.isUpperCase(word.charAt(0))
                ? LogicToken.Kind.TYPE_NAME
                : LogicToken.Kind.IDENTIFIER;
    }

    private static Optional<LogicToken> operator(
            String line, int index, SourceRef position, DiagnosticCollector diagnostics) {
        String rest = line.substring(index);
        if (rest.startsWith("==")) {
            return Optional.of(new LogicToken(LogicToken.Kind.EQUAL_EQUAL, "==", position));
        }
        if (rest.startsWith("!=")) {
            return Optional.of(new LogicToken(LogicToken.Kind.NOT_EQUAL, "!=", position));
        }
        if (rest.startsWith("<=")) {
            return Optional.of(new LogicToken(LogicToken.Kind.LESS_OR_EQUAL, "<=", position));
        }
        if (rest.startsWith(">=")) {
            return Optional.of(new LogicToken(LogicToken.Kind.GREATER_OR_EQUAL, ">=", position));
        }
        if (rest.startsWith("&&") || rest.startsWith("||")) {
            error(diagnostics, position,
                    "'" + rest.substring(0, 2) + "' is not Harpia Logic; use 'and' or 'or'");
            return Optional.empty();
        }
        char character = rest.charAt(0);
        LogicToken.Kind kind = switch (character) {
            case '+' -> LogicToken.Kind.PLUS;
            case '-' -> LogicToken.Kind.MINUS;
            case '*' -> LogicToken.Kind.STAR;
            case '/' -> LogicToken.Kind.SLASH;
            case '<' -> LogicToken.Kind.LESS;
            case '>' -> LogicToken.Kind.GREATER;
            case '=' -> LogicToken.Kind.ASSIGN;
            case '(' -> LogicToken.Kind.LEFT_PAREN;
            case ')' -> LogicToken.Kind.RIGHT_PAREN;
            case ',' -> LogicToken.Kind.COMMA;
            case '.' -> LogicToken.Kind.DOT;
            default -> null;
        };
        if (kind == null) {
            error(diagnostics, position, reason(character));
            return Optional.empty();
        }
        return Optional.of(new LogicToken(kind, String.valueOf(character), position));
    }

    private static String reason(char character) {
        return switch (character) {
            case '!' -> "'!' is not Harpia Logic; use 'not'";
            case '%' -> "'%' is reserved for the Percentage literal and is not a modulo operator";
            case '\t' -> "tabs are not allowed inside a logic block";
            case ';' -> "a logic statement ends at the end of the line; ';' is not used";
            default -> "unexpected character '" + character + "' in a logic expression";
        };
    }

    private static int closingQuote(String line, int start) {
        for (int index = start + 1; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\\') {
                index++;
                continue;
            }
            if (character == '"') {
                return index;
            }
        }
        return -1;
    }

    private static void error(
            DiagnosticCollector diagnostics, SourceRef where, String message) {
        diagnostics.error(ErrorCodes.SYNTAX_LOGIC_TOKEN, message, where);
    }
}
