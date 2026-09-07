package dev.harpia.target.javaspring;

import javax.lang.model.SourceVersion;

/** Java 21 identifier checks owned by the Java/Spring target. */
final class JavaNames {

    private JavaNames() {
    }

    static boolean isPackageName(String value) {
        return value != null
                && !value.isBlank()
                && SourceVersion.isName(value, SourceVersion.RELEASE_21);
    }
}
