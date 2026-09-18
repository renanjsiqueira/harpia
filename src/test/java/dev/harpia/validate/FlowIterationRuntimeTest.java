package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a loop does, counted and ordered by running it.
 *
 * <p>Counting body executions by reading the generated Java would count the text, not the runs, so
 * the body calls a Command whose repository remembers what it was given. The number of remembered
 * items is the number of times the body ran, their order is the order the loop walked, and their
 * fields are what the item's members delivered — three observations from one recording.
 */
class FlowIterationRuntimeTest {

    @TempDir
    Path projectRoot;

    /** C16 — zero, one and three items run the body zero, one and three times. */
    @Test
    void bodyCountMatchesCollectionSize(@TempDir Path classes) throws Exception {
        Application application = run(classes);

        assertThat(application.place()).as("an empty collection runs the body zero times").isEmpty();
        assertThat(application.place(item("A", 1, "1.00"))).hasSize(1);
        assertThat(application.place(item("A", 1, "1.00"), item("B", 1, "1.00"),
                        item("C", 1, "1.00")))
                .as("three items, three runs")
                .hasSize(3);
    }

    /** C17 — the body sees the collection in the order the collection has. */
    @Test
    void iterationPreservesCollectionOrder(@TempDir Path classes) throws Exception {
        Application application = run(classes);

        List<String> seen = application.place(
                item("A", 1, "1.00"), item("B", 1, "1.00"), item("C", 1, "1.00"));

        assertThat(seen).containsExactly("A", "B", "C");
    }

    /** C18 — reading a member of the item delivers that item's value to the operation called. */
    @Test
    void itemMembersReachTheConsumer(@TempDir Path classes) throws Exception {
        Application application = run(classes);

        application.place(item("A", 2, "1.00"), item("B", 3, "1.00"));

        assertThat(application.quantities())
                .as("two and three, from the items rather than from anywhere else")
                .containsExactly(2, 3);
    }

    /** C22 — items of 20.00 and 30.00 total exactly 50.00, with no binary floating point. */
    @Test
    void calculateTotalUsesExactDecimalArithmetic(@TempDir Path classes) throws Exception {
        Application application = run(classes);

        application.place(item("A", 1, "20.00"), item("B", 1, "30.00"));

        assertThat(application.total())
                .as("20.00 + 30.00, in Decimal")
                .isEqualByComparingTo("50.00");
        application.place(item("A", 1, "0.10"), item("B", 1, "0.20"));
        assertThat(application.total())
                .as("the cents a double would lose")
                .isEqualByComparingTo("0.30");
    }

    // --- harness -----------------------------------------------------------------------------

    private record Item(String sku, int quantity, String unitPrice) {
    }

    private static Item item(String sku, int quantity, String unitPrice) {
        return new Item(sku, quantity, unitPrice);
    }

    /** The generated application, wired with repositories that only remember what they are given. */
    private final class Application {

        private final ClassLoader loader;
        private final List<Object> orders = new ArrayList<>();
        private final List<Object> visits = new ArrayList<>();
        private final Object service;

        private Application(ClassLoader loader) throws Exception {
            this.loader = loader;
            Object visitService = instantiate(
                    "service.ItemVisitService", repository(visits, "repository.ItemVisitRepository"));
            this.service = instantiate(
                    "service.SalesOrderService",
                    repository(orders, "repository.SalesOrderRepository"),
                    visitService);
        }

        private Class<?> load(String name) throws ClassNotFoundException {
            return loader.loadClass("com.example.commerce." + name);
        }

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

        /**
         * Places an order with these items and answers the skus the body recorded, in order.
         *
         * <p>Each call starts from an empty recording, so what comes back is what this order did
         * and not what the test did before it.
         */
        List<String> place(Item... items) {
            orders.clear();
            visits.clear();
            try {
                Class<?> orderItem = load("domain.OrderItem");
                List<Object> payload = new ArrayList<>();
                for (Item item : items) {
                    Object entity = orderItem.getConstructor().newInstance();
                    orderItem.getMethod("setSku", String.class).invoke(entity, item.sku());
                    orderItem.getMethod("setQuantity", Integer.class)
                            .invoke(entity, item.quantity());
                    orderItem.getMethod("setUnitPrice", BigDecimal.class)
                            .invoke(entity, new BigDecimal(item.unitPrice()));
                    payload.add(entity);
                }
                Object request = load("dto.PlaceOrderRequest").getConstructors()[0]
                        .newInstance("A-1", payload);
                List.of(service.getClass().getMethods()).stream()
                        .filter(method -> method.getName().equals("placeOrder"))
                        .findFirst()
                        .orElseThrow()
                        .invoke(service, request);
                return recorded("getSku").stream().map(String.class::cast).toList();
            } catch (InvocationTargetException failure) {
                throw new IllegalStateException(failure.getCause());
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        List<Object> recorded(String accessor) {
            return visits.stream().map(visit -> {
                try {
                    return visit.getClass().getMethod(accessor).invoke(visit);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }).toList();
        }

        List<Integer> quantities() {
            return recorded("getQuantity").stream().map(Integer.class::cast).toList();
        }

        BigDecimal total() throws Exception {
            assertThat(orders).as("the order was saved once").hasSize(1);
            return (BigDecimal) orders.getFirst().getClass().getMethod("getTotal")
                    .invoke(orders.getFirst());
        }
    }

    private Application run(Path classes) throws Exception {
        project();
        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));
        assertThat(result.diagnostics()).isEmpty();
        Files.createDirectories(classes);
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
        return new Application(new URLClassLoader(
                new java.net.URL[] {classes.toUri().toURL()},
                FlowIterationRuntimeTest.class.getClassLoader()));
    }

    private void project() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required unique
                - total: Decimal required default 0
                - items: List<OrderItem> owned required

                ## Logic AddToTotal

                ### Input

                - current: Decimal
                - amount: Decimal

                ### Output

                Decimal

                ```logic
                return current + amount
                ```

                ## Scenario Twenty and thirty

                ### Given

                - current: 20.00
                - amount: 30.00

                ### When

                AddToTotal

                ### Then

                - result: 50.00

                ## Command PlaceOrder

                ### Input

                - orderNumber: String required
                - items: List<OrderItem> required

                ### Flow

                ```flow
                validate input
                order = create SalesOrder from input
                for each line in items
                    call RecordVisit(sku = line.sku, quantity = line.quantity)
                    running = call AddToTotal(current = order.total, amount = line.unitPrice)
                    set order.total = running
                save order
                return order
                ```

                ### Output

                201 SalesOrder
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("specs/item.harpia.md"), """
                # OrderItem

                ## Data

                - id: UUID generated
                - sku: String required
                - quantity: Int required
                - unitPrice: Decimal required

                ## Query GetOrderItem

                ### Flow

                ```flow
                item = load OrderItem by id
                return item
                ```

                ### Output

                200 OrderItem
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("specs/visit.harpia.md"), """
                # ItemVisit

                ## Data

                - id: UUID generated
                - sku: String required
                - quantity: Int required

                ## Command RecordVisit

                ### Input

                - sku: String required
                - quantity: Int required

                ### Flow

                ```flow
                validate input
                visit = create ItemVisit from input
                save visit
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
