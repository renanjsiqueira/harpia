# Harpia

> **Describe backend intent. Generate the starting codebase. Own the result.**

Harpia is an open-source semantic specification language and backend project bootstrapper for
humans and AI agents. You describe application intent in structured Markdown; a deterministic
compiler turns the supported semantics into conventional source code.

The first and currently supported target is **Java 21+ with Spring Boot**.

[English](#english) · [Português](#português) · [Current status](BACKLOG.md) ·
[Architecture](ARCHITECTURE.md) · [Language draft](docs/spec/harpia-language-v1-draft.md) ·
[License](LICENSE)

```text
Human or AI → *.harpia.md → semantic compiler → Java/Spring project → developer-owned code
```

## English

### Why Harpia?

LLMs are useful for reasoning about requirements, but repeatedly generating framework boilerplate
is expensive and probabilistic. The same prompt can produce different architectures, libraries,
validation strategies, error handling, and tests.

Harpia separates reasoning from mechanical implementation:

- humans and AI describe **what the backend means**;
- Harpia validates that intent and transforms it into a target-independent application model;
- a target deterministically materializes the supported implementation;
- developers receive normal source code with no Harpia runtime dependency.

> **AI may be probabilistic. Compilation should not be.**

Harpia is not a smaller Java syntax, a natural-language interpreter, a low-code runtime, or an AI
agent. It is a compact semantic layer for recurring backend concepts.

For example:

```text
email: Email required unique
```

is one declaration that the current Java/Spring target can use to derive Java types, bean
validation, JPA metadata, a database uniqueness constraint, request/response models, duplicate
handling, and tests. This is **semantic compression**: more software meaning, not merely fewer
characters.

### Current status

Harpia is under active development. The executable V0 and the first V1 slice currently provide:

| Area | Available today |
|---|---|
| Input | `harpia.yaml`, one or more `spec/*.harpia.md` files, and optional `bindings/*.harpia.md` files |
| Language | Scalar entities, CRUD-oriented V0 use cases, explicit V1 `Command`/`Query`, a closed Flow DSL, pure typed Logic, Logic scenarios |
| CLI | `validate`, `build`, `inspect`, `targets`, and `version` |
| Target | `java-spring` |
| Generated project | Maven, Spring Boot, services, JPA, Flyway and tests; REST adapters only for operations with an HTTP binding |
| Guarantees | Deterministic ordering, stable diagnostics, idempotent writes, manifest-based cleanup, and no Harpia runtime dependency |

External binding files, integrations, events, security, MCP, brownfield reconstruction, and
additional targets belong to the roadmap; they are not silently accepted by the current compiler.
[`BACKLOG.md`](BACKLOG.md) is the canonical source for implementation status and priority.

### Quick start

Requirements:

- JDK 21 or newer;
- Maven 3.9 or newer.

Build the Harpia CLI from the repository root:

```bash
mvn package
```

Validate and compile the included customer service:

```bash
./bin/harpia validate --dir examples/customer
./bin/harpia build --dir examples/customer
```

Expected summaries:

```text
Validation succeeded.
Build succeeded: 16 files in generated (...).
```

Then test the generated project as an ordinary Maven application:

```bash
mvn -f examples/customer/generated/pom.xml test
```

The complete working example is in [`examples/customer`](examples/customer).

### Project structure

A V1 project using the default paths has this structure:

```text
customer-service/
├── harpia.yaml
├── spec/
│   └── customer.harpia.md
└── bindings/
    └── http.harpia.md       # optional
```

`harpia.yaml` selects the target, project coordinates, source directory, and output directory:

```yaml
harpia:
  schemaVersion: 1
  languageVersion: 1

project:
  name: customer-service
  group: com.example
  artifact: customer-service
  package: com.example.customer

target:
  id: java-spring
  language:
    version: 21
  options:
    springBootVersion: "3.3.6"
  properties:
    server.port: "8081"
    logging.level.root: "INFO"

database:
  vendor: postgres

security:
  provider: basic

paths:
  specs: spec
  bindings: bindings
  output: generated

generation:
  migrations: true
  tests: true
```

`target.properties` is deployment configuration the project states for itself. It lives inside the
target block because a property name belongs to a framework and not to a specification: nothing
above the target boundary reads these. They are written into the generated `application.yaml` in one
sorted order whatever order they were typed in, and a key the generated project already sets is
refused rather than overwritten — the provider chose `spring.jpa.hibernate.ddl-auto: validate` to
match the migrations Harpia generated, and a project quietly flipping it would make those
migrations a lie.

`harpia.languageVersion` selects the Harpia grammar and semantics. It is intentionally independent
from `target.language.version`, which selects Java 21 for the `java-spring` target. Version `0` is
the stable CRUD grammar; version `1` currently adds explicit `Command` and `Query` declarations.

The Markdown specification contains readable documentation and formal sections. This minimal
excerpt mirrors the included [`customer.harpia.md`](examples/customer/specs/customer.harpia.md):

````markdown
# Customer

Represents a customer registered in the platform.

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean required default true

## Create Customer

### Endpoint

POST /customers

### Access

public

### Input

- name: String required
- email: Email required

### Flow

```flow
validate input
customer = create Customer from input
save customer
return customer
```

### Output

201 Customer

### Errors

- invalid input -> 400
- duplicate email -> 409
````

### Internal Command and Query (V1)

Set `harpia.languageVersion: 1` and prefix the operation with `Command` or `Query`. `Endpoint` and
`Access` are optional as a pair, so an operation can exist without exposing HTTP:

````markdown
## Query List Customers

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

Running the same CLI commands generates the application service and its tests, but no controller
for this operation. To expose it, add `bindings/http.harpia.md`:

````markdown
# HTTP Bindings

## Bind ListCustomers

### Endpoint

GET /customers

### Access

public
````

The operation symbol is its title without spaces. The compiler resolves it, checks duplicate
bindings and routes, validates `{id}` against the Flow, infers the HTTP capability, and then lets
the Java/Spring target generate the web adapter. See the exact executable grammar in
[`harpia-bindings-v1.md`](docs/spec/harpia-bindings-v1.md).

The full example also declares get, list, update, and delete use cases. Harpia generates this
application baseline:

```text
generated/
├── .harpia-manifest
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/customer/
    │   │   ├── CustomerServiceApplication.java
    │   │   ├── domain/Customer.java
    │   │   ├── dto/{CreateCustomerRequest,UpdateCustomerRequest,CustomerResponse}.java
    │   │   ├── error/{ApiError,ApiExceptionHandler,NotFoundException}.java
    │   │   ├── repository/CustomerRepository.java
    │   │   ├── service/CustomerService.java
    │   │   └── web/CustomerController.java
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/V1__init.sql
    └── test/java/com/example/customer/
        ├── service/CustomerServiceTest.java
        └── web/CustomerControllerTest.java
```

To start the generated service, provide a PostgreSQL database and run Spring Boot:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/customer
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
mvn -f examples/customer/generated/pom.xml spring-boot:run
```

### Harpia Logic

Business calculations can be expressed as pure, typed functions. They compile to ordinary static
Java methods without Spring or a Harpia runtime:

````markdown
## Logic CalculateDiscount

### Input

- total: Decimal
- vip: Boolean

### Output

Decimal

```logic
if vip
    return total * 0.20

if total >= 1000
    return total * 0.10

return 0
```
````

Logic supports typed expressions, single-assignment variables, `if`/`else`, named calls, and
`min`/`max`. Side effects such as persistence and messaging are rejected inside Logic.

> **Flow describes what happens. Logic describes how business values are computed.**

Scenarios turn declared examples into generated JUnit tests:

````markdown
## Scenario VIP discount

### Given

- total: 100
- vip: true

### When

CalculateDiscount

### Then

- result: 20.00
````

Try the complete example:

```bash
./bin/harpia validate --dir examples/business-logic/pricing
./bin/harpia build --dir examples/business-logic/pricing
mvn -f examples/business-logic/pricing/generated/pom.xml test
```

See the [Harpia Logic specification](docs/spec/harpia-logic-v1-draft.md) for its exact grammar and
implemented boundary.

### Language surface available today

The current language deliberately has a narrow, validated surface:

- scalar field types: `String`, `Text`, `Int`, `Long`, `Decimal`, `Boolean`, `UUID`, `Email`,
  `Date`, and `DateTime`;
- field modifiers: `required`, `unique`, `generated`, `default`, and V1 relationship `owned`;
- HTTP methods: `GET`, `POST`, `PUT`, and `DELETE`;
- access mode: `public`;
- output forms: `Entity`, `List<Entity>`, and `nothing` with a 2xx status;
- V1 entity relationships: `customer: Customer`, shared `items: List<Item>`, and dependent
  `items: List<Item> owned`; `Reference<Customer>` remains an identity-only foreign key;
- V1 typed outbound ports: `## Integration FraudService` with operation `Input`, `Output`, and
  named `Errors`; transport details remain outside the semantic specification;
- declared errors: invalid input (400), duplicate field (409), and not found (404).

Flow V0 accepts these operation shapes:

```text
validate input
x = create Entity from input
x = load Entity by id
update x from input
xs = list Entity
save x
delete x
return x|nothing
```

Flow V1 adds small, typed orchestration commands. A positive precondition names the domain error
that is raised when it is false; the status is declared once in `### Errors`:

```text
require amount <= balance otherwise insufficient balance
fail insufficient balance when amount > balance
```

Normal Markdown prose remains documentation. It never silently becomes executable behavior.
Arbitrary Java, authentication, events, integrations, and unrecognized Flow sentences are rejected
instead of guessed.

A minimal transport-independent integration contract looks like this:

````markdown
## Integration FraudService

### Operation CheckOrder

#### Input

- orderId: UUID required

#### Output

FraudResult

#### Errors

- RateLimited
- ServiceUnavailable

## Value FraudResult

- approved: Boolean required
- score: Decimal required
````

`Integration` exchanges scalar, `Enum`, and `Value` data. It deliberately does not name HTTP,
Spring, URLs, credentials, or persistence entities; bindings and providers supply those details.

Every generated client carries a deadline: a call that can hang forever is not a dependency, it is
a thread held until something else gives up. How long to wait follows the network and the agreement
with the other side rather than anything the specification said, so it is read from configuration —
`harpia.integration.connect-timeout` and `harpia.integration.read-timeout`, defaulting to `2s` and
`10s`. It is applied by a generated `RestClientCustomizer` rather than inside each client, so a test
can still install its own request factory the way `MockRestServiceServer` does.

A response is checked against the contract the port declared: a body that omits something the
declaration called required is not a smaller answer, it is not an answer, and it raises the port's
exception instead of arriving as `null` for the flow to carry on with. The violations are sorted, so
the same bad response always says the same thing.

A failed call raises the port's own exception — `<Integration>Exception` — carrying the operation
and, when there was a response, its status. An Integration exists so the caller does not have to
know how the other side is reached, and an exception is part of what a caller has to know: without
this it would catch `RestClientResponseException`, Spring vocabulary arriving through a declaration
that never mentioned HTTP.

### Catalog

[`docs/catalog.md`](docs/catalog.md) is the short answer to "how do I say X". Every entry is a whole
specification rather than a fragment, so one can be copied into `specs/` and built as-is, and every
entry is compiled by `CatalogTest` on each run: an example that stops working fails the build
instead of misleading a reader. Entries marked with a diagnostic code show what is refused, and the
refusal is verified too.

### CLI

```bash
./bin/harpia --help
./bin/harpia validate --dir <project>
./bin/harpia validate --dir <project> --json
./bin/harpia build --dir <project>
./bin/harpia build --dir <project> --json
./bin/harpia build --dir <project> --clean
./bin/harpia build --dir <project> --clean --force
./bin/harpia inspect --dir <project> --stage ast
./bin/harpia inspect --dir <project> --stage symbols
./bin/harpia inspect --dir <project> --stage business-ir
./bin/harpia inspect --dir <project> --stage application-ir
./bin/harpia inspect --dir <project> --stage business-ir --json
./bin/harpia capabilities --dir <project>
./bin/harpia capabilities --dir <project> --json
./bin/harpia targets
./bin/harpia targets --json
./bin/harpia targets java-spring
./bin/harpia version
```

`validate` never writes output. `inspect` prints the exact intermediate model reached by the same
compiler pipeline; it does not recompute a parallel representation. `build` synchronizes the
generated directory idempotently.
`--clean` removes stale files listed as Harpia-owned in `.harpia-manifest`; files outside the
manifest are reported and not deleted. Keep hand-written code outside Harpia-managed paths while
you intend to regenerate the project.

`--json` puts one object on stdout and nothing else: no summary line, and no diagnostics on stderr
to reassemble. It is for callers that act on the result rather than read it.

```json
{"contract":1,"command":"validate","ok":true,"exitCode":0,"diagnostics":[]}
```

`contract` is the shape of that object, so a consumer can refuse a version it does not know instead
of guessing. Each diagnostic carries `severity`, `code`, `message`, and — when the compiler measured
one — a `where` with `file`, `line`, `column` and an optional `endLine`/`endColumn`; a second
location travels as `related` rather than as prose inside the message. `build` adds an `output`
object with the directory and the `created`, `updated`, `unchanged`, `stale`, `deleted` and
`unknown` file lists. `inspect` adds `stage` and the stage text as one escaped `rendered` string.
`targets` lists every catalogued target as data, including the ones this compiler cannot generate,
and answers an unknown name with `unknownTarget` plus the catalogue. A run that never compiled
reports in the same shape, without the command's own extra field.

A successful `build` also writes `.harpia/handoff.json` next to the project. A build leaves a
directory behind, and whoever picks it up next asks the same questions every time: which target is
this, what does it depend on, which files are mine to edit, and what did Harpia deliberately leave
unimplemented. It names the target and its status, the capabilities and their providers, where the
ownership manifest is — it points at `.harpia-manifest` rather than keeping a second copy of the
file list that could disagree with the first — and every `custom` Logic whose contract is generated
but whose implementation is still yours. It carries no timestamp: two identical builds leave
identical bytes.

It also reports the four gates. Harpia runs `validate` and `build` itself, so it states those as
facts. It cannot run `test` and `package`: those happen in the toolchain of the language it
generated, so they are reported as `pending` with the command that runs them — claiming they passed
would invent a result nobody produced. `next` names the one thing to do, and an unimplemented
contract comes before any gate, because the project will not start without the bean and running the
tests first would only produce a failure that says less.

`capabilities` answers the other half of `targets`: what these specifications actually asked for.
Each entry names the capability, who supplies it — `<target>` when the target implements it itself
rather than choosing a provider — and every declaration that required it, with its location.

### Java API

`HarpiaCompiler` is usable directly, without the CLI:

```java
CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));
result.diagnostics().forEach(System.out::println);
result.tree().ifPresent(tree -> tree.files().forEach(...));
```

Everything in this compiler is typed, but being typed is not the same as being promised: most of it
is free to change with the next slice. `HarpiaContract` names the part that is not — the types a
harness, an editor plugin or an agent actually touches — and `HarpiaContract.VERSION` is the same
number the CLI prints as `contract`. The Java API and the JSON output are one promise seen twice.

That surface is pinned by a golden file, so widening or narrowing it is a decision rather than an
accident. Diagnostic codes have their own ledger: a new code is additive and breaks nobody, while a
renamed constant or a reused number would point existing tooling at another refusal, so those are
refused.

Run `./bin/harpia targets` rather than assuming target support. Unsupported targets fail before
generation and never fall back to Java/Spring.

### Compiler architecture

The core language does not contain Java or Spring types:

```text
*.harpia.md
      ↓
CommonMark structure + Harpia parser
      ↓
Module ASTs + deterministic Project AST
      ↓
Semantic analysis
      ↓
Business IR
      ↓
Capability resolution + Application IR
      ↓
Target resolution
      ↓
Java/Spring transformer
      ↓
Structured Java Target Model
      ↓
Renderer + logic-free templates
      ↓
Generated Maven project
```

The design rule is:

> **Transformers translate semantics. Templates render syntax.**

Java source is rendered from a structured target model. Templates are currently limited to
mechanical Maven, YAML, and SQL output; business decisions do not live in templates.

### Determinism and ownership

For the same specification, configuration, compiler version, and target, Harpia is designed to
produce the same files. Generation avoids timestamps, random identifiers, unstable iteration, and
LLM calls. The compiler suite checks golden output byte for byte, recompiles generated Java, and
runs the generated Maven project.

Generated code is intentionally conventional. While you keep rebuilding into the same directory,
files listed in `.harpia-manifest` are managed output and may be updated by Harpia. When the
bootstrap is complete, you can stop regenerating and evolve the Java/Spring project directly.

> **Generate it. Own it. Keep coding.**

### License

Harpia is licensed under the [Apache License 2.0](LICENSE).

The license covers the **compiler** — its source, templates and documentation — and **not** the
code it writes into your project. Generated files carry no attribution requirement and no
licensing obligation of any kind: license, distribute or sell the result however you want. The
`// Generated by Harpia. Do not edit.` header exists so the next build knows which files it may
safely rewrite; it is not a claim of ownership. See [`NOTICE`](NOTICE).

### Current separation and direction

Harpia's long-term model separates concerns like this:

```text
*.harpia.md  = what the software does
bindings/    = how the software connects
harpia.yaml  = target, providers, and project configuration
```

The external HTTP binding above is executable today. The roadmap expands it and explores explicit
entities, value objects, enums, rules, formulas,
decisions, events, integrations, providers, additional targets, semantic inspection, an MCP agent
API, and LLM-assisted brownfield reconstruction. These ideas are architectural direction until
their backlog entries are implemented and tested.

Compilation from Harpia remains deterministic. A future reverse-engineering tool may use an LLM to
infer intent from existing code, but its output would be a candidate specification that Harpia
validates and a human reviews.

### Documentation

- [Official backlog and current status](BACKLOG.md)
- [Architecture](ARCHITECTURE.md)
- [Current implementation architecture](docs/current-architecture.md)
- [Supported and planned targets](TARGETS.md)
- [Language specification draft](docs/spec/harpia-language-v1-draft.md)
- [Harpia Logic specification](docs/spec/harpia-logic-v1-draft.md)
- [HTTP binding V1 specification](docs/spec/harpia-bindings-v1.md)
- [Product vision](docs/vision.md)
- [Semantic compression benchmarks](docs/benchmarks.md)
- [Historical language coverage snapshot](LANGUAGE_COVERAGE.md)
- [Historical Spring coverage snapshot](SPRING_COVERAGE.md)
- [Historical roadmap](docs/roadmap.md)
- [MCP design reference](docs/mcp-roadmap.md)

### Development and contributing

Build and run the complete compiler test suite:

```bash
mvn test
```

Useful contribution areas include parser and diagnostics quality, language design, Java/Spring
generation, deterministic tests, examples, target architecture, and documentation. Before adding a
new language construct, ask whether it represents portable application meaning or only abbreviates
framework syntax. Specialized algorithms and SDK-specific behavior should remain ordinary target
code when they do not provide reusable semantic compression.

## Português

Harpia é uma linguagem de especificação semântica e um gerador inicial de projetos backend para
pessoas e agentes de IA. Você descreve dados, casos de uso, fluxos e cálculos em Markdown
estruturado; o compilador valida essa intenção e gera código convencional de forma determinística.

O target disponível hoje é **Java 21+ com Spring Boot**.

### Por que usar?

Em vez de pedir que uma LLM regenere centenas de linhas de boilerplate a cada mudança, ela pode
produzir uma especificação Harpia pequena e revisável. O compilador assume a parte mecânica que já
conhece; o resultado é um projeto Maven normal, sem runtime Harpia obrigatório.

```text
Pessoa ou IA → intenção em Markdown → compilador Harpia → backend Java/Spring → código do desenvolvedor
```

### Início rápido

Com JDK 21+ e Maven 3.9+ instalados, execute na raiz do repositório:

```bash
mvn package
./bin/harpia validate --dir examples/customer
./bin/harpia build --dir examples/customer
mvn -f examples/customer/generated/pom.xml test
```

Isso valida a especificação em
[`examples/customer/specs/customer.harpia.md`](examples/customer/specs/customer.harpia.md), gera o
projeto em `examples/customer/generated` e executa seus testes Spring. O segundo exemplo mostra
lógica de negócio pura:

```bash
./bin/harpia validate --dir examples/business-logic/pricing
./bin/harpia build --dir examples/business-logic/pricing
mvn -f examples/business-logic/pricing/generated/pom.xml test
```

### O que funciona hoje

- entidades escalares com campos tipados, obrigatórios, únicos, gerados e com valor default;
- casos de uso CRUD com endpoints `GET`, `POST`, `PUT` e `DELETE`;
- `Command` e `Query` explícitos na V1, com exposição HTTP opcional;
- bindings HTTP externos com resolução de operação, rota e parâmetro `{id}`;
- Flow V0 para validar, criar, carregar, listar, atualizar, salvar, excluir e retornar;
- Logic pura e tipada, incluindo cenários convertidos em testes JUnit;
- geração de Maven, Spring Boot, DTOs, controller, service, JPA, repository, validação, tratamento
  de erros, Flyway, configuração e testes;
- escrita determinística e idempotente, com manifesto para limpeza segura;
- comandos `validate`, `build`, `inspect`, `targets` e `version`.

Bindings avançados, MCP, integrações, eventos, segurança, engenharia reversa e outros targets ainda
são roadmap. O estado oficial, com evidência por item, está no
[`BACKLOG.md`](BACKLOG.md). Para entender a separação entre linguagem e Java/Spring, leia
[`ARCHITECTURE.md`](ARCHITECTURE.md) e [`TARGETS.md`](TARGETS.md).

Na configuração atual, `harpia.schemaVersion: 1` versiona o YAML,
`harpia.languageVersion: 0` seleciona a gramática CRUD estável; a versão `1` acrescenta hoje
`Command` e `Query` explícitos; e `target.language.version: 21` seleciona Java. Essas três versões
são independentes.

### Licença

O Harpia é licenciado sob a [Apache License 2.0](LICENSE).

A licença cobre o **compilador** — código-fonte, templates e documentação — e **não** o código que
ele escreve no seu projeto. O que é gerado é seu: não há exigência de atribuição nem obrigação de
licenciamento sobre ele, e você pode licenciar, distribuir ou vender o resultado como quiser. O
cabeçalho `// Generated by Harpia. Do not edit.` existe para que o build seguinte saiba quais
arquivos pode reescrever com segurança — não é reivindicação de propriedade. Veja o
[`NOTICE`](NOTICE).

> **Descreva a intenção. Gere a base. Assuma o código.**
