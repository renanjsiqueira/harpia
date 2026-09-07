# Harpia Language Specification — V1 Draft

Status: proposta arquitetural, **não implementada**. A sintaxe normativa aceita pelo compilador
continua em [`harpia-language.md`](harpia-language.md).

Este draft transforma a V0 incrementalmente em uma Semantic Software Specification Language sem
recriar Java e sem invalidar specs existentes antes de haver migração e formatter.

## 1. Objetivos da V1

- tornar Command e Query conceitos explícitos;
- introduzir Project AST e referências cross-file;
- estabelecer Type System nominal;
- separar Business IR, requirements de capabilities e Application IR;
- permitir Event como primeiro slice de comportamento além de CRUD;
- preparar ValueObject, Enum, Integration e Custom Java sem fingir suporte;
- manter gramática pequena, formal e determinística.

Não são objetivos da V1 inicial: condicionais gerais, loops, async, workflow, cloud providers,
outras linguagens de destino ou snippets Java na spec.

## 2. Versionamento e compatibilidade

O formato canônico proposto é:

```yaml
harpia:
  languageVersion: 1
```

O formato atual:

```yaml
harpia: 1
```

é um schema marker legado da fase V0, não uma language version explícita. Durante a série `0.x`, o
loader deve aceitar os dois formatos, mapear o legado para a gramática V0 e emitir no máximo um
warning de migração. Não deve existir detecção heurística de versão a partir do conteúdo Markdown.

Uma spec V0 válida continua válida sob o modo de compatibilidade. A V1 recomenda Command/Query
explícitos; os H2 de use case legados continuam reconhecidos até uma versão major documentada.

## 3. Unidade de fonte

Cada `*.harpia.md` é um módulo de feature com exatamente um H1. O H1 define o nome do módulo e,
quando existe `## Data`, também a entidade primária implícita compatível com a V0.

```ebnf
module            = feature-heading, { module-member | documentation } ;
feature-heading   = h1, sp, type-name ;
module-member     = primary-data
                  | value-declaration
                  | enum-declaration
                  | command-declaration
                  | query-declaration
                  | event-declaration
                  | integration-declaration
                  | custom-declaration
                  | scenario-declaration ;
primary-data      = h2, sp, "Data", newline, { field-item | documentation } ;
```

Declarações futuras são identificadas pelo prefixo formal do H2. Isso evita que toda seção nova
seja confundida com um caso de uso.

```text
## Value Money
## Enum OrderStatus
## Command RegisterCustomer
## Query GetCustomer
## Event CustomerRegistered
## Integration FraudService
## Custom RiskCalculator
## Scenario Register customer successfully
```

## 4. Command e Query

```ebnf
command-declaration = h2, sp, "Command", sp, symbol-name, newline,
                      [ endpoint-section ], [ access-section ],
                      [ input-section ], [ rules-section ],
                      [ implementation-section ], flow-section,
                      output-section, [ errors-section ], [ scenarios-section ] ;

query-declaration   = h2, sp, "Query", sp, symbol-name, newline,
                      [ endpoint-section ], [ access-section ],
                      [ input-section ], [ rules-section ],
                      [ implementation-section ], flow-section,
                      output-section, [ errors-section ], [ scenarios-section ] ;
```

Regras semânticas propostas:

- Command representa mutação e possui transação por default quando usa persistence;
- Query representa leitura e rejeita `create`, `save`, `delete`, `emit` e `send` por default;
- Endpoint é um binding opcional; Command/Query não dependem de HTTP;
- nomes de Command/Query pertencem a namespaces globais distintos;
- `### Implementation custom X` substitui o flow e gera um contrato Java;
- não se infere endpoint a partir do nome. Se deve existir controller, o endpoint é explícito.

O comportamento transacional default precisa aparecer em `harpia inspect --stage application-ir`.

## 5. Primeiro slice V1: Command + Event

Forma canônica proposta:

````md
# Customer

## Data

- id: UUID generated
- email: Email required unique
- active: Boolean default true

## Command RegisterCustomer

### Endpoint

POST /customers

### Access

public

### Input

- email: Email required

### Flow

```flow
validate input
customer = create Customer from input
save customer
emit CustomerRegistered(customerId = customer.id, email = customer.email)
return customer
```

### Output

201 Customer

### Errors

- invalid input -> 400
- duplicate email -> 409

## Event CustomerRegistered

- customerId: UUID
- email: Email
````

Argumentos de Event são nomeados. Posicionais economizam caracteres, mas são frágeis a reordenação
e menos claros para humanos e LLMs.

## 6. Flow V1 incremental

Os oito comandos V0 permanecem:

```text
validate input
x = create E from input
x = load E by id
update x from input
xs = list E
save x
delete x
return x|nothing
```

A primeira extensão adiciona somente `emit`:

```ebnf
emit          = "emit", sp, event-name, "(", [ arguments ], ")" ;
arguments     = argument, { ",", sp, argument } ;
argument      = field-name, sp, "=", sp, expression ;
expression    = variable | member-access | input-access | literal ;
member-access = variable, ".", field-name ;
input-access  = "input.", field-name ;
```

O parser produz `EmitEvent(eventSymbol, arguments, sourceRange)`. O Semantic Analyzer resolve o
Event, valida exatamente os campos obrigatórios e compara o tipo de cada expressão.

Operações seguintes entram uma por vez, com AST e semântica próprias:

```text
require <rule> otherwise <error>
fail <error>
set <target> = <expression>
call <integration>.<operation>(named arguments)
send <email> to <expression>
```

`load E by id` mantém semântica de resultado obrigatório e erro `not found`. `find` será reservado
para buscas que retornam zero, um ou muitos resultados; portanto não é sinônimo de `load`.

`if`, loops, parallel e async não entram até existir evidência de que construções semânticas mais
específicas não resolvem os casos importantes.

## 7. Rules V1

Rules não interpretam inglês. O núcleo inicial é uma expressão tipada e sem efeitos colaterais:

```ebnf
rule-block     = rule-expression, { newline, rule-expression } ;
rule-expression = comparison
                | predicate
                | "not", sp, rule-expression
                | rule-expression, sp, ( "and" | "or" ), sp, rule-expression ;
comparison     = expression, sp, comparison-op, sp, expression ;
comparison-op  = "==" | "!=" | ">" | ">=" | "<" | "<=" ;
predicate      = "present", sp, expression
               | "unique", sp, field-name
               | "valid", sp, expression ;
```

Precedência, parênteses e conjunto de predicates precisam ser congelados antes da implementação.
Frases como `customer should probably be active` permanecem documentação.

## 8. Type System V1

O modelo interno deve ser uma álgebra selada, não uma enum crescente nem nomes Java:

```java
sealed interface TypeRef {
    record Scalar(ScalarKind kind) implements TypeRef {}
    record Named(SymbolId symbol, NamedKind kind) implements TypeRef {}
    record ListOf(TypeRef element) implements TypeRef {}
    record OptionalOf(TypeRef element) implements TypeRef {}
    record ReferenceTo(SymbolId entity) implements TypeRef {}
    record PageOf(TypeRef element) implements TypeRef {}
    record FileType(FileConstraints constraints) implements TypeRef {}
}
```

Escalares preservados:

```text
String Text Int Long Decimal Boolean UUID Email Date DateTime
```

Tipos nominais:

```text
Entity ValueObject Enum EventPayload IntegrationInput IntegrationOutput
```

Regras de superfície:

- um nome de Entity em campo singular resolve internamente para `ReferenceTo`;
- `List<Entity>` é relação múltipla, não generic Java arbitrário;
- `Optional<T>`, `List<T>` e `Page<T>` são os únicos containers inicialmente planejados;
- ausência de `required` em input continua significando opcional para compatibilidade V0;
- o formatter V1 não introduz a keyword redundante `optional`;
- tipos desconhecidos só são diagnosticados depois de a SymbolTable conter todos os arquivos;
- mapeamento Java/PostgreSQL pertence à Application IR/provider, não ao `TypeRef` de negócio.

## 9. Project AST

```java
record ProjectAst(LanguageVersion languageVersion,
                  List<ModuleAst> modules,
                  SourceIndex sources) {}

record ModuleAst(SymbolName name,
                 List<DeclarationAst> declarations,
                 SourceRange range) {}

sealed interface DeclarationAst permits EntityAst, ValueObjectAst, EnumAst,
        CommandAst, QueryAst, EventAst, IntegrationAst, CustomContractAst, ScenarioAst {}
```

`SpecAst` V0 deve ser convertido por um adapter para `ModuleAst`, permitindo migração incremental
sem duplicar todas as gramáticas existentes.

## 10. SymbolTable

A resolução é determinística e feita em duas passagens:

```text
Pass 1 — Declare
  percorre módulos por path ordenado
  registra SymbolId, kind, qualified name e SourceRange
  reporta duplicatas com related location

Pass 2 — Resolve
  resolve campos, outputs, events, flows, policies e integrations
  valida kind esperado e detecta referências desconhecidas
```

Namespaces iniciais:

```text
types       Entity, ValueObject, Enum
operations  Command, Query
messages    Event, Email
ports       Integration, Custom Contract
tests       Scenario
policies    Policy
```

Dois símbolos de kinds diferentes podem compartilhar nome apenas quando isso não torna uma
referência ambígua. A regra conservadora inicial é unicidade por namespace em todo o projeto.

`SymbolId` é derivado do qualified name e kind; nunca usa UUID aleatório, ordem de hash ou relógio.

## 11. SourceRange e source mapping

Modelo proposto:

```java
record SourcePosition(int line, int column, int offset) {}
record SourceRange(String file, SourcePosition start, SourcePosition end) {}
record RelatedLocation(String message, SourceRange range) {}
```

- linhas/colunas continuam 1-indexadas e contam code points Unicode;
- `end` é exclusivo;
- offsets são relativos ao source UTF-16 normalizado apenas internamente, nunca exibidos como
  coluna sem conversão;
- `SourceRef` permanece como adapter de início durante a migração;
- todo Declaration, Field, FlowStep, type reference e argument relevante carrega `SourceRange`;
- artefatos gerados recebem `GeneratedOrigin` com um ou mais ranges de origem.

O primeiro source map pode ser um manifesto lateral determinístico; comentários linha a linha no
Java só entram se não degradarem o código convencional.

## 12. Diagnostics V1

```java
record Diagnostic(DiagnosticCode code,
                  Severity severity,
                  String message,
                  Optional<SourceRange> source,
                  List<RelatedLocation> related,
                  Optional<String> hint) {}
```

Regras:

- códigos são tipos/constantes centrais, nunca strings inventadas pelo emitter;
- related locations substituem mensagens que embutem `file:line:column` manualmente;
- a ordenação inclui source principal, código, mensagem, related locations e hint;
- diagnostics podem renderizar snippet/caret, mas o dado estruturado não depende do terminal;
- `validate` em formato humano e um futuro `--format json` usam o mesmo objeto;
- parser, symbol resolution, semantic analysis, capability resolution, provider e generation têm
  famílias de código distintas.

Famílias propostas, preservando as atuais:

```text
HRP1xxx syntax
HRP2xxx semantic/symbol/type
HRP3xxx configuration
HRP4xxx known but unavailable language feature
HRP5xxx I/O/output
HRP6xxx capability/provider resolution
HRP7xxx generation/source mapping
HRP8xxx lint
```

## 13. Business IR e Application IR

Business IR V1:

```java
record BusinessProject(List<BusinessModule> modules,
                       SymbolTable symbols,
                       CapabilityRequirementSet requirements) {}
```

Ela contém Entity, ValueObject, Enum, Command, Query, Rule, Event e operações tipadas. Não contém
Spring, JPA, PostgreSQL, JWT, SMTP ou classes Java.

Requirements são inferidos:

```text
Endpoint     -> http
save/load    -> persistence
emit         -> events
send         -> email
authenticated -> authentication
call         -> integration
```

Application IR resolve essas necessidades:

```java
record ApplicationProject(Target target,
                          List<PersistentType> persistence,
                          List<HttpOperation> http,
                          List<ApplicationOperation> operations,
                          List<EventPublication> events,
                          List<CustomContract> customContracts,
                          DependencySet dependencies) {}
```

Ainda não é source Java. Por exemplo, `EventPublication` pode estar resolvido para `LOCAL`, mas não
contém uma chamada textual a `ApplicationEventPublisher`.

## 14. Capabilities e providers iniciais

Foundation mínima:

| Capability | Provider inicial | Contribuição esperada |
|---|---|---|
| `http` | `spring-mvc` implícito no target | handlers, serialization, advice |
| `persistence` | `postgresql` | JPA, repository, Flyway, datasource |
| `events` | `local` | event records e publication port/adapter |
| `custom` | `java` | interfaces, source root e DI contract |

Providers permanecem internos inicialmente. Não há SPI pública antes de dois providers reais para a
mesma capability demonstrarem o contrato necessário.

## 15. Catálogo inicial de inferências

| Semântica | Inferências permitidas |
|---|---|
| `required` | input validation, non-null domain field, `NOT NULL`, testes derivados |
| `unique` | unique constraint, lookup de conflito, error mapping quando declarado, testes |
| `generated` em id | omissão de input, geração UUID, primary key |
| Command com escrita | transação default |
| Query | read-only e proibição de side effects por default |
| Endpoint | HTTP binding, request/response serialization |
| `emit` | requirement `events`, payload tipado e publicação após operação |

Inferências não documentadas são bugs. `inspect` deve exibir a expansão semântica.

## 16. Critério para estabilizar uma construção

Uma keyword sai de draft somente quando o mesmo change set contém:

1. gramática normativa;
2. AST com SourceRange;
3. símbolos/tipos envolvidos;
4. validação semântica e diagnostics;
5. Business IR;
6. requirement de capability quando aplicável;
7. Application IR/provider;
8. geração Java;
9. golden test;
10. compilação/teste Maven do projeto gerado;
11. atualização de `LANGUAGE_COVERAGE.md` e SKILL.md.

