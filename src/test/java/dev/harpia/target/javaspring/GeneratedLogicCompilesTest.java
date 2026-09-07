package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.emit.GeneratedTree;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the whole Logic slice: a specification becomes Java that javac accepts and that computes
 * the declared business result. There is no Harpia runtime in the loop, and no Spring context: the
 * generated class is plain Java.
 */
class GeneratedLogicCompilesTest {

    private static Class<?> discount;
    private static Class<?> total;

    @BeforeAll
    static void compileGeneratedSources(@TempDir Path classes) throws Exception {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/business-logic/pricing")));
        assertThat(result.diagnostics()).isEmpty();

        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> file : result.tree().orElseThrow().files().entrySet()) {
            if (file.getKey().contains("/logic/")) {
                sources.add(new InMemorySource(file.getKey(), file.getValue()));
            }
        }
        assertThat(sources).hasSize(4);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac).as("a JDK is required to compile the generated sources").isNotNull();
        StringBuilder errors = new StringBuilder();
        try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, null)) {
            boolean compiled = javac.getTask(
                            null,
                            files,
                            diagnostic -> errors.append(diagnostic).append('\n'),
                            List.of("-d", classes.toString()),
                            null,
                            sources)
                    .call();
            assertThat(compiled).as("generated Java must compile: %s", errors).isTrue();
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[] {classes.toUri().toURL()},
                GeneratedLogicCompilesTest.class.getClassLoader());
        discount = loader.loadClass("com.example.pricing.logic.CalculateDiscount");
        total = loader.loadClass("com.example.pricing.logic.CalculateTotal");
    }

    @Test
    void aVipOrderGetsTwentyPercent() throws Exception {
        assertThat(applyDiscount("100", true)).isEqualByComparingTo("20.00");
    }

    @Test
    void anOrderOfAtLeastOneThousandGetsTenPercent() throws Exception {
        assertThat(applyDiscount("1000", false)).isEqualByComparingTo("100.00");
        assertThat(applyDiscount("999.99", false)).isEqualByComparingTo("0");
    }

    @Test
    void aSmallNonVipOrderGetsNothing() throws Exception {
        assertThat(applyDiscount("100", false)).isEqualByComparingTo("0");
    }

    @Test
    void theCompositeLogicCapsTheDiscountAndAddsShipping() throws Exception {
        BigDecimal result = (BigDecimal) total
                .getMethod("apply", BigDecimal.class, Boolean.class, BigDecimal.class)
                .invoke(null, new BigDecimal("100"), true, new BigDecimal("10"));

        assertThat(result).isEqualByComparingTo("95.00");
    }

    @Test
    void theGeneratedClassIsPureAndNotInstantiable() {
        assertThat(discount.getDeclaredConstructors()).hasSize(1);
        assertThat(discount.getDeclaredConstructors()[0].canAccess(null)).isFalse();
        assertThat(java.lang.reflect.Modifier.isFinal(discount.getModifiers())).isTrue();
        assertThat(discount.getDeclaredFields()).isEmpty();
    }

    private static BigDecimal applyDiscount(String amount, boolean vip) throws Exception {
        return (BigDecimal) discount
                .getMethod("apply", BigDecimal.class, Boolean.class)
                .invoke(null, new BigDecimal(amount), vip);
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        private InMemorySource(String path, String code) {
            super(URI.create("string:///" + path), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }
    }
}
