package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a composed Flow computes, observed by running it.
 *
 * <p>A generated service that compiles has proved nothing about which value reached which
 * instruction. The whole point of composition is that the second step sees what the first produced
 * rather than the input it came from, and those two are only distinguishable by the number that
 * comes out: the fixtures below are arranged so that re-reading the input gives a different answer
 * from consuming the result, and the assertion names both.
 *
 * <p>No Spring context and no database. The generated service takes its repository through the
 * constructor, so a proxy that hands the entity back is enough to watch what the flow did to it —
 * and it also records what a called Command was given, which is the only way to see arguments
 * arriving by name rather than by position.
 */
class FlowCompositionRuntimeTest {

    @TempDir
    Path projectRoot;

    /** C5 — the second call is given 10.00 by the first, not the 100.00 the input carried. */
    @Test
    void secondCallConsumesTheFirstResult(@TempDir Path classes) throws Exception {
        Application application = run(classes, """
                validate input
                order = create SalesOrder from input
                discount = call CalculateDiscount(total = total)
                adjusted = call CalculateDiscount(total = discount)
                set order.total = adjusted
                save order
                return order
                """);

        Object response = application.placeOrder("A-1", new BigDecimal("100.00"), true);

        assertThat((BigDecimal) application.component(response, "total"))
                .as("ten percent of the first result (10.00), not of the input (100.00)")
                .isEqualByComparingTo("1.00")
                .isNotEqualByComparingTo("10.00");
    }

    /** C6 — a Command is given its arguments by name, whatever order they were written in. */
    @Test
    void namedCommandArgumentsReachTheCallee(@TempDir Path classes) throws Exception {
        Application application = run(classes, """
                validate input
                order = create SalesOrder from input
                call RecordAudit(orderNumber = orderNumber, amount = total)
                save order
                return order
                """);
        application.placeOrder("A-1", new BigDecimal("100.00"), true);
        Object straight = application.audited();

        Application reversed = run(classes.resolve("reversed"), """
                validate input
                order = create SalesOrder from input
                call RecordAudit(amount = total, orderNumber = orderNumber)
                save order
                return order
                """);
        reversed.placeOrder("A-1", new BigDecimal("100.00"), true);
        Object swapped = reversed.audited();

        for (Object entry : List.of(straight, swapped)) {
            assertThat(get(entry, "getOrderNumber")).isEqualTo("A-1");
            assertThat((BigDecimal) get(entry, "getAmount")).isEqualByComparingTo("100.00");
        }
    }

    /** C7 — set writes the value the call produced, and the input of the same name is not read. */
    @Test
    void setConsumesTheVisibleLocalResult(@TempDir Path classes) throws Exception {
        Application application = run(classes, """
                validate input
                order = create SalesOrder from input
                discount = call CalculateDiscount(total = total)
                set order.total = discount
                save order
                return order
                """);

        Object response = application.placeOrder("A-1", new BigDecimal("100.00"), true);

        assertThat((BigDecimal) application.component(response, "total"))
                .as("the discount the call produced, not the 100.00 the input carried")
                .isEqualByComparingTo("10.00")
                .isNotEqualByComparingTo("100.00");
    }

    /** C8 — require reads the local Boolean: true reaches the marker, false raises and stops. */
    @Test
    void requireUsesTheLocalBoolean(@TempDir Path classes) throws Exception {
        String flow = """
                validate input
                order = create SalesOrder from input
                approved = call IsApproved(vip = vip)
                require approved otherwise rejected order
                set order.marker = 1.00
                save order
                return order
                """;

        Application accepted = run(classes, flow);
        Object response = accepted.placeOrder("A-1", new BigDecimal("100.00"), true);
        assertThat((BigDecimal) accepted.component(response, "marker")).isEqualByComparingTo("1.00");

        Application refused = run(classes.resolve("refused"), flow);
        assertThatThrownBy(() -> refused.placeOrder("A-1", new BigDecimal("100.00"), false))
                .hasRootCauseInstanceOf(Throwable.class)
                .satisfies(thrown -> assertThat(rootCause(thrown).getClass().getSimpleName())
                        .isEqualTo("RejectedOrderException"));
        assertThat(refused.saved())
                .as("the instruction after a failed require did not run")
                .isEmpty();
    }

    /** C9 — fail reads the local Boolean: true raises, false reaches the marker. */
    @Test
    void failUsesTheLocalBoolean(@TempDir Path classes) throws Exception {
        String flow = """
                validate input
                order = create SalesOrder from input
                refused = call IsRefused(vip = vip)
                fail rejected order when refused
                set order.marker = 1.00
                save order
                return order
                """;

        Application raised = run(classes, flow);
        assertThatThrownBy(() -> raised.placeOrder("A-1", new BigDecimal("100.00"), true))
                .satisfies(thrown -> assertThat(rootCause(thrown).getClass().getSimpleName())
                        .isEqualTo("RejectedOrderException"));
        assertThat(raised.saved()).isEmpty();

        Application passed = run(classes.resolve("passed"), flow);
        Object response = passed.placeOrder("A-1", new BigDecimal("100.00"), false);
        assertThat((BigDecimal) passed.component(response, "marker")).isEqualByComparingTo("1.00");
    }

    /** C10 — if reads the local Boolean and only one branch leaves its mark. */
    @Test
    void ifUsesTheLocalBoolean(@TempDir Path classes) throws Exception {
        String flow = """
                validate input
                order = create SalesOrder from input
                approved = call IsApproved(vip = vip)
                if approved
                    set order.marker = 1.00
                else
                    set order.marker = 2.00
                save order
                return order
                """;

        Application whenTrue = run(classes, flow);
        assertThat((BigDecimal) whenTrue.component(
                        whenTrue.placeOrder("A-1", new BigDecimal("100.00"), true), "marker"))
                .as("only the true branch ran").isEqualByComparingTo("1.00");

        Application whenFalse = run(classes.resolve("false"), flow);
        assertThat((BigDecimal) whenFalse.component(
                        whenFalse.placeOrder("A-1", new BigDecimal("100.00"), false), "marker"))
                .as("only the false branch ran").isEqualByComparingTo("2.00");
    }

    // --- harness -----------------------------------------------------------------------------

    private static Throwable rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static Object get(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    /** The generated application, loaded and wired with repositories that only remember. */
    private final class Application {

        private final ClassLoader loader;
        private final List<Object> saved = new ArrayList<>();
        private final List<Object> audits = new ArrayList<>();
        private final Object service;

        private Application(ClassLoader loader) throws Exception {
            this.loader = loader;
            Object auditService = instantiate(
                    "service.AuditEntryService", repository(audits, "repository.AuditEntryRepository"));
            this.service = instantiate(
                    "service.SalesOrderService",
                    repository(saved, "repository.SalesOrderRepository"),
                    auditService);
        }

        private Class<?> load(String name) throws ClassNotFoundException {
            return loader.loadClass("com.example.commerce." + name);
        }

        /**
         * A repository that answers {@code save} with what it was given and remembers it.
         *
         * <p>Persistence is not what these checks are about, and a real one would drag a database
         * into a question about which value an instruction read. What the stub does have to be is
         * honest about the one thing the flow depends on: {@code save} returns the entity.
         */
        private Object repository(List<Object> log, String type) throws ClassNotFoundException {
            Class<?> contract = load(type);
            return Proxy.newProxyInstance(
                    loader,
                    new Class<?>[] {contract},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("save")) {
                            log.add(arguments[0]);
                            return arguments[0];
                        }
                        return method.getReturnType().isPrimitive() ? false : null;
                    });
        }

        private Object instantiate(String type, Object... dependencies) throws Exception {
            Constructor<?> constructor = load(type).getConstructors()[0];
            return constructor.newInstance(
                    List.of(dependencies).subList(0, constructor.getParameterCount()).toArray());
        }

        /** Calls the generated command, building its request from the declared component order. */
        Object placeOrder(String orderNumber, BigDecimal total, boolean vip) {
            try {
                Class<?> request = load("dto.PlaceOrderRequest");
                Object payload = request.getConstructors()[0]
                        .newInstance(orderNumber, total, vip);
                Method method = List.of(service.getClass().getMethods()).stream()
                        .filter(candidate -> candidate.getName().equals("placeOrder"))
                        .findFirst()
                        .orElseThrow();
                return method.invoke(service, payload);
            } catch (InvocationTargetException failure) {
                throw new IllegalStateException(failure.getCause());
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        Object component(Object response, String name) throws Exception {
            return response.getClass().getMethod(name).invoke(response);
        }

        List<Object> saved() {
            return saved;
        }

        Object audited() {
            assertThat(audits).as("the called Command saved exactly one audit entry").hasSize(1);
            return audits.getFirst();
        }
    }

    private Application run(Path classes, String flow) throws Exception {
        project(flow);
        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));
        assertThat(result.diagnostics())
                .as("the fixture has to compile before it can be run")
                .isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();
        Files.createDirectories(classes);
        GeneratedJava.compiles(files, classes);
        URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] {classes.toUri().toURL()},
                FlowCompositionRuntimeTest.class.getClassLoader());
        return new Application(loader);
    }

    private void project(String flow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required unique
                - total: Decimal required default 0
                - marker: Decimal required default 0
                - vip: Boolean required default false

                ## Logic CalculateDiscount

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ```logic
                return total * 0.10
                ```

                ## Scenario Ten percent of a hundred

                ### Given

                - total: 100.00

                ### When

                CalculateDiscount

                ### Then

                - result: 10.0000

                ## Logic IsApproved

                ### Input

                - vip: Boolean

                ### Output

                Boolean

                ```logic
                return vip
                ```

                ## Scenario A vip is approved

                ### Given

                - vip: true

                ### When

                IsApproved

                ### Then

                - result: true

                ## Logic IsRefused

                ### Input

                - vip: Boolean

                ### Output

                Boolean

                ```logic
                return vip
                ```

                ## Scenario A vip is refused by this fixture

                ### Given

                - vip: true

                ### When

                IsRefused

                ### Then

                - result: true

                ## Command PlaceOrder

                ### Input

                - orderNumber: String required
                - total: Decimal required
                - vip: Boolean required

                ### Flow

                ```flow
                %s```

                ### Output

                201 SalesOrder

                ### Errors

                - rejected order -> 422
                """.formatted(flow), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("specs/audit.harpia.md"), """
                # AuditEntry

                ## Data

                - id: UUID generated
                - orderNumber: String required
                - amount: Decimal required

                ## Command RecordAudit

                ### Input

                - orderNumber: String required
                - amount: Decimal required

                ### Flow

                ```flow
                validate input
                entry = create AuditEntry from input
                save entry
                return nothing
                ```

                ### Output

                204 nothing
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: commerce-service
                  group: com.example
                  artifact: commerce-service
                  package: com.example.commerce

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.6"

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """, StandardCharsets.UTF_8);
    }
}
