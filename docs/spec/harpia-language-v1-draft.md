# Harpia Language Specification — V1 Draft

Status: **implementação incremental**. `Command` e `Query` já são executáveis em
`languageVersion: 1`; as demais construções deste documento continuam propostas. A gramática V0
normativa permanece em [`harpia-language.md`](harpia-language.md).

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

Não são objetivos da V1 inicial: loops gerais, async, workflow, cloud providers, outras linguagens
de destino ou snippets Java na spec. Flow possui apenas branching limitado e tipado; algoritmos
continuam pertencendo a `Logic` ou a código custom.

## 2. Versionamento e compatibilidade

O formato canônico proposto é:

```yaml
harpia:
  schemaVersion: 1
  languageVersion: 1
```

O compilador atual usa a mesma dimensão explícita para a gramática V0:

```yaml
harpia:
  schemaVersion: 1
  languageVersion: 0
```

`schemaVersion` versiona somente a estrutura de `harpia.yaml`; `languageVersion` seleciona a
gramática e a semântica Harpia; `target.language.version` continua pertencendo ao target (Java no
primeiro target pack). O antigo scalar `harpia: 1` foi removido antes da adoção pública, sem camada
de compatibilidade. Não existe detecção heurística de versão a partir do conteúdo Markdown.

`languageVersion: 1` seleciona um registry próprio. Ele acrescenta `Command` e `Query`, mantém H2
de use case legado como fallback e rejeita construções ainda futuras com diagnóstico estável. Em
V0, um prefixo que somente a V1 entende é rejeitado, nunca reinterpretado silenciosamente.

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

Status: **implementado** em `languageVersion: 1`. O que já vale hoje:

- `## Command <Nome>` e `## Query <Nome>` são kinds de declaração próprios;
- a natureza deixa de ser inferida da forma do flow: um Command é transacional e uma Query é
  `readOnly` por declaração, não por acaso dos seus passos;
- uma Query que declara `create`, `update`, `save` ou `delete` é rejeitada com `HRP2120`;
- `Endpoint` e `Access` formam um binding HTTP inline opcional; se um aparecer, o outro é
  obrigatório;
- sem binding HTTP, a operação continua nos IRs e gera service/teste, mas não controller;
- o H2 sem prefixo continua válido e mantém a inferência V0, então nada já escrito muda de sentido;
- em `languageVersion: 0`, um `## Command X` é recusado com `HRP1107` em vez de ser lido em
  silêncio como um caso de uso chamado `CommandX`;
- **uma operação pertence à entidade que seu flow nomeia**, não ao arquivo onde foi escrita. Na V0
  as duas coincidem por regra; a partir da V1 um `## Command` pode viver num módulo próprio e operar
  sobre entidade declarada em outro arquivo. Uma operação que nomeia duas entidades é recusada, e
  uma entidade que nenhuma operação do projeto toca continua sendo reportada como órfã.

```ebnf
command-declaration = h2, sp, "Command", sp, symbol-name, newline,
                      [ endpoint-section, access-section ],
                      [ input-section ], [ rules-section ],
                      [ implementation-section ], flow-section,
                      output-section, [ errors-section ], [ scenarios-section ] ;

query-declaration   = h2, sp, "Query", sp, symbol-name, newline,
                      [ endpoint-section, access-section ],
                      [ input-section ], [ rules-section ],
                      [ implementation-section ], flow-section,
                      output-section, [ errors-section ], [ scenarios-section ] ;
```

Regras semânticas implementadas neste slice:

- Command representa mutação e possui transação por default quando usa persistence;
- Query representa leitura e rejeita `create`, `save`, `delete`, `emit` e `send` por default;
- Endpoint é um binding opcional; Command/Query não dependem de HTTP;
- nomes de Command/Query pertencem ao namespace global de operações;
- não se infere endpoint a partir do nome. Se deve existir controller, o endpoint é explícito.

Bindings HTTP em arquivo já seguem a especificação executável
[`harpia-bindings-v1.md`](harpia-bindings-v1.md). `### Implementation custom X` e os demais itens
deste draft ainda são propostas.

O comportamento transacional default precisa aparecer em `harpia inspect --stage application-ir`.

## 5. Próximo slice proposto: Command + Event

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

`if`/`else` está implementado como branching de orquestração: condição booleana sobre o input,
indentação de quatro espaços, no máximo dois níveis e sem declarar variáveis ou retornar dentro de
um ramo. Loops, parallel e async continuam fora deste recorte.

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
    record RelationshipTo(SymbolId entity, Loading loading, Lifecycle lifecycle) implements TypeRef {}
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

- `Reference<Entity>` resolve para `ReferenceTo` e preserva somente a identidade tipada;
- um nome de Entity em campo singular resolve para `RelationshipTo` lazy e independente;
- `List<Entity>` é uma relação múltipla lazy e independente, não generic Java arbitrário;
- `Optional<T>`, `List<T>` e `Page<T>` são os únicos containers inicialmente planejados;
- ausência de `required` em input continua significando opcional para compatibilidade V0;
- o formatter V1 não introduz a keyword redundante `optional`;
- tipos desconhecidos só são diagnosticados depois de a SymbolTable conter todos os arquivos;
- mapeamento Java/PostgreSQL pertence à Application IR/provider, não ao `TypeRef` de negócio.

## 9. Project AST

Status: **fundação implementada para as declarações V0 atuais**. `LanguageVersion` e `SourceIndex`
serão acrescentados nos próximos slices sem voltar a usar uma lista informal de specs.

```java
record ProjectAst(List<ModuleAst> modules) {}

record ModuleAst(String file,
                 String name,
                 List<DeclarationAst> declarations,
                 SourceRef where) {}

sealed interface DeclarationAst permits EntityDeclaration, UseCaseDeclaration,
        LogicDeclaration, ScenarioDeclaration {}
```

`ProjectAst` ordena módulos por source path; `ModuleAst` preserva a ordem das declarações. O antigo
root `SpecAst` foi substituído por esses containers e permanece apenas como namespace dos nós V0
menores. O `DeclarationParserRegistry` despacha `Data`, `Logic`, `Scenario` e o fallback de use case
por `DeclarationKind`, permitindo adicionar os kinds V1 sem transformar `SpecParser` em um parser
monolítico.

A forma alvo ainda acrescentará `LanguageVersion`, `SourceIndex`, nomes tipados e os nós V1
`ValueObjectAst`, `EnumAst`, `CommandAst`, `QueryAst`, `EventAst`, `IntegrationAst` e
`CustomContractAst` conforme cada slice for implementado.

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

Implementado com uma diferença deliberada em relação ao esboço original:

```java
record SourceRef(String file, int line, int column, Optional<Position> end) {}
record Position(int line, int column) {}
record RelatedLocation(String message, SourceRef where) {}
```

O fim vive no mesmo `SourceRef` em vez de um `SourceRange` separado, porque um tipo paralelo
obrigaria a reescrever todo produtor de localização do compilador para ganhar uma coordenada. O
`offset` não entrou: nada o consome ainda, e enfiar um valor que ninguém lê contraria a regra de não
gerar código sem consumidor. Ele volta quando existir LSP ou source map.

- linhas/colunas continuam 1-indexadas e contam code points Unicode;
- `end` é exclusivo e opcional: a maioria das posições nasce de um ponto, e inventar um fim seria
  mentira;
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
