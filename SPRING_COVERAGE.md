# Harpia Spring Coverage

> **Snapshot histórico não normativo.** A cobertura Spring atual e seus itens canônicos vivem em
> [`BACKLOG.md`](BACKLOG.md). Esta matriz foi preservada como referência de superfície e pode conter
> estados anteriores ao fechamento do CRUD V0.

Esta matriz mede a superfície de aplicações Spring Boot que possui um caminho Harpia. Ela **não**
mede classes, annotations ou extension points do framework.

Princípio: **Harpia should cover the Spring Boot application surface, not reproduce the Spring
Boot API surface.** A coluna “Path” usa `A` (Semantic), `B` (Capability/Provider), `C` (Custom Java)
e `D` (Not Planned).

Status verificado em 2026-09-06. O slice bootstrap/entity/repository/Flyway é escrito, comparado
com golden e passa `mvn -o test`; o gate CRUD/REST completo ainda depende de DTO/service/controller.

| Status | Meaning |
|---|---|
| `SUPPORTED` | o gate end-to-end específico passou |
| `PARTIAL` | existe model/provider/output útil, mas o gate não fechou |
| `PLANNED` | arquitetura aceita; nenhuma implementação comprovada |
| `RESEARCH` | exige ADR/spike antes de compromisso |
| `NOT_PLANNED` | superfície deliberadamente fora da linguagem |

## Método de cálculo

- Universo elegível: linhas `SPRxxx` com status `SUPPORTED`, `PARTIAL`, `PLANNED` ou `RESEARCH`.
- Cobertura estrita: `SUPPORTED / total`.
- Progresso ponderado: `(SUPPORTED + 0,5 × PARTIAL) / total`.
- `CUSTOM` é um caminho de produto, mas não conta como implementação antes do escape hatch existir.
- `NOT_PLANNED` documenta uma fronteira e fica fora do denominador de implementação.
- “Path coverage” mede somente classificação arquitetural, não software entregue.

Os totais são recalculados no final do documento; nenhuma dependência apenas declarada no pom conta
como uma feature Spring suportada.

## Core, configuration and runtime model

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR001 | Spring Boot application bootstrap | B | spring-boot | `SUPPORTED` | P0 | S | MT0 | Java Target Model + renderer; Maven compila |
| SPR002 | Java 21 target | B | javac/Maven | `SUPPORTED` | P0 | XS | MT0 | release 21 e generated Maven gate verdes |
| SPR003 | Maven dependency/build model | B | Maven | `SUPPORTED` | P0 | S | MT0 | dependências resolvidas fora do template e build offline verde |
| SPR004 | External `application.yaml` | B | spring-config | `SUPPORTED` | P0 | S | MT0 | template determinístico alimentado por propriedades resolvidas |
| SPR005 | Environment variables | B | environment | `PARTIAL` | P0 | XS | SPR004 | datasource usa placeholders sem segredo inline |
| SPR006 | Typed application properties | B | spring-config | `PLANNED` | P2 | M | F1 | config Harpia permanece independente de Spring |
| SPR007 | Environments dev/test/staging/prod | B | environment | `PLANNED` | P2 | L | SPR006 | W11 |
| SPR008 | Spring Profiles mapping | B | spring-profiles | `PLANNED` | P3 | M | SPR007 | provider concern, não domínio |
| SPR009 | Secret references/providers | B | environment/vault future | `PARTIAL` | P1 | L | SPR006 | apenas datasource por ambiente existe |
| SPR010 | Configuration metadata | B | spring-configuration-processor | `PLANNED` | P3 | M | SPR006 | W11 |
| SPR011 | Graceful shutdown | B | spring-runtime | `PLANNED` | P2 | S | E0 | W11 |
| SPR012 | SSL/TLS server configuration | B | server provider | `PLANNED` | P2 | M | EP10 | W11 |

## Web and transport

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR013 | Spring MVC provider selection | B | spring-mvc | `SUPPORTED` | P0 | S | MT0 | capability HTTP e starters web/validation resolvidos no target |
| SPR014 | REST endpoints | A+B | spring-mvc | `PARTIAL` | P0 | L | E0.7 | syntax/IR existem; controller não |
| SPR015 | Controllers | B | spring-mvc | `PLANNED` | P0 | M | SPR014/E0.7 | emitter ausente |
| SPR016 | Request DTOs | A+B | serialization | `PLANNED` | P0 | M | E0.6 | input IR existe |
| SPR017 | Response DTOs | A+B | serialization | `PLANNED` | P0 | M | E0.6 | output IR existe |
| SPR018 | Jakarta Validation | A+B | spring-validation | `PARTIAL` | P0 | M | E0.6 | entity recebe Email/NotBlank/NotNull; request DTO ainda falta |
| SPR019 | Uniform exception handling | A+B | spring-mvc | `PLANNED` | P0 | M | E0.6 | errors V0 já são semânticos |
| SPR020 | JSON serialization | B | Jackson/Spring MVC | `PLANNED` | P0 | S | SPR016/SPR017 | W1 |
| SPR021 | XML serialization | B | Jackson XML | `PLANNED` | P3 | M | contracts | W11 |
| SPR022 | OpenAPI export | B | springdoc | `PLANNED` | P1 | L | contracts + SPR014 | W6 |
| SPR023 | OpenAPI import | tooling | importer | `PLANNED` | P2 | XL | Project AST | W10 |
| SPR024 | WebFlux | B | spring-webflux | `PLANNED` | P2 | XL | protocol independence | W11 |
| SPR025 | Reactive execution model | B | Reactor | `PLANNED` | P2 | XL | effects + ADR-008 | W11 |
| SPR026 | GraphQL | A+B | spring-graphql | `PLANNED` | P2 | XL | Command/Query bindings | W11 |
| SPR027 | WebSocket | A+B | spring-websocket | `PLANNED` | P3 | XL | Event/subscription | W11 |
| SPR028 | SSE | A+B | spring-mvc/webflux | `PLANNED` | P3 | L | Event/subscription | W11 |
| SPR029 | Static resources | B | spring-web | `PLANNED` | P3 | S | config | W11 |
| SPR030 | Server-side views | B | Thymeleaf/FreeMarker/Mustache | `PLANNED` | P3 | L | view abstraction | W11 |

## Data and persistence

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR031 | Persistence capability | A+B | persistence | `PARTIAL` | P0 | M | E0.1 | inferred from entity/flow |
| SPR032 | Spring Data JPA provider | B | spring-data-jpa | `SUPPORTED` | P0 | L | E0.3–E0.5 | dependency, entity e repository gerados/compilados |
| SPR033 | JPA entity mapping | B | Hibernate/JPA | `SUPPORTED` | P0 | M | E0.3/E0.4 | mapper resolve annotations no target; Business IR permanece limpa |
| SPR034 | Spring Data repository | B | spring-data-jpa | `SUPPORTED` | P0 | M | E0.5 | interface tipada gerada pelo Java Target Model |
| SPR035 | Local transactions | A+B | spring-tx | `PLANNED` | P0 | M | Command/effect model | S1/S3 |
| SPR036 | Read-only transactions | A+B | spring-tx | `PLANNED` | P1 | S | Query | S3 |
| SPR037 | PostgreSQL | B | postgresql | `PARTIAL` | P0 | M | E0.2/E0.5 | driver/config/schema gerados; falta teste contra banco real |
| SPR038 | Flyway migrations | B | flyway | `PARTIAL` | P0 | M | E0.5 | SQL determinístico e golden; falta aplicar em PostgreSQL no gate |
| SPR039 | Liquibase migrations | B | liquibase | `PLANNED` | P3 | L | migration abstraction | W11 |
| SPR040 | Spring Data JDBC | B | spring-data-jdbc | `PLANNED` | P2 | XL | persistence contract proven by JPA | W11 |
| SPR041 | Spring Data R2DBC | B | spring-data-r2dbc | `PLANNED` | P3 | XL | reactive + persistence | W11 |
| SPR042 | MongoDB | B | spring-data-mongodb | `PLANNED` | P2 | XL | document model ADR | W11 |
| SPR043 | Redis data | B | spring-data-redis | `PLANNED` | P2 | L | key-value capability | W11 |
| SPR044 | Elasticsearch | B | spring-data-elasticsearch | `PLANNED` | P3 | XL | search abstraction | W11 |
| SPR045 | Cassandra | B | spring-data-cassandra | `PLANNED` | P3 | XL | wide-column abstraction | W11 |
| SPR046 | Couchbase | B | spring-data-couchbase | `PLANNED` | P3 | XL | document abstraction | W11 |
| SPR047 | Neo4j | B | spring-data-neo4j | `PLANNED` | P3 | XL | graph abstraction | W11 |
| SPR048 | LDAP data access | B | spring-ldap | `PLANNED` | P3 | L | directory abstraction | W11 |
| SPR049 | Schema import | tooling | database importer | `PLANNED` | P2 | XL | Project AST/type mapping | W10 |
| SPR050 | Schema snapshot/diff/history | B+tooling | migration engine | `PLANNED` | P2 | XL | E0.5 + semantic diff | W10 |
| SPR051 | Optimistic locking | A+B | JPA/version provider | `PLANNED` | P2 | M | Entity lifecycle | W12 |
| SPR052 | Soft delete | A+B | persistence provider | `PLANNED` | P2 | L | Entity lifecycle/query | W12 |
| SPR053 | Retention/TTL | A+B | persistence/scheduler | `PLANNED` | P2 | L | lifecycle + scheduling | W12 |

## Cache and storage

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR054 | Spring Cache abstraction | B | spring-cache | `PLANNED` | P2 | M | Query/cache semantics | W11 |
| SPR055 | Local cache | B | caffeine/simple | `PLANNED` | P2 | M | SPR054 | first provider |
| SPR056 | Redis cache | B | spring-data-redis | `PLANNED` | P2 | M | SPR054/SPR055 | distributed provider |
| SPR057 | Filesystem storage | A+B | Java NIO/Spring Resource | `PLANNED` | P2 | L | Storage contract | W12 |
| SPR058 | S3/Azure Blob storage | B+C | cloud/custom | `PLANNED` | P3 | XL | SPR057 + Custom Java | W12/Custom |

## Events and messaging

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR059 | Local domain events | A+B | ApplicationEventPublisher | `PLANNED` | P1 | L | Event + emit | S1 |
| SPR060 | Event handlers | A+B | Spring listener | `PLANNED` | P1 | L | SPR059 | pós-S1 |
| SPR061 | JMS | B | spring-jms | `PLANNED` | P3 | XL | messaging contract | W11 |
| SPR062 | ActiveMQ | B | spring-jms | `PLANNED` | P3 | L | SPR061 | W11 |
| SPR063 | Artemis | B | spring-jms | `PLANNED` | P3 | L | SPR061 | W11 |
| SPR064 | AMQP | B | spring-amqp | `PLANNED` | P2 | XL | messaging contract | W11 |
| SPR065 | RabbitMQ | B | spring-amqp | `PLANNED` | P2 | L | SPR064 | W11 |
| SPR066 | Kafka | B | spring-kafka | `PLANNED` | P2 | XL | local events + delivery semantics | W11 |
| SPR067 | Pulsar | B | spring-pulsar | `PLANNED` | P3 | XL | messaging contract | W11 |
| SPR068 | STOMP/WebSocket messaging | B | spring-messaging | `PLANNED` | P3 | XL | channels/subscriptions | W11 |
| SPR069 | Topic/queue/order/partition semantics | A+B | messaging provider | `PLANNED` | P2 | XL | Event/Handler | W11 |
| SPR070 | Dead letter/retry/delivery semantics | A+B | messaging/reliability | `PLANNED` | P2 | XL | idempotency + SPR069 | W11 |
| SPR071 | AsyncAPI import/export | tooling | AsyncAPI adapter | `PLANNED` | P3 | XL | Event contracts | W10/W11 |

## Security

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR072 | Spring Security baseline | B | spring-security | `PLANNED` | P1 | L | Access/Policy | W6 |
| SPR073 | Authentication | A+B | auth provider | `PLANNED` | P1 | L | SPR072 | W6 |
| SPR074 | Authorization/roles | A+B | auth provider | `PLANNED` | P1 | L | Rules/Policy | W6 |
| SPR075 | JWT resource server | B | spring-security-oauth2 | `PLANNED` | P1 | L | SPR073 | first auth provider |
| SPR076 | OAuth2 | B | spring-security-oauth2 | `PLANNED` | P2 | XL | auth contract | W11 |
| SPR077 | OIDC | B | spring-security-oauth2 | `PLANNED` | P2 | XL | SPR076 | W11 |
| SPR078 | Method-security equivalent | A+B | spring-security | `PLANNED` | P2 | M | Policy | W6 |
| SPR079 | LDAP authentication | B | spring-security-ldap | `PLANNED` | P3 | L | auth contract | W11 |
| SPR080 | Authorization Server | B+C | Spring Authorization Server | `PLANNED` | P3 | XL | mature security model | W13 |

## Integrations, email and resilience

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR081 | Synchronous REST client | A+B | RestClient | `PLANNED` | P1 | L | Integration contract | S5 |
| SPR082 | Reactive REST client | A+B | WebClient | `PLANNED` | P3 | XL | Integration + reactive | W11 |
| SPR083 | SOAP client | A+B+C | Spring WS/custom | `PLANNED` | P3 | XL | Integration contract | W11 |
| SPR084 | SMTP email | A+B | spring-mail | `PLANNED` | P1 | L | Email + secrets | W6 |
| SPR085 | Inbound webhook | A+B | spring-mvc | `PLANNED` | P2 | M | Command/Endpoint | W7 |
| SPR086 | Outbound webhook | A+B | HTTP + Event | `PLANNED` | P2 | L | Event/Integration | W7 |
| SPR087 | Timeout | B | client provider | `PLANNED` | P1 | M | Integration | S5 |
| SPR088 | Retry/backoff | B | resilience provider | `PLANNED` | P2 | L | idempotency + timeout | W7 |
| SPR089 | Circuit breaker/fallback/bulkhead | B | resilience provider | `PLANNED` | P2 | XL | SPR087/SPR088 | W11 |
| SPR090 | Rate limiting | A+B | web/cache provider | `PLANNED` | P2 | L | identity + cache | W12 |
| SPR091 | Idempotency store/replay | A+B | persistence/cache | `PLANNED` | P1 | XL | Command/transactions | W7 |
| SPR092 | JTA/XA distributed transactions | B | JTA provider | `PLANNED` | P3 | XL | integrations + saga evidence | W12 |

## Scheduling, batch and execution

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR093 | Spring Scheduling | A+B | spring-scheduling | `PLANNED` | P2 | L | Schedule + Command | W12 |
| SPR094 | Quartz | B | quartz | `PLANNED` | P3 | L | Schedule contract | W12 |
| SPR095 | Spring Batch | A+B | spring-batch | `PLANNED` | P2 | XL | Job + Scenario + reliability | W12 |
| SPR096 | Reader/processor/writer/chunk | A+B | spring-batch | `PLANNED` | P2 | XL | SPR095 | W12 |
| SPR097 | Batch retry/skip/restart | A+B | spring-batch | `PLANNED` | P2 | XL | idempotency + SPR095 | W12 |
| SPR098 | Async execution | A+B | task executor | `PLANNED` | P2 | L | effect model | W12 |
| SPR099 | Virtual-thread provider | B | Java 21 | `PLANNED` | P2 | M | SPR098 | W12 |
| SPR100 | Parallel/structured execution | A+B | execution provider | `PLANNED` | P3 | XL | deterministic semantics | W12 |

## Observability and management

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR101 | Technical logging | B | SLF4J/logging | `PLANNED` | P2 | M | operation model | W11 |
| SPR102 | Micrometer metrics | B | micrometer | `PLANNED` | P2 | L | operation model | W11 |
| SPR103 | OpenTelemetry traces | B | otel | `PLANNED` | P2 | L | operation model | W11 |
| SPR104 | Prometheus | B | micrometer-registry | `PLANNED` | P2 | M | SPR102 | W11 |
| SPR105 | Datadog/New Relic/OTLP | B | registry/exporter | `PLANNED` | P3 | L | SPR102/SPR103 | W11 |
| SPR106 | Automatic operation instrumentation | B | observability provider | `PLANNED` | P2 | L | Command/Query/Event/Integration | W11 |
| SPR107 | Actuator baseline | B | spring-actuator | `PLANNED` | P2 | M | security/config | W11 |
| SPR108 | Health/readiness/liveness | B | actuator/provider health | `PLANNED` | P2 | M | capabilities | W11 |
| SPR109 | Actuator info/metrics/prometheus | B | actuator | `PLANNED` | P2 | M | SPR102/SPR107 | W11 |
| SPR110 | Actuator env/configprops/mappings/beans | B | actuator | `PLANNED` | P3 | M | SPR107/security | W11 |
| SPR111 | HTTP exchanges/scheduled tasks | B | actuator | `PLANNED` | P3 | M | SPR093/SPR107 | W11 |
| SPR112 | Thread dump/heap dump/loggers | B | actuator/JVM | `PLANNED` | P3 | M | explicit secure exposure | W11 |
| SPR113 | JMX management | B | JMX | `PLANNED` | P3 | L | management abstraction | W11 |

## Testing and development services

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR114 | JUnit tests in generated project | A+B | JUnit/Spring Test | `PLANNED` | P0 | L | E0.9 | generator tests absent |
| SPR115 | MVC slice tests | B | `@WebMvcTest` equivalent | `PLANNED` | P0 | M | E0.7/E0.9 | W1 |
| SPR116 | Service unit tests | B | Mockito/JUnit | `PLANNED` | P0 | M | E0.7/E0.9 | W1 |
| SPR117 | Data slice tests | B | `@DataJpaTest` equivalent | `PLANNED` | P1 | M | E0.5/Scenario | W5 |
| SPR118 | Security tests | B | spring-security-test | `PLANNED` | P1 | L | EP10 | W6 |
| SPR119 | GraphQL/messaging/JSON slices | B | Spring Test | `PLANNED` | P3 | XL | respective providers | W11 |
| SPR120 | Testcontainers | B | testcontainers | `PLANNED` | P2 | L | provider catalog | W11 |
| SPR121 | Spring Service Connections | B | spring-boot-testcontainers | `PLANNED` | P2 | M | SPR120 | W11 |
| SPR122 | Docker Compose dev services | B | spring-boot-docker-compose | `PLANNED` | P3 | M | providers | W11 |
| SPR123 | DevTools | B | spring-boot-devtools | `PLANNED` | P3 | XS | packaging profile | W11 |

## Packaging and Spring ecosystem

| ID | Spring area/feature | Harpia path | Provider | Status | P | Effort | Depends | Milestone / evidence |
|---|---|---|---|---|---|---|---|---|
| SPR124 | Executable JAR | B | spring-boot-maven-plugin | `PARTIAL` | P0 | M | E0.9 | plugin emitido; aplicação incompleta |
| SPR125 | WAR | B | servlet container | `PLANNED` | P3 | M | packaging abstraction | W11 |
| SPR126 | Layered JAR | B | spring-boot plugin | `PLANNED` | P3 | S | SPR124 | W11 |
| SPR127 | Docker image | B | container provider | `PLANNED` | P2 | L | executable JAR | W11 |
| SPR128 | Buildpacks | B | Spring Boot build-image | `PLANNED` | P2 | M | SPR127 | W11 |
| SPR129 | AOT | B | spring-aot | `PLANNED` | P3 | XL | full generator | W11 |
| SPR130 | GraalVM native image | B | native build tools | `PLANNED` | P3 | XL | SPR129 | W11 |
| SPR131 | Checkpoint/Restore | B | CRaC/runtime | `PLANNED` | P3 | XL | runtime model | W11 |
| SPR132 | Spring Modulith | A+B | modulith | `RESEARCH` | P3 | XL | Module/Event/architecture rules | W13 |
| SPR133 | Spring Cloud Gateway | B | cloud-gateway | `RESEARCH` | P3 | XL | API/Policy | W13 |
| SPR134 | Spring Cloud Config/Discovery | B | spring-cloud | `RESEARCH` | P3 | XL | environment/service model | W13 |
| SPR135 | Spring Cloud Stream | B | cloud-stream | `RESEARCH` | P3 | XL | messaging abstraction | W13 |
| SPR136 | Spring Integration | B+C | spring-integration | `RESEARCH` | P3 | XL | Integration/Flow boundary | W13 |
| SPR137 | Spring Authorization Server | B+C | auth-server | `RESEARCH` | P3 | XL | mature security model | W13 |
| SPR138 | Spring AI in generated applications | B+C | spring-ai/custom | `RESEARCH` | P3 | XL | runtime AI use case | W13; não é Harpia MCP |

## Boundaries that will not become language keywords

| ID | Spring API surface | Path | Status | Reason |
|---|---|---|---|---|
| SPR139 | Annotation-by-annotation DSL | D | `NOT_PLANNED` | intenção substitui `@Cacheable`, `@Scheduled`, `@PreAuthorize`, etc. |
| SPR140 | `Mono`/`Flux` in Business Spec | D | `NOT_PLANNED` | execution model belongs to provider/application configuration |
| SPR141 | `JpaRepository`/JPA annotations in Business IR | D | `NOT_PLANNED` | persistence provider owns framework mapping |
| SPR142 | `RestClient`/`WebClient` in Business Spec | D | `NOT_PLANNED` | Integration expresses the port, provider selects client |
| SPR143 | `logger.info` language statements | D | `NOT_PLANNED` | technical logging is generated; business meaning uses Event |

## Current result

This section is calculated from the enumerated rows and must be checked whenever the table changes.

```text
Eligible Spring application features: 138
SUPPORTED: 8
PARTIAL: 8
PLANNED: 115
RESEARCH: 7
NOT_PLANNED boundaries: 5 (excluded)

SPRING COVERAGE % (strict end-to-end): 8 / 138 = 5.8%
SPRING PROGRESS % (weighted): (8 + 0.5 × 8) / 138 = 8.7%
SPRING PATH CLASSIFICATION: 143/143 = 100%
```

The 100% classification result means every listed area has an architectural destination; it does
**not** mean it is implemented. Spring coverage becomes non-zero only after a row passes its own
end-to-end gate.
