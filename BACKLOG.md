# Harpia Backlog

Fonte única oficial de escopo, status, prioridade e evidência da Harpia. Auditoria reorganizada em
**2026-09-10** a partir da codebase; documentação sozinha não conta como implementação.

## Product Definition

> **Harpia is a semantic backend specification compiler and project bootstrapper.**

> **Harpia does not need to model the entire lifecycle of a backend application.**

> **Harpia needs to generate a strong, conventional and compilable starting point.**

> **After generation, the developer or AI agent owns the generated code and may continue development directly in the target language.**

> **Generate it. Own it. Keep coding.**

Harpia não substitui Java, não permanece obrigatoriamente como fonte de verdade de cada linha e
não precisa alcançar expressividade universal. O modo primário é **greenfield bootstrap**:

```text
Requirement → Human / AI Agent → Harpia Specification → Harpia Compiler
            → Generated Backend Baseline → Compile + Tests → HANDOFF
            → Developer / AI Agent → normal target-language development
```

No target atual: `Harpia → Java 21 + Spring Boot → mvn test/package → HANDOFF → Java normal`.

## Core Principles

- **Bootstrap-first:** valor de baseline supera completude teórica; queremos a maior baseline útil
  com a menor superfície semântica.
- **Admission rule:** se uma capacidade não é necessária antes do handoff, ela não entra
  automaticamente no Core V1.
- **Anti-feature:** **Harpia must not become Java written in Markdown.**
- **One mature construct:** `Logic` expressa computation e decision no Core; Formula e Decision
  dedicadas ficam upstream.
- **Boundaries:** Spec diz o que o software faz; Binding diz como se comunica; Config escolhe
  target, provider e ambiente.
- **Determinism:** IA pode produzir intenção; `validate` e `build` nunca dependem de LLM.
- **Meaning before syntax:** transformer resolve significado; renderer/template escreve sintaxe.
- **Custom by design:** comportamento raro, implementation-specific ou target-dependent prefere
  código custom ao crescimento da linguagem.

### Feature Admission Rule

Uma feature semântica candidata deve responder positivamente à maioria:

1. É comum em backends greenfield?
2. É necessária antes do handoff?
3. Remove implementação repetitiva relevante?
4. Expressa intenção em vez de framework?
5. Pode ser validada deterministicamente?
6. É portável entre targets?
7. Agentes conseguem gerá-la com confiabilidade?
8. O compiler consegue entregá-la de forma mais consistente do que código target direto?

Caso contrário, a classificação correta é `NEXT`, `LABS` ou `CUSTOM`.

### Generated Code Ownership

O código gerado pertence ao developer, pode ser modificado por pessoa ou agente, não exige runtime
Harpia e não precisa ser compreendido novamente pelo compiler. Regeneração é opcional. Managed
Mode, se existir, é pesquisa separada e nunca condição do bootstrap.

### Harpia Code Relationship

Harpia é open source e utilizável de forma independente. Harpia Code pode orquestrá-la como
harness determinístico, sem ser implementado neste repositório:

```text
Harpia Code → AI Agent → Harpia Harness
                       ├─ validate / diagnostics
                       ├─ inspect / target metadata
                       └─ build / verification
                       → Generated Backend → HANDOFF → Agent continua em Java
```

## Current State

O estado numérico e o último gate ficam em [Current Coverage](#current-coverage). Hoje há compiler
multi-file, AST/IRs, diagnostics/source maps, Logic, Enum, ValueObject, Rule/Invariant, CRUD REST,
JPA/PostgreSQL/Flyway, target Java/Spring e projeto Maven gerado. O único target suportado é
`java-spring`; MCP e brownfield ainda não existem.

## Status Legend

| Status | Significado |
|---|---|
| `DONE` | Implementação funcional e evidência compatível com o recorte declarado. |
| `PARTIAL` | Há valor implementado, mas falta parte do contrato ou gate. |
| `TODO` | Planejado, sem implementação comprovada. |
| `BLOCKED` | Impedimento externo explícito e atual. |
| `RESEARCH` | Hipótese que precisa de spike/ADR antes de compromisso. |
| `NOT_SUPPORTED` | Identificador honesto sem generator/provider executável. |
| `CUSTOM` | Caminho oficial é código target customizado. |
| `WONT_DO` | Fora do produto ou contrário aos princípios. |

Somente `DONE` usa checkbox marcado. Todo `DONE`/`PARTIAL` mantém evidência real. Dependências e IDs
são canônicos; referências em tabelas não entram novamente nas contagens.

## Priority Legend

| Prioridade | Uso após esta reorganização |
|---|---|
| `P0` | Bloqueia um critério do Core V1 (ou fundação já concluída que o bloqueava). |
| `P1` | Próximo upstream de alto valor, sem bloquear V1. |
| `P2` | Upstream posterior. |
| `P3` | Exploração futura. |
| `P4` | Pesquisa, target não suportado ou limite deliberado. |

Esforço continua em `XS`, `S`, `M`, `L`, `XL`.

# Harpia Core V1

Core V1 é o mínimo concluível necessário para gerar, testar, empacotar e entregar uma baseline
backend greenfield de complexidade intermediária. Os itens incompletos nesta seção são blockers;
ideias fora dela não participam do Definition of Done.

## Core V1 Reference Application

O gate de produto será um **Commerce Service** com `Customer`, `Address`, `Product`, `Order` e
`OrderItem`; enums `CustomerTier`/`OrderStatus`; Logic `CalculateDiscount`/`CalculateTotal`;
Commands `CreateOrder`/`CancelOrder`; Queries `GetOrder`/`SearchOrders`; PostgreSQL/JPA/Flyway;
REST com path/query/body/errors; `FraudService` HTTP; `OrderCreated`; e endpoints autenticados com
role `ADMIN`. Kafka é opcional se ameaçar o fechamento da V1; Event local não é.

Estado atual do cenário:

| Já suportado | Ainda necessário |
|---|---|
| Entity, campos, Enum, ValueObject, Rule/Invariant, Logic, Reference e relationships owned | Aggregate e ownership de aggregate |
| Command/Query CRUD, filtros/sort/page, require, Integration/Operation ports e binding HTTP básico | Flow com calls e loop |
| PostgreSQL, JPA e migration Flyway inicial | FraudService HTTP client e timeout básico |
| Java/Spring Maven com testes gerados | Event/emit, JWT/role e package gate |
| código developer-owned e source mapping | custom DI/layout, JSON e handoff manifest |

## Product Milestones

1. **Compiler Foundation** — majoritariamente concluído.
2. **Real Domain Model** — nominal types e relationships.
3. **Real Application Flow** — require/fail/set/call/branches/loop e queries básicas.
4. **Real External Backend** — HTTP integration e Event.
5. **Production Baseline** — security, config, testes e package.
6. **Harness Ready** — resultados estruturados e handoff.
7. **Core V1** — Reference Application passa todos os gates.

## Done Foundation

A base comprovada inclui parser/AST/ProjectAst, SymbolTable, análise semântica, Business IR,
Application IR, diagnostics/ranges/source maps, geração determinística, Java Target Model,
Java/Spring, REST CRUD, JPA/PostgreSQL/Flyway, Logic pura, testes gerados, golden/determinismo e
ownership pós-geração. Os registros canônicos e suas evidências permanecem abaixo.

## Language and Type Foundation

- [x] `CORE-001` **Arquivos `*.harpia.md`** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`SpecDiscovery`](src/main/java/dev/harpia/source/SpecDiscovery.java), [`SpecDiscoveryTest`](src/test/java/dev/harpia/source/SpecDiscoveryTest.java).

- [x] `CORE-002` **Markdown como especificação executável estruturada** — `DONE` no recorte V0 · `P0` · `M` · Area: `Language Core`
  - Evidence: [`MarkdownStructure`](src/main/java/dev/harpia/ast/MarkdownStructure.java), [`SpecParserTest`](src/test/java/dev/harpia/parse/SpecParserTest.java).

- [x] `CORE-003` **Parser estrutural CommonMark** — `DONE` · `P0` · `M` · Area: `Language Core`
  - Evidence: [`MarkdownStructure`](src/main/java/dev/harpia/ast/MarkdownStructure.java), [`RawSpanTest`](src/test/java/dev/harpia/ast/RawSpanTest.java).

- [x] `CORE-004` **Syntax AST V0** — `DONE` para Entity/use case/Logic atuais · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ProjectAst`](src/main/java/dev/harpia/parse/ProjectAst.java), [`ModuleAst`](src/main/java/dev/harpia/parse/ModuleAst.java), [`SpecAst`](src/main/java/dev/harpia/parse/SpecAst.java), [`LogicAst`](src/main/java/dev/harpia/parse/LogicAst.java).

- [x] `CORE-005` **Parser da gramática V0** — `DONE` · `P0` · `L` · Area: `Language Core`
  - Evidence: [`SpecParser`](src/main/java/dev/harpia/parse/SpecParser.java), [`DeclarationParserRegistry`](src/main/java/dev/harpia/parse/DeclarationParserRegistry.java), [`SpecParserDiagnosticsTest`](src/test/java/dev/harpia/parse/SpecParserDiagnosticsTest.java).

- [x] `CORE-006` **Descoberta e compilação multi-file determinística** — `DONE` · `P0` · `M` · Area: `Language Core`
  - Evidence: [`SpecDiscovery`](src/main/java/dev/harpia/source/SpecDiscovery.java), [`HarpiaCompiler`](src/main/java/dev/harpia/HarpiaCompiler.java).

- [x] `CORE-007` **Modelo de projeto multi-file** — `DONE`; o pipeline inteiro consome `ProjectAst` ordenado por path · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ProjectAst`](src/main/java/dev/harpia/parse/ProjectAst.java), [`HarpiaCompiler`](src/main/java/dev/harpia/HarpiaCompiler.java), [`ProjectAstTest`](src/test/java/dev/harpia/parse/ProjectAstTest.java).

- [x] `CORE-008` **Project AST explícita** — `DONE` para os kinds executáveis atuais; `ModuleAst` preserva declarações ordenadas e o registry permite extensão incremental · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ModuleAst`](src/main/java/dev/harpia/parse/ModuleAst.java), [`DeclarationAst`](src/main/java/dev/harpia/parse/DeclarationAst.java), [`DeclarationParserRegistryTest`](src/test/java/dev/harpia/parse/DeclarationParserRegistryTest.java).

- [x] `CORE-009` **Symbol Table global em duas passagens** — `DONE` para todos os namespaces/kinds executáveis; declare global determinístico e resolve nos analyzers · `P0` · `L` · Area: `Language Core`
  - Evidence: [`SymbolTable`](src/main/java/dev/harpia/symbol/SymbolTable.java), [`SymbolTableTest`](src/test/java/dev/harpia/symbol/SymbolTableTest.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java).

- [ ] `CORE-010` **Escopos léxicos** — `PARTIAL`; existem em Logic, não no conjunto futuro da linguagem · `P0` · `M` · Area: `Language Core`
  - Evidence: [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [ ] `CORE-011` **Referências cross-file** — `PARTIAL`; a partir da V1 uma operação pertence à entidade que seu flow nomeia, podendo viver em módulo próprio; faltam os tipos nominais (`TYPE-*`) como referência de campo · `P0` · `L` · Area: `Language Core`
  - Evidence: [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`Resolver`](src/main/java/dev/harpia/model/Resolver.java), [`CrossModuleReferenceTest`](src/test/java/dev/harpia/validate/CrossModuleReferenceTest.java).

- [x] `CORE-012` **Semantic Analyzer V0** — `DONE` para o recorte executável atual · `P0` · `L` · Area: `Language Core`
  - Evidence: [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`SemanticValidatorTest`](src/test/java/dev/harpia/validate/SemanticValidatorTest.java).

- [x] `CORE-013` **Business IR independente de target** — `DONE` para o recorte V0 · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ProjectModel`](src/main/java/dev/harpia/model/ProjectModel.java), [`ArchitectureBoundaryTest`](src/test/java/dev/harpia/ArchitectureBoundaryTest.java).

- [x] `CORE-014` **Application IR independente de Java/Spring** — `DONE` para o recorte V0 · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ApplicationProject`](src/main/java/dev/harpia/application/ApplicationProject.java), [`ApplicationModelBuilderTest`](src/test/java/dev/harpia/application/ApplicationModelBuilderTest.java).

- [x] `CORE-015` **SourceLocation pontual** — `DONE` · `P0` · `M` · Area: `Language Core`
  - Evidence: [`SourceRef`](src/main/java/dev/harpia/diag/SourceRef.java), [`RawSpan`](src/main/java/dev/harpia/ast/RawSpan.java).

- [x] `CORE-016` **SourceRange, related locations e source mapping por símbolo/linha gerada** — `DONE`; ranges exclusivos e related locations chegam ao índice imutável de cada `GeneratedFile` · `P0` · `L` · Area: `Language Core`
  - Evidence: [`SourceRef`](src/main/java/dev/harpia/diag/SourceRef.java), [`RelatedLocation`](src/main/java/dev/harpia/diag/RelatedLocation.java), [`GeneratedSourceMapping`](src/main/java/dev/harpia/emit/GeneratedSourceMapping.java), [`JavaSourceRendererTest`](src/test/java/dev/harpia/target/javaspring/renderer/JavaSourceRendererTest.java).

- [x] `CORE-017` **Diagnostics estruturados, códigos estáveis e ordenação determinística** — `DONE` · `P0` · `M` · Area: `Language Core`
  - Evidence: [`Diagnostic`](src/main/java/dev/harpia/diag/Diagnostic.java), [`DiagnosticOrderingTest`](src/test/java/dev/harpia/diag/DiagnosticOrderingTest.java).

- [x] `CORE-018` **Versão da linguagem independente** — `DONE`; schema `1`, linguagem Harpia `0` e linguagem do target Java `21` são dimensões explícitas; a versão Harpia seleciona parser registry, pertence ao `ProjectAst` e aparece no inspect · `P0` · `M` · Area: `Language Core`
  - Evidence: [`LanguageVersion`](src/main/java/dev/harpia/LanguageVersion.java), [`ConfigValidator`](src/main/java/dev/harpia/config/ConfigValidator.java), [`ProjectAst`](src/main/java/dev/harpia/parse/ProjectAst.java), [`ConfigLoaderTest`](src/test/java/dev/harpia/config/ConfigLoaderTest.java), [`InspectorTest`](src/test/java/dev/harpia/inspect/InspectorTest.java).

- [x] `CORE-019` **Versão do compilador** — `DONE` · `P1` · `XS` · Area: `Language Core`
  - Evidence: [`VersionCommand`](src/main/java/dev/harpia/cli/VersionCommand.java), [`harpia-version.properties`](src/main/resources/harpia-version.properties).

- [x] `CORE-020` **Compilação determinística e ordem estável de arquivos** — `DONE` · `P0` · `M` · Area: `Language Core`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`GeneratedTree`](src/main/java/dev/harpia/emit/GeneratedTree.java).

- [x] `CORE-021` **Saída reproduzível, sem timestamps ou aleatoriedade** — `DONE` · `P0` · `S` · Area: `Language Core`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`OutputNormalizer`](src/main/java/dev/harpia/emit/OutputNormalizer.java).

- [x] `CORE-022` **Validação de projeto** — `DONE` para V0 · `P0` · `M` · Area: `Language Core`
  - Evidence: [`HarpiaCompiler`](src/main/java/dev/harpia/HarpiaCompiler.java), [`ValidateExampleTest`](src/test/java/dev/harpia/ValidateExampleTest.java).

- [x] `CORE-023` **Compilação de projeto** — `DONE` para V0 · `P0` · `M` · Area: `Language Core`
  - Evidence: [`HarpiaCompiler`](src/main/java/dev/harpia/HarpiaCompiler.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [x] `CORE-026` **Inspect de AST/symbols/Business IR/Application IR** — `DONE`; renderiza os modelos carregados em `CompileResult.Stages`, sem pipeline paralelo · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`Inspector`](src/main/java/dev/harpia/inspect/Inspector.java), [`InspectorTest`](src/test/java/dev/harpia/inspect/InspectorTest.java), [`SemanticFixtureTest`](src/test/java/dev/harpia/SemanticFixtureTest.java).

- [x] `CORE-027` **Escrita idempotente, manifesto e proteção de arquivos desconhecidos** — `DONE` · `P0` · `L` · Area: `Ownership`
  - Evidence: [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).

- [x] `TYPE-001` **String** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`LineGrammarTest`](src/test/java/dev/harpia/parse/LineGrammarTest.java).

- [x] `TYPE-002` **Text** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java).

- [x] `TYPE-003` **Int** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `TYPE-004` **Long** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java).

- [x] `TYPE-005` **Decimal** — `DONE` com precisão arbitrária no target Java · `P0` · `S` · Area: `Language Core`
  - Evidence: [`LogicType`](src/main/java/dev/harpia/logic/LogicType.java), [`GeneratedLogicCompilesTest`](src/test/java/dev/harpia/target/javaspring/GeneratedLogicCompilesTest.java).

- [x] `TYPE-006` **Boolean** — `DONE` · `P0` · `XS` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `TYPE-007` **UUID** — `DONE` · `P0` · `S` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [x] `TYPE-008` **Email** — `DONE` como tipo semântico escalar com validação Spring · `P0` · `S` · Area: `Language Core`
  - Evidence: [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java), [`JavaSpringEntityTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformerTest.java).

- [x] `TYPE-009` **Date** — `DONE` como campo/literal V0 · `P0` · `S` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java).

- [x] `TYPE-010` **DateTime** — `DONE` como campo/literal V0 · `P0` · `S` · Area: `Language Core`
  - Evidence: [`TypeRef`](src/main/java/dev/harpia/model/TypeRef.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java).

- [x] `TYPE-020` **Enum nominal** — `DONE` na V1; `## Enum <Nome>` declara um conjunto fechado de valores, entra na symbol table, é referenciável como tipo de campo e gera enum Java, coluna e constraint. O tipo de campo passou a ser álgebra selada (`FieldType`/`ApplicationFieldType`), então todo mapeamento precisa responder pelo nominal em vez de tratá-lo como texto · `P1` · `L` · Area: `Domain`
  - Evidence: [`EnumDeclarationParser`](src/main/java/dev/harpia/parse/EnumDeclarationParser.java), [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`ApplicationFieldType`](src/main/java/dev/harpia/application/ApplicationFieldType.java), [`JavaSpringEnumTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEnumTransformer.java), [`DeclaredEnumTest`](src/test/java/dev/harpia/validate/DeclaredEnumTest.java).

- [x] `TYPE-021` **List&lt;T&gt; geral** — `DONE` na V1 como tipo de campo; `List<Escalar>` e `List<Enum>` viram `@ElementCollection` com tabela própria ligada ao dono, e `required` significa não-vazia. `List<Entity>` continua relacionamento (`DOM-012`) e coleção de valor ou de coleção é recusada. Como parâmetro/retorno de Logic é `LOGIC-006` · `P1` · `L` · Area: `Language Core`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`ListFieldTest`](src/test/java/dev/harpia/validate/ListFieldTest.java).

- [x] `TYPE-022` **Optional&lt;T&gt; explícito** — `DONE` na V1; `Optional<T>` move a possibilidade de ausência para o tipo que o chamador recebe, sem mudar como o valor é armazenado: coluna nullable e campo JPA nu, getter e response DTO em `Optional<T>`. `Optional<T> required` é contradição recusada (`HRP2126`) · `P1` · `M` · Area: `Language Core`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java), [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`OptionalFieldTest`](src/test/java/dev/harpia/validate/OptionalFieldTest.java).

- [x] `TYPE-023` **Reference&lt;T&gt;** — `DONE` na V1; `Reference<Entidade>` é identidade tipada: coluna `<campo>_id` com o tipo do id do alvo e chave estrangeira na migração, sem `@ManyToOne`, cascade ou fetch. O alvo precisa ser uma entidade declarada. Associação com ciclo de vida é `DOM-012` · `P0` · `L` · Area: `Domain`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`ReferenceFieldTest`](src/test/java/dev/harpia/validate/ReferenceFieldTest.java).

- [x] `TYPE-024` **ValueObject nominal** — `DONE` na V1; `## Value <Nome>` declara um grupo de campos sem identidade, referenciável como tipo de campo, gerado como `@Embeddable` e armazenado como colunas prefixadas da entidade que o contém, com `@AttributeOverride` casando com a migração. Campos `generated`/`unique` são recusados: identidade é de entidade · `P1` · `L` · Area: `Domain`
  - Evidence: [`ValueDeclarationParser`](src/main/java/dev/harpia/parse/ValueDeclarationParser.java), [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaSpringValueTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringValueTransformer.java), [`DeclaredValueTest`](src/test/java/dev/harpia/validate/DeclaredValueTest.java).


## Domain, Rules and Logic

- [x] `DOM-001` **Entity escalar V0 end-to-end** — `DONE`; parser, AST, semântica, IR, Java/JPA e testes · `P0` · `L` · Area: `Domain`
  - Evidence: [`SpecParser`](src/main/java/dev/harpia/parse/SpecParser.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [x] `DOM-002` **Fields tipados** — `DONE` para os dez escalares V0 · `P0` · `M` · Area: `Domain`
  - Evidence: [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java), [`FieldModel`](src/main/java/dev/harpia/model/FieldModel.java).

- [x] `DOM-003` **Campo `required`** — `DONE` · `P0` · `S` · Area: `Domain`
  - Evidence: [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java).

- [x] `DOM-004` **Campo opcional implícito** — `DONE`; ausência de `required`, sem keyword redundante · `P0` · `XS` · Area: `Domain`
  - Evidence: [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java), [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java).

- [x] `DOM-005` **Campo `unique`** — `DONE` com constraint JPA/SQL e erro duplicado V0 · `P0` · `M` · Area: `Domain`
  - Evidence: [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `DOM-006` **Campo `generated`** — `DONE` para identificador UUID V0 · `P0` · `S` · Area: `Domain`
  - Evidence: [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`SemanticValidatorTest`](src/test/java/dev/harpia/validate/SemanticValidatorTest.java).

- [x] `DOM-007` **Campo `default`** — `DONE` para literais escalares V0 · `P0` · `M` · Area: `Domain`
  - Evidence: [`JavaDefaultValueMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaDefaultValueMapper.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [x] `DOM-012` **Relationships e referências** — `DONE` na V1; um Entity usado como tipo vira associação singular e `List<Entity>` vira associação múltipla compartilhável. Ambos carregam metadata explícita `LAZY`/`INDEPENDENT`, sem cascade; `Reference<T>` permanece identidade tipada sem carregamento. O Application IR expõe cardinalidade, loading e ciclo de vida · `P0` · `XL` · Area: `Domain`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`ApplicationFieldType`](src/main/java/dev/harpia/application/ApplicationFieldType.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`RelationshipFieldTest`](src/test/java/dev/harpia/validate/RelationshipFieldTest.java).

- [x] `DOM-013` **Owned relationship** — `DONE` na V1; o modifier `owned` em `Entity`/`List<Entity>` troca o ciclo de vida para `DEPENDENT`, torna o alvo exclusivo e gera one-to-one/one-to-many com cascade total e orphan removal. Uso em escalar, ValueObject ou `Reference<T>` é recusado por `HRP2131` · `P0` · `L` · Area: `Domain`
  - Evidence: [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java), [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`OwnedRelationshipTest`](src/test/java/dev/harpia/validate/OwnedRelationshipTest.java).

- [x] `DOM-014` **Embedded ValueObject** — `DONE`; um campo de ValueObject gera `@Embedded`/`@AttributeOverride` e colunas prefixadas coerentes com a migration inicial · `P0` · `L` · Area: `Domain`
  - Evidence: [`JavaSpringValueTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringValueTransformer.java), [`DeclaredValueTest`](src/test/java/dev/harpia/validate/DeclaredValueTest.java).

- [x] `RULE-001` **Rule executável tipada** — `DONE` na V1; um item de lista sob `### Rules` é uma condição booleana sobre o input, tipada pelo mesmo analisador de expressões da Logic, carregada pelos dois IRs e verificada onde o flow declara `validate input`. Prosa continua documentação e a V0 não muda de significado · `P1` · `L` · Area: `Domain`
  - Evidence: [`UseCaseDeclarationParser`](src/main/java/dev/harpia/parse/UseCaseDeclarationParser.java), [`LogicAnalyzer.analyzeExpression`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`RuleModel`](src/main/java/dev/harpia/model/RuleModel.java), [`ApplicationRule`](src/main/java/dev/harpia/application/ApplicationRule.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`RuleTest`](src/test/java/dev/harpia/validate/RuleTest.java).

- [x] `RULE-002` **Invariant** — `DONE` na V1; `## Invariants` declara condições sobre a entidade, tipadas contra os campos dela pelo mesmo analisador de expressões, e verificadas antes de cada `save` — o momento em que o estado se torna durável. Violação é `InvariantViolationException` com 422, porque uma requisição bem formada pedindo um estado proibido não é input inválido · `P1` · `L` · Area: `Domain`
  - Evidence: [`InvariantDeclarationParser`](src/main/java/dev/harpia/parse/InvariantDeclarationParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`InvariantTest`](src/test/java/dev/harpia/validate/InvariantTest.java).

- [x] `RULE-009` **Validação Java gerada a partir de Rule/Invariant** — `DONE`; regras validam o request no boundary declarado e invariants validam a entidade antes do `save`, com exceptions/status próprios · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`RuleTest`](src/test/java/dev/harpia/validate/RuleTest.java), [`InvariantTest`](src/test/java/dev/harpia/validate/InvariantTest.java).

- [x] `LOGIC-008` **Declaração `## Logic`** — `DONE` · `P1` · `L` · Area: `Logic`
  - Evidence: [`LogicDeclarationParser`](src/main/java/dev/harpia/parse/LogicDeclarationParser.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).

- [x] `LOGIC-009` **Input e output tipados de Logic** — `DONE` para escalares V0 · `P1` · `M` · Area: `Logic`
  - Evidence: [`LogicAst`](src/main/java/dev/harpia/parse/LogicAst.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java).

- [x] `LOGIC-010` **Variáveis e atribuição única** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`TypedStatement`](src/main/java/dev/harpia/logic/TypedStatement.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `LOGIC-011` **Return e análise de retorno em todos os caminhos** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`TypedStatement`](src/main/java/dev/harpia/logic/TypedStatement.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `LOGIC-012` **If / else** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`LogicBlockParser`](src/main/java/dev/harpia/parse/LogicBlockParser.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).

- [x] `LOGIC-013` **Chamadas nomeadas para Logic reutilizável** — `DONE`, com rejeição de recursão · `P1` · `L` · Area: `Logic`
  - Evidence: [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `LOGIC-014` **Expression AST tipada e Logic AST** — `DONE` para o recorte atual · `P0` · `L` · Area: `Logic`
  - Evidence: [`TypedExpression`](src/main/java/dev/harpia/logic/TypedExpression.java), [`LogicAst`](src/main/java/dev/harpia/parse/LogicAst.java).

- [x] `LOGIC-015` **Pureza e proibição de side effects em Logic** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`Effect`](src/main/java/dev/harpia/logic/Effect.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).

- [x] `LOGIC-016` **Operadores `+ - * / == != > >= < <= and or not`** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`BinaryOperator`](src/main/java/dev/harpia/logic/BinaryOperator.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).

- [x] `LOGIC-018` **Parênteses e precedência canônica** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`LogicExpressionParser`](src/main/java/dev/harpia/parse/LogicExpressionParser.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).

- [x] `LOGIC-019` **Inferência e verificação estática de tipos em Logic** — `DONE` para escalares V0 · `P1` · `L` · Area: `Logic`
  - Evidence: [`LogicType`](src/main/java/dev/harpia/logic/LogicType.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [x] `LOGIC-025` **Geração Java pura para Logic** — `DONE` para L1/L2 · `P1` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`GeneratedLogicCompilesTest`](src/test/java/dev/harpia/target/javaspring/GeneratedLogicCompilesTest.java).


## Flow, Commands and Queries

- [x] `FLOW-001` **Flow como AST estruturada, sem strings genéricas** — `DONE` para os oito comandos V0 · `P0` · `L` · Area: `API`
  - Evidence: [`FlowStep`](src/main/java/dev/harpia/model/FlowStep.java), [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java).

- [x] `FLOW-002` **`validate input`** — `DONE` end-to-end · `P0` · `S` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `FLOW-003` **`create Entity from input`** — `DONE` end-to-end · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `FLOW-004` **`find`** — `DONE` na V1 para busca por campo único; `x = find Entity by campo` gera finder derivado no repositório e `orElseThrow(NotFound)` no serviço. O campo precisa ser `unique` (`HRP2128`) e existir no input, senão a busca teria mais de uma resposta para uma variável só. Busca por campo não-único é `QUERY-003` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`JavaSpringRepositoryTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringRepositoryTransformer.java), [`FindByTest`](src/test/java/dev/harpia/validate/FindByTest.java).

- [x] `FLOW-005` **`load Entity by id`** — `DONE` end-to-end · `P0` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `FLOW-006` **`list Entity`** — `DONE` com ordem estável por id · `P0` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `FLOW-007` **`update variable from input`** — `DONE` end-to-end · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `FLOW-008` **`save`** — `DONE` end-to-end · `P0` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `FLOW-009` **`delete`** — `DONE` end-to-end · `P0` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `FLOW-010` **`return` entity/list/nothing** — `DONE` para V0 · `P0` · `M` · Area: `API`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java).

- [x] `FLOW-011` **`set`, `add` e `remove`** — `DONE` na V1 para campos escalares e coleções; `set x.campo = <expressão>` atribui, `add <elemento> to x.coleção` e `remove <elemento> from x.coleção` mudam a coleção no lugar. A expressão é tipada contra o tipo do campo — ou do **elemento**, no caso da coleção — e usar a forma errada para a espécie do campo é recusado. Campo `generated` não pode ser atribuído · `P0` · `L` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`SetFieldTest`](src/test/java/dev/harpia/validate/SetFieldTest.java), [`CollectionChangeTest`](src/test/java/dev/harpia/validate/CollectionChangeTest.java).

- [x] `FLOW-012` **`require`** — `DONE` na V1 para precondition inline sobre o input; `require <condição> otherwise <erro>` continua quando a condição booleana tipada é verdadeira e, quando falsa, levanta um erro de domínio declarado em `### Errors`. O status não é duplicado no Flow e V0 recusa a construção · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`RequireInstructionTest`](src/test/java/dev/harpia/validate/RequireInstructionTest.java).

- [x] `FLOW-013` **`fail` com erro tipado** — `DONE` na V1; `fail <erro> when <condição>` levanta um erro de domínio declarado em `### Errors`, com a condição tipada contra o input pelo mesmo analisador de Rules e Invariants. Um `fail` sem guarda não é comando de flow, e levantar erro não declarado é `HRP2127` · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`FailInstructionTest`](src/test/java/dev/harpia/validate/FailInstructionTest.java).

- [ ] `FLOW-014` **`call` Logic/Command/Integration** — `PARTIAL`; a V1 aceita chamadas compactas e multilinha para Logic e `Integration.Operation`, resolve argumentos nomeados pela assinatura global, valida nomes/completude/tipos, preserva o resultado nos IRs e gera a chamada Java. Integration possui provider HTTP em `INTEG-004`; faltam consumir variáveis escalares anteriores, Command e o adapter de Logic custom · `P0` · `L` · Area: `API`
  - Evidence: [`FlowBlockParser`](src/main/java/dev/harpia/parse/FlowBlockParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`FlowCallModel`](src/main/java/dev/harpia/model/FlowCallModel.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`FlowCallTest`](src/test/java/dev/harpia/validate/FlowCallTest.java).
  - Depends on: `CORE-009`, `CMD-001`, `INTEG-001`.

- [ ] `FLOW-015` **`emit` Event** — `TODO` · `P0` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`.

- [x] `FLOW-019` **If / else em Flow** — `DONE` na V1; `if <condição>`/`else` usa expressões booleanas tipadas sobre o input e blocos com quatro espaços, preserva os ramos nos AST/IRs/inspect e gera Java na posição declarada. São permitidos dois níveis; ramos usam variáveis já definidas, mas não definem variável nem retornam (`HRP2130`) · `P0` · `L` · Area: `API`
  - Evidence: [`FlowBlockParser`](src/main/java/dev/harpia/parse/FlowBlockParser.java), [`FlowStep`](src/main/java/dev/harpia/model/FlowStep.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`FlowConditionalTest`](src/test/java/dev/harpia/validate/FlowConditionalTest.java).

- [ ] `FLOW-020` **For each** — `TODO` · `P0` · `L` · Area: `API`
  - Depends on: `TYPE-021`.

- [x] `CMD-001` **Command como símbolo explícito independente de HTTP** — `DONE`; `## Command` é kind V1, dita a transação e pode omitir o binding HTTP sem desaparecer dos IRs ou da geração de service · `P0` · `L` · Area: `API`
  - Evidence: [`OperationNature`](src/main/java/dev/harpia/model/OperationNature.java), [`OperationDeclarationTest`](src/test/java/dev/harpia/parse/OperationDeclarationTest.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java).

- [x] `CMD-002` **Input tipado de operação V0** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java), [`JavaSpringDtoTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringDtoTransformer.java).

- [x] `CMD-003` **Output tipado de operação V0** — `DONE` para Entity/List/ nothing · `P0` · `M` · Area: `API`
  - Evidence: [`OutputModel`](src/main/java/dev/harpia/model/OutputModel.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).

- [x] `CMD-004` **Errors tipados gerais** — `DONE` para a declaração do tipo; além das três condições detectadas, um erro de domínio nomeado (`insufficient balance -> 422`) vira símbolo canônico, tipo gerado e mapeamento de status, com um status por erro no projeto inteiro. O gatilho é `FLOW-013` (`fail`), pronto · `P0` · `L` · Area: `API`
  - Evidence: [`ErrorLineParser`](src/main/java/dev/harpia/parse/ErrorLineParser.java), [`Naming`](src/main/java/dev/harpia/model/Naming.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`DomainErrorTest`](src/test/java/dev/harpia/validate/DomainErrorTest.java).

- [x] `CMD-006` **Flow de mutação CRUD V0** — `DONE` · `P0` · `L` · Area: `API`
  - Evidence: [`FlowStep`](src/main/java/dev/harpia/model/FlowStep.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [ ] `CMD-007` **Access de Command** — `PARTIAL`; somente `public` · `P0` · `L` · Area: `Security`
  - Evidence: [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java), [`UnsupportedFeatureDetector`](src/main/java/dev/harpia/parse/UnsupportedFeatureDetector.java).

- [ ] `CMD-008` **Política de transação de Command** — `PARTIAL`; inferência por espécie CRUD, sem configuração declarativa · `P0` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java).

- [x] `CMD-011` **Exposure opcional por bindings** — `DONE`; Command/Query V1 podem permanecer internos ou ser expostos por binding HTTP externo; capability/controller/teste web só existem quando há binding · `P0` · `L` · Area: `API`
  - Depends on: `BIND-001`.
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).

- [x] `QUERY-001` **Query como símbolo explícito independente de HTTP** — `DONE`; `## Query` é kind V1, rejeita mutação (`HRP2120`) e pode existir sem capability ou adapter HTTP · `P0` · `L` · Area: `API`
  - Evidence: [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`OperationNatureTest`](src/test/java/dev/harpia/validate/OperationNatureTest.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java).

- [x] `QUERY-002` **Input/output/Flow de Query CRUD V0** — `DONE` para load-by-id e list-all · `P0` · `L` · Area: `API`
  - Evidence: [`Resolver`](src/main/java/dev/harpia/model/Resolver.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `QUERY-003` **Filters** — `DONE` na V1 para igualdade; `x = list Entity by campo [and campo]` gera finder derivado multi-campo no repositório e mantém a ordem estável por id. É a contrapartida de `find`: uma lista comporta muitos, então o campo não precisa ser único. Operadores além de igualdade e filtro opcional continuam abertos · `P0` · `L` · Area: `Persistence`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`JavaSpringRepositoryTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringRepositoryTransformer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`ListFilterTest`](src/test/java/dev/harpia/validate/ListFilterTest.java).

- [x] `QUERY-004` **Sorting declarativo** — `DONE` na V1; `sorted by campo [asc|desc] [and ...]` vale para `list` com e sem filtro, e o id permanece como desempate final para que a ordem declarada **refine** a ordem estável em vez de substituí-la. Direção omitida é ascendente · `P0` · `M` · Area: `Persistence`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`SortingTest`](src/test/java/dev/harpia/validate/SortingTest.java).

- [x] `QUERY-005` **Pagination offset/page** — `DONE` na V1; `paged` sobre `list` com ou sem filtro vira `PageRequest.of(page, size, <ordem>)`, e o repositório recebe `Pageable` em vez de `Sort`. `page` e `size` são inputs declarados (`HRP2129`) — a única exceção à regra de que todo input nomeia um campo da entidade, porque descrevem a requisição e não a entidade. O envelope com total/páginas é `TYPE-025`/`API-010` · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`PaginationTest`](src/test/java/dev/harpia/validate/PaginationTest.java).

- [ ] `QUERY-009` **Access de Query** — `PARTIAL`; somente `public` · `P0` · `L` · Area: `Security`
  - Evidence: [`AccessRule`](src/main/java/dev/harpia/model/AccessRule.java), [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java).


## Inbound HTTP and Bindings

- [x] `API-001` **Endpoint HTTP V0** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`EndpointParser`](src/main/java/dev/harpia/parse/EndpointParser.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).

- [x] `API-002` **GET, POST, PUT e DELETE** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`HttpBinding`](src/main/java/dev/harpia/model/HttpBinding.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `API-003` **PATCH** — `DONE` na V1; `PATCH` entra na gramática de `### Endpoint` e significa atualização parcial: `update ... from input` copia só os campos que chegaram, um input `required` é `HRP2136` e um flow que não atualiza nada é `HRP2137`. A V0 mantém os quatro verbos (`HRP1010`) · `P0` · `M` · Area: `API`
  - Evidence: [`PartialUpdateTest`](src/test/java/dev/harpia/binding/PartialUpdateTest.java), [`EndpointParser`](src/main/java/dev/harpia/parse/EndpointParser.java), [`BindingValidator`](src/main/java/dev/harpia/binding/BindingValidator.java), [`ApplicationOperation.partialUpdate`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java).

- [x] `API-004` **Path params gerais** — `DONE` na V1; um path aceita `{nome}` em qualquer segmento e em qualquer quantidade. Num endpoint inline o nome **é** o mapeamento: `{tenant}` é preenchido pelo input `tenant`, e um parâmetro sem input correspondente é `HRP2131`. `{id}` mantém o significado de sempre — o registro que o flow carrega · `P0` · `M` · Area: `API`
  - Um valor ligado fora do corpo carrega o que a URL sabe escrever: escalar ou enum declarado (com o tipo declarado no parâmetro); coleção e Value são `HRP2138` em vez de estourarem dentro do target.
  - Evidence: [`EndpointParser`](src/main/java/dev/harpia/parse/EndpointParser.java), [`BindingResolver`](src/main/java/dev/harpia/binding/BindingResolver.java), [`BindingValidator`](src/main/java/dev/harpia/binding/BindingValidator.java), [`PathParameterTest`](src/test/java/dev/harpia/binding/PathParameterTest.java), [`UrlBoundInputTest`](src/test/java/dev/harpia/binding/UrlBoundInputTest.java).

- [x] `API-005` **Query params e headers** — `DONE`; num endpoint inline, `GET` e `DELETE` não têm corpo, então os inputs que o path não consumiu viram query parameters com o próprio nome — `GET /customers?page=…&size=…` em vez de um corpo em GET; os demais verbos seguem com corpo. Headers vêm do mapeamento explícito de um binding externo (`- x: header X-Nome` → `@RequestHeader`), com as constraints declaradas e o valor enviado em texto puro no teste de controller gerado; não há o que inferir num endpoint inline, onde nada nomeia um header · `P0` · `L` · Area: `API`
  - Evidence: [`QueryParameterTest`](src/test/java/dev/harpia/binding/QueryParameterTest.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java), [`BindingResolver`](src/main/java/dev/harpia/binding/BindingResolver.java).
  - Depends on: `BIND-002`.

- [x] `API-006` **Request body DTO e response body DTO** — `DONE` para CRUD V0 · `P0` · `L` · Area: `API`
  - Evidence: [`JavaSpringDtoTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringDtoTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `API-007` **Status codes 2xx e errors V0** — `DONE` para o conjunto fechado atual · `P0` · `M` · Area: `API`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java).

- [ ] `API-008` **Authentication e authorization HTTP** — `TODO` · `P0` · `XL` · Area: `Security`
  - Depends on: `SEC-002`, `SEC-003`.

- [x] `API-009` **Validação HTTP** — `DONE` para o recorte atual; `validate input` vale na fronteira HTTP inteira, não só no corpo: parâmetros de path/query/header carregam as constraints declaradas, `ConstraintViolationException` responde o status declarado, e o teste de controller gerado envia valores de requisição em texto puro e um valor realmente inválido · `P0` · `M` · Area: `API`
  - Evidence: [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).

- [x] `API-010` **Pagination HTTP** — `DONE` na V1; um endpoint cujo output é `Page<Entidade>` responde o envelope `PageResponse<T>` no corpo, com o status declarado · `P0` · `M` · Area: `API`
  - Depends on: `QUERY-005` (pronto).
  - Evidence: [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`PageOutputTest`](src/test/java/dev/harpia/validate/PageOutputTest.java).

- [x] `API-015` **Error envelope V0** — `DONE` para status/error/message · `P0` · `M` · Area: `API`
  - Evidence: [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `BIND-001` **Diretórios `spec/`, `bindings/` e separação formal Spec/Binding/Config** — `DONE`; defaults distintos, descoberta segura e binding opcional entram pelo mesmo compiler sem misturar config com semântica · `P0` · `L` · Area: `Integration`
  - Depends on: `CORE-008`, `CORE-018`.
  - Evidence: [`BindingDiscovery`](src/main/java/dev/harpia/source/BindingDiscovery.java), [`PathsConfig`](src/main/java/dev/harpia/config/HarpiaConfig.java), [`BindingDiscoveryTest`](src/test/java/dev/harpia/source/BindingDiscoveryTest.java).

- [x] `BIND-002` **Binding AST e Binding Model** — `DONE` para a fundação tipada e extensível por kind; `BindingAst.Declaration` é selada, `BindingModel` preserva origem e source refs e os IRs carregam exposição opcional · `P0` · `XL` · Area: `Integration`
  - Evidence: [`BindingAst`](src/main/java/dev/harpia/binding/BindingAst.java), [`BindingModel`](src/main/java/dev/harpia/binding/BindingModel.java), [`UseCaseModel`](src/main/java/dev/harpia/model/UseCaseModel.java).

- [x] `BIND-003` **Binding Parser** — `DONE` para o primeiro kind HTTP V1, com H1/H2/H3 formais e diagnostics de versão/estrutura · `P0` · `L` · Area: `Integration`
  - Evidence: [`BindingParser`](src/main/java/dev/harpia/binding/BindingParser.java), [`BindingParserTest`](src/test/java/dev/harpia/binding/BindingParserTest.java).

- [x] `BIND-004` **Binding Validator/Resolver tipado** — `DONE`; resolve símbolos de operação e valida binding único, rota única e coerência de `{id}` entre path e Flow · `P0` · `XL` · Area: `Integration`
  - Evidence: [`BindingResolver`](src/main/java/dev/harpia/binding/BindingResolver.java), [`BindingValidator`](src/main/java/dev/harpia/binding/BindingValidator.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).

- [x] `BIND-005` **Source mapping de bindings** — `DONE`; declaration/endpoint preservam ranges e cada método de controller aponta da linha Java gerada para a origem exata do endpoint Markdown · `P1` · `M` · Area: `Integration`
  - Evidence: [`BindingModel`](src/main/java/dev/harpia/binding/BindingModel.java), [`GeneratedSourceMapping`](src/main/java/dev/harpia/emit/GeneratedSourceMapping.java), [`JavaSourceRenderer`](src/main/java/dev/harpia/target/javaspring/renderer/JavaSourceRenderer.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).

- [x] `BIND-006` **HTTP binding: base URL, method/path, path/query/header/body/response mapping** — `DONE` no contrato básico do Core V1; base URL, method/path, mappings explícitos de request e `output: body`/`none` atravessam parser, resolver, IR e controller Java compilável. Media types permanecem em `BIND-007` no Next · `P0` · `XL` · Area: `Integration`
  - Evidence: [`BindingParser`](src/main/java/dev/harpia/binding/BindingParser.java), [`BindingValidator`](src/main/java/dev/harpia/binding/BindingValidator.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).


## Persistence

- [x] `PERSIST-001` **Capability lógica de persistence** — `DONE` · `P0` · `M` · Area: `Persistence`
  - Evidence: [`CapabilityAnalyzer`](src/main/java/dev/harpia/capability/CapabilityAnalyzer.java), [`CapabilityResolverTest`](src/test/java/dev/harpia/capability/CapabilityResolverTest.java).

- [x] `PERSIST-002` **Save/load/list/delete V0** — `DONE` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `PERSIST-003` **Repository generation** — `DONE` · `P0` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringRepositoryTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringRepositoryTransformer.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [x] `PERSIST-004` **SQL/PostgreSQL provider V0** — `DONE` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`PostgresTypes`](src/main/java/dev/harpia/target/javaspring/PostgresTypes.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java).

- [x] `PERSIST-005` **JPA provider V0** — `DONE` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [ ] `PERSIST-008` **Transactions declarativas** — `PARTIAL`; transação Spring é inferida para mutações CRUD · `P0` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `PERSIST-009` **Relationships persistentes** — `DONE` no target Java/Spring; relação singular gera `@ManyToOne`/`@JoinColumn` e FK, coleção independente gera `@ManyToMany`/join table com PK composta e duas FKs. Loading é lazy e nenhum cascade é inferido · `P0` · `XL` · Area: `Persistence`
  - Evidence: [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`RelationshipFieldTest`](src/test/java/dev/harpia/validate/RelationshipFieldTest.java).

- [x] `PERSIST-010` **Constraints e índices gerais** — `DONE`; `indexed` declara um índice não único (`ix_<tabela>_<coluna>`) que atravessa AST, Business IR e Application IR até o `CREATE INDEX` da migration, e `unique indexed` é recusado porque a constraint única já indexa a coluna · `P0` · `L` · Area: `Persistence`
  - Evidence: [`IndexedFieldTest`](src/test/java/dev/harpia/target/javaspring/IndexedFieldTest.java), [`SqlMigrationModel.Index`](src/main/java/dev/harpia/target/javaspring/model/SqlMigrationModel.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java).

- [x] `PERSIST-015` **Schema inicial determinístico** — `DONE` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`SqlMigrationModel`](src/main/java/dev/harpia/target/javaspring/model/SqlMigrationModel.java), [`JavaSpringMigrationTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformerTest.java).

- [x] `PERSIST-016` **Flyway V1 init migration** — `DONE` · `P0` · `M` · Area: `Persistence`
  - Evidence: [`MigrationEmitter`](src/main/java/dev/harpia/target/javaspring/MigrationEmitter.java), [`migration.sql.mustache`](src/main/resources/targets/java-spring/templates/migration.sql.mustache).


## Integration, Events and Security

- [x] `INTEG-001` **Integration e Operation como portas tipadas** — `DONE`; `## Integration <Nome>` declara uma outbound port com uma ou mais `### Operation <Nome>`, entra pelo registry, possui namespace determinístico próprio e atravessa AST, SymbolTable, Business IR e Application IR sem escolher transporte · `P0` · `XL` · Area: `Integration`
  - Evidence: [`IntegrationDeclarationParser`](src/main/java/dev/harpia/parse/IntegrationDeclarationParser.java), [`IntegrationAst`](src/main/java/dev/harpia/parse/IntegrationAst.java), [`SymbolTable`](src/main/java/dev/harpia/symbol/SymbolTable.java), [`IntegrationModel`](src/main/java/dev/harpia/model/IntegrationModel.java), [`ApplicationIntegration`](src/main/java/dev/harpia/application/ApplicationIntegration.java), [`IntegrationDeclarationTest`](src/test/java/dev/harpia/validate/IntegrationDeclarationTest.java).

- [x] `INTEG-002` **Typed Input/Output/Errors de integração** — `DONE`; cada Operation exige `#### Output`, aceita `#### Input` tipado e variantes PascalCase em `#### Errors`. Escalares, Enum, Value, `List<T>` e `Optional<T>` atravessam AST, SymbolTable, Business IR e Application IR; Entity/`Reference<Entity>` são recusados para não vazar persistência pela porta. Binding, transporte e política de falha continuam separados · `P0` · `L` · Area: `Integration`
  - Evidence: [`IntegrationOperationParser`](src/main/java/dev/harpia/parse/IntegrationOperationParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`IntegrationModel`](src/main/java/dev/harpia/model/IntegrationModel.java), [`ApplicationIntegration`](src/main/java/dev/harpia/application/ApplicationIntegration.java), [`IntegrationContractTest`](src/test/java/dev/harpia/validate/IntegrationContractTest.java).

- [x] `INTEG-003` **Flow call integration** — `DONE`; `call Integration.Operation(...)` resolve a porta e a operação em namespaces próprios, ordena argumentos nomeados pela assinatura, valida escalares/Enum/Value/`List`/`Optional`, exige atribuição exatamente quando há resultado e preserva a invocação nos AST/Business IR/Application IR. O Core permanece independente de transporte e o target resolve a porta pelo provider de `INTEG-004` · `P0` · `M` · Area: `Integration`
  - Evidence: [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`IntegrationCallModel`](src/main/java/dev/harpia/model/IntegrationCallModel.java), [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`IntegrationFlowCallTest`](src/test/java/dev/harpia/validate/IntegrationFlowCallTest.java).
  - Depends on: `INTEG-001`, `FLOW-014`.

- [x] `INTEG-004` **HTTP/REST client provider** — `DONE` no contrato Core V1; `## Bind Integration.Operation` associa base URL absoluta, verbo/path, path/query/header/body e resposta à porta tipada. O `java-spring` gera um `RestClient` por Integration, tipos escalares/Enum/Value/`List`/`Optional`, injeta somente os clients usados nos services e recusa chamadas sem binding com `HRP7008`. Autenticação e response validation permanecem em `INTEG-010` · `P0` · `L` · Area: `Integration`
  - Evidence: [`IntegrationBindingResolver`](src/main/java/dev/harpia/binding/IntegrationBindingResolver.java), [`IntegrationBindingValidator`](src/main/java/dev/harpia/binding/IntegrationBindingValidator.java), [`JavaSpringIntegrationClientTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringIntegrationClientTransformer.java), [`IntegrationHttpClientTest`](src/test/java/dev/harpia/target/javaspring/IntegrationHttpClientTest.java).
  - Depends on: `INTEG-001`, `BIND-006`.

- [ ] `INTEG-010` **Autenticação básica e response validation de integração** — `TODO`; o Core cobre API key/bearer e validação básica de resposta. OAuth e políticas avançadas ficam em `BIND-008` no Next · `P0` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `INTEG-002`.

- [x] `EVENT-001` **Event e payload tipado** — `DONE`; `## Event <Nome>` com `### Payload` declara um fato consumado, entra no namespace próprio `events` e atravessa AST, SymbolTable, Business IR e Application IR sem escolher transporte — nenhum estágio nomeia tópico, broker ou listener. O payload aceita escalares, Enum, Value, `List<T>`, `Optional<T>` e `Reference<Entity>`; uma Entity é `HRP2141`, porque o evento diz a **qual** registro algo aconteceu e não carrega a linha cuja vida o leitor não compartilha. V0 recusa com `HRP1107` · `P0` · `L` · Area: `Messaging`
  - Evidence: [`EventDeclarationTest`](src/test/java/dev/harpia/validate/EventDeclarationTest.java), [`EventAst`](src/main/java/dev/harpia/parse/EventAst.java), [`EventDeclarationParser`](src/main/java/dev/harpia/parse/EventDeclarationParser.java), [`EventModel`](src/main/java/dev/harpia/model/EventModel.java), [`ApplicationEvent`](src/main/java/dev/harpia/application/ApplicationEvent.java).

- [ ] `EVENT-003` **Emit Event** — `TODO` · `P0` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `FLOW-015`.

- [ ] `EVENT-005` **Local events provider** — `TODO` · `P0` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `EVENT-004`.

- [x] `EVENT-007` **Contratos de Event gerados** — `DONE`; cada Event declarado vira um record imutável no pacote `event` do projeto gerado, com o payload mapeado pelos mesmos tipos dos DTOs — um `Reference<T>` chega como a identidade, não como a linha. Nada é publicado nem assinado ali: o contrato existe independentemente de algum provider vir a carregá-lo · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`EventContractTest`](src/test/java/dev/harpia/target/javaspring/EventContractTest.java), [`JavaSpringEventTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEventTransformer.java).
  - Depends on: `EVENT-001`.

- [x] `SEC-001` **Access `public`** — `DONE` · `P0` · `XS` · Area: `Security`
  - Evidence: [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java), [`AccessRule`](src/main/java/dev/harpia/model/AccessRule.java).

- [x] `SEC-002` **Access `authenticated`** — `DONE`; `### Access` aceita `authenticated` na V1 e exige a capability `security`, que o target implementa por conta própria. No Java/Spring vira uma `SecurityFilterChain`: cada rota declarada com sua regra, `denyAll` no fim (o padrão só pega caminho que ninguém declarou), CSRF desligado e sessão stateless — uma API que lê a identidade da requisição não tem sessão para uma requisição forjada montar. Um projeto sem endpoint autenticado não ganha dependência nem configuração. O teste de controller gerado importa a chain, porque um slice test seria julgado pela regra padrão do Boot, e para um endpoint autenticado prova a recusa (401) em vez de afirmar um status que uma requisição anônima não alcança. V0 recusa com `HRP4001` · `P0` · `L` · Area: `Security`
  - Evidence: [`AuthenticatedAccessTest`](src/test/java/dev/harpia/target/javaspring/AuthenticatedAccessTest.java), [`JavaSpringSecurityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringSecurityTransformer.java), [`CapabilityAnalyzer`](src/main/java/dev/harpia/capability/CapabilityAnalyzer.java).
  - Nota: o projeto gerado precisa de Spring Boot >= 3.4 para `spring-boot-starter-security` resolver no cache offline desta máquina.

- [x] `SEC-003` **Role e scope** — `DONE`. `### Access` aceita `role admin` e `role admin or auditor`, com verificação "qualquer um deles" — listar mais de um é como se diz que algo está aberto a mais de um tipo de pessoa. O nome é `lower_snake_case` como tudo o mais na especificação e vira `hasAnyRole("ADMIN", ...)`, a grafia do framework para a mesma coisa. Um role exige identidade, então também exige a capability `security`. Cada operação vira sua própria regra, chaveada por método e path. `scope orders:read or orders:write` pede uma permissão em vez de um tipo de pessoa: não é convertido para maiúsculas como um role, porque a grafia é de quem emite o token, e vira `hasAnyAuthority("SCOPE_...")`. Declarar scope com `security.provider: basic` é `HRP6002` — a regra não casaria com nada e o endpoint se leria aberto e se comportaria fechado · `P0` · `L` · Area: `Security`
  - Evidence: [`RoleAccessTest`](src/test/java/dev/harpia/target/javaspring/RoleAccessTest.java), [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java), [`AccessRule`](src/main/java/dev/harpia/model/AccessRule.java), [`JavaSpringSecurityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringSecurityTransformer.java).
  - Depends on: `SEC-002`, `RULE-003`.

- [x] `SEC-005` **JWT** — `DONE`; `security.provider` em `harpia.yaml` escolhe como uma identidade é provada, do mesmo jeito que `database.vendor` escolhe o banco: `basic` (padrão, o que o framework dá de graça) ou `jwt`, que torna o projeto gerado um resource server OAuth2. A cadeia é idêntica nos dois — quais endpoints exigem identidade não muda com como ela é provada. O conjunto de chaves é `${JWT_JWK_SET_URI}`, resposta do deployment, e um `src/test/resources/application.yaml` gerado dá aos testes um valor próprio: o contexto lê essa propriedade ao subir e um teste não tem deployment para preencher o placeholder · `P0` · `L` · Area: `Security`
  - Evidence: [`SecurityProviderTest`](src/test/java/dev/harpia/target/javaspring/SecurityProviderTest.java), [`CapabilityResolver`](src/main/java/dev/harpia/capability/CapabilityResolver.java), [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java).
  - Nota: `spring-boot-starter-oauth2-resource-server` exige Spring Boot >= 3.4 no cache offline desta máquina.
  - Depends on: `SEC-002`, `BIND-008`.

- [x] `SEC-007` **Spring Security provider** — `DONE`; o provider é a soma de `SEC-002`, `SEC-003` e `SEC-005` — dependência, filter chain a partir do access declarado, `basic`/`jwt` e testes gerados — mais a peça que faltava: uma recusa responde com o **mesmo** `ApiError` que qualquer outra falha. Ela acontece na filter chain, antes de qualquer `@ExceptionHandler`, então sem isso a API teria dois contratos de erro e quem chama trataria os dois. O corpo compartilhado passa a ser gerado mesmo num projeto que não declara falha alguma, porque a recusa é uma · `P0` · `L` · Area: `Security`
  - Evidence: [`AuthenticatedAccessTest`](src/test/java/dev/harpia/target/javaspring/AuthenticatedAccessTest.java), [`JavaSpringSecurityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringSecurityTransformer.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java).
  - Depends on: `SEC-002`, `SEC-003`.

- [ ] `RELY-001` **Timeout e failure mapping** — `PARTIAL`; timeout e fronteira de falha prontos. Todo client carrega um prazo, aplicado por um `RestClientCustomizer` gerado a partir de `harpia.integration.connect-timeout` e `.read-timeout` (padrão `2s`/`10s`) — customizer e não construtor, porque fixar o request factory dentro do client substituiria silenciosamente o que o `MockRestServiceServer` instala, e um client que ninguém consegue testar do jeito normal é um negócio pior. Uma chamada que falha levanta a exceção da própria porta (`<Integration>Exception`), com a operação e, quando houve resposta, o status: sem isso o chamador pegaria `RestClientResponseException`, vocabulário do Spring chegando por uma declaração que nunca mencionou HTTP. Falta ligar as **variantes nomeadas** de `#### Errors` a essa falha, o que precisa de uma regra de reconhecimento no binding · `P0` · `M` · Area: `Runtime`
  - Evidence: [`IntegrationHttpClientTest`](src/test/java/dev/harpia/target/javaspring/IntegrationHttpClientTest.java), [`JavaSpringIntegrationClientTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringIntegrationClientTransformer.java).
  - Depends on: `INTEG-001`, `CMD-004`.


## Configuration and Testing

- [x] `CONFIG-001` **Externalized YAML configuration** — `DONE`; `target.properties` é o modelo geral: o projeto declara propriedades de deploy que chegam ao `application.yaml` gerado, numa ordem só, qualquer que tenha sido a ordem digitada. Vivem dentro do bloco do target porque um nome de propriedade pertence a um framework e não a uma especificação — nada acima da fronteira do target as lê. Uma chave que o projeto gerado já define é recusada em vez de sobrescrita, e uma chave que não tem forma de caminho de propriedade também: o arquivo pareceria configurado e seria inerte · `P0` · `L` · Area: `Operations`
  - Evidence: [`DeclaredPropertyTest`](src/test/java/dev/harpia/target/javaspring/DeclaredPropertyTest.java), [`AppConfigEmitter`](src/main/java/dev/harpia/target/javaspring/AppConfigEmitter.java), [`ConfigValidator`](src/main/java/dev/harpia/config/ConfigValidator.java).
  - Evidence: [`AppConfigEmitter`](src/main/java/dev/harpia/target/javaspring/AppConfigEmitter.java), [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache).

- [x] `CONFIG-002` **Environment variables básicas** — `DONE` no recorte Core V1; datasource usa placeholders externos determinísticos para URL, username e password. `Secret` e redaction geral permanecem em `TYPE-019`/`SEC-009` no Next · `P0` · `M` · Area: `Security`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).

- [x] `CONFIG-005` **Validação estrutural de `harpia.yaml`** — `DONE` · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ConfigValidator`](src/main/java/dev/harpia/config/ConfigValidator.java), [`ConfigLoaderTest`](src/test/java/dev/harpia/config/ConfigLoaderTest.java).

- [x] `CONFIG-006` **Provider/config validation do target suportado** — `DONE` no recorte Core V1; PostgreSQL, paths, package Java e opções obrigatórias do `java-spring` são validados. Validação genérica para providers futuros acompanha o respectivo provider · `P0` · `L` · Area: `Operations`
  - Evidence: [`ProviderValidation`](src/main/java/dev/harpia/application/ProviderValidation.java), [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`ConfigLoaderTest`](src/test/java/dev/harpia/config/ConfigLoaderTest.java).

- [x] `TEST-002` **Testes unitários de service gerados para CRUD V0** — `DONE` · `P0` · `L` · Area: `Testing`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `TEST-003` **Testes REST/controller gerados para status e erros V0** — `DONE` · `P0` · `L` · Area: `Testing`
  - Evidence: [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).

- [x] `TEST-006` **Testes de Logic e equivalência Java** — `DONE` para L1/L2 · `P1` · `L` · Area: `Testing`
  - Evidence: [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java), [`GeneratedLogicCompilesTest`](src/test/java/dev/harpia/target/javaspring/GeneratedLogicCompilesTest.java).

- [x] `TEST-008` **Mocks derivados da especificação V0** — `DONE` para service/controller CRUD · `P0` · `M` · Area: `Testing`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java).

- [x] `TEST-012` **Golden tests versionados** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`customer golden`](src/test/resources/fixtures/targets/java-spring/customer/pom.xml).

- [x] `TEST-013` **Determinism tests** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).

- [x] `TEST-014` **Build/test Maven offline do projeto gerado** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [x] `DET-003` **Clean + rebuild equality test em filesystem** — `DONE`; um diretório reconstruído com `--clean` sobre um build antigo é byte a byte idêntico a um build feito do zero, manifest incluído · `P0` · `S` · Area: `Testing`
  - Evidence: [`RebuildEqualityTest`](src/test/java/dev/harpia/RebuildEqualityTest.java), [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).


## Target Architecture and Java/Spring

- [x] `TARGET-001` **Target API / `HarpiaTarget`** — `DONE` · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`HarpiaTarget`](src/main/java/dev/harpia/target/HarpiaTarget.java), [`ArchitectureBoundaryTest`](src/test/java/dev/harpia/ArchitectureBoundaryTest.java).

- [x] `TARGET-002` **TargetId, TargetDescriptor e TargetStatus** — `DONE` · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetId`](src/main/java/dev/harpia/target/TargetId.java), [`TargetDescriptor`](src/main/java/dev/harpia/target/TargetDescriptor.java).

- [x] `TARGET-003` **TargetRegistry extensível** — `DONE`; um target registrado descreve a si mesmo e resolve sem constar do catálogo estático, e o hint de diagnóstico lista o que o registry realmente contém · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetRegistry`](src/main/java/dev/harpia/target/TargetRegistry.java), [`TargetResolver`](src/main/java/dev/harpia/target/TargetResolver.java), [`RegisteredTargetTest`](src/test/java/dev/harpia/target/RegisteredTargetTest.java).

- [x] `TARGET-004` **TargetResolver sem fallback silencioso** — `DONE` para catálogo built-in · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetResolver`](src/main/java/dev/harpia/target/TargetResolver.java), [`UnsupportedTargetTest`](src/test/java/dev/harpia/target/UnsupportedTargetTest.java).

- [x] `TARGET-005` **Target capabilities reais** — `DONE` para HTTP/persistence do target atual · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetCatalog`](src/main/java/dev/harpia/target/TargetCatalog.java), [`TargetCapabilityTest`](src/test/java/dev/harpia/target/TargetCapabilityTest.java).

- [x] `TARGET-006` **Target transformer boundary** — `DONE` · `P0` · `L` · Area: `Multi-target`
  - Evidence: [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java), [`ArchitectureBoundaryTest`](src/test/java/dev/harpia/ArchitectureBoundaryTest.java).

- [x] `TARGET-007` **Target Model estruturado** — `DONE` para Java/Spring atual · `P0` · `L` · Area: `Multi-target`
  - Evidence: [`JavaProjectModel`](src/main/java/dev/harpia/target/javaspring/model/JavaProjectModel.java), [`JavaTypeModel`](src/main/java/dev/harpia/target/javaspring/model/JavaTypeModel.java).

- [x] `TARGET-008` **Target Renderer** — `DONE` para Java · `P0` · `L` · Area: `Multi-target`
  - Evidence: [`JavaSourceRenderer`](src/main/java/dev/harpia/target/javaspring/renderer/JavaSourceRenderer.java), [`JavaSourceRendererTest`](src/test/java/dev/harpia/target/javaspring/renderer/JavaSourceRendererTest.java).

- [x] `TARGET-009` **Templates target-owned e sem semântica de negócio** — `DONE` no target atual · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`JavaSpringTemplates`](src/main/java/dev/harpia/target/javaspring/JavaSpringTemplates.java), [`JavaSpringTemplateRulesTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTemplateRulesTest.java).

- [x] `TARGET-010` **GeneratedFile abstraction com tipo e origem** — `DONE` · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`GeneratedFile`](src/main/java/dev/harpia/emit/GeneratedFile.java), [`GeneratedTreeTest`](src/test/java/dev/harpia/emit/GeneratedTreeTest.java).

- [x] `TARGET-011` **Mappings específicos por target** — `DONE` no target atual · `P0` · `L` · Area: `Multi-target`
  - Evidence: [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java), [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java).

- [x] `TARGET-012` **Dependencies/config/layout específicos por target** — `DONE` no target atual · `P0` · `L` · Area: `Multi-target`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`JavaLayout`](src/main/java/dev/harpia/target/javaspring/JavaLayout.java).

- [x] `TARGET-014` **Descoberta de targets e stable DTOs** — `DONE` via CLI/core · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetInfo`](src/main/java/dev/harpia/target/TargetInfo.java), [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java).

- [x] `TARGET-018` **Capability catalog V0** — `DONE` para HTTP/persistence/events/custom como IDs lógicos · `P1` · `M` · Area: `Multi-target`
  - Evidence: [`Capability`](src/main/java/dev/harpia/capability/Capability.java), [`CapabilityRequirementSet`](src/main/java/dev/harpia/capability/CapabilityRequirementSet.java).

- [x] `TGT-JAVA` **`java-spring`** — `DONE` como único target executável, no recorte V0 · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`JavaSpringTargetTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTargetTest.java).

- [x] `JAVA-001` **JavaSpringTarget e project transformer** — `DONE` · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java).

- [x] `JAVA-002` **Entity transformer** — `DONE` para Entity V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`JavaSpringEntityTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformerTest.java).

- [x] `JAVA-003` **ValueObject e Enum transformers** — `DONE`; Enum gera enum Java/JPA e ValueObject gera classe embeddable com storage coerente · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringEnumTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEnumTransformer.java), [`JavaSpringValueTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringValueTransformer.java), [`DeclaredEnumTest`](src/test/java/dev/harpia/validate/DeclaredEnumTest.java), [`DeclaredValueTest`](src/test/java/dev/harpia/validate/DeclaredValueTest.java).

- [ ] `JAVA-004` **Command/Query transformers explícitos** — `PARTIAL`; a natureza declarada (`Command`/`Query`/inferida) atravessa a Application IR e chega ao target, que a documenta no serviço gerado; falta especializar a geração além da forma CRUD · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java), [`OperationNatureTest`](src/test/java/dev/harpia/validate/OperationNatureTest.java).

- [x] `JAVA-005` **Logic transformer** — `DONE` para L1/L2 · `P1` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`LogicEmitterTest`](src/test/java/dev/harpia/target/javaspring/LogicEmitterTest.java).

- [x] `JAVA-006` **Flow CRUD transformer** — `DONE` para oito comandos V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [x] `JAVA-007` **Persistence e validation transformers/mappers** — `DONE` para V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java).

- [x] `JAVA-009` **JavaTypeMapper e dependency resolver** — `DONE` no recorte atual · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java), [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java).

- [x] `JAVA-010` **Import e package resolver determinísticos** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaImportResolver`](src/main/java/dev/harpia/target/javaspring/renderer/JavaImportResolver.java), [`JavaLayout`](src/main/java/dev/harpia/target/javaspring/JavaLayout.java).

- [x] `JAVA-011` **JavaProjectModel e JavaSourceFile** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaProjectModel`](src/main/java/dev/harpia/target/javaspring/model/JavaProjectModel.java), [`JavaSourceFile`](src/main/java/dev/harpia/target/javaspring/model/JavaSourceFile.java).

- [x] `JAVA-012` **Class/Record/Interface model sem AST Java completo** — `DONE` via `JavaTypeModel.Kind` · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaTypeModel`](src/main/java/dev/harpia/target/javaspring/model/JavaTypeModel.java), [`JavaSourceRendererTest`](src/test/java/dev/harpia/target/javaspring/renderer/JavaSourceRendererTest.java).

- [x] `JAVA-013` **Field/Method/Parameter/Constructor/Annotation/Import models** — `DONE` · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`model package`](src/main/java/dev/harpia/target/javaspring/model/JavaMethodModel.java), [`JavaSourceRenderer`](src/main/java/dev/harpia/target/javaspring/renderer/JavaSourceRenderer.java).

- [x] `JAVA-014` **JavaSourceRenderer com ordem e formatação estáveis** — `DONE` · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSourceRenderer`](src/main/java/dev/harpia/target/javaspring/renderer/JavaSourceRenderer.java), [`JavaSourceRendererTest`](src/test/java/dev/harpia/target/javaspring/renderer/JavaSourceRendererTest.java).

- [x] `TPL-001` **Target template loader e set default versionado** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTemplates`](src/main/java/dev/harpia/target/javaspring/JavaSpringTemplates.java), [`TargetDescriptor`](src/main/java/dev/harpia/target/TargetDescriptor.java).

- [x] `TPL-002` **pom.xml template** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).

- [x] `TPL-003` **application.yaml template** — `DONE` · `P0` · `S` · Area: `Java/Spring Target`
  - Evidence: [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache), [`AppConfigEmitter`](src/main/java/dev/harpia/target/javaspring/AppConfigEmitter.java).

- [x] `TPL-005` **Flyway migration template** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`migration.sql.mustache`](src/main/resources/targets/java-spring/templates/migration.sql.mustache), [`JavaSpringMigrationTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformerTest.java).

- [x] `TPL-007` **Bootstrap, repository, controller, error handler e testes Java** — `DONE` via Target Model/renderer, deliberadamente não via templates semânticos · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java), [`JavaSpringTemplateRulesTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTemplateRulesTest.java).

- [x] `TPL-008` **Regra: template não interpreta required/unique/Rule/Flow/dependencies/transações** — `DONE` como gate arquitetural · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTemplateRulesTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTemplateRulesTest.java), [`JavaSpringEntityTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformerTest.java).

- [x] `SPRING-001` **Java 21+ target policy** — `DONE`; mínimo 21 validado · `P0` · `S` · Area: `Java/Spring Target`
  - Evidence: [`TargetCatalog`](src/main/java/dev/harpia/target/TargetCatalog.java), [`UnsupportedTargetTest`](src/test/java/dev/harpia/target/UnsupportedTargetTest.java).

- [x] `SPRING-002` **Spring Boot + Maven bootstrap** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringBootstrapTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringBootstrapTransformer.java), [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache).

- [x] `SPRING-003` **Spring MVC REST CRUD V0** — `DONE` no recorte atual · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [x] `SPRING-004` **Spring Data JPA + PostgreSQL + Flyway V0** — `DONE` · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).

- [x] `SPRING-005` **Spring Validation V0** — `DONE` para required/Email e `validate input` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [ ] `SPRING-006` **Spring Security/JWT** — `TODO` · `P0` · `XL` · Area: `Java/Spring Target`
  - Depends on: `SEC-005`, `SEC-007`.

- [ ] `SPRING-008` **Local Spring Events** — `TODO` · `P0` · `L` · Area: `Java/Spring Target`
  - Depends on: `EVENT-005`.

- [x] `SPRING-009` **Spring/JUnit/Mockito test baseline gerada** — `DONE` para CRUD V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java).

- [x] `SPRING-011` **Projeto convencional sem Harpia runtime** — `DONE` para o recorte gerado · `P0` · `M` · Area: `Ownership`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java).

- [x] `SPRING-012` **Executable JAR verificado** — `DONE`; o gate do projeto gerado passou de `mvn test` para `mvn -o package`, que roda os testes e constrói o artefato numa invocação só, e o teste abre o jar: `Main-Class` é o `JarLauncher`, `Start-Class` é a aplicação gerada e `BOOT-INF/lib/` carrega as dependências · `P0` · `S` · Area: `Java/Spring Target`
  - Evidence: [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java), [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache).


## CLI, Ownership and Harness

- [x] `CLI-002` **`harpia validate`** — `DONE` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`ValidateCommand`](src/main/java/dev/harpia/cli/ValidateCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).

- [x] `CLI-003` **`harpia build`** — `DONE` · `P0` · `L` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).

- [x] `CLI-008` **`harpia inspect`** — `DONE` para `ast`, `symbols`, `business-ir` e `application-ir` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`InspectCommand`](src/main/java/dev/harpia/cli/InspectCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).

- [x] `CLI-009` **`harpia version`** — `DONE` · `P1` · `XS` · Area: `CLI/DX`
  - Evidence: [`VersionCommand`](src/main/java/dev/harpia/cli/VersionCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).

- [x] `CLI-011` **`harpia targets`** — `DONE` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).

- [x] `OWN-001` **Código gerado é fonte comum, sem runtime Harpia** — `DONE` no target atual · `P0` · `M` · Area: `Ownership`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [x] `OWN-002` **Projeto pode ser commitado, modificado e destacado do Harpia** — `DONE` como propriedade do bootstrap atual · `P0` · `XS` · Area: `Ownership`
  - Evidence: saída não referencia `dev.harpia`; ver [`customer golden`](src/test/resources/fixtures/targets/java-spring/customer/pom.xml).

- [x] `OWN-003` **Manifesto limita ownership do writer** — `DONE` · `P0` · `M` · Area: `Ownership`
  - Evidence: [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).

- [x] `GREEN-002` **Baseline domain/API/persistence/config/test** — `DONE` para Customer CRUD V0 · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [ ] `GREEN-003` **Baseline integration/messaging/security** — `TODO` · `P0` · `XL` · Area: `Java/Spring Target`
  - Depends on: `INTEG-001`, `MSG-001`, `SEC-007`.

- [x] `GREEN-006` **Developer ownership após geração** — `DONE` no modo bootstrap atual · `P0` · `XS` · Area: `Ownership`
  - Evidence: `OWN-001`–`OWN-003`.

- [x] `DOC-001` **Documentação compacta e exemplos para humanos/LLMs** — `DONE`; [`docs/catalog.md`](docs/catalog.md) é o catálogo único: cada entrada é uma **especificação inteira**, pronta para copiar, e `CatalogTest` compila todas a cada execução — documentação que não pode derivar, porque uma entrada que parou de funcionar quebra o build em vez de enganar quem lê. As entradas marcadas com código mostram o que é **recusado**, e a recusa também é verificada. Escrever o catálogo já encontrou um defeito real: `{id}` fora do último segmento não era reconhecido como o registro que o flow carrega · `P0` · `L` · Area: `CLI/DX`
  - Evidence: [`docs/catalog.md`](docs/catalog.md), [`CatalogTest`](src/test/java/dev/harpia/CatalogTest.java), [`README`](README.md), [`language spec`](docs/spec/harpia-language.md).

- [x] `HARNESS-001` **Core Java API e resultados estruturados estáveis** — `DONE`; `HarpiaContract` nomeia o que é promessa — ser tipado não é o mesmo que ser prometido — e `HarpiaContract.VERSION` é o mesmo número que a CLI imprime como `contract`: a API Java e o JSON são uma promessa vista duas vezes. A superfície é fixada por golden, então alargá-la ou estreitá-la é decisão e não acidente; os códigos de diagnóstico têm um livro-razão próprio, que só cresce, porque um código novo não quebra ninguém e um constante renomeado apontaria a ferramenta existente para outra recusa · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`HarpiaContract`](src/main/java/dev/harpia/HarpiaContract.java), [`HarpiaContractTest`](src/test/java/dev/harpia/HarpiaContractTest.java), [`contract.txt`](src/test/resources/fixtures/api/contract.txt).

- [x] `HARNESS-002` **CLI JSON para validate/build/inspect/targets/capabilities** — `DONE`; os cinco comandos emitem um único objeto no stdout, sem linha de resumo e sem diagnostics no stderr para remontar. Um envelope só: `contract` (para o consumidor recusar uma versão que não conhece), `command`, `ok`, `exitCode` e `diagnostics` — cada um com `severity`/`code`/`message`, o `where` com span opcional e `related` como dado, não como prosa na mensagem. `build` acrescenta `output`; `inspect`, `stage` e `rendered`; `targets` lista o catálogo inteiro como dado, inclusive o que não gera; `capabilities` é um comando novo e diz o que o projeto exige, quem supre (`<target>` quando o próprio target implementa) e qual declaração pediu · `P0` · `L` · Area: `CLI/DX`
  - Evidence: [`CliJsonReportTest`](src/test/java/dev/harpia/cli/CliJsonReportTest.java), [`JsonReport`](src/main/java/dev/harpia/cli/JsonReport.java), [`Json`](src/main/java/dev/harpia/cli/Json.java), [`CapabilitiesCommand`](src/main/java/dev/harpia/cli/CapabilitiesCommand.java).
  - Depends on: `HARNESS-001`, `CORE-017`, `TARGET-014`.

- [x] `HARNESS-003` **Handoff manifest `.harpia/handoff.json`** — `DONE`; um build bem-sucedido escreve o manifesto ao lado do projeto, declarando target e status, capabilities com seus providers (`<target>` quando o próprio target implementa), onde está o manifesto de ownership — aponta para `.harpia-manifest` em vez de guardar uma segunda cópia da lista que poderia discordar da primeira — e cada Logic `custom` cuja interface foi gerada e cuja implementação continua sendo do usuário. Sem timestamp: dois builds idênticos deixam bytes idênticos. `CompileResult` ganhou `targetId()`, que faltava para responder "qual target rodou" sem reler a configuração · `P0` · `M` · Area: `Ownership`
  - Evidence: [`HandoffManifestTest`](src/test/java/dev/harpia/cli/HandoffManifestTest.java), [`HandoffManifest`](src/main/java/dev/harpia/cli/HandoffManifest.java), [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java).
  - Depends on: `HARNESS-001`, `CORE-027`, `CUSTOM-002`.

- [x] `HARNESS-004` **Resultado de verificação e instruções de handoff** — `DONE`; o handoff reporta os quatro gates e quem roda cada um. Harpia executou `validate` e `build`, então os afirma; `test` e `package` rodam na toolchain da linguagem gerada, então saem como `pending` com o comando que os roda — dizer que passaram inventaria um resultado que ninguém produziu. Os comandos vêm do próprio target (`TargetDescriptor.Gate`), porque como se testa o que `java-spring` gera é um fato do target. `next` nomeia a única coisa a fazer, e um contrato custom sem implementação vem antes de qualquer gate · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`HandoffManifestTest`](src/test/java/dev/harpia/cli/HandoffManifestTest.java), [`TargetDescriptor.Gate`](src/main/java/dev/harpia/target/TargetDescriptor.java), [`TargetCatalog`](src/main/java/dev/harpia/target/TargetCatalog.java).
  - Depends on: `HARNESS-002`, `HARNESS-003`, `SPRING-012`.

## Core V1 Blockers

Em ordem de dependência e valor para a Reference Application:

1. `FLOW-014` e `FLOW-020`: calls e iteração controlada.
2. `INTEG-003`–`INTEG-004`, `INTEG-010`, `RELY-001`: chamada e provider HTTP do FraudService.
3. `EVENT-001`, `EVENT-003`, `EVENT-005`, `EVENT-007`, `FLOW-015`: Event local e emit.
4. `API-008`: `SEC-002`, `SEC-003`, `SEC-005` e `SEC-007` fechados — access declarado, filter chain gerada, `basic`/`jwt` e recusa no mesmo corpo de erro da API.
5. `CUSTOM-002`, `CUSTOM-003`: contract, DI e layout custom protegido.
6. `GREEN-003`: baseline integrada verificável (`SPRING-012` fechado: `mvn package` e jar executável são gate).
7. `HARNESS-001`–`HARNESS-004`: fechados — contrato Java versionado, JSON dos cinco comandos, `.harpia/handoff.json` e os quatro gates.
8. `DOC-001`: catálogo compacto e exemplos canônicos para humanos e agentes.
9. Reference Application end-to-end cobrindo o conjunto acima.
10. `PERSIST-008` e demais parciais P0 após o baseline integrado revelar o recorte necessário.

## Core Technical Debt

- `JAVA-004` ainda traduz Command/Query apenas pelas formas CRUD conhecidas.
- `CUSTOM-003` precisa de layout que compile offline sem tornar o código do usuário descartável.
- O source map está no resultado em memória; o contrato externo depende de `HARNESS-001/002`.
- `GREEN-003` ainda não tem baseline integrada de integration/messaging/security a verificar.

## Explicitly Deferred from Core V1

Formula/Decision dedicadas, Money/Currency, query engine avançado, OpenAPI, schema evolution,
retries/circuit-breaker universais, idempotency, cache, scheduling, state machine, batch,
workflow/Saga, outbox, multi-tenancy, observability avançada, outros targets, brownfield, full MCP
e Managed Mode continuam registrados abaixo, mas não bloqueiam V1.

# Harpia Next / Upstream

Expansões de alto valor após a baseline estar utilizável. Nenhum item desta seção entra no cálculo
de conclusão do Core V1.

## Language Upstream

- [ ] `CORE-024` **Formatter e formatação canônica** — `TODO` · `P1` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-016`, `CORE-018`.

- [ ] `CORE-025` **Linter separado de validação** — `TODO` · `P2` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-017`.

- [ ] `TYPE-011` **Duration** — `TODO` · `P2` · `M` · Area: `Language Core`

- [ ] `TYPE-012` **Percentage** — `TODO` · `P1` · `M` · Area: `Logic`

- [ ] `TYPE-013` **Money** — `TODO` · `P1` · `L` · Area: `Logic`

- [ ] `TYPE-014` **Currency** — `TODO` · `P1` · `M` · Area: `Logic`

- [ ] `TYPE-015` **File** — `TODO` · `P2` · `M` · Area: `Integration`

- [ ] `TYPE-016` **URL** — `TODO` · `P2` · `S` · Area: `Language Core`

- [ ] `TYPE-019` **Secret** — `TODO` · `P1` · `M` · Area: `Security`

- [x] `TYPE-025` **Page&lt;T&gt;** — `DONE` na V1; `### Output` aceita `Page<Entidade>` e o target emite um `PageResponse<T>` genérico uma vez por projeto, com conteúdo, página, tamanho, total de registros e de páginas. O finder passa a devolver `Page` para trazer a contagem da mesma consulta que fatiou. Declarar `Page` sem listagem paginada é recusado · `P1` · `M` · Area: `API`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`JavaSpringDtoTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringDtoTransformer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`PageOutputTest`](src/test/java/dev/harpia/validate/PageOutputTest.java).

- [ ] `DOM-008` **Campos imutáveis** — `TODO` · `P2` · `M` · Area: `Domain`

- [ ] `DOM-009` **Campos derivados** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `LOGIC-001`.

- [ ] `DOM-010` **Campos sensitive/encrypted** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `CORE-009`, `TYPE-019`.

- [ ] `DOM-011` **Campos internal/input-only/output-only** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `CMD-002`, `QUERY-002`.

- [ ] `DOM-015` **Aggregate e Aggregate Root** — `TODO` · `P2` · `XL` · Area: `Domain`
  - Depends on: `DOM-012`, `RULE-002`.

- [ ] `DOM-016` **Ownership explícito** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `DOM-015`.

- [ ] `DOM-017` **Lifecycle** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `STATE-001`.

- [ ] `RULE-003` **Policy reutilizável** — `TODO` · `P1` · `XL` · Area: `Security`
  - Depends on: `RULE-001`, `SEC-003`.

- [ ] `RULE-004` **Requires / precondition** — `PARTIAL`; `FLOW-012` entrega a precondition inline sobre o input, mas member access sobre variáveis de Flow e referência nominal a Rules ainda não existem · `P1` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `FLOW-012`.
  - Evidence: [`RequireInstructionTest`](src/test/java/dev/harpia/validate/RequireInstructionTest.java).

- [ ] `RULE-005` **Ensures / postcondition** — `TODO` · `P2` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `CMD-001`.

- [ ] `RULE-006` **Referência ao estado anterior** — `TODO` · `P2` · `M` · Area: `Domain`
  - Depends on: `RULE-005`, `STATE-001`.

- [ ] `RULE-007` **Expressões de validação reutilizáveis** — `TODO` · `P1` · `L` · Area: `Domain`
  - Depends on: `RULE-001`, `LOGIC-014`.

- [ ] `RULE-008` **Diagnostics de regras** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `CORE-016`, `CORE-017`.

- [ ] `RULE-010` **Testes gerados de regras e contratos de negócio** — `TODO` · `P1` · `L` · Area: `Testing`
  - Depends on: `RULE-001`, `TEST-001`.

- [ ] `LOGIC-001` **Formula como construção explícita** — `TODO` · `P1` · `M` · Area: `Logic`
  - Depends on: `CORE-009`, `LOGIC-014`.

- [ ] `LOGIC-002` **Input/output tipados de Formula** — `TODO` · `P1` · `S` · Area: `Logic`
  - Depends on: `LOGIC-001`.

- [ ] `LOGIC-003` **Expressão pura e Formula reutilizável** — `TODO` · `P1` · `M` · Area: `Logic`
  - Depends on: `LOGIC-001`, `LOGIC-014`.

- [ ] `LOGIC-004` **Decision como construção explícita** — `TODO` · `P1` · `L` · Area: `Logic`
  - Depends on: `RULE-001`, `LOGIC-014`.

- [ ] `LOGIC-005` **Decision `when` / `otherwise` com first-match** — `TODO` · `P1` · `M` · Area: `Logic`
  - Depends on: `LOGIC-004`.

- [ ] `LOGIC-006` **Análise de exaustividade e decision tables** — `TODO` · `P2` · `L` · Area: `Logic`
  - Depends on: `LOGIC-005`, `TYPE-020`.

- [ ] `LOGIC-007` **Testes gerados de Decision** — `TODO` · `P2` · `M` · Area: `Testing`
  - Depends on: `LOGIC-004`, `TEST-001`.

- [ ] `LOGIC-017` **Operador módulo `%`** — `TODO`; `%` é hoje reservado para Percentage · `P2` · `S` · Area: `Logic`

- [ ] `LOGIC-020` **Registro extensível/estável de funções puras** — `PARTIAL`; registry fechado contém apenas `min` e `max` numéricos binários · `P1` · `M` · Area: `Logic`
  - Evidence: [`BuiltinRegistry`](src/main/java/dev/harpia/logic/BuiltinRegistry.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).

- [ ] `LOGIC-021` **Coleções: sum/count/min/max/average/any/all/none/first/distinct/contains/filter/map** — `TODO` · `P2` · `XL` · Area: `Logic`
  - Depends on: `TYPE-021`, `LOGIC-020`.

- [ ] `LOGIC-022` **Money/Percentage/Currency, scale e rounding sem floating point** — `TODO` · `P1` · `XL` · Area: `Logic`
  - Depends on: `TYPE-012`, `TYPE-013`, `TYPE-014`.

- [ ] `LOGIC-023` **Date/time: today, now, Duration e aritmética** — `TODO` · `P2` · `L` · Area: `Logic`
  - Depends on: `TYPE-011`, `CORE-020`.

- [ ] `LOGIC-024` **daysBetween/monthsBetween/yearsBetween** — `TODO` · `P2` · `M` · Area: `Logic`
  - Depends on: `LOGIC-023`.

- [ ] `FLOW-016` **`send` Email/Message** — `TODO` · `P2` · `M` · Area: `Integration`
  - Depends on: `EMAIL-001`, `MSG-001`.

- [ ] `FLOW-017` **Transação explícita** — `PARTIAL`; operações de mutação recebem transação inferida, sem construção Flow · `P1` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationModelBuilder`](src/main/java/dev/harpia/application/ApplicationModelBuilder.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).

- [ ] `FLOW-018` **`transition`** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-001`.

- [ ] `FLOW-021` **Async** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `EVENT-001`, `RELY-001`.


## API and Query Upstream

- [ ] `CMD-005` **Rules de Command** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `CMD-001`, `RULE-001`.

- [ ] `CMD-009` **Idempotency de Command** — `TODO` · `P1` · `L` · Area: `Runtime`
  - Depends on: `IDEMP-001`.

- [ ] `CMD-010` **Events de Command** — `TODO` · `P1` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `FLOW-015`.

- [ ] `CMD-012` **Implementação custom de Command** — `TODO` · `P1` · `M` · Area: `Extensibility`
  - Depends on: `CUSTOM-001`.

- [ ] `QUERY-006` **Cursor pagination** — `TODO` · `P2` · `L` · Area: `API`
  - Depends on: `QUERY-005`.

- [ ] `QUERY-007` **Projection** — `TODO` · `P1` · `L` · Area: `Persistence`
  - Depends on: `CORE-009`, `TYPE-021`.

- [ ] `QUERY-008` **Cache de Query** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `CACHE-001`.

- [ ] `QUERY-010` **Count e Exists** — `TODO` · `P1` · `M` · Area: `Persistence`

- [ ] `QUERY-011` **Distinct** — `TODO` · `P2` · `M` · Area: `Persistence`

- [ ] `QUERY-012` **Aggregation e Grouping** — `TODO` · `P2` · `XL` · Area: `Persistence`
  - Depends on: `LOGIC-021`.

- [ ] `QUERY-013` **Nested filters e optional filters** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `QUERY-003`, `TYPE-022`.

- [ ] `API-011` **Multipart e file upload/download** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`, `BIND-002`.

- [ ] `API-012` **Versionamento e deprecation de API** — `TODO` · `P2` · `L` · Area: `API`

- [ ] `API-013` **OpenAPI generation** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `CMD-011`, `QUERY-001`, `BIND-002`.

- [ ] `BIND-007` **HTTP status/content-type/accept mapping** — `PARTIAL`; status é mapeado, media types não · `P1` · `M` · Area: `Integration`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).

- [ ] `BIND-008` **OAuth e autenticação HTTP avançada** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `INTEG-010`, `SEC-002`, `SEC-005`.

- [ ] `BIND-010` **HTTP timeout/retry/proxy/multipart/response validation** — `TODO` · `P2` · `XL` · Area: `Integration`
  - Depends on: `RELY-001`, `RELY-002`, `API-011`.

- [ ] `BIND-011` **Messaging binding: event/topic/queue/routing/partition/serialization/headers** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-001`, `MSG-001`.

- [ ] `BIND-012` **Messaging retry e DLQ binding** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `BIND-011`, `RELY-002`.

- [ ] `BIND-013` **Persistence binding: capability/provider/datasource/schema** — `PARTIAL`; provider PostgreSQL e config estão no `harpia.yaml`, sem binding próprio · `P1` · `L` · Area: `Persistence`
  - Evidence: [`CapabilityResolver`](src/main/java/dev/harpia/capability/CapabilityResolver.java), [`HarpiaConfig`](src/main/java/dev/harpia/config/HarpiaConfig.java).


## Persistence Upstream

- [ ] `PERSIST-006` **JDBC provider** — `TODO` · `P2` · `L` · Area: `Persistence`

- [ ] `PERSIST-011` **Optimistic locking** — `TODO` · `P2` · `M` · Area: `Persistence`

- [ ] `PERSIST-012` **Pessimistic locking** — `RESEARCH` · `P3` · `L` · Area: `Persistence`

- [ ] `PERSIST-013` **Soft delete** — `TODO` · `P2` · `L` · Area: `Persistence`

- [ ] `PERSIST-014` **Audit de entidade** — `TODO` · `P2` · `L` · Area: `Persistence`

- [ ] `PERSIST-017` **Liquibase provider** — `TODO` · `P3` · `L` · Area: `Persistence`

- [ ] `PERSIST-018` **MySQL provider** — `TODO` · `P3` · `L` · Area: `Persistence`

- [ ] `DBEV-001` **Schema snapshot versionado** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `PERSIST-015`, `CORE-020`.

- [ ] `DBEV-002` **Schema diff e migration planning** — `TODO` · `P2` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-001`.


## Integration, Messaging and Security Upstream

- [ ] `INTEG-007` **Inbound/outbound Webhook** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `API-001`, `EVENT-001`.

- [ ] `INTEG-008` **Custom integration provider** — `TODO` · `P2` · `M` · Area: `Extensibility`
  - Depends on: `INTEG-001`, `CUSTOM-001`.

- [ ] `INTEG-009` **Timeout/retry/backoff/circuit breaker/fallback/rate limit** — `TODO` · `P2` · `XL` · Area: `Integration`
  - Depends on: `INTEG-001`, `RELY-001`–`RELY-006`.

- [ ] `EVENT-002` **Versionamento e compatibilidade de Event** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `INTEL-006`.

- [ ] `EVENT-004` **Handler / On Event** — `TODO` · `P1` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `CMD-001`.

- [ ] `EVENT-006` **Inbound/outbound events** — `TODO` · `P2` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `BIND-011`.

- [ ] `MSG-001` **Capability e modelo lógico de messaging** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-001`, `BIND-011`.

- [ ] `MSG-002` **Producer/consumer, topic/queue e consumer group** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`.

- [ ] `MSG-003` **Routing, partition key, ordering, headers e correlation ID** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `EVENT-002`.

- [ ] `MSG-004` **Acknowledgement, retry e DLQ** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-012`.

- [ ] `MSG-006` **Message versioning** — `TODO` · `P2` · `M` · Area: `Messaging`
  - Depends on: `EVENT-002`.

- [ ] `MSG-007` **Kafka provider** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-011`, `IDEMP-003`.

- [ ] `MSG-008` **RabbitMQ provider** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-011`.

- [ ] `IDEMP-001` **Modelo geral de idempotency e key expression** — `TODO` · `P1` · `XL` · Area: `Runtime`
  - Depends on: `CMD-001`, `CORE-009`.

- [ ] `IDEMP-002` **HTTP idempotency** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `IDEMP-001`, `API-001`.

- [ ] `IDEMP-003` **Message idempotency** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `IDEMP-001`, `MSG-001`.

- [ ] `IDEMP-004` **Webhook idempotency** — `TODO` · `P2` · `M` · Area: `Integration`
  - Depends on: `IDEMP-001`, `INTEG-007`.

- [ ] `IDEMP-005` **Integration idempotency** — `TODO` · `P2` · `M` · Area: `Integration`
  - Depends on: `IDEMP-001`, `INTEG-001`.

- [ ] `IDEMP-006` **Duplicate detection, response replay, store e TTL** — `TODO` · `P1` · `L` · Area: `Runtime`
  - Depends on: `IDEMP-001`, `PERSIST-001`.

- [ ] `IDEMP-007` **Testes gerados de idempotency** — `TODO` · `P2` · `M` · Area: `Testing`
  - Depends on: `IDEMP-006`, `TEST-001`.

- [ ] `SEC-004` **Ownership/object-level/field-level authorization** — `TODO` · `P2` · `XL` · Area: `Security`
  - Depends on: `SEC-003`, `DOM-016`.

- [ ] `SEC-009` **Secret handling** — `PARTIAL`; datasource usa referências de environment, sem tipo Secret/redaction geral · `P1` · `L` · Area: `Security`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache).

- [ ] `SEC-010` **Sensitive fields, masking e redacted logging** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `DOM-010`, `OBS-001`.

- [ ] `SEC-012` **Audit de acesso** — `TODO` · `P2` · `L` · Area: `Security`
  - Depends on: `SEC-003`, `OBS-001`.

- [ ] `RELY-002` **Retry, conditions e backoff** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `RELY-001`, `IDEMP-001`.

- [ ] `RELY-003` **Circuit breaker e fallback** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `RELY-001`, `RELY-002`.

- [ ] `RELY-005` **Rate limiting** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `SEC-002`, `CACHE-001`.

- [ ] `RELY-006` **Política de idempotência antes de retry inseguro** — `TODO` · `P1` · `M` · Area: `Runtime`
  - Depends on: `RELY-002`, `IDEMP-001`.

- [ ] `EMAIL-001` **Email como operação/capability** — `TODO`; a seção é rejeitada em V0 · `P1` · `L` · Area: `Integration`
  - Depends on: `CORE-009`, `TYPE-008`.

- [ ] `EMAIL-002` **Subject, recipients, template e inputs tipados** — `TODO` · `P1` · `L` · Area: `Integration`
  - Depends on: `EMAIL-001`.

- [ ] `EMAIL-003` **CC/BCC e attachments** — `TODO` · `P3` · `M` · Area: `Integration`
  - Depends on: `EMAIL-002`, `FILE-001`.

- [ ] `EMAIL-004` **SMTP provider** — `TODO` · `P1` · `M` · Area: `Integration`
  - Depends on: `EMAIL-001`, `TYPE-019`.


## Runtime Upstream

- [ ] `CACHE-001` **Cache capability, key e TTL** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `CORE-009`, `QUERY-001`.

- [ ] `CACHE-002` **Cacheable Query** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `CACHE-001`, `QUERY-001`.

- [ ] `CACHE-003` **Invalidação manual/on mutation/on event** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `CACHE-001`, `CMD-001`, `EVENT-001`.

- [ ] `CACHE-004` **Local cache provider** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `CACHE-001`.

- [ ] `CACHE-005` **Redis/Spring Cache provider** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `CACHE-001`, `PERSIST-020`.

- [ ] `SCHED-001` **Schedule com cron/every** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `CMD-001`, `CORE-009`.

- [ ] `SCHED-002` **Executar Command com retry/timeout** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `SCHED-001`, `RELY-001`, `RELY-002`.

- [ ] `SCHED-003` **Single-instance execution e distributed lock** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `SCHED-001`, `RELY-007`.

- [ ] `SCHED-004` **Spring Scheduling provider** — `TODO` · `P2` · `M` · Area: `Java/Spring Target`
  - Depends on: `SCHED-001`.

- [ ] `SCHED-005` **Quartz provider** — `RESEARCH` · `P4` · `L` · Area: `Runtime`
  - Depends on: `SCHED-001`.

- [ ] `STATE-001` **States e transitions** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `TYPE-020`, `CMD-001`, `RULE-001`.

- [ ] `STATE-002` **Transition Command e validation** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-001`.

- [ ] `STATE-003` **Diagnostics de transição inválida** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-002`, `CORE-017`.

- [ ] `STATE-004` **Testes gerados e transition events** — `TODO` · `P2` · `L` · Area: `Testing`
  - Depends on: `STATE-002`, `EVENT-001`, `TEST-001`.

- [ ] `FILE-001` **File, upload e download** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `TYPE-015`, `BIND-002`.

- [ ] `FILE-002` **Validação de tamanho/content type/formato/checksum** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`, `RULE-001`.

- [ ] `FILE-003` **Storage capability e filesystem provider** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`.

- [ ] `FILE-004` **S3 provider e signed URLs** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-003`.

- [ ] `FILE-006` **Retention** — `TODO` · `P3` · `M` · Area: `Integration`
  - Depends on: `FILE-003`, `SCHED-001`.

- [ ] `OBS-001` **Logging técnico seguro** — `TODO` · `P2` · `M` · Area: `Operations`
  - Depends on: `CMD-001`, `SEC-010`.

- [ ] `OBS-002` **Metrics** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `CMD-001`, `QUERY-001`.

- [ ] `OBS-003` **Traces e context propagation** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `INTEG-001`, `MSG-001`.

- [ ] `OBS-004` **Correlation ID, request ID e trace context** — `TODO` · `P2` · `M` · Area: `Operations`
  - Depends on: `API-001`, `MSG-003`.

- [ ] `OBS-005` **Micrometer/OpenTelemetry providers** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
  - Depends on: `OBS-002`, `OBS-003`.

- [ ] `OBS-006` **Prometheus provider** — `TODO` · `P2` · `M` · Area: `Operations`
  - Depends on: `OBS-005`.

- [ ] `OPS-001` **Health/readiness/liveness e dependency health** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `OBS-002`, `CONFIG-001`.

- [ ] `OPS-002` **Graceful shutdown e consumer draining** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `MSG-001`.

- [ ] `OPS-003` **Actuator metrics/Prometheus/info/loggers/management endpoints** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
  - Depends on: `OBS-005`, `SEC-003`.

- [ ] `OPS-004` **Runtime diagnostics** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `CORE-017`, `OBS-001`.

- [ ] `CONFIG-003` **Profiles/environments dev/test/staging/production** — `TODO` · `P2` · `L` · Area: `Operations`

- [ ] `CONFIG-004` **Property binding tipado** — `TODO` · `P2` · `M` · Area: `Operations`


## Testing, Target and Java Upstream

- [ ] `TEST-001` **Scenario / Given / When / Then** — `TODO` · `P1` · `XL` · Area: `Testing`
  - Depends on: `CORE-009`, `CMD-001`, `QUERY-001`, `RULE-001`.

- [ ] `TEST-004` **Testes de persistência/integration gerados** — `TODO` · `P1` · `L` · Area: `Testing`
  - Depends on: `TEST-001`, `PERSIST-005`.

- [ ] `TEST-005` **Testes de security/event/integration contract** — `TODO` · `P2` · `XL` · Area: `Testing`
  - Depends on: `SEC-007`, `EVENT-005`, `INTEG-001`.

- [ ] `TEST-007` **Testes de Formula/Decision** — `TODO` · `P2` · `M` · Area: `Testing`
  - Depends on: `LOGIC-001`, `LOGIC-004`.

- [ ] `TEST-009` **Contract test suite de providers/targets** — `PARTIAL`; existem testes do provider atual e golden de um target, sem suite SPI reutilizável · `P1` · `L` · Area: `Testing`
  - Evidence: [`ProviderContributionsTest`](src/test/java/dev/harpia/target/javaspring/ProviderContributionsTest.java), [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java).

- [ ] `TEST-010` **Testcontainers e Service Connections** — `TODO` · `P2` · `L` · Area: `Testing`

- [ ] `TEST-011` **Fixtures geradas de Scenario** — `TODO` · `P2` · `L` · Area: `Testing`
  - Depends on: `TEST-001`.

- [ ] `TARGET-013` **Conformance suite reutilizável de target** — `PARTIAL`; golden/compile/build cobrem Java/Spring, sem contrato genérico plugável · `P1` · `L` · Area: `Multi-target`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

- [ ] `JAVA-008` **Security/event/integration/email/scheduling transformers** — `TODO` · `P2` · `XL` · Area: `Java/Spring Target`
  - Depends on: modelos semânticos correspondentes.

- [ ] `TPL-004` **application-test.yml** — `TODO` · `P1` · `S` · Area: `Java/Spring Target`

- [ ] `TPL-006` **Dockerfile/config adicionais** — `TODO` · `P2` · `M` · Area: `Java/Spring Target`

- [ ] `SPRING-007` **Spring Mail** — `TODO` · `P1` · `L` · Area: `Java/Spring Target`
  - Depends on: `EMAIL-004`.

- [ ] `SPRING-010` **Testcontainers** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`

- [ ] `SPR-EC-002` **Spring Security** — `TODO` · `P1` · `XL` · Area: `Java/Spring Target`
  - Depends on: `SEC-007`; canonical delivery: `SPRING-006`.

- [ ] `ADV-007` **Buildpacks** — `TODO` · `P3` · `M` · Area: `Java/Spring Target`
  - Depends on: `SPRING-012`, `GREEN-004`.


## DX and Agent Upstream

- [ ] `CLI-001` **`harpia init`** — `TODO` · `P1` · `M` · Area: `CLI/DX`

- [ ] `CLI-004` **`harpia test`** — `TODO` · `P1` · `M` · Area: `CLI/DX`

- [ ] `CLI-005` **`harpia clean`** — `PARTIAL`; existe `build --clean [--force]`, não comando próprio · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).

- [ ] `CLI-006` **`harpia fmt`** — `TODO` · `P1` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-024`.

- [ ] `CLI-007` **`harpia lint`** — `TODO` · `P2` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-025`.

- [ ] `CLI-010` **`harpia capabilities`** — `PARTIAL`; `harpia targets <id>` exibe capabilities, sem comando global próprio · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java), [`TargetCatalogTest`](src/test/java/dev/harpia/target/TargetCatalogTest.java).

- [ ] `CLI-012` **`harpia diff` / `impact` / `explain` / `why`** — `TODO` · `P2` · `XL` · Area: `CLI/DX`
  - Depends on: `INTEL-001`–`INTEL-004`.

- [ ] `CLI-013` **`harpia migrate diff`** — `TODO` · `P2` · `L` · Area: `CLI/DX`
  - Depends on: `DBEV-002`.

- [ ] `CLI-015` **`harpia mcp`** — `TODO` · `P1` · `S` · Area: `MCP`
  - Depends on: `MCP-001`.

- [ ] `INTEL-003` **Explain** — `TODO` · `P1` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-026`, `CORE-017`.

- [ ] `INTEL-009` **Semantic/project search** — `TODO` · `P2` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-009`.

- [ ] `MCP-001` **MCP server** — `TODO`; não existe dependência, transporte ou adapter MCP no repositório · `P1` · `XL` · Area: `MCP`
  - Depends on: `CORE-008`, `CORE-026`, `CORE-027`.

- [ ] `MCP-002` **STDIO transport** — `TODO` · `P1` · `M` · Area: `MCP`
  - Depends on: `MCP-001`.

- [ ] `MCP-004` **Language resources: overview/version/grammar/types/entities** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-001`, `CORE-018`.

- [ ] `MCP-005` **Language resources: commands/queries/rules/Formula/Decision/Logic/Flow** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: capacidades correspondentes e `MCP-004`.

- [ ] `MCP-006` **Language resources: events/integrations/scenarios/capabilities/targets/examples** — `TODO` · `P2` · `L` · Area: `MCP`
  - Depends on: capacidades correspondentes, `TARGET-014`.

- [ ] `MCP-007` **Project resources: info/config/specs/model/diagnostics** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-001`, `CORE-008`, `CORE-026`.

- [ ] `MCP-008` **Project resources: entities/commands/queries/events/integrations/capabilities/targets** — `TODO` · `P1` · `XL` · Area: `MCP`
  - Depends on: símbolos correspondentes, `CORE-009`.

- [ ] `MCP-009` **Read tools `project_info` e `project_validate`** — `TODO` · `P1` · `M` · Area: `MCP`
  - Depends on: `MCP-001`, `CORE-022`.

- [ ] `MCP-010` **Read tools `entity_list/get`, `command_list/get`, `query_list/get`** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-007`, `CORE-009`, `CMD-001`, `QUERY-001`.

- [ ] `MCP-011` **Read tools `event_list/get`, `integration_list/get`, `logic_get`** — `TODO` · `P2` · `L` · Area: `MCP`
  - Depends on: modelos correspondentes, `MCP-007`.

- [ ] `MCP-012` **Read tools `project_search`, `diagnostics_get`, `get_targets`, `get_target_capabilities`** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `INTEL-009`, `CORE-017`, `TARGET-014`.

- [ ] `MCP-013` **Build tools `project_build/test/clean/format/lint`** — `TODO` · `P1` · `XL` · Area: `MCP`
  - Depends on: `MCP-001`, `CLI-003`–`CLI-007`.

- [ ] `MCP-018` **Structured outputs e progressive disclosure** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-001`, schemas versionados.

- [ ] `MCP-020` **Workspace safety e proibição de shell arbitrário** — `TODO` no adapter MCP; primitives de path/output já existem no core · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-001`; reuse `CORE-027` e guards de [`SpecDiscovery`](src/main/java/dev/harpia/source/SpecDiscovery.java).

- [ ] `MCP-021` **MCP API version, schemas e handshake de versões** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `CORE-018`, `CORE-019`, `TARGET-002`.

- [ ] `DET-001` **Output hashing** — `PARTIAL`; testes calculam hash, o produto não o publica · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java).

- [ ] `DET-002` **Build fingerprint versionado** — `TODO` · `P1` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-018`, `TARGET-002`, `DET-001`.

- [ ] `GREEN-001` **Project starter completo** — `PARTIAL`; `build` requer config/specs existentes, não há `init` · `P1` · `L` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`README`](README.md).

- [ ] `GREEN-004` **Docker baseline** — `TODO` · `P2` · `M` · Area: `Java/Spring Target`
  - Depends on: `SPRING-012`.

- [ ] `GREEN-005` **Observability baseline** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
  - Depends on: `OBS-005`, `OPS-001`.

- [ ] `BENCH-001` **SCR compiler-only corpus e runner** — `TODO`; protocolo existe em documentação, sem medição executável · `P1` · `L` · Area: `CLI/DX`
  - Depends on: baseline V0 congelada, corpus versionado.

- [ ] `BENCH-002` **CCR/TCR agent benchmark** — `TODO` · `P2` · `XL` · Area: `MCP`
  - Depends on: `MCP-009`–`MCP-019`.

- [ ] `DOC-002` **Agent skill derivada da mesma fonte canônica** — `TODO` · `P2` · `M` · Area: `MCP`
  - Depends on: `MCP-004`–`MCP-006`, catálogo estruturado de coverage.


## Custom Dependencies

- [ ] `CUSTOM-004` **Custom Maven dependencies/proprietary SDKs** — `TODO` · `P2` · `M` · Area: `Extensibility`
  - Depends on: `CUSTOM-001`, `TARGET-016`.

# Harpia Labs / Research

Pesquisa e expansão opcional. Labs não possui promessa de conclusão e nunca entra no Definition
of Done do Core V1.

## Language and Domain Labs

- [ ] `TYPE-017` **Phone** — `TODO` · `P3` · `S` · Area: `Language Core`

- [ ] `TYPE-018` **IPAddress** — `TODO` · `P3` · `S` · Area: `Security`

- [ ] `TYPE-026` **CPF** — `TODO` · `P3` · `S` · Area: `Domain`

- [ ] `TYPE-027` **CNPJ** — `TODO` · `P3` · `S` · Area: `Domain`

- [ ] `TYPE-028` **Bibliotecas extensíveis de tipos semânticos customizados** — `RESEARCH` · `P4` · `XL` · Area: `Extensibility`
  - Depends on: `CORE-018`, `TYPE-024`.

- [ ] `DOM-018` **Module e bounded context** — `TODO` · `P3` · `XL` · Area: `Domain`
  - Depends on: `CORE-008`, `CORE-009`.

- [ ] `DOM-019` **Composição de features/módulos** — `TODO` · `P3` · `L` · Area: `Domain`
  - Depends on: `DOM-018`.

- [ ] `FLOW-022` **Parallel com semântica determinística** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `FLOW-021`, `DIST-001`.

- [ ] `QUERY-014` **Query DSL tipada** — `TODO` · `P3` · `XL` · Area: `Persistence`
  - Depends on: `QUERY-001`, `QUERY-003`, `CORE-009`.

- [ ] `QUERY-015` **Full-text search** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `QUERY-014`.

- [ ] `API-014` **OpenAPI import** — `RESEARCH` · `P4` · `XL` · Area: `Brownfield`
  - Depends on: `API-013`, `BROWN-001`.

- [ ] `API-016` **Conditional requests / ETag** — `RESEARCH` · `P4` · `L` · Area: `API`

- [ ] `API-017` **Async / HTTP 202** — `RESEARCH` · `P4` · `L` · Area: `API`
  - Depends on: `FLOW-021`, `EVENT-001`.


## Data Labs

- [ ] `PERSIST-007` **R2DBC provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`

- [ ] `PERSIST-019` **MongoDB provider** — `TODO` · `P3` · `XL` · Area: `Persistence`

- [ ] `PERSIST-020` **Redis data provider** — `TODO` · `P3` · `L` · Area: `Persistence`

- [ ] `PERSIST-021` **Cassandra/Couchbase providers** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`

- [ ] `PERSIST-022` **Neo4j provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`

- [ ] `PERSIST-023` **Elasticsearch/OpenSearch provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`

- [ ] `DBEV-003` **Rename detection** — `RESEARCH` · `P4` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`, `CORE-016`.

- [ ] `DBEV-004` **Destructive/breaking change detection** — `TODO` · `P3` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`.

- [ ] `DBEV-005` **Nullable transition e default backfill** — `TODO` · `P3` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`, `DBEV-004`.

- [ ] `DBEV-006` **Expand/contract e zero-downtime analysis** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-004`, `DBEV-005`.

- [ ] `DBEV-007` **Online migration provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-006`.


## Distributed Systems Labs

- [ ] `BIND-009` **HTTP mTLS** — `RESEARCH` · `P4` · `L` · Area: `Security`

- [ ] `INTEG-005` **SOAP provider** — `TODO` · `P3` · `XL` · Area: `Integration`
  - Depends on: `INTEG-001`.

- [ ] `INTEG-006` **gRPC provider** — `RESEARCH` · `P4` · `XL` · Area: `Integration`

- [ ] `MSG-005` **Delivery at-least-once, deduplication e replay** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `MSG-004`, `IDEMP-003`, `DIST-006`.

- [ ] `MSG-009` **JMS/ActiveMQ/Artemis providers** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `MSG-001`.

- [ ] `MSG-010` **Pulsar provider** — `RESEARCH` · `P4` · `XL` · Area: `Messaging`

- [ ] `MSG-011` **SQS provider** — `RESEARCH` · `P4` · `L` · Area: `Messaging`

- [ ] `DIST-001` **Transactional Outbox** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-003`, `PERSIST-008`, `PERSIST-015`.

- [ ] `DIST-002` **Inbox e idempotent consumer** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `IDEMP-003`.

- [ ] `DIST-003` **After-commit event** — `TODO` · `P3` · `M` · Area: `Messaging`
  - Depends on: `EVENT-005`, `PERSIST-008`.

- [ ] `DIST-004` **Eventual consistency** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `DIST-001`, `DIST-002`.

- [ ] `DIST-005` **Optimistic concurrency e locks** — `TODO` · `P3` · `L` · Area: `Persistence`
  - Depends on: `PERSIST-011`.

- [ ] `DIST-006` **Event replay** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `EVENT-002`, `MSG-005`.

- [ ] `DIST-007` **Distributed locks** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `RELY-007`.

- [ ] `DIST-008` **Consistência transaction/event** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `DIST-001`, `DIST-003`.

- [ ] `RELY-004` **Bulkhead e concurrency limits** — `TODO` · `P3` · `L` · Area: `Runtime`

- [ ] `RELY-007` **Distributed lock** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `PERSIST-001`.


## Security and Runtime Labs

- [ ] `SEC-006` **OAuth2/OIDC Resource Server** — `TODO` · `P3` · `XL` · Area: `Security`
  - Depends on: `SEC-005`.

- [ ] `SEC-008` **LDAP provider** — `RESEARCH` · `P4` · `L` · Area: `Security`

- [ ] `SEC-011` **Encrypted fields** — `TODO` · `P3` · `XL` · Area: `Security`
  - Depends on: `TYPE-019`, `SEC-010`.

- [ ] `SEC-013` **PII semantics/data governance** — `RESEARCH` · `P4` · `XL` · Area: `Security`
  - Depends on: `SEC-010`, `SEC-012`.

- [ ] `EMAIL-005` **SES provider** — `RESEARCH` · `P4` · `L` · Area: `Integration`
  - Depends on: `EMAIL-001`.

- [ ] `BATCH-001` **Job e Step** — `TODO` · `P3` · `XL` · Area: `Runtime`
  - Depends on: `CMD-001`, `TEST-001`.

- [ ] `BATCH-002` **Source/Processor/Writer** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `BATCH-001`, `INTEG-001`.

- [ ] `BATCH-003` **Chunk e batch size** — `TODO` · `P3` · `M` · Area: `Runtime`
  - Depends on: `BATCH-002`.

- [ ] `BATCH-004` **Retry/skip/restart/checkpoint** — `TODO` · `P3` · `XL` · Area: `Runtime`
  - Depends on: `BATCH-001`, `IDEMP-001`, `RELY-002`.

- [ ] `BATCH-005` **Spring Batch provider** — `TODO` · `P3` · `XL` · Area: `Java/Spring Target`
  - Depends on: `BATCH-001`–`BATCH-004`.

- [ ] `WORK-001` **Workflow e Steps** — `TODO` · `P3` · `XL` · Area: `Runtime`
  - Depends on: `CMD-001`, `EVENT-001`, `STATE-001`.

- [ ] `WORK-002` **Durable workflow e estado persistido** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `WORK-001`, `PERSIST-001`.

- [ ] `WORK-003` **Wait por evento/schedule/timeout** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `WORK-001`, `EVENT-001`, `SCHED-001`.

- [ ] `WORK-004` **Retry e compensation / Saga** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `WORK-001`, `RELY-002`, `IDEMP-001`, `DIST-004`.

- [ ] `WORK-005` **Provider externo de workflow** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `WORK-002`.

- [ ] `FILE-005` **Azure Blob provider** — `RESEARCH` · `P4` · `L` · Area: `Integration`
  - Depends on: `FILE-003`.

- [ ] `TENANT-001` **TenantScoped e tenant context** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `CORE-009`, `SEC-002`.

- [ ] `TENANT-002` **Tenant filtering e authorization** — `RESEARCH` · `P4` · `XL` · Area: `Security`
  - Depends on: `TENANT-001`, `SEC-004`.

- [ ] `TENANT-003` **Column/schema/database strategies e isolation** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `TENANT-001`, `PERSIST-001`, `DBEV-001`.

- [ ] `OBS-007` **Datadog/New Relic/OTLP exporters** — `RESEARCH` · `P4` · `L` · Area: `Operations`
  - Depends on: `OBS-005`.

- [ ] `OBS-008` **Instrumentação automática de Command/Query/Integration/Event/retry/Job/Schedule/DB** — `TODO` · `P3` · `XL` · Area: `Operations`
  - Depends on: `OBS-002`, `OBS-003` e os modelos de operação correspondentes.


## Target Labs

- [ ] `TARGET-015` **Target SDK e community target loading** — `TODO` · `P3` · `XL` · Area: `Extensibility`
  - Depends on: `TARGET-003`, `TARGET-013`, `CORE-018`.

- [ ] `TARGET-016` **Provider SPI e community providers** — `TODO` · `P3` · `XL` · Area: `Extensibility`
  - Depends on: segundo provider real, `BIND-013`.

- [ ] `TARGET-017` **Plugin architecture** — `RESEARCH` · `P4` · `XL` · Area: `Extensibility`
  - Depends on: `TARGET-015`, `TARGET-016`.

- [ ] `TGT-KOTLIN` **`kotlin-spring`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-CSHARP` **`csharp-aspnet`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-TS` **`typescript-nestjs`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-PYTHON` **`python-fastapi`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-GO` **`go`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-CLOJURE` **`clojure-jvm`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-PHP` **`php-laravel`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-RUST` **`rust`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-ELIXIR` **`elixir-phoenix`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

- [ ] `TGT-RUBY` **`ruby-rails`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

Nenhum target `NOT_SUPPORTED` possui generator falso ou capabilities fictícias.

- [ ] `SPR-EC-001` **Spring Batch** — `TODO` · `P3` · `XL` · Area: `Java/Spring Target`
  - Depends on: `BATCH-001`; canonical provider: `BATCH-005`.

- [ ] `SPR-EC-003` **Spring Integration** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `INTEG-001`, evidência de providers menores.

- [ ] `SPR-EC-004` **Spring Cloud Gateway** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `API-012`, `SEC-003`.

- [ ] `SPR-EC-005` **Spring Cloud Stream** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `MSG-001`, ao menos um broker estável.

- [ ] `SPR-EC-006` **Spring Cloud Config / discovery** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `CONFIG-003`, modelo de serviços.

- [ ] `SPR-EC-007` **Spring Cloud Circuit Breaker** — `RESEARCH` · `P4` · `L` · Area: `Java/Spring Target`
  - Depends on: `RELY-003`.

- [ ] `SPR-EC-008` **Spring Modulith** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `DOM-018`, `EVENT-005`, `INTEL-008`.

- [ ] `SPR-EC-009` **Spring Authorization Server** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: modelo de segurança maduro.

- [ ] `SPR-EC-010` **Spring AI em aplicações geradas** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: caso de uso runtime recorrente; não é MCP.

- [ ] `ADV-001` **Reactive execution model / WebFlux** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: modelo de efeitos, `PERSIST-007`.

- [ ] `ADV-002` **GraphQL** — `TODO` · `P3` · `XL` · Area: `API`
  - Depends on: `CMD-011`, `QUERY-001`, `BIND-002`.

- [ ] `ADV-003` **WebSocket e SSE** — `TODO` · `P3` · `XL` · Area: `API`
  - Depends on: `EVENT-001`, `BIND-002`.

- [ ] `ADV-004` **STOMP messaging** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `ADV-003`, `MSG-001`.

- [ ] `ADV-005` **WebClient provider** — `RESEARCH` · `P4` · `L` · Area: `Integration`
  - Depends on: `ADV-001`, `INTEG-004`.

- [ ] `ADV-006` **JTA/XA distributed transactions** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: evidência de que Saga/outbox não atende o caso.

- [ ] `ADV-008` **AOT** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: target Java/Spring amplo e estável.

- [ ] `ADV-009` **GraalVM native image** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `ADV-008`.

- [ ] `ADV-010` **CQRS** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `CMD-001`, `QUERY-001`, `EVENT-001`.

- [ ] `ADV-011` **Event Sourcing** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `EVENT-002`, `DIST-006`, `DBEV-001`.

- [ ] `ADV-012` **Full durable workflow engine e Saga orchestration** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `WORK-001`–`WORK-004`.

- [ ] `ADV-013` **Structured concurrency** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: `FLOW-022`.

- [ ] `ADV-014` **Load balancing, service discovery e distributed config** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: multiple services/integrations estabilizados.

- [ ] `ADV-015` **API Gateway** — `RESEARCH` · `P4` · `XL` · Area: `API`
  - Depends on: `SPR-EC-004`.

- [ ] `ADV-016` **Sharding, multiple datasources e read replicas** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-001`, provider model estável.

- [ ] `ADV-017` **Advanced privacy/data governance** — `RESEARCH` · `P4` · `XL` · Area: `Security`
  - Depends on: `SEC-013`.

- [ ] `ADV-018` **SLO definitions e performance policies** — `RESEARCH` · `P4` · `XL` · Area: `Operations`
  - Depends on: `OBS-002`, `OBS-008`.

- [ ] `ADV-019` **Architecture enforcement de aplicações** — `TODO` · `P3` · `XL` · Area: `CLI/DX`
  - Depends on: `DOM-018`, `INTEL-008`.


## Managed Mode, MCP and Tooling Labs

- [ ] `CLI-014` **`harpia import` / `reverse`** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-001`.

- [ ] `INTEL-001` **Semantic diff** — `TODO` · `P3` · `XL` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-016`.

- [ ] `INTEL-002` **Impact analysis** — `TODO` · `P3` · `XL` · Area: `CLI/DX`
  - Depends on: `CORE-009`, `INTEL-001`.

- [ ] `INTEL-004` **Why / provenance de inferências** — `TODO` · `P3` · `L` · Area: `CLI/DX`
  - Depends on: `INTEL-003`, `CORE-016`.

- [ ] `INTEL-005` **Breaking API analysis** — `TODO` · `P3` · `L` · Area: `API`
  - Depends on: `API-013`, `INTEL-001`.

- [ ] `INTEL-006` **Breaking Event analysis** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `EVENT-002`, `INTEL-001`.

- [ ] `INTEL-007` **Breaking schema analysis** — `TODO` · `P3` · `L` · Area: `Persistence`
  - Depends on: `DBEV-004`, `INTEL-001`.

- [ ] `INTEL-008` **Architecture rules e module dependency analysis** — `PARTIAL`; testes protegem fronteiras internas do compiler, sem DSL/regra de projeto gerado · `P3` · `XL` · Area: `CLI/DX`
  - Evidence: [`ArchitectureBoundaryTest`](src/test/java/dev/harpia/ArchitectureBoundaryTest.java), [`ApplicationModelBuilderTest`](src/test/java/dev/harpia/application/ApplicationModelBuilderTest.java).

- [ ] `MCP-003` **HTTP transport** — `RESEARCH` · `P4` · `XL` · Area: `MCP`
  - Depends on: STDIO estável, threat model, authentication e rate limit.

- [ ] `MCP-014` **Semantic mutations add_entity/add_field/add_value_object/add_enum** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: `CORE-024`, `CORE-016`, atomic writer.

- [ ] `MCP-015` **Semantic mutations add_command/add_query/add_rule/add_formula/add_decision/add_logic** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: construções correspondentes, `MCP-014`.

- [ ] `MCP-016` **Semantic mutations add_invariant/add_event/add_email/add_integration/add_scenario** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: construções correspondentes, `MCP-014`.

- [ ] `MCP-017` **`apply_spec_patch` versionado e semantic diff** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: `MCP-014`, `INTEL-001`, `INTEL-002`.

- [ ] `MCP-019` **Atomic mutations, dry-run, digest e rollback** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: `CORE-016`, `CORE-024`, workspace lock.

- [ ] `MCP-022` **Prompts progressivos sobre tools/resources estáveis** — `TODO` · `P3` · `L` · Area: `MCP`
  - Depends on: `MCP-009`–`MCP-019`.

- [ ] `MCP-023` **Project locking e controle de concorrência** — `TODO` · `P3` · `L` · Area: `MCP`
  - Depends on: `MCP-013`, `MCP-019`.

- [ ] `OWN-004` **Managed Generation Mode explícito e opcional** — `RESEARCH` · `P4` · `XL` · Area: `Ownership`
  - Depends on: `CORE-016`, `INTEL-001`, estratégia de merge.


## AI Labs — Brownfield / Reverse Engineering

- [ ] `BROWN-001` **`harpia reverse` / `harpia import code` pipeline** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: cobertura V1 estável, `CORE-008`, `CORE-009`, `BIND-002`, `CORE-022`.

- [ ] `BROWN-002` **Java AST e Spring annotation scanning** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-001`.

- [ ] `BROWN-003` **Controller/service/JPA/repository analysis** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `CMD-001`, `QUERY-001`, `PERSIST-005`.

- [ ] `BROWN-004` **Spring Security analysis** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `SEC-007`.

- [ ] `BROWN-005` **Kafka/Rabbit analysis** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `MSG-007`, `MSG-008`.

- [ ] `BROWN-006` **RestClient/WebClient integration analysis** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `INTEG-004`.

- [ ] `BROWN-007` **Maven/Gradle dependency analysis** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-001`.

- [ ] `BROWN-008` **application.yml/Flyway/Liquibase analysis** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-001`, `BIND-013`, `DBEV-001`.

- [ ] `BROWN-009` **OpenAPI analysis/import** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `API-014`.

- [ ] `BROWN-010` **Dependency/integration/event detection** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, modelos semânticos correspondentes.

- [ ] `BROWN-011` **LLM business flow/rule/Formula/Decision inference** — `RESEARCH` · `P4` · `XL` · Area: `Brownfield`
  - Depends on: parsers estáticos, construções semânticas estáveis e avaliação de confiança.

- [ ] `BROWN-012` **Draft Harpia e binding generation** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-003`, `BROWN-010`, `BIND-001`.

- [ ] `BROWN-013` **Confidence score e source evidence** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-012`, `CORE-016`.

- [ ] `BROWN-014` **Human review e semantic validation** — `TODO` · `P3` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-012`, `BROWN-013`, `CORE-022`.

- [ ] `BROWN-015` **IMPORT_REPORT com construct/confidence/files/lines/warnings/unsupported/custom recommendations** — `TODO` · `P3` · `M` · Area: `Brownfield`
  - Depends on: `BROWN-013`, `BROWN-014`.

# Custom / Target Code

> **Custom code is how Harpia intentionally avoids becoming Java written in Markdown.**

PDF/image/video processing, proprietary SDKs, advanced ML/math, JNI/native code, reflection,
bytecode manipulation, special protocols, target-specific optimization e advanced concurrency não
precisam virar linguagem. Se o comportamento é incomum ou implementation-specific, o compiler
gera um contract quando útil e o developer/agent continua no target.

## Core V1 Escape Hatch

Os três itens seguintes pertencem ao horizonte `CORE_V1` porque tornam o handoff deliberado.
- [x] `CUSTOM-001` **Custom implementation como feature oficial** — `DONE` na V1 para Logic; `### Implementation` com `custom <Contrato>` substitui o corpo, o compilador mantém assinatura e type checking e gera a interface, sem gerar implementação. Scenario sobre uma Logic custom é recusado (`HRP2124`), porque Harpia teria de executar código que não é dela · `P1` · `L` · Area: `Extensibility`
  - Evidence: [`LogicDeclarationParser`](src/main/java/dev/harpia/parse/LogicDeclarationParser.java), [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`ScenarioAnalyzer`](src/main/java/dev/harpia/validate/ScenarioAnalyzer.java), [`CustomImplementationTest`](src/test/java/dev/harpia/logic/CustomImplementationTest.java).

- [ ] `CUSTOM-002` **Interface/contract gerado e dependency injection** — `PARTIAL`; a interface é gerada com a assinatura tipada; falta injetar o bean no chamador, o que depende de `FLOW-014` (`call` Logic a partir de Flow) · `P0` · `L` · Area: `Extensibility`
  - Depends on: `CUSTOM-001` (pronto), `FLOW-014`.

- [ ] `CUSTOM-003` **Diretório custom protegido e preservado** — `PARTIAL`; a garantia de propriedade está provada (EP21): um arquivo que Harpia não escreveu sobrevive byte a byte a `--clean --force`, nunca entra no manifesto, e `--clean` sem `--force` recusa em vez de decidir pelo usuário. Falta **escolher o layout**: onde a implementação custom vive de modo a compilar junto com a interface gerada — ver nota abaixo · `P0` · `M` · Area: `Ownership`
  - Evidence: [`CustomCodeOwnershipTest`](src/test/java/dev/harpia/emit/CustomCodeOwnershipTest.java), [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java).
  - Aberto: `docs/roadmap.md` prevê `custom/` como irmão de `generated/`, o que exigiria um source root extra no pom. `build-helper-maven-plugin` não resolve offline, então essa escolha quebraria o gate `mvn -o test` do projeto gerado. A alternativa é a implementação viver dentro da árvore de fontes gerada (compila e é component-scanned sem plugin nenhum), ao custo de `rm -rf generated` destruir o trabalho do usuário. Decisão do dono do produto.


## Deliberately Custom

- [ ] `FILE-007` **Virus scanning** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: custom Integration/provider after `CUSTOM-001`.

- [ ] `CUSTOM-005` **JNI/native integration** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-006` **Proprietary SDK e unusual protocols** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`, `INTEG-008`.

- [ ] `CUSTOM-007` **Hardware integration** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-008` **Low-level cryptography** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`; Harpia modela intenção de segurança, não primitivas criptográficas.

- [ ] `CUSTOM-009` **Reflection e bytecode manipulation** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-010` **Custom ML** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-011` **Specialized math/algorithms** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-012` **PDF processing** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-013` **Image/video processing** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.

- [ ] `CUSTOM-014` **Arbitrary advanced Java behavior** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: `CUSTOM-001`.


## Explicit Product Non-goals

- [ ] `SCOPE-001` **Frontend: React/Vue/Angular e UI generation** — `WONT_DO` no foco atual · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-002` **Server-side UI/static resource generation** — `WONT_DO` no foco atual · `P4` · `L` · Area: `Scope`

- [ ] `SCOPE-003` **Mobile** — `WONT_DO` · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-004` **Desktop** — `WONT_DO` · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-005` **Games** — `WONT_DO` · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-006` **DSL annotation-by-annotation ou nomes de framework na Business IR** — `WONT_DO` · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-007` **LLM dentro de validate/build** — `WONT_DO` · `P4` · `L` · Area: `Scope`

- [ ] `SCOPE-008` **Round-trip brownfield para Java idêntico** — `WONT_DO` · `P4` · `XL` · Area: `Scope`

- [ ] `SCOPE-009` **MCP com shell arbitrário ou edição direta de generated/** — `WONT_DO` · `P4` · `L` · Area: `Scope`

# Supported Targets

Esta tabela é referência, não duplica itens nas contagens. `java-spring` pertence ao Core V1; os
demais IDs vivem em Target Labs e continuam honestamente `NOT_SUPPORTED`.

| Tier | Target IDs | Compromisso |
|---|---|---|
| Supported | `TGT-JAVA` | Java 21 + Spring Boot é o único target necessário para V1. |
| Lab Tier 1 | `TGT-KOTLIN`, `TGT-CSHARP`, `TGT-TS` | possível pesquisa futura |
| Lab Tier 2 | `TGT-PYTHON`, `TGT-GO` | possível pesquisa futura |
| Lab Tier 3 | `TGT-CLOJURE`, `TGT-PHP`, `TGT-RUST`, `TGT-ELIXIR`, `TGT-RUBY` | possível pesquisa futura |

Não há fake generators. Target SDK/Provider SPI só avançam após casos reais provarem a abstração.

# Current Coverage
As métricas são separadas por horizonte. `Completion` usa `DONE + 0,5 × PARTIAL`; Labs e limites
deliberados não têm meta de 100%.

| Horizon | Total | Done | Partial | Todo | Other | Completion |
|---|---:|---:|---:|---:|---:|---:|
| Core V1 | 213 | 176 | 16 | 21 | 0 | 86,4% |
| Next / Upstream | 183 | 1 | 12 | 168 | 2 | 3,8% |
| Labs / Research | 134 | 0 | 1 | 69 | 64 | N/A |
| Custom / Non-goals | 20 | 0 | 0 | 0 | 20 | N/A |
| **Canonical total** | **550** | **177** | **29** | **258** | **86** | — |

`Other` reúne `RESEARCH`, `NOT_SUPPORTED`, `CUSTOM` e `WONT_DO`. Ele não mascara trabalho do Core:
a seleção Core contém apenas itens implementáveis `DONE`, `PARTIAL` ou `TODO`.

## Current Strengths

- compiler determinístico multi-file com parser, AST, ProjectAst e SymbolTable;
- semantic analysis, Business IR e Application IR independentes do target;
- diagnostics estáveis, related locations e source mapping por símbolo/range gerado;
- Target API/registry/resolver, Java Target Model e renderer/templates com fronteiras testadas;
- Java/Spring REST CRUD, validation, JPA/PostgreSQL/Flyway e Maven convencional;
- Logic pura tipada, Enum, ValueObject, Rule e Invariant;
- testes gerados, golden, determinismo, javac e Maven do projeto gerado;
- output sem runtime Harpia, manifesto de ownership e custom contract inicial.

## Audit Snapshot

Baseline auditada no fechamento de `INTEG-004`:

| Métrica | Resultado |
|---|---:|
| Produção Java | 230 arquivos / 25.163 linhas |
| Testes Java | 99 arquivos / 15.253 linhas |
| Gate | `mvn -o test` — **BUILD SUCCESS** |
| Testes executados | 520; 0 failures, 0 errors, 0 skipped |

# Core V1 Completion Criteria

Core V1 está pronto somente quando a Reference Application executa automaticamente:

```text
parse → validate → build → generate Java/Spring → compile → test → package → handoff
```

Gates obrigatórios:

- `harpia validate`;
- `harpia build`;
- `mvn test` no projeto gerado;
- `mvn package` no projeto gerado;
- build determinístico e golden estável;
- Java convencional sem runtime Harpia obrigatório;
- output editável e developer-owned;
- source mapping e diagnostics estruturados consumíveis por agentes;
- handoff informa target, capabilities geradas e contracts custom não resolvidos.

Core V1 **não depende** de segundo target, full MCP, brownfield, Saga/workflow, Batch, Kafka
exactly-once, Outbox, distributed locks, Elasticsearch/MongoDB/Redis, multi-tenancy, observability
avançada, semantic diff, zero-downtime migration, marketplace ou plugin ecosystem.

# Recommended Next Tasks

## Top 10 Core V1 Next Tasks

1. Fechar `FLOW-014` e implementar `RELY-001` sobre o FraudService HTTP já gerado.
2. Implementar `FLOW-020` somente no recorte de coleção exigido pela Reference Application.
3. Implementar `EVENT-001`/`EVENT-003`/`EVENT-005`/`EVENT-007` e `FLOW-015`.
4. Implementar `SEC-002`/`SEC-003`/`SEC-005`/`SEC-007` e fechar `API-008`.
5. Fechar `CUSTOM-002`/`CUSTOM-003` e provar DI/layout custom no Maven gerado.
6. Fechar `SPRING-012`/`GREEN-003`, incluindo `mvn package` e o baseline integrado.
7. Entregar `HARNESS-001`–`HARNESS-004` com JSON e handoff determinísticos.
8. Executar a Commerce Reference Application E2E e fechar `DOC-001` com o exemplo canônico.
9. Fechar `RULE-004` ao ampliar preconditions para valores produzidos pelo Flow.
10. Fechar `PERSIST-008` e demais parciais P0 após o baseline integrado definir o recorte.

## Key Scope Reductions

- Formula/Decision, Money/Currency e operações avançadas de coleções saíram do Core para Next.
- Schema evolution saiu do bootstrap: só migration inicial permanece no Core.
- Kafka não bloqueia Event nem V1; brokers e delivery guarantees estão em Next/Labs.
- Retry, circuit breaker, idempotency, cache, scheduling e state machine são upstream.
- Batch, workflow/Saga, outbox, multi-tenancy e distributed locks são Labs.
- MCP completo e semantic intelligence não bloqueiam o harness JSON mínimo.
- Outros targets e brownfield são pesquisa; `java-spring` basta para V1.
- Comportamento raro permanece Custom/Target Code em vez de expandir a DSL.

## Backlog Maintenance Contract

- Atualize status somente com evidência de produção/teste; docs sozinhos não bastam.
- Ao alterar um item, atualize cobertura, data e Audit Snapshot no mesmo change set.
- Um item novo precisa declarar implicitamente por seção ou explicitamente seu horizonte.
- `P0` fora do Core é erro de classificação.
- Labs nunca aparece como blocker nem no denominador de conclusão do Core.
- Não apague ideias futuras: reclassifique entre Core, Next, Labs ou Custom.
- Filtro final: **Harpia should not model everything a backend can become. Harpia should model
  enough intent to create an excellent backend starting point.**
- Quando a baseline está correta, compilável e testada, Harpia terminou seu trabalho; o agent ou
  developer continua no target.
