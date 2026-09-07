# Harpia Language Coverage

> **Snapshot histórico não normativo.** O inventário, status, prioridades e contagens oficiais
> vivem em [`BACKLOG.md`](BACKLOG.md). Esta matriz foi preservada para rastrear a classificação
> anterior e pode conter números ou gates superados pela codebase atual.

Última revisão: 2026-09-06.

O identificador estável de uma feature é `seção/nome` até existir o catálogo estruturado planejado
em F2. Os identificadores, prioridades, status e evidências canônicos agora são definidos em
[`BACKLOG.md`](BACKLOG.md); esta matriz registra apenas o modelo de classificação anterior.

## Como ler o status

| Status | Significado |
|---|---|
| `SUPPORTED` | funciona de ponta a ponta no estágio aplicável e possui testes |
| `PARTIAL` | existe em parte do pipeline, mas ainda não chega a uma aplicação Java gerada e testada |
| `PLANNED` | pertence ao roadmap, porém não está disponível para uso |
| `RESEARCH` | precisa de ADR/spike antes de compromisso de sintaxe ou provider |
| `CUSTOM` | deve ser atendido por Custom Java, não por nova semântica Harpia |
| `NOT_PLANNED` | conflita com os princípios ou está explicitamente fora do produto |

## Método de cálculo

- Universo elegível: linhas `SUPPORTED`, `PARTIAL`, `PLANNED` ou `RESEARCH` desta matriz.
- Cobertura estrita: `SUPPORTED / universo elegível`.
- Progresso ponderado: `(SUPPORTED + 0,5 × PARTIAL) / universo elegível`.
- `CUSTOM` e `NOT_PLANNED` ficam fora do denominador: são fronteiras, não implementação Harpia.
- Um item puramente diagnóstico/tooling pode ser `SUPPORTED` sem Java gerado quando seu próprio
  teste terminal fecha o contrato.

Os totais calculados ficam no fim do documento. “Planejado” não aumenta nenhuma porcentagem.

O target Java/Spring já gera, escreve e compila a fatia bootstrap/entity/repository/Flyway. CRUD e
REST completos continuam `PARTIAL` enquanto faltarem DTOs, services, controllers e testes gerados.

## Targets

A cobertura abaixo descreve o target `java-spring`, o único com generator. Um target sem generator
não recebe nenhum checkmark: cobertura é o que existe, não o que é concebível.
Detalhes em [TARGETS.md](TARGETS.md).

| Target | Status | REST | SQL | Auth | Events | Logic |
|---|---|---|---|---|---|---|
| java-spring | `SUPPORTED` | `PARTIAL` | `PARTIAL` | — | — | `PARTIAL` |
| kotlin-spring | `NOT_SUPPORTED` | — | — | — | — | — |
| csharp-aspnet | `NOT_SUPPORTED` | — | — | — | — | — |
| typescript-nestjs | `NOT_SUPPORTED` | — | — | — | — | — |
| python-fastapi | `NOT_SUPPORTED` | — | — | — | — | — |
| go | `NOT_SUPPORTED` | — | — | — | — | — |
| clojure-jvm | `NOT_SUPPORTED` | — | — | — | — | — |
| php-laravel | `NOT_SUPPORTED` | — | — | — | — | — |
| rust | `NOT_SUPPORTED` | — | — | — | — | — |
| elixir-phoenix | `NOT_SUPPORTED` | — | — | — | — | — |
| ruby-rails | `NOT_SUPPORTED` | — | — | — | — | — |

Toda linha marcada `A — Semantic` neste documento é **independente de target**: ela descreve o que
a construção significa. As colunas "Provider" e "Java/Spring target" descrevem como `java-spring`
a implementa hoje, e mudariam para outro target sem alterar a semântica.

## Critério arquitetural

| Cobertura | Uso |
|---|---|
| `A — Semantic` | intenção de negócio ou comportamento recorrente da aplicação |
| `B — Capability` | necessidade técnica abstrata selecionada por provider |
| `B — Provider` | implementação substituível de uma capability |
| `C — Custom` | comportamento específico/low-level por contrato Java gerado |
| `—` | não planejado |

## Language and compiler foundation

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| `.harpia.md` source | A — Semantic | Markdown estruturado | — | compiler input | `SUPPORTED` | atual | `SpecDiscovery`, `SourceFile`, parser e testes reais |
| Executable Markdown | A — Semantic | headings, lists, fenced blocks | — | AST | `SUPPORTED` | atual | prosa nunca adquire semântica implicitamente |
| `harpia.yaml` | B — Capability | config tipada | — | Application settings | `SUPPORTED` | atual | Safe YAML, keys/paths/values validados |
| Compiler version | ferramenta | `harpia version` | — | build metadata | `SUPPORTED` | atual | recurso estático, sem relógio |
| Language version | B — Capability | `harpia.languageVersion` | — | parser selection | `PLANNED` | F1 | `harpia: 1` é marker legado |
| MCP API version | ferramenta | protocol metadata | — | MCP compatibility | `PLANNED` | M0 | independente de compiler/language version |
| Markdown structural AST | A — Semantic | `MarkdownStructure` | — | parser input | `SUPPORTED` | atual | CommonMark só estrutura blocos |
| Module AST | A — Semantic | módulo por arquivo | — | `ModuleAst` | `PARTIAL` | F1 | `SpecAst.moduleName`/Data opcional existem; declaração comum não |
| Project AST | A — Semantic | projeto multi-file | — | `ProjectAst` | `PLANNED` | F1 | compilador ainda mantém `List<SpecAst>` |
| Multi-file discovery | ferramenta | `specs/**/*.harpia.md` | — | ordered sources | `SUPPORTED` | atual | paths normalizados e ordenados |
| Global SymbolTable | A — Semantic | namespaces | — | resolved symbols | `PARTIAL` | F1 | duas passagens existem somente para Logic |
| Scope model | A — Semantic | parâmetros/blocos/flow | — | typed bindings | `PARTIAL` | F1 | Logic tem scopes; projeto/nominais não |
| Cross-file references | A — Semantic | named reference | — | symbol link | `PARTIAL` | F1 | Logic chama Logic cross-file; entidades proíbem referência externa |
| Semantic Analyzer | A — Semantic | validação V0 + Logic | — | typed Business IR | `PARTIAL` | F1 | analyzers existem, mas não há projeto global uniforme |
| Business IR | A — Semantic | Entity/UseCase/Logic | — | framework-free model | `PARTIAL` | F1 | real, ainda V0 e fragmentada por records |
| Capability requirements | B — Capability | inferidos de entity/flow/endpoint | provider registry | requirements | `SUPPORTED` | E0.1 | HTTP/persistence source-backed; Logic pura não cria requirement |
| Capability resolution | B — Capability | config + requirements | target-native/postgresql | resolved providers | `SUPPORTED` | E0.2 | target, capability e provider são conceitos separados |
| Application IR | B — Capability | lowering generator-ready | providers | generator model | `PARTIAL` | E0.2/F1 | CRUD/Logic existem; tipos e annotations concretos ficam no target |
| Java/Spring generator | B — Provider | `build` | java-spring | target model + generated tree | `PARTIAL` | E0 | bootstrap/entity/repository/Flyway/Logic compilam; DTO/service/controller faltam |
| Source position | ferramenta | file/line/column | — | diagnostics | `SUPPORTED` | atual | `SourceRef` 1-indexado |
| Source range | ferramenta | start/end | — | diagnostics/source map | `PLANNED` | F1 | endLine/endColumn ausentes |
| Related locations | ferramenta | primary + related | — | diagnostics | `PLANNED` | F1 | hoje duplicata inclui primeiro local na mensagem |
| Suggested fixes | ferramenta | structured edit | — | CLI/MCP | `PLANNED` | F2/M3 | somente quando deterministicamente seguro |
| Compiler error codes | ferramenta | `HRP1xxx`–`HRP7xxx` | — | diagnostics | `SUPPORTED` | atual | inclui target resolution/transformation/template failures |
| Deterministic diagnostics | ferramenta | stable ordering | — | CLI/MCP | `SUPPORTED` | atual | ordenação total testada |
| Deterministic compilation | ferramenta | same inputs → same tree | — | GeneratedTree | `SUPPORTED` | E0.9 | hash e golden byte a byte testados no target atual |
| Idempotent generation | ferramenta | manifest sync | — | filesystem | `SUPPORTED` | E0.8 | writer preserva arquivos desconhecidos e não reescreve conteúdo igual |
| Source maps | ferramenta | spec → generated symbol | — | Java/diagnostics | `PARTIAL` | F2 | `GeneratedFile` leva `SourceRef`; faltam ranges e mapping por símbolo/linha |
| Language compatibility | ferramenta | legacy V0 adapter | — | version migration | `PARTIAL` | F1 | V0 preservada; política formal/version selector faltam |
| Schema evolution | B — Capability | snapshot/diff | database provider | migration | `PLANNED` | DB1 | após mapping/migration V0 |
| Inference catalog | ferramenta | inspect/explain | — | documented decisions | `PLANNED` | F2 | torna semantic compression inspecionável |

## Core Domain

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Entity | A — Semantic | `# Customer` + `## Data` | persistence | JPA entity + tabela | `SUPPORTED` | E0 | Vertical completo, com testes gerados que executam |
| Scalar fields | A — Semantic | `name: String` | persistence | campo + coluna tipada | `SUPPORTED` | E0 | Dez tipos V0 mapeados para Java e PostgreSQL |
| Required | A — Semantic | `required` | validation + persistence | `@NotNull`/`@NotBlank` + `NOT NULL` | `SUPPORTED` | E0 | Teste gerado observa o 400 |
| Unique | A — Semantic | `unique` | persistence | `unique = true` + `CONSTRAINT uq_...` | `SUPPORTED` | E0 | Teste gerado observa o 409 e o campo em conflito |
| Generated ID | A — Semantic | `id: UUID generated` | persistence | `@GeneratedValue(UUID)` + PK | `PARTIAL` | E0 | Gerado e compilando; nenhum teste gerado observa a geração em si |
| Defaults | A — Semantic | `default <literal>` | persistence | inicializador de campo | `PARTIAL` | E0 | Default é de aplicação, não do banco; nenhum teste gerado o observa ainda |
| Value Object | A — Semantic | `## Value ...` | — | record/value mapping | `PLANNED` | S2 | Grammar V1 draft |
| Enum | A — Semantic | `## Enum ...` | — | Java enum + DB mapping | `PLANNED` | S2 | Deve entrar antes de State Machine |
| Rule | A — Semantic | fenced `rules` | validation | typed predicate/validator | `PLANNED` | S3 | Nunca prosa livre |
| Invariant | A — Semantic | `## Invariants` | validation + persistence | guards/tests | `PLANNED` | S3 | Proteção em fronteiras de mutação |
| Relationship | A — Semantic | named type/`List<T>` | persistence | JPA relation + FK | `PLANNED` | S2 | V0 rejeita com `HRP4002`; ownership precisa de semântica própria |
| State | A — Semantic | `## State` | persistence | enum/state field | `PLANNED` | S7 | Depois de Enum e Rules |
| Transition | A — Semantic | `### Transitions`/`transition` | persistence | validated state mutation | `PLANNED` | S7 | Transições inválidas devem falhar no compiler quando estáticas |

## Extended types, modeling and lifecycle

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| File | A — Semantic | `File` + constraints | storage | metadata/value | `PLANNED` | S8 | bytes pertencem ao provider |
| URL | A — Semantic | `URL` | validation | value mapping | `PLANNED` | S2+ | tipo semântico, não `java.net.URL` |
| Phone | A — Semantic | `Phone` | validation | value mapping | `PLANNED` | S2+ | normalização exige política explícita |
| IPAddress | A — Semantic | `IPAddress` | validation | value mapping | `PLANNED` | S2+ | sem vazar classe Java |
| Secret | A+B | `Secret` reference | secret provider | opaque value | `PLANNED` | S4 | nunca literal inline |
| `List<T>` | A — Semantic | container fechado | — | typed collection | `PLANNED` | S2/L6 | campo/relação/Logic possuem semânticas distintas |
| `Optional<T>` | A — Semantic | container fechado | — | optional value | `PLANNED` | S2 | keyword `optional` redundante não é necessária |
| `Reference<T>` | A — Semantic | named entity type | persistence | relation handle | `PLANNED` | S2 | syntax não expõe JPA |
| `Page<T>` | A — Semantic | paged output | persistence/http | page contract | `PLANNED` | S4 | não expõe Spring `Page` na Business IR |
| CPF/CNPJ/Document libraries | A+B | imported semantic type library | validation provider | value/validation | `RESEARCH` | pós-S2 | extensão versionada, não core obrigatório |
| Aggregate / Root / Members | A — Semantic | `## Aggregate` | persistence/events | aggregate mapping | `PLANNED` | pós-S2 | depende de relations, ownership e invariants |
| Immutable modifier | A — Semantic | `immutable` | persistence | update restrictions | `PLANNED` | S2/S3 | precisa de mutation semantics |
| Derived modifier | A — Semantic | `derived` | computation | computed property | `PLANNED` | pós-L3 | origem precisa ser Formula/Logic tipada |
| Sensitive modifier | A+B | `sensitive` | serialization/logging | redaction | `PLANNED` | S4/S8 | afeta DTOs, logs e diagnostics |
| Encrypted modifier | A+B | `encrypted` | crypto provider | encrypted storage | `PLANNED` | pós-S4 | key management fora da spec |
| Internal modifier | A — Semantic | `internal` | serialization | hidden output | `PLANNED` | S4 | contrato decide exposição |
| Input/output modifiers | A — Semantic | `input`/`output` | serialization | contract shaping | `RESEARCH` | S4 | só entra se projections não resolverem melhor |
| Owned modifier | A — Semantic | `owned` | persistence | lifecycle/cascade mapping | `PLANNED` | S2 | intenção de lifecycle, não cascade JPA |
| Soft delete | A+B | `soft-delete` | persistence | deleted timestamp/filter | `PLANNED` | S8 | queries e restore entram no mesmo slice |
| Optimistic locking | A+B | `optimistic` | persistence | version check | `PLANNED` | S8 | conflito possui erro semântico |
| Retention | A+B | `Retention 30d` | persistence/scheduler | TTL/cleanup | `PLANNED` | S8 | provider escolhe mecanismo |
| Audit | A+B | audit capability | persistence/events | timestamps/history | `PLANNED` | S4/S8 | separar metadata de audit trail |
| Tenant scoped | A+B | `TenantScoped` | tenancy provider | column/schema/database | `RESEARCH` | S9 | não escolher estratégia na Business Spec |

## Business Computation

Camada formal de lógica de negócio. Especificação em
[`docs/spec/harpia-logic-v1-draft.md`](docs/spec/harpia-logic-v1-draft.md); análise da base atual em
[`docs/logic/analysis.md`](docs/logic/analysis.md).

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Logic | A — Semantic | `## Logic Name` + fenced `logic` | nenhum (puro) | `final class` com `static apply` | `PARTIAL` | L1/L2 | Parser, tipos, IR e Java gerado existem e compilam no teste `GeneratedLogicCompilesTest`; falta apenas o `OutputWriter` (E0.8) |
| Expressions | A — Semantic | `+ - * /`, comparações, `and`/`or`/`not`, parênteses, chamadas | — | expressão Java equivalente | `PARTIAL` | L1/L2 | AST tipada com `SourceRef` por nó; nunca string |
| Conditional | A — Semantic | `if` / `else` com indentação de quatro espaços | — | `if`/`else` Java | `PARTIAL` | L2 | Retorno definitivo é provado pelo compilador |
| Type inference | A — Semantic | derivado da expressão | — | tipo Java resolvido | `PARTIAL` | L1 | Torre `Int < Long < Decimal`, sem estreitamento implícito |
| Type checking | A — Semantic | `HRP2103` | — | — | `SUPPORTED` | L1 | O compilador Java nunca descobre erro da linguagem Harpia |
| Purity boundary | A — Semantic | `HRP2106` | — | — | `SUPPORTED` | L1 | `save`/`emit`/`send` dentro de Logic ensinam a fronteira |
| Built-in registry | A — Semantic | `min`, `max` | — | `Math.min`/`BigDecimal.min` | `PARTIAL` | L1 | Registry fechado; cada função entra com o tipo que a justifica |
| Logic → Logic | A — Semantic | `Name(arg = expr)` com argumentos nomeados | — | chamada estática | `PARTIAL` | L1 | Grafo de chamadas acíclico verificado |
| Formula | A — Semantic | `## Formula` + fenced `formula` | — | classe pura de uma expressão | `PLANNED` | L3 | Depende de `List<T>` para o caso motivador |
| Decision | A — Semantic | `## Decision` + fenced `decision` | — | decisão tabular gerada | `PLANNED` | L5 | Forma canônica é o bloco; `first matching rule wins` |
| Decision table | A — Semantic | tabela Markdown multidimensional | — | idem | `PLANNED` | pós-L5 | Só quando houver mais de uma coluna de condição |
| Money / Currency | A — Semantic | `BRL 100`, tipo `Money` | — | `BigDecimal` + moeda | `PLANNED` | L4 | `BRL 100 + USD 10` deve ser erro de tipo |
| Percentage | A — Semantic | `10%` | — | `BigDecimal` | `PLANNED` | L4 | `%` já é reservado e rejeitado como módulo |
| Rounding policy | B — Capability | `money.rounding` no `harpia.yaml` | — | `RoundingMode` | `PLANNED` | L4 | Política nunca vive na Business Spec |
| Collections | A — Semantic | `List<T>`, `sum`, `count`, `filter`, `map`, `any`, `all` | — | Streams internos | `PLANNED` | L6 | Forma canônica de filtro é `filter(items, item.x)` |
| Dates / Duration | A — Semantic | `today`, `now`, `+ 30m`, `daysBetween` | — | `java.time` | `PLANNED` | L7 | `mo` mapeia para `Period`, não `Duration` |
| Pattern matching | A — Semantic | `match` | — | `switch` | `PLANNED` | pós-L7 | Depois de Enum e do sistema de expressões estável |
| Reassignment / `+=` | — | nenhum | — | — | `NOT_PLANNED` | — | Atribuição única; acumulação pertence a `Decision` |
| Flow → Logic | A — Semantic | `x = Name(arg = expr)` no flow | — | chamada no service gerado | `PLANNED` | L8 | Bloqueado por E0.7: o corpo do service ainda não é gerado |
| Scenario sobre Logic | A — Semantic | `## Scenario` + Given/When/Then | test | JUnit puro sem Spring | `SUPPORTED` | L9 | Único oráculo de uma função pura; o compilador nunca avalia a Logic para adivinhar o esperado |
| Scenario ausente | A — Semantic | — | test | — | `SUPPORTED` | L9 | `HRP2119` avisa em vez de silenciosamente não gerar teste |
| Recursão em Logic | C — Custom | `### Implementation custom` | custom | Java do usuário | `CUSTOM` | S6 | Fronteira permanente contra Turing-completude |
| Loops em Logic | — | nenhum | — | — | `NOT_PLANNED` | — | `for each` pertence a Flow; Logic usa `map`/`filter`/agregação |
| Interpretador de expressão em runtime | — | nenhum | — | — | `NOT_PLANNED` | — | Harpia é build-time; o alvo é bytecode |

## Application

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Use case | A — Semantic | qualquer H2 diferente de `Data` na V0 | — | método de service | `SUPPORTED` | E0 | Um teste gerado por flow |
| Command | A — Semantic | `## Command Name` | transaction | transactional service operation | `PLANNED` | S1 | Escrita é transacional por default no draft |
| Query | A — Semantic | `## Query Name` | persistence | read-only service operation | `PLANNED` | S2 | Side effects proibidos por default |
| REST endpoint | A — Semantic | `### Endpoint` | spring-mvc | método de controller | `SUPPORTED` | E0 | Teste gerado observa o status declarado de cada endpoint |
| Input contract | A — Semantic | `### Input` | serialization | record de request | `SUPPORTED` | E0 | Bean Validation derivada de `required` e do tipo `Email` |
| Output contract | A — Semantic | `### Output` | serialization | record de response + status | `SUPPORTED` | E0 | Entity, List<Entity>, nothing |
| Error mapping | A — Semantic | `### Errors` | spring-mvc | `@RestControllerAdvice` | `SUPPORTED` | E0 | Só as condições declaradas geram handler, e cada uma tem teste |
| Flow | A — Semantic | fenced `flow` | — | corpo do service | `SUPPORTED` | E0 | Os 8 comandos viram Java preservando os nomes das variáveis |
| Transaction | A+B | inferida do Command | spring-tx | `@Transactional` | `SUPPORTED` | E0 | Escrita transacional, leitura `readOnly` |
| Validation enforcement | A — Semantic | `validate input` | validation | `@Valid` no corpo da requisição | `SUPPORTED` | E0 | Um flow que não declara `validate input` não valida |
| Transaction | A+B | Command default/`### Transaction` | spring-tx | `@Transactional` boundary | `PLANNED` | S3 | Default previsível reduz ruído |
| Idempotency | A+B | `### Idempotency` | persistence/cache | key store + replay | `PLANNED` | S4 | Alta compressão semântica |
| Pagination | A — Semantic | `Page<T>`/query metadata | persistence | Spring `Page` mapping | `PLANNED` | S4 | Não expor Spring Data na Business IR |
| Filtering | A — Semantic | typed query predicates | persistence | query/specification implementation | `PLANNED` | S4 | Sem frases livres |
| Sorting | A — Semantic | `### Sort` | persistence | stable sort | `PLANNED` | S4 | Ordem determinística obrigatória |
| Projection | A — Semantic | named output shape | serialization + persistence | DTO projection | `PLANNED` | S4 | Evitar retornar entity diretamente |
| Scenario sobre Command/Query | A — Semantic | `## Scenario` | test | JUnit/Spring test | `PLANNED` | S4 | Hoje `## Scenario` cobre apenas computações |

## Contracts, effects and behavior evolution

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Protocol-independent behavior | A — Semantic | Command/Query sem Endpoint | transport | service/handler | `PLANNED` | F1/S1 | Command não implica POST; Query não implica GET |
| Effect model | A — Semantic | inferred effect set | providers | compiler metadata | `PARTIAL` | F1/S1 | Logic PURE e persistence detection existem; enum global não |
| Pure effect | A — Semantic | Logic | — | plain Java | `SUPPORTED` | L1/L2 | side effects rejeitados e testados |
| Persistence read/write effects | A+B | flow operations | persistence | transaction boundary | `PARTIAL` | S1/S3 | capability é inferida; Command/Query ainda não |
| Integration/Event/Email/Storage effects | A+B | typed flow operations | respective provider | adapters | `PLANNED` | S1/S5/S8 | effect checking precede providers |
| Preconditions / Requires | A — Semantic | `Requires`/`require` | — | guard | `PLANNED` | S3 | reutiliza Rules |
| Postconditions / Ensures | A — Semantic | `Ensures` | — | assertion/test | `PLANNED` | pós-S3 | precisa de previous/current state |
| Guarantees | A — Semantic | `Guarantees` | — | contract/test | `RESEARCH` | pós-S3 | não duplicar Ensures sem diferença formal |
| Previous state | A — Semantic | `previous` | persistence | snapshot/comparison | `PLANNED` | pós-S3 | somente em fronteiras de mutação |
| First-class Contract | A — Semantic | Contract/Input/Errors | — | interface/validation | `RESEARCH` | pós-S3 | só entra se Command/Query não cobrirem o caso |
| `find` flow operation | A — Semantic | optional/many lookup | persistence | repository query | `PLANNED` | pós-S2 | diferente de `load`, que exige resultado |
| `require` flow operation | A — Semantic | rule + error | — | guard | `PLANNED` | S3 | uma operação nova por slice |
| `fail` flow operation | A — Semantic | typed error | transport-neutral errors | exception/result | `PLANNED` | pós-S3 | depende de error symbols |
| `set` flow operation | A — Semantic | target + expression | — | mutation | `PLANNED` | pós-S3 | invariants precisam envolver a mutação |
| `emit` flow operation | A — Semantic | named Event arguments | events | publisher | `PLANNED` | S1 | primeiro novo flow planejado |
| `send` flow operation | A — Semantic | Email + recipient | email | sender | `PLANNED` | S4 | depois de Email contract |
| `call` flow operation | A — Semantic | Integration.Operation | integration | client port | `PLANNED` | S5 | argumentos nomeados |
| Explicit transaction operation | A+B | transaction scope | transaction | boundary | `RESEARCH` | pós-S3 | Command default deve resolver maioria dos casos |
| Local transaction | A+B | Command default | spring-tx | transactional service | `PLANNED` | S1/S3 | escrita é transacional por default proposto |
| Read-only transaction | A+B | Query metadata | spring-tx | read-only boundary | `PLANNED` | S3 | Query side effects proibidos |
| Requires-new transaction | B — Capability | transaction policy | spring-tx | propagation | `RESEARCH` | futuro | não expor nome Spring na linguagem |
| Distributed transaction | B — Capability | transaction requirement | JTA/XA | coordinator | `PLANNED` | S9 | depois de saga/idempotency evidence |
| `transition` flow operation | A — Semantic | entity state target | persistence | guarded mutation | `PLANNED` | S7 | depende de Enum/State/Rules |
| `for each` in Flow | A — Semantic | effectful iteration | execution | loop | `PLANNED` | S9 | Logic continua usando agregadores |
| `if`/`else` in Flow | A — Semantic | conditional orchestration | — | branch | `PLANNED` | S9 | somente após constructs mais semânticos |

## Security

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Public access | A — Semantic | `### Access public` | none | permit endpoint | `PARTIAL` | E0 | Único access aceito pela V0 |
| Authentication | A+B | `authenticated` | JWT | Spring Security | `PLANNED` | S4 | V0 rejeita com `HRP4001` |
| Roles | A+B | `role ADMIN` | JWT | authorities | `PLANNED` | S4 | Business IR não conhece Spring Security |
| Authorization policy | A — Semantic | `## Policy` | authentication | authorization component | `PLANNED` | S4 | Regra tipada de negócio |
| OAuth2/Cognito/Auth0 | B — Provider | mesma semântica Access | provider futuro | resource server/adapters | `PLANNED` | pós-S4 | Nenhum provider nesta fase |
| Criptografia customizada | C — Custom | custom contract | custom dependency | Java implementation | `CUSTOM` | S6 | Não ampliar a grammar |

## Integration

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Domain event declaration | A — Semantic | `## Event` | events | record/event contract | `PLANNED` | S1 | Primeiro slice além de CRUD |
| Event emission | A — Semantic | `emit Event(...)` | local events | publisher call | `PLANNED` | S1 | V0 ainda rejeita seção Event |
| Event consumer | A — Semantic | `## On Event` | events | listener | `PLANNED` | S5 | Depois do publisher local |
| Email declaration | A — Semantic | `## Email` | email | message/template contract | `PLANNED` | S4 | V0 rejeita com `HRP4003` |
| Send email | A — Semantic | `send Email to ...` | SMTP | sender adapter | `PLANNED` | S4 | Secrets somente por ambiente |
| Integration contract | A — Semantic | `## Integration` | integration | port/client DTOs | `PLANNED` | S5 | Segundo vertical slice |
| HTTP integration | B — Provider | Integration Operation | HTTP | RestClient adapter | `PLANNED` | S5 | Base URL na configuração |
| Integration error mapping | A+B | `### Errors` | HTTP | typed failures | `PLANNED` | S5 | 4xx/5xx para erros do domínio |
| Inbound webhook | A — Semantic | Command + Endpoint | spring-mvc | controller | `PLANNED` | S5 | Reusa endpoint, sem keyword obrigatória |
| Outbound webhook | A+B | `## Webhook` | HTTP | event subscriber/client | `PLANNED` | pós-S5 | Destino fora da Business Spec |
| Messaging | B — Capability | Event/On Event | Kafka/SQS/etc. | producer/consumer adapters | `PLANNED` | S8 | Não implementar broker antes de local events |
| Proprietary SDK | C — Custom | custom contract | custom dependency | user Java adapter | `CUSTOM` | S6 | Terceiro vertical slice |

## Data

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Persistence intent | A — Semantic | `save`, `load`, `delete`, `list` | PostgreSQL | JPA/Spring Data/Flyway | `SUPPORTED` | E0 | Repository, service e testes gerados |
| PostgreSQL | B — Provider | nenhuma construção de negócio | postgresql | driver/JPA/Flyway SQL | `SUPPORTED` | E0 | Mapeamento de tipos, entity, constraints e schema gerados e testados |
| SQL migrations | B — Provider | derivada de Data | Flyway | `V1__init.sql` | `SUPPORTED` | E0 | Emitida e alinhada com a entity; a constraint é reconhecida pelo error handler |
| Cache | B — Capability | query cache policy | local/Redis | Spring Cache adapter | `PLANNED` | S8 | Redis não aparece na spec |
| Search | B — Capability | query semantics | provider futuro | adapter | `PLANNED` | pós-S8 | Somente após query model |
| File/storage intent | A — Semantic | `## Storage`/`store` | storage | port + metadata | `PLANNED` | S8 | Limites e formatos são semânticos |
| Filesystem storage | B — Provider | Storage | filesystem | Java NIO adapter | `PLANNED` | S8 | Primeiro provider de storage candidato |
| S3/Azure Blob | B — Provider | mesma Storage | cloud | SDK adapter | `PLANNED` | futuro | Não implementar provider cloud agora |
| NoSQL | B — Provider | entidade/query compatível | futuro | adapter específico | `PLANNED` | futuro | Não deve distorcer o modelo V1 atual |

## Execution

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Synchronous execution | A — Semantic | default | spring | method call/request | `PARTIAL` | E0 | Modelo existe, código não |
| Async | A+B | `### Execution async` | executor | async boundary | `PLANNED` | S8 | Não expor `CompletableFuture` |
| Parallelism | A+B | `parallel` | execution provider | virtual threads/executor | `PLANNED` | S9 | Só após semântica determinística |
| Schedule | A+B | `## Schedule` | scheduler | Spring Scheduling | `PLANNED` | S8 | Expressão formal, não cron técnico na Business IR |
| Job/Batch | A+B | `## Job` | batch | Spring Batch/adapters | `PLANNED` | S9 | Não antecipar framework |
| Workflow | A — Semantic | `## Workflow` | orchestration | generated coordinator | `PLANNED` | S9 | Depois de Command/Event |
| Saga/compensation | A+B | Workflow compensation | orchestration | coordinator/state | `PLANNED` | S9 | Requer idempotência e eventos maduros |

## Reliability

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Timeout | B — Capability | integration policy | HTTP/resilience | client timeout | `PLANNED` | S5 | Application spec/config, não regra de negócio |
| Retry | B — Capability | integration policy | resilience | retry interceptor | `PLANNED` | S5 | Política precisa definir idempotência |
| Circuit breaker | B — Capability | integration policy | resilience | circuit breaker adapter | `PLANNED` | pós-S5 | Provider ainda não escolhido |
| Rate limit | B — Capability | access/operation policy | provider futuro | filter/interceptor | `PLANNED` | S8 | Pode incluir quota semântica |
| Distributed lock | B — Capability | execution policy | provider futuro | lock adapter | `PLANNED` | futuro | Não é keyword de flow |

## Platform

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Secrets | B — Capability | referências na config | environment | Spring properties | `PARTIAL` | E0/S4 | Datasource usa placeholders de ambiente; demais secrets ainda não existem |
| Observability | B — Capability | nenhuma keyword de negócio | OpenTelemetry | tracing/metrics/logging | `PLANNED` | S8 | Business events continuam semânticos |
| Technical logging | B — Provider | nenhum `logger` na spec | logging | structured logging | `PLANNED` | S8 | Não adicionar DSL de log |
| Health checks | B — Capability | config | actuator/provider | health/readiness | `PLANNED` | S8 | Integrações podem contribuir health indicators |
| Feature flags | A+B | `### Feature` | local/futuro | guard/adapter | `PLANNED` | S8 | Nome da flag pode ser application concern |
| OpenAPI | B — Capability | derivado de contracts | springdoc/provider | OpenAPI document | `PLANNED` | S4 | Derivado, não Business IR técnico |
| Target abstraction | B — Capability | `target.id` no `harpia.yaml` | — | `HarpiaTarget` SPI | `SUPPORTED` | atual | Descriptor, catálogo, registry e resolver; ver TARGETS.md |
| Target discovery | ferramenta | `harpia targets` | — | listagem e detalhe | `SUPPORTED` | atual | Fonte única é o `TargetCatalog` |
| Unsupported target diagnostic | B — Capability | `target.id` desconhecido/indisponível | — | `HRP7001`/`HRP7002` | `SUPPORTED` | atual | Falha imediata, sem fallback e sem gerar nada |
| Target capability check | B — Capability | derivada da Business IR | — | `HRP7003` | `SUPPORTED` | atual | Um target só declara o que implementa |
| Target configuration | B — Capability | `target.language.version`, `target.options` | — | `TargetConfiguration` | `SUPPORTED` | atual | Forma legada aceita com `HRP3009` |
| Semantic golden fixtures | ferramenta | `fixtures/semantic/` | — | Business IR e Application IR | `SUPPORTED` | atual | Target-independentes por construção |
| Target golden fixture | ferramenta | `fixtures/targets/java-spring/` | — | árvore Java/Spring | `SUPPORTED` | atual | Customer comparado byte a byte |
| Deterministic multi-target | B — Capability | — | — | hash estável por target | `SUPPORTED` | atual | `DeterminismTest` |
| Target plugin loading | B — Capability | — | — | registry externo | `PLANNED` | futuro | O registry já é instância, não `switch` |
| MCP target discovery | B — Capability | `harpia://targets` | — | resources/tools | `PLANNED` | futuro | Não existe servidor MCP no repositório |
| Formatter | ferramenta | `harpia fmt` | — | source rewrite | `PLANNED` | F3 | Idempotente e preserva documentação |
| Linter | ferramenta | `harpia lint` | — | recommendations | `PLANNED` | F3 | Warnings separados de validate |
| Inspect | ferramenta | `harpia inspect --stage` | — | AST, symbols, Business IR, Application IR | `SUPPORTED` | EP01 | Prova externa das fronteiras; a saída é golden |

## Developer experience and semantic intelligence

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| `harpia init` | ferramenta | CLI | — | project skeleton | `PLANNED` | F2 | criação atômica sem sobrescrever target |
| `harpia validate` | ferramenta | CLI | — | diagnostics | `SUPPORTED` | atual | exit codes/stdout/stderr testados |
| `harpia build` | ferramenta | CLI | — | GeneratedTree/output | `SUPPORTED` | E0.8 | escrita, manifesto, clean/force e idempotência testados |
| `harpia clean` | ferramenta | CLI | — | owned output | `PLANNED` | E0.8 | exige manifesto; unknown files preservados |
| `harpia test` | ferramenta | CLI | Maven/provider | generated tests | `PLANNED` | E0.9 | sem shell arbitrário via MCP |
| `harpia capabilities` | ferramenta | CLI/MCP | registry | catalog output | `PLANNED` | F2/M1 | status e provider discovery estruturados |
| `harpia version` | ferramenta | CLI | — | compiler metadata | `SUPPORTED` | atual | versão estática testada |
| Semantic diff | ferramenta | `harpia diff` | — | model diff | `PLANNED` | I1 | não compara Java gerado |
| Impact analysis | ferramenta | `harpia impact Symbol` | — | dependency graph | `PLANNED` | I2 | depende de Project AST/SymbolTable/source maps |
| Explain | ferramenta | `harpia explain Symbol` | — | semantic explanation | `PLANNED` | I2 | inclui inferências e providers |
| Why | ferramenta | `harpia why GeneratedSymbol` | — | provenance | `PLANNED` | I2 | depende de source map |
| Breaking-change analysis | ferramenta | semantic compatibility | — | diagnostics/report | `PLANNED` | I3 | fields/contracts/events/integrations |
| API versioning/deprecation | A+B | API metadata | transport | routes/contracts | `PLANNED` | pós-I3 | exige breaking-change model |
| Semantic search | ferramenta | `project_search` | — | structured/lexical index | `PLANNED` | I2/M4 | embeddings não são requisito |
| Architecture rules | A+ferramenta | allowed/forbidden dependencies | — | compiler diagnostics | `PLANNED` | I4 | depende de Module/Context symbols |
| Module | A — Semantic | `## Module`/project structure | — | package/module boundary | `PLANNED` | F1/I4 | Logic moduleName atual não é declaração formal |
| Bounded Context | A — Semantic | Context declaration | — | dependency boundary | `PLANNED` | I4 | depois de Module |
| Feature composition | A — Semantic | `uses` | — | composed operation | `RESEARCH` | futuro | preferir chamada explícita e evitar herança |
| Database import | ferramenta | `harpia import database` | database provider | baseline spec | `PLANNED` | DB2 | precisa preservar intenção não inferível como TODO |
| OpenAPI import | ferramenta | `harpia import openapi` | HTTP provider | contracts | `PLANNED` | I3 | não inferir regras de negócio inexistentes |
| Migration diff | ferramenta | `harpia migrate diff` | database provider | migration plan | `PLANNED` | DB1 | snapshot/version history obrigatórios |
| Generated unit tests | A+B | derived contracts/Scenario | JUnit | tests | `PLANNED` | E0.9/S4 | service/controller primeiro |
| Generated integration tests | A+B | Scenario + providers | Spring Test | tests | `PLANNED` | S4 | rede/banco só via provider explícito |
| Property testing | A+B | Invariant | test provider | generated properties | `PLANNED` | pós-S4 | depende de generators de valores tipados |
| SCR benchmark | ferramenta | corpus | tokenizer | metrics | `PLANNED` | E0.9/B1 | tokenizer e artefatos brutos obrigatórios |
| CCR/TCR benchmark | ferramenta | agent protocol | tokenizer | metrics | `PLANNED` | B2 | só após MCP/workflows comparáveis |

## MCP agent surface

Detalhes e fases vivem em [`docs/mcp-roadmap.md`](docs/mcp-roadmap.md).

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| MCP STDIO server | ferramenta | `harpia mcp` | MCP transport | core facade | `PLANNED` | M1 | nenhuma implementação existe |
| Language resources | ferramenta | `harpia://language/*` | MCP | compact docs | `PLANNED` | M1 | progressive disclosure |
| Project resources | ferramenta | `harpia://project/*` | MCP | model snapshots | `PLANNED` | M1 | workspace-scoped e read-only primeiro |
| MCP read tools | ferramenta | project/entity/Logic/diagnostics tools | MCP | structured output | `PLANNED` | M1 | chamam exatamente o core da CLI |
| MCP build tools | ferramenta | validate/build/test/clean | MCP | structured execution | `PLANNED` | M2 | depende de writer e policy de execução |
| Semantic mutation | ferramenta | `add_*` | MCP | source patch | `PLANNED` | M3 | dry-run, validate, atomic write, rollback |
| Semantic patch | ferramenta | `apply_spec_patch` | MCP | source patch | `PLANNED` | M3 | formato estruturado e versionado |
| Diagnostic fix | ferramenta | `apply_diagnostic_fix` | MCP | safe edit | `PLANNED` | M3 | somente correções determinísticas |
| MCP prompts | ferramenta | create/fix/explain prompts | MCP | reusable workflow | `PLANNED` | M4 | depois das tools, nunca substitui semântica |
| MCP HTTP transport | ferramenta | remote transport | MCP | server | `RESEARCH` | M5 | STDIO e security model devem estabilizar primeiro |
| Agent skill | ferramenta | `skills/harpia/SKILL.md` | — | agent guidance | `PLANNED` | M1 | compacto, derivado da mesma fonte de docs |

## Extension

| Capability | Cobertura | Harpia construct | Provider | Java/Spring target | Status | Milestone | Notes |
|---|---|---|---|---|---|---|---|
| Custom implementation | C — Custom | `### Implementation custom X` | custom | generated interface + user bean | `PLANNED` | S6 | Escape hatch obrigatório, ainda indisponível |
| Custom dependencies | B — Capability | `harpia.yaml` | Maven | pinned dependency | `PLANNED` | S6 | Nunca na Business Spec |
| Java interoperability | C — Custom | generated contracts | Java | normal Java/Spring DI | `PLANNED` | S6 | Sem runtime Harpia |
| Complex math/image/PDF/ML | C — Custom | custom contract | user-selected | user Java | `CUSTOM` | S6 | Não criar keywords específicas |
| JNI/hardware/native protocol | C — Custom | custom contract | user-selected | user Java/native | `CUSTOM` | S6 | Fora da semântica core |
| Reflection/bytecode manipulation | C — Custom | custom contract | user-selected | user Java | `CUSTOM` | S6 | Nunca executado pelo compiler |
| Java snippets in spec | — | nenhum | — | — | `NOT_PLANNED` | — | Viola fronteira intenção/implementação |
| Shell/eval/scripts in spec | — | nenhum | — | — | `NOT_PLANNED` | — | Viola segurança e determinismo |
| Runtime Harpia proprietário | — | nenhum | — | — | `NOT_PLANNED` | — | Harpia é build-time only |
| Bytecode direto | — | nenhum | — | — | `NOT_PLANNED` | — | Java source convencional é o target |
| Template engine exposto | — | nenhum | — | — | `NOT_PLANNED` | — | Templates são detalhe interno |
| Outros language targets | B — Capability | mesmo Application IR | target packs futuros | Kotlin/C#/TS/Python/etc. | `PLANNED` | horizonte | catalogados `NOT_SUPPORTED`; sem generator ou fallback |

## Current result

Contagem sobre as linhas acima:

```text
Eligible language features: 214
SUPPORTED: 20
PARTIAL: 35
PLANNED: 150
RESEARCH: 9
CUSTOM boundaries: 6 (excluded)
NOT_PLANNED boundaries: 8 (excluded)

LANGUAGE COVERAGE % (strict): 20 / 214 = 9.3%
LANGUAGE PROGRESS % (weighted): (20 + 0.5 × 35) / 214 = 17.5%
```

O número estrito mede contratos já fechados, incluindo tooling cujo gate próprio passa. O número
ponderado mostra trabalho parcial sem promovê-lo a suporte. Nenhum item apenas planejado, Custom ou
Not Planned aumenta a porcentagem.

## Regra de atualização

Uma linha só muda para `SUPPORTED` quando existir um teste que percorra todos os estágios aplicáveis:

```text
Source → Parser → AST → Symbols → Semantic Analysis → Business IR
       → Capability Resolution → Application IR → Java → Maven test
```

Para ferramentas sem Java gerado, o teste deve percorrer sua saída terminal completa. Recursos
planejados podem ter exemplos documentais, mas esses exemplos precisam estar marcados como
`planned` e não podem passar por `harpia validate` fingindo suporte.
