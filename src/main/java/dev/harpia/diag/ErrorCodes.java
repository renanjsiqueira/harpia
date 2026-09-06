package dev.harpia.diag;

/**
 * The full diagnostic catalogue. Families: {@code HRP1xxx} syntax, {@code HRP2xxx} semantics,
 * {@code HRP3xxx} configuration, {@code HRP4xxx} out of MVP scope, {@code HRP5xxx} I/O.
 *
 * <p>The {@code HRP} prefix supersedes the {@code HARP} prefix used in the original product brief.
 *
 * <p>Codes in the 1xxx and 2xxx families are reserved here but only raised from iteration 3 on,
 * when the parser and the semantic validator land. Keeping the constants in one place stops the
 * catalogue from drifting between code, README and {@code docs/errors.md}.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // --- HRP1xxx: syntax (reserved, raised from iteration 3) ---------------------------------
    public static final String SYNTAX_H1 = "HRP1001";
    public static final String SYNTAX_ENTITY_NAME = "HRP1002";
    public static final String SYNTAX_DATA_SECTION = "HRP1003";
    public static final String SYNTAX_FIELD_LINE = "HRP1004";
    public static final String SYNTAX_UNKNOWN_TYPE = "HRP1005";
    public static final String SYNTAX_USE_CASE_SECTION = "HRP1006";
    public static final String SYNTAX_FLOW_COMMAND = "HRP1007";
    public static final String SYNTAX_ERROR_CONDITION = "HRP1008";
    public static final String SYNTAX_USE_CASE_TITLE = "HRP1009";
    public static final String SYNTAX_ENDPOINT = "HRP1010";

    // --- HRP2xxx: semantics (reserved, raised from iteration 3) ------------------------------
    public static final String SEMANTIC_DUPLICATE_ENTITY = "HRP2001";
    public static final String SEMANTIC_DUPLICATE_FIELD = "HRP2002";
    public static final String SEMANTIC_ID_FIELD = "HRP2003";
    public static final String SEMANTIC_DEFAULT_TYPE = "HRP2004";
    public static final String SEMANTIC_DUPLICATE_ROUTE = "HRP2005";
    public static final String SEMANTIC_PATH_VAR_FLOW = "HRP2006";
    public static final String SEMANTIC_UNDEFINED_VAR = "HRP2007";
    public static final String SEMANTIC_FLOW_RETURN = "HRP2008";
    public static final String SEMANTIC_RETURN_TYPE = "HRP2009";
    public static final String SEMANTIC_FOREIGN_ENTITY = "HRP2010";
    public static final String SEMANTIC_INPUT_FIELD_UNKNOWN = "HRP2011";
    public static final String SEMANTIC_INPUT_FIELD_TYPE = "HRP2012";
    public static final String SEMANTIC_DUPLICATE_ON_NON_UNIQUE = "HRP2013";
    public static final String SEMANTIC_DUPLICATE_USE_CASE = "HRP2014";
    public static final String SEMANTIC_JAVA_RESERVED = "HRP2015";
    public static final String SEMANTIC_POSTGRES_RESERVED = "HRP2016";
    public static final String SEMANTIC_ORPHAN_ENTITY = "HRP2017";

    // --- HRP3xxx: configuration ---------------------------------------------------------------
    /** {@code harpia.yaml} is missing. Exit 2. */
    public static final String CONFIG_MISSING = "HRP3001";
    /** Malformed YAML, or a YAML tag the safe loader refuses to handle. */
    public static final String CONFIG_MALFORMED = "HRP3002";
    /** Unknown or duplicated key. */
    public static final String CONFIG_UNKNOWN_KEY = "HRP3003";
    /** {@code project.group} / {@code project.package} is not a valid Java package. */
    public static final String CONFIG_INVALID_PACKAGE = "HRP3004";
    /** {@code specs/} missing or without {@code *.harpia.md}. Exit 2. */
    public static final String CONFIG_NO_SPECS = "HRP3005";
    /** {@code harpia:} missing or different from 1. */
    public static final String CONFIG_SCHEMA_VERSION = "HRP3006";
    /** Value outside M1 support. */
    public static final String CONFIG_UNSUPPORTED_VALUE = "HRP3007";
    /** Required key missing, or scalar of the wrong type. */
    public static final String CONFIG_MISSING_KEY = "HRP3008";

    // --- HRP4xxx: out of MVP scope ------------------------------------------------------------
    /** Authentication requested (capability or {@code ### Access: authenticated}). */
    public static final String UNSUPPORTED_AUTHENTICATION = "HRP4001";
    /** Relationship syntax. */
    public static final String UNSUPPORTED_RELATIONSHIP = "HRP4002";
    /** Email or events requested. */
    public static final String UNSUPPORTED_EMAIL_OR_EVENTS = "HRP4003";

    // --- HRP5xxx: I/O -------------------------------------------------------------------------
    /** Unknown file under the output directory. Warning; never deleted. */
    public static final String IO_UNKNOWN_OUTPUT_FILE = "HRP5001";
    /** Read/write failure: permissions, disk full, unreadable path. Exit 2. */
    public static final String IO_FAILURE = "HRP5002";
    /** Source file is not valid UTF-8. */
    public static final String IO_NOT_UTF8 = "HRP5003";
    /** A resolved path escapes {@code paths.output} (or {@code paths.specs}). Exit 2. */
    public static final String IO_PATH_ESCAPE = "HRP5004";
    /** Symlink inside {@code specs/}: never followed, ignored with a warning. */
    public static final String IO_SYMLINK_IGNORED = "HRP5005";
    /** {@code harpia init} refuses to overwrite an existing target file. Exit 2. */
    public static final String IO_INIT_TARGET_EXISTS = "HRP5006";
}
