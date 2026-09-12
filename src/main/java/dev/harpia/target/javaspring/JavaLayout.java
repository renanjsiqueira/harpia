package dev.harpia.target.javaspring;

import java.util.Objects;

/**
 * Java naming decisions for one project.
 *
 * <p>Turning a namespace into a package path, and an application name into a class name, is a Java
 * concern. It lives behind the target boundary so no other layer has to know what a package is.
 */
public record JavaLayout(String packageName, String applicationClassName) {

    public static final String LOGIC = "logic";
    public static final String DOMAIN = "domain";
    public static final String REPOSITORY = "repository";
    public static final String DTO = "dto";
    public static final String SERVICE = "service";
    public static final String WEB = "web";
    public static final String ERROR = "error";
    public static final String EVENT = "event";
    public static final String CONFIG = "config";
    public static final String INTEGRATION = "integration";

    public JavaLayout {
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(applicationClassName, "applicationClassName");
    }

    public static JavaLayout of(String namespace, String applicationName) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(applicationName, "applicationName");
        return new JavaLayout(namespace, typeName(applicationName) + "Application");
    }

    public String packagePath() {
        return packageName.replace('.', '/');
    }

    public String packageName(String suffix) {
        return packageName + "." + suffix;
    }

    public String packagePath(String suffix) {
        return packagePath() + "/" + suffix;
    }

    /** Where a generated test lives; Maven keeps test sources in their own root. */
    public static String testSourcePath(String packagePath, String className) {
        return "src/test/java/" + packagePath + "/" + className + ".java";
    }

    public static String testTypeName(String typeName) {
        return typeName + "Test";
    }

    public static String sourcePath(String packagePath, String className) {
        return "src/main/java/" + packagePath + "/" + className + ".java";
    }

    public static String repositoryTypeName(String entityTypeName) {
        return entityTypeName + "Repository";
    }

    public static String serviceTypeName(String entityTypeName) {
        return entityTypeName + "Service";
    }

    public static String controllerTypeName(String entityTypeName) {
        return entityTypeName + "Controller";
    }

    /** Java bean accessor naming, so no transformer re-derives it. */
    public static String accessor(String prefix, String fieldName) {
        return prefix
                + fieldName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + fieldName.substring(1);
    }


    private static String typeName(String source) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (!Character.isLetterOrDigit(character)) {
                capitalize = true;
                continue;
            }
            if (result.isEmpty() && !Character.isJavaIdentifierStart(character)) {
                result.append('_');
            }
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = false;
        }
        return result.isEmpty() ? "Harpia" : result.toString();
    }
}
