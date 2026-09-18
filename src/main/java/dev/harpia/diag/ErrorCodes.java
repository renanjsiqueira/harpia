package dev.harpia.diag;

/**
 * The diagnostic catalogue. Families: {@code HRP1xxx} syntax, {@code HRP2xxx} semantics,
 * {@code HRP3xxx} configuration, {@code HRP4xxx} out of current scope, {@code HRP5xxx} I/O,
 * and {@code HRP6xxx} capability/provider resolution.
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

    // --- HRP12xx: external binding syntax ----------------------------------------------------
    /** Malformed external binding file or declaration heading. */
    public static final String SYNTAX_BINDING_FILE = "HRP1201";
    /** Missing, repeated or malformed section of an external binding. */
    public static final String SYNTAX_BINDING_SECTION = "HRP1202";

    // --- HRP11xx: Harpia Logic syntax ---------------------------------------------------------
    /** Malformed {@code ## Logic Name} declaration or its subsections. */
    public static final String SYNTAX_LOGIC_SECTION = "HRP1101";
    /** Malformed {@code ## Scenario Title} declaration or its subsections. */
    public static final String SYNTAX_SCENARIO_SECTION = "HRP1106";
    /** A declaration this language version does not know, but a later one does. */
    public static final String SYNTAX_DECLARATION_TOO_NEW = "HRP1107";
    /** An enum heading or one of its values is malformed. */
    public static final String SYNTAX_ENUM_VALUE = "HRP1108";
    /** A value heading or one of its fields is malformed. */
    public static final String SYNTAX_VALUE_FIELD = "HRP1109";
    /** An invariant list is malformed. */
    public static final String SYNTAX_INVARIANT = "HRP1110";
    /** An Integration heading or one of its Operation ports is malformed. */
    public static final String SYNTAX_INTEGRATION = "HRP1111";
    /** The structure of a `## Event` declaration is not what the grammar admits. */
    public static final String SYNTAX_EVENT = "HRP1112";
    /** Indentation inside a {@code logic} block is not a multiple of four spaces. */
    public static final String SYNTAX_LOGIC_INDENT = "HRP1102";
    /** A character or word that does not belong to the Harpia Logic lexicon. */
    public static final String SYNTAX_LOGIC_TOKEN = "HRP1103";
    /** A line that is not an assignment, a conditional or a return. */
    public static final String SYNTAX_LOGIC_STATEMENT = "HRP1104";
    /** A malformed expression. */
    public static final String SYNTAX_LOGIC_EXPRESSION = "HRP1105";

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

    // --- HRP21xx: Harpia Logic semantics -------------------------------------------------------
    /** Two Logic declarations share a name. Logic names are unique across the project. */
    public static final String SEMANTIC_LOGIC_DUPLICATE = "HRP2101";
    /** A name is used before it is bound, or is not a parameter of the enclosing Logic. */
    public static final String SEMANTIC_LOGIC_UNKNOWN_NAME = "HRP2102";
    /** An operator was applied to operand types it does not accept. */
    public static final String SEMANTIC_LOGIC_TYPE = "HRP2103";
    /** The returned expression does not match the declared output type. */
    public static final String SEMANTIC_LOGIC_RETURN_TYPE = "HRP2104";
    /** A path through the Logic body finishes without returning a value. */
    public static final String SEMANTIC_LOGIC_MISSING_RETURN = "HRP2105";
    /** A side-effecting operation was used inside a pure computation. */
    public static final String SEMANTIC_LOGIC_SIDE_EFFECT = "HRP2106";
    /** The called function is neither a declared Logic nor a built-in. */
    public static final String SEMANTIC_LOGIC_UNKNOWN_FUNCTION = "HRP2107";
    /** Call arguments do not match the callee signature. */
    public static final String SEMANTIC_LOGIC_ARGUMENT = "HRP2108";
    /** Direct or indirect recursion between Logic declarations. */
    public static final String SEMANTIC_LOGIC_RECURSION = "HRP2109";
    /** A declared value is never read. Warning. */
    public static final String SEMANTIC_LOGIC_UNUSED = "HRP2110";
    /** A parameter name is declared twice. */
    public static final String SEMANTIC_LOGIC_DUPLICATE_PARAMETER = "HRP2111";
    /** A name is assigned more than once, or shadows an outer binding. */
    public static final String SEMANTIC_LOGIC_REASSIGNMENT = "HRP2112";
    /** A statement can never be reached because every path before it returns. */
    public static final String SEMANTIC_LOGIC_UNREACHABLE = "HRP2113";
    /** A construct is valid Harpia Logic grammar but its types do not exist yet. */
    public static final String SEMANTIC_LOGIC_UNSUPPORTED = "HRP2114";
    /** Two scenarios share a name. */
    public static final String SEMANTIC_SCENARIO_DUPLICATE = "HRP2115";
    /** A scenario names a computation that is not declared. */
    public static final String SEMANTIC_SCENARIO_UNKNOWN_TARGET = "HRP2116";
    /** A scenario does not bind every input exactly once, or binds an unknown one. */
    public static final String SEMANTIC_SCENARIO_BINDING = "HRP2117";
    /** A scenario value does not belong to the declared type. */
    public static final String SEMANTIC_SCENARIO_VALUE = "HRP2118";
    /** Tests were requested but a computation declares no scenario. Warning. */
    public static final String SEMANTIC_SCENARIO_MISSING = "HRP2119";
    /** A Query declares a flow step that mutates state. */
    public static final String SEMANTIC_QUERY_MUTATES = "HRP2120";
    /** The same domain error is declared with two different statuses. */
    public static final String SEMANTIC_ERROR_STATUS_CONFLICT = "HRP2121";
    /** An operation declares rules but no input for them to constrain. */
    public static final String SEMANTIC_RULE_WITHOUT_INPUT = "HRP2122";
    /** A field names a type no declaration in the project provides. */
    public static final String SEMANTIC_UNKNOWN_TYPE = "HRP2123";
    /** A scenario targets a computation whose implementation Harpia does not own. */
    public static final String SEMANTIC_SCENARIO_CUSTOM = "HRP2124";
    /** A module declares invariants but no entity for them to constrain. */
    public static final String SEMANTIC_INVARIANT_WITHOUT_ENTITY = "HRP2125";
    /** A field is declared both optional and required. */
    public static final String SEMANTIC_OPTIONAL_REQUIRED = "HRP2126";
    /** A flow raises an error the operation never declared. */
    public static final String SEMANTIC_FAIL_UNDECLARED = "HRP2127";
    /** A find names a field that cannot identify a single record. */
    public static final String SEMANTIC_FIND_NOT_UNIQUE = "HRP2128";
    /** A paged listing has no page and size to read. */
    public static final String SEMANTIC_PAGED_WITHOUT_INPUT = "HRP2129";
    /** A branch attempts to define a flow variable or return from a nested scope. */
    public static final String SEMANTIC_FLOW_BRANCH_SCOPE = "HRP2130";
    /** The owned modifier is attached to something that is not an entity relationship. */
    public static final String SEMANTIC_OWNED_RELATIONSHIP = "HRP2131";
    /** Two events share a name in the event namespace. */
    public static final String SEMANTIC_DUPLICATE_EVENT = "HRP2139";
    /** An event declares the same payload field more than once. */
    public static final String SEMANTIC_DUPLICATE_EVENT_FIELD = "HRP2140";
    /** An event payload carries an entity instead of the identity of one. */
    public static final String SEMANTIC_EVENT_PAYLOAD_TYPE = "HRP2141";
    /** Two integrations share a name in the integration namespace. */
    public static final String SEMANTIC_DUPLICATE_INTEGRATION = "HRP2132";
    /** An integration contract exposes an entity or identity-bearing reference as a value. */
    public static final String SEMANTIC_INTEGRATION_TYPE = "HRP2133";
    /** An integration operation declares the same input name more than once. */
    public static final String SEMANTIC_DUPLICATE_INTEGRATION_INPUT = "HRP2134";
    /** A Flow call target is unknown, ambiguous or not callable in the implemented slice. */
    public static final String SEMANTIC_FLOW_CALL_TARGET = "HRP2142";
    /** Named arguments do not match the signature of a Flow call target. */
    public static final String SEMANTIC_FLOW_CALL_ARGUMENT = "HRP2143";
    /** A Flow call discards a result or assigns an operation that returns nothing. */
    public static final String SEMANTIC_FLOW_CALL_RESULT = "HRP2144";

    /** A form of iteration outside the Core V1 slice: nesting, break, continue, async, mutation. */
    public static final String SEMANTIC_ITERATION = "HRP2146";

    /** A member that the type of a Flow value does not declare, or declares with another type. */
    public static final String SEMANTIC_FLOW_MEMBER = "HRP2149";
    /** A path parameter names nothing the operation can fill it with. */
    public static final String SEMANTIC_PATH_PARAM_INPUT = "HRP2135";
    /** A partial update demands an input the client is free to omit. */
    public static final String SEMANTIC_PATCH_REQUIRED_INPUT = "HRP2136";
    /** A partial update is bound to a flow that updates nothing from the request. */
    public static final String SEMANTIC_PATCH_WITHOUT_UPDATE = "HRP2137";
    /** An input bound to a path or query carries something a URL cannot spell. */
    public static final String SEMANTIC_URL_BOUND_INPUT = "HRP2138";

    // --- HRP22xx: external binding semantics -------------------------------------------------
    /** A binding references an operation that is not declared. */
    public static final String SEMANTIC_BINDING_OPERATION = "HRP2201";
    /** More than one binding attempts to expose the same operation. */
    public static final String SEMANTIC_DUPLICATE_BINDING = "HRP2202";
    /** A request or response mapping does not match the bound operation contract. */
    public static final String SEMANTIC_BINDING_MAPPING = "HRP2203";
    /** One Integration is bound with more than one authentication scheme. */
    public static final String SEMANTIC_BINDING_AUTH = "HRP2204";

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
    /** {@code harpia.schemaVersion} is missing or not supported. */
    public static final String CONFIG_SCHEMA_VERSION = "HRP3006";
    /** Value outside M1 support. */
    public static final String CONFIG_UNSUPPORTED_VALUE = "HRP3007";
    /** Required key missing, or scalar of the wrong type. */
    public static final String CONFIG_MISSING_KEY = "HRP3008";
    /** A legacy configuration shape that still works but will be removed. Warning. */
    public static final String CONFIG_DEPRECATED = "HRP3009";
    /** {@code harpia.languageVersion} is not implemented by this compiler. */
    public static final String CONFIG_LANGUAGE_VERSION = "HRP3010";

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
    /** A resolved path escapes output, specs or bindings. Exit 2. */
    public static final String IO_PATH_ESCAPE = "HRP5004";
    /** Symlink inside a source tree: never followed, ignored with a warning. */
    public static final String IO_SYMLINK_IGNORED = "HRP5005";
    /** {@code harpia init} refuses to overwrite an existing target file. Exit 2. */
    public static final String IO_INIT_TARGET_EXISTS = "HRP5006";
    /** {@code --clean} was requested while unknown files sit in the output directory. Exit 2. */
    public static final String IO_CLEAN_BLOCKED = "HRP5007";

    // --- HRP6xxx: capability/provider resolution ---------------------------------------------
    /** A Business IR requirement has no configured provider. */
    public static final String CAPABILITY_PROVIDER_MISSING = "HRP6001";
    /** A provider/configuration cannot satisfy a Business IR requirement. */
    public static final String CAPABILITY_PROVIDER_UNSUPPORTED = "HRP6002";

    // --- HRP7xxx: target resolution ------------------------------------------------------------
    /** The configured target is catalogued but this compiler has no generator for it. */
    public static final String TARGET_NOT_SUPPORTED = "HRP7001";
    /** The configured target identifier is not in the catalogue at all. */
    public static final String TARGET_UNKNOWN = "HRP7002";
    /** The specification requires a capability the resolved target does not implement. */
    public static final String TARGET_CAPABILITY_UNSUPPORTED = "HRP7003";
    /** The configured language version is below what the target requires. */
    public static final String TARGET_LANGUAGE_VERSION = "HRP7004";
    /** A target-specific option is missing or invalid. */
    public static final String TARGET_OPTION = "HRP7005";
    /** A target-owned declarative template could not be rendered. */
    public static final String TARGET_TEMPLATE_FAILURE = "HRP7006";
    /** Application IR could not be transformed into the selected target model. */
    public static final String TARGET_TRANSFORMATION_FAILURE = "HRP7007";
    /** A valid Application IR construct has no implementation in the selected target. */
    public static final String TARGET_CONSTRUCT_UNSUPPORTED = "HRP7008";
}
