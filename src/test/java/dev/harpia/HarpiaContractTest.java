package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The promised surface, written down.
 *
 * <p>Being typed is not the same as being promised. Every record in this compiler has a shape, and
 * almost all of them are free to change with the next slice; the ones a harness actually touches
 * are not. Renaming a method on {@code CompileResult} compiles, passes every other test, and breaks
 * whoever was calling it — so the surface is a golden file, and changing it has to be a decision.
 *
 * <p>When this test fails: if the change was accidental, revert it. If it was intended, regenerate
 * the golden and raise {@link HarpiaContract#VERSION}, because a consumer pinned to the old number
 * is now looking at something else.
 */
class HarpiaContractTest {

    private static final Path GOLDEN = Path.of("src/test/resources/fixtures/api/contract.txt");
    private static final Path CODES = Path.of("src/test/resources/fixtures/api/contract-codes.txt");

    @Test
    void thePromisedSurfaceIsTheOneThatWasPromised() {
        assertThat(surface())
                .as("regenerate %s and raise HarpiaContract.VERSION only if this change is meant "
                        + "to reach consumers", GOLDEN)
                .isEqualTo(golden());
    }

    @Test
    void theJavaApiAndTheCliReportTheSameContractVersion() {
        assertThat(HarpiaContract.VERSION)
                .as("one promise seen twice cannot carry two numbers")
                .isEqualTo(1);
        assertThat(surface())
                .as("the version is part of the surface, so a change to it lands in the golden")
                .contains("public static final int VERSION");
    }

    /**
     * A diagnostic code is written into someone's tooling the day they first see it.
     *
     * <p>So the ledger grows and never changes: a new code is additive and breaks nobody, while a
     * renamed constant or a reused number silently points existing tooling at another refusal.
     */
    @Test
    void everyCodeEverPromisedStillMeansWhatItMeant() {
        assertThat(codes())
                .as("a code already published cannot change; add a new one instead")
                .containsAllEntriesOf(ledger());
    }

    @Test
    void everyPromisedTypeIsPublicAndReachableWithoutReflection() {
        assertThat(HarpiaContract.types())
                .allSatisfy(type -> assertThat(Modifier.isPublic(type.getModifiers()))
                        .as("%s is promised but not public", type.getName())
                        .isTrue());
    }

    /**
     * One line per promised member, sorted.
     *
     * <p>Sorted rather than declaration-ordered: moving a method inside a file is not a change to
     * what is promised, and a golden that failed on it would train people to regenerate without
     * reading.
     */
    private static String surface() {
        StringBuilder out = new StringBuilder();
        for (Class<?> type : HarpiaContract.types()) {
            out.append(type.getName()).append('\n');
            // The codes have their own ledger, because what breaks a consumer there is a code
            // changing or disappearing, never a new one appearing.
            if (type == dev.harpia.diag.ErrorCodes.class) {
                continue;
            }
            members(type).forEach(member -> out.append("  ").append(member).append('\n'));
        }
        return out.toString();
    }

    private static List<String> members(Class<?> type) {
        List<String> members = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                members.add(modifiers(field.getModifiers()) + simple(field.getType()) + " "
                        + field.getName());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                members.add("new " + simple(type) + parameters(constructor.getParameterTypes()));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                members.add(modifiers(method.getModifiers()) + simple(method.getReturnType()) + " "
                        + method.getName() + parameters(method.getParameterTypes()));
            }
        }
        members.sort(Comparator.naturalOrder());
        return members;
    }

    private static String modifiers(int modifiers) {
        StringBuilder out = new StringBuilder("public ");
        if (Modifier.isStatic(modifiers)) {
            out.append("static ");
        }
        if (Modifier.isFinal(modifiers)) {
            out.append("final ");
        }
        return out.toString();
    }

    private static String parameters(Class<?>[] types) {
        StringBuilder out = new StringBuilder("(");
        for (int index = 0; index < types.length; index++) {
            out.append(index == 0 ? "" : ", ").append(simple(types[index]));
        }
        return out.append(')').toString();
    }

    /** Nested types keep their owner, so {@code Stages} and a stray {@code Stages} differ. */
    private static String simple(Class<?> type) {
        if (type.isArray()) {
            return simple(type.getComponentType()) + "[]";
        }
        String name = type.getName();
        int packageEnd = name.lastIndexOf('.');
        return packageEnd < 0 ? name : name.substring(packageEnd + 1);
    }

    private static java.util.Map<String, String> codes() {
        java.util.Map<String, String> codes = new java.util.LinkedHashMap<>();
        for (Field field : dev.harpia.diag.ErrorCodes.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    codes.put(field.getName(), (String) field.get(null));
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(field.getName() + " is public but unreadable", exception);
                }
            }
        }
        return codes;
    }

    private static java.util.Map<String, String> ledger() {
        java.util.Map<String, String> promised = new java.util.LinkedHashMap<>();
        for (String line : read(CODES).split("\n")) {
            if (!line.isBlank()) {
                int separator = line.indexOf('=');
                promised.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return promised;
    }

    private static String golden() {
        return read(GOLDEN);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
