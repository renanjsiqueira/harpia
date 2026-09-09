# Harpia Backlog

Fonte única oficial de status e prioridade do produto, da linguagem, do compilador, dos targets,
do MCP e do brownfield. Auditoria atualizada em **2026-09-07** diretamente sobre a codebase e os
testes; documentos anteriores foram usados apenas como inventário de intenção.

> **Harpia is a backend semantic specification language and project bootstrapper.**

> **Java/Spring is the first supported target, not the definition of the Harpia language.**

> **Generated code belongs to the developer. Intent belongs to Harpia during the generation/bootstrap process.**

> **Harpia does not require generated code to remain permanently managed by Harpia.**

> **Generate it. Own it.**

> **AI may infer or write intent. Harpia deterministically materializes implementation.**

> **Spec defines what the software does. Bindings define how the software connects. Configuration defines target, providers and environment.**

> **Transformers translate semantics. Templates render syntax. Templates must not contain business semantics.**

> **Custom code remains the escape hatch for everything Harpia should not model.**

## Como manter este arquivo

- Cada item tem um identificador estável, um status, uma prioridade, um esforço e uma área.
- Somente `DONE` usa checkbox marcado. `PARTIAL` permanece desmarcado.
- Todo `DONE` ou `PARTIAL` aponta para evidência de produção e/ou teste.
- `DONE` significa pronto no recorte descrito pelo item, não cobertura universal de backend.
- Dependências apontam para IDs canônicos; uma capacidade não é duplicada em vários épicos.
- Tabelas de cobertura mais abaixo apenas referenciam itens canônicos e não entram nas contagens.
- Ao alterar um item, atualizar as contagens e a data de auditoria no mesmo change set.

### Legenda de status

| Status | Significado |
|---|---|
| `DONE` | Implementação funcional e teste compatível com o recorte declarado. |
| `PARTIAL` | Existe implementação útil, mas faltam camadas, generalidade ou gate end-to-end. |
| `TODO` | Planejado, sem implementação comprovada. |
| `BLOCKED` | Não pode avançar antes de decisão ou dependência externa explícita. |
| `RESEARCH` | Precisa de spike/ADR antes de compromisso de produto ou sintaxe. |
| `NOT_SUPPORTED` | Identificador reconhecido, mas não há gerador/provider executável. |
| `CUSTOM` | O caminho oficial é código customizado, não nova semântica obrigatória. |
| `WONT_DO` | Deliberadamente fora do produto ou contrário aos princípios. |

### Prioridade e esforço

| Prioridade | Uso |
|---|---|
| `P0` | Fundação, blocker ou necessário agora. |
| `P1` | Alto impacto e próximo roadmap. |
| `P2` | Importante depois do core. |
| `P3` | Expansão futura. |
| `P4` | Pesquisa, nicho ou longo prazo. |

Esforço: `XS`, `S`, `M`, `L`, `XL`.

## Escopo e fluxo do produto

O foco atual é **backend**. O fluxo greenfield é:

```text
Requirements → Human / AI → Harpia → deterministic compiler → generated backend → developer-owned codebase
```

O fluxo brownfield planejado é:

```text
Existing backend → static analysis → structured facts → LLM semantic reconstruction
                 → draft Harpia → validation → human review
```

Reverse engineering pode ser probabilístico. `validate` e `build` permanecem determinísticos e
não dependem de LLM.

## Estado atual comprovado

| Fato auditado | Estado em 2026-09-09 |
|---|---|
| Código de produção | 207 arquivos Java |
| Testes | 66 arquivos Java, 357 testes verdes, 0 falhas, 0 erros, 0 ignorados |
| Linguagem executável | V0 escalar; V1 acrescenta `## Command`, `## Query`, `## Enum`, `## Value`, `### Rules` executáveis e erros de domínio nomeados |
| Pipeline | Markdown → syntax AST → semantic analysis → Business IR → Application IR → target |
| Target executável | Somente `java-spring` |
| Projeto gerado | Maven, Spring Boot, DTO, service, JPA, repository, Flyway e testes; controller somente quando há binding HTTP |
| Gates | golden byte a byte, determinismo, `javac` e `mvn -o test` do projeto gerado |
| MCP | Inexistente |
| Brownfield | Inexistente |

Evidência transversal: [`HarpiaCompiler`](src/main/java/dev/harpia/HarpiaCompiler.java),
[`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java)
e o resultado local de `mvn -o test` descrito acima.

## P0 — Foundation / Current Priority

Visão operacional dos itens P0 ainda abertos; os registros canônicos permanecem nos épicos.

| Slice | IDs | Estado | Por que agora |
|---|---|---|---|
| Operações explícitas | `CMD-001`, `QUERY-001`, `CMD-011`, `JAVA-004` | `DONE` / `PARTIAL` | Command/Query independem de HTTP, aceitam exposição externa e a natureza declarada chega ao target; falta especialização dos transformers além de CRUD. |
| Escopos e referências | `CORE-010`, `CORE-011` | `PARTIAL` | Operações já cruzam módulos na V1; falta escopo léxico fora de Logic e tipos nominais. |
| Source mapping detalhado | `CORE-016` | `DONE` | Range, related locations e índice de origem por símbolo/linha gerada estão no resultado do compiler. |
| Binding foundation | `BIND-001`–`BIND-006` | `DONE` / `PARTIAL` | AST/model/parser/resolver externos prontos; base URL e mappings query/header/path/body chegam ao controller gerado; falta content negotiation. |
| Target extensibility | `TARGET-003` | `DONE` | Registry é a autoridade sobre o que gera; catálogo estático descreve apenas intenção. |
| V0 hardening | — | `DONE` | `CMD-004`, `API-009` e `DET-003` fechados; erros de domínio, validação em toda a fronteira HTTP e rebuild físico provado. |
| MCP safety gate | `MCP-020` | `TODO` | Obrigatório antes de expor operações a agentes. |

---

## EPIC — Core Language and Compiler

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
- [ ] `CORE-010` **Escopos léxicos** — `PARTIAL`; existem em Logic, não no conjunto futuro da linguagem · `P1` · `M` · Area: `Language Core`
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
- [ ] `CORE-024` **Formatter e formatação canônica** — `TODO` · `P1` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-016`, `CORE-018`.
- [ ] `CORE-025` **Linter separado de validação** — `TODO` · `P2` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-017`.
- [x] `CORE-026` **Inspect de AST/symbols/Business IR/Application IR** — `DONE`; renderiza os modelos carregados em `CompileResult.Stages`, sem pipeline paralelo · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`Inspector`](src/main/java/dev/harpia/inspect/Inspector.java), [`InspectorTest`](src/test/java/dev/harpia/inspect/InspectorTest.java), [`SemanticFixtureTest`](src/test/java/dev/harpia/SemanticFixtureTest.java).
- [x] `CORE-027` **Escrita idempotente, manifesto e proteção de arquivos desconhecidos** — `DONE` · `P0` · `L` · Area: `Ownership`
  - Evidence: [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).

## EPIC — Type System

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
- [ ] `TYPE-011` **Duration** — `TODO` · `P2` · `M` · Area: `Language Core`
- [ ] `TYPE-012` **Percentage** — `TODO` · `P1` · `M` · Area: `Logic`
- [ ] `TYPE-013` **Money** — `TODO` · `P1` · `L` · Area: `Logic`
- [ ] `TYPE-014` **Currency** — `TODO` · `P1` · `M` · Area: `Logic`
- [ ] `TYPE-015` **File** — `TODO` · `P2` · `M` · Area: `Integration`
- [ ] `TYPE-016` **URL** — `TODO` · `P2` · `S` · Area: `Language Core`
- [ ] `TYPE-017` **Phone** — `TODO` · `P3` · `S` · Area: `Language Core`
- [ ] `TYPE-018` **IPAddress** — `TODO` · `P3` · `S` · Area: `Security`
- [ ] `TYPE-019` **Secret** — `TODO` · `P1` · `M` · Area: `Security`
- [x] `TYPE-020` **Enum nominal** — `DONE` na V1; `## Enum <Nome>` declara um conjunto fechado de valores, entra na symbol table, é referenciável como tipo de campo e gera enum Java, coluna e constraint. O tipo de campo passou a ser álgebra selada (`FieldType`/`ApplicationFieldType`), então todo mapeamento precisa responder pelo nominal em vez de tratá-lo como texto · `P1` · `L` · Area: `Domain`
  - Evidence: [`EnumDeclarationParser`](src/main/java/dev/harpia/parse/EnumDeclarationParser.java), [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`ApplicationFieldType`](src/main/java/dev/harpia/application/ApplicationFieldType.java), [`JavaSpringEnumTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEnumTransformer.java), [`DeclaredEnumTest`](src/test/java/dev/harpia/validate/DeclaredEnumTest.java).
- [x] `TYPE-021` **List&lt;T&gt; geral** — `DONE` na V1 como tipo de campo; `List<Escalar>` e `List<Enum>` viram `@ElementCollection` com tabela própria ligada ao dono, e `required` significa não-vazia. `List<Entity>` continua relacionamento (`DOM-012`) e coleção de valor ou de coleção é recusada. Como parâmetro/retorno de Logic é `LOGIC-006` · `P1` · `L` · Area: `Language Core`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`ListFieldTest`](src/test/java/dev/harpia/validate/ListFieldTest.java).
- [x] `TYPE-022` **Optional&lt;T&gt; explícito** — `DONE` na V1; `Optional<T>` move a possibilidade de ausência para o tipo que o chamador recebe, sem mudar como o valor é armazenado: coluna nullable e campo JPA nu, getter e response DTO em `Optional<T>`. `Optional<T> required` é contradição recusada (`HRP2126`) · `P1` · `M` · Area: `Language Core`
  - Evidence: [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaTypeMapper`](src/main/java/dev/harpia/target/javaspring/mapping/JavaTypeMapper.java), [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`OptionalFieldTest`](src/test/java/dev/harpia/validate/OptionalFieldTest.java).
- [ ] `TYPE-023` **Reference&lt;T&gt;** — `TODO`; a álgebra de tipo de campo e o primeiro nominal já existem · `P1` · `L` · Area: `Domain`
  - Depends on: `CORE-009`, `TYPE-020` (pronto).
- [x] `TYPE-024` **ValueObject nominal** — `DONE` na V1; `## Value <Nome>` declara um grupo de campos sem identidade, referenciável como tipo de campo, gerado como `@Embeddable` e armazenado como colunas prefixadas da entidade que o contém, com `@AttributeOverride` casando com a migração. Campos `generated`/`unique` são recusados: identidade é de entidade · `P1` · `L` · Area: `Domain`
  - Evidence: [`ValueDeclarationParser`](src/main/java/dev/harpia/parse/ValueDeclarationParser.java), [`FieldType`](src/main/java/dev/harpia/model/FieldType.java), [`JavaSpringValueTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringValueTransformer.java), [`DeclaredValueTest`](src/test/java/dev/harpia/validate/DeclaredValueTest.java).
- [ ] `TYPE-025` **Page&lt;T&gt;** — `TODO` · `P1` · `M` · Area: `API`
  - Depends on: `TYPE-021`, `QUERY-005`.
- [ ] `TYPE-026` **CPF** — `TODO` · `P3` · `S` · Area: `Domain`
- [ ] `TYPE-027` **CNPJ** — `TODO` · `P3` · `S` · Area: `Domain`
- [ ] `TYPE-028` **Bibliotecas extensíveis de tipos semânticos customizados** — `RESEARCH` · `P4` · `XL` · Area: `Extensibility`
  - Depends on: `CORE-018`, `TYPE-024`.

## EPIC — Domain Modeling

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
- [ ] `DOM-008` **Campos imutáveis** — `TODO` · `P2` · `M` · Area: `Domain`
- [ ] `DOM-009` **Campos derivados** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `LOGIC-001`.
- [ ] `DOM-010` **Campos sensitive/encrypted** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `CORE-009`, `TYPE-019`.
- [ ] `DOM-011` **Campos internal/input-only/output-only** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `CMD-002`, `QUERY-002`.
- [ ] `DOM-012` **Relationships e referências** — `TODO`; a sintaxe é rejeitada explicitamente em V0 · `P1` · `XL` · Area: `Domain`
  - Depends on: `TYPE-023`, `CORE-009`, `PERSIST-005`.
- [ ] `DOM-013` **Owned relationship** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `DOM-012`.
- [ ] `DOM-014` **Embedded ValueObject** — `TODO` · `P1` · `L` · Area: `Domain`
  - Depends on: `TYPE-024`.
- [ ] `DOM-015` **Aggregate e Aggregate Root** — `TODO` · `P2` · `XL` · Area: `Domain`
  - Depends on: `DOM-012`, `RULE-002`.
- [ ] `DOM-016` **Ownership explícito** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `DOM-015`.
- [ ] `DOM-017` **Lifecycle** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `STATE-001`.
- [ ] `DOM-018` **Module e bounded context** — `TODO` · `P2` · `XL` · Area: `Domain`
  - Depends on: `CORE-008`, `CORE-009`.
- [ ] `DOM-019` **Composição de features/módulos** — `TODO` · `P2` · `L` · Area: `Domain`
  - Depends on: `DOM-018`.

## EPIC — Rules, Invariants and Policies

- [x] `RULE-001` **Rule executável tipada** — `DONE` na V1; um item de lista sob `### Rules` é uma condição booleana sobre o input, tipada pelo mesmo analisador de expressões da Logic, carregada pelos dois IRs e verificada onde o flow declara `validate input`. Prosa continua documentação e a V0 não muda de significado · `P1` · `L` · Area: `Domain`
  - Evidence: [`UseCaseDeclarationParser`](src/main/java/dev/harpia/parse/UseCaseDeclarationParser.java), [`LogicAnalyzer.analyzeExpression`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`RuleModel`](src/main/java/dev/harpia/model/RuleModel.java), [`ApplicationRule`](src/main/java/dev/harpia/application/ApplicationRule.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`RuleTest`](src/test/java/dev/harpia/validate/RuleTest.java).
- [x] `RULE-002` **Invariant** — `DONE` na V1; `## Invariants` declara condições sobre a entidade, tipadas contra os campos dela pelo mesmo analisador de expressões, e verificadas antes de cada `save` — o momento em que o estado se torna durável. Violação é `InvariantViolationException` com 422, porque uma requisição bem formada pedindo um estado proibido não é input inválido · `P1` · `L` · Area: `Domain`
  - Evidence: [`InvariantDeclarationParser`](src/main/java/dev/harpia/parse/InvariantDeclarationParser.java), [`LogicAnalyzer`](src/main/java/dev/harpia/validate/LogicAnalyzer.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`InvariantTest`](src/test/java/dev/harpia/validate/InvariantTest.java).
- [ ] `RULE-003` **Policy reutilizável** — `TODO` · `P1` · `XL` · Area: `Security`
  - Depends on: `RULE-001`, `SEC-003`.
- [ ] `RULE-004` **Requires / precondition** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `FLOW-012`.
- [ ] `RULE-005` **Ensures / postcondition** — `TODO` · `P2` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `CMD-001`.
- [ ] `RULE-006` **Referência ao estado anterior** — `TODO` · `P2` · `M` · Area: `Domain`
  - Depends on: `RULE-005`, `STATE-001`.
- [ ] `RULE-007` **Expressões de validação reutilizáveis** — `TODO` · `P1` · `L` · Area: `Domain`
  - Depends on: `RULE-001`, `LOGIC-014`.
- [ ] `RULE-008` **Diagnostics de regras** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `RULE-001`, `CORE-016`, `CORE-017`.
- [ ] `RULE-009` **Validação Java gerada a partir de Rule/Invariant** — `TODO` · `P1` · `L` · Area: `Java/Spring Target`
  - Depends on: `RULE-001`, `RULE-002`.
- [ ] `RULE-010` **Testes gerados de regras e contratos de negócio** — `TODO` · `P1` · `L` · Area: `Testing`
  - Depends on: `RULE-001`, `TEST-001`.

## EPIC — Harpia Logic

Princípio: **Formula computes. Decision chooses. Logic reasons. Flow acts.**

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
- [ ] `LOGIC-017` **Operador módulo `%`** — `TODO`; `%` é hoje reservado para Percentage · `P2` · `S` · Area: `Logic`
- [x] `LOGIC-018` **Parênteses e precedência canônica** — `DONE` · `P1` · `M` · Area: `Logic`
  - Evidence: [`LogicExpressionParser`](src/main/java/dev/harpia/parse/LogicExpressionParser.java), [`LogicGrammarTest`](src/test/java/dev/harpia/logic/LogicGrammarTest.java).
- [x] `LOGIC-019` **Inferência e verificação estática de tipos em Logic** — `DONE` para escalares V0 · `P1` · `L` · Area: `Logic`
  - Evidence: [`LogicType`](src/main/java/dev/harpia/logic/LogicType.java), [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java).
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
- [x] `LOGIC-025` **Geração Java pura para Logic** — `DONE` para L1/L2 · `P1` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`GeneratedLogicCompilesTest`](src/test/java/dev/harpia/target/javaspring/GeneratedLogicCompilesTest.java).

## EPIC — Flow DSL

- [x] `FLOW-001` **Flow como AST estruturada, sem strings genéricas** — `DONE` para os oito comandos V0 · `P0` · `L` · Area: `API`
  - Evidence: [`FlowStep`](src/main/java/dev/harpia/model/FlowStep.java), [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java).
- [x] `FLOW-002` **`validate input`** — `DONE` end-to-end · `P0` · `S` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [x] `FLOW-003` **`create Entity from input`** — `DONE` end-to-end · `P0` · `M` · Area: `API`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `FLOW-004` **`find`** — `PARTIAL`; somente `load Entity by id` e `list Entity` existem · `P1` · `L` · Area: `Persistence`
  - Evidence: [`FlowLineParser`](src/main/java/dev/harpia/parse/FlowLineParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java).
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
- [ ] `FLOW-011` **`set`, `add` e `remove`** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `TYPE-021`, `DOM-012`.
- [ ] `FLOW-012` **`require`** — `TODO` · `P1` · `M` · Area: `API`
  - Depends on: `RULE-001`.
- [ ] `FLOW-013` **`fail` com erro tipado** — `TODO`; o tipo já existe e é mapeado, falta a instrução que o levanta · `P1` · `M` · Area: `API`
  - Depends on: `CMD-004` (pronto).
- [ ] `FLOW-014` **`call` Logic/Command/Integration** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `CORE-009`, `CMD-001`, `INTEG-001`.
- [ ] `FLOW-015` **`emit` Event** — `TODO` · `P1` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`.
- [ ] `FLOW-016` **`send` Email/Message** — `TODO` · `P2` · `M` · Area: `Integration`
  - Depends on: `EMAIL-001`, `MSG-001`.
- [ ] `FLOW-017` **Transação explícita** — `PARTIAL`; operações de mutação recebem transação inferida, sem construção Flow · `P1` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationModelBuilder`](src/main/java/dev/harpia/application/ApplicationModelBuilder.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `FLOW-018` **`transition`** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-001`.
- [ ] `FLOW-019` **If / else em Flow** — `TODO`; existe apenas em Logic pura · `P2` · `L` · Area: `API`
- [ ] `FLOW-020` **For each** — `TODO` · `P2` · `L` · Area: `API`
  - Depends on: `TYPE-021`.
- [ ] `FLOW-021` **Async** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `EVENT-001`, `RELY-001`.
- [ ] `FLOW-022` **Parallel com semântica determinística** — `RESEARCH` · `P3` · `XL` · Area: `Runtime`
  - Depends on: `FLOW-021`, `DIST-001`.

## EPIC — Commands

- [x] `CMD-001` **Command como símbolo explícito independente de HTTP** — `DONE`; `## Command` é kind V1, dita a transação e pode omitir o binding HTTP sem desaparecer dos IRs ou da geração de service · `P0` · `L` · Area: `API`
  - Evidence: [`OperationNature`](src/main/java/dev/harpia/model/OperationNature.java), [`OperationDeclarationTest`](src/test/java/dev/harpia/parse/OperationDeclarationTest.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java).
- [x] `CMD-002` **Input tipado de operação V0** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`FieldLineParser`](src/main/java/dev/harpia/parse/FieldLineParser.java), [`JavaSpringDtoTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringDtoTransformer.java).
- [x] `CMD-003` **Output tipado de operação V0** — `DONE` para Entity/List/ nothing · `P0` · `M` · Area: `API`
  - Evidence: [`OutputModel`](src/main/java/dev/harpia/model/OutputModel.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).
- [x] `CMD-004` **Errors tipados gerais** — `DONE` para a declaração do tipo; além das três condições detectadas, um erro de domínio nomeado (`insufficient balance -> 422`) vira símbolo canônico, tipo gerado e mapeamento de status, com um status por erro no projeto inteiro. O gatilho é `FLOW-013` (`fail`), que depende deste · `P0` · `L` · Area: `API`
  - Evidence: [`ErrorLineParser`](src/main/java/dev/harpia/parse/ErrorLineParser.java), [`Naming`](src/main/java/dev/harpia/model/Naming.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`DomainErrorTest`](src/test/java/dev/harpia/validate/DomainErrorTest.java).
- [ ] `CMD-005` **Rules de Command** — `TODO` · `P1` · `M` · Area: `Domain`
  - Depends on: `CMD-001`, `RULE-001`.
- [x] `CMD-006` **Flow de mutação CRUD V0** — `DONE` · `P0` · `L` · Area: `API`
  - Evidence: [`FlowStep`](src/main/java/dev/harpia/model/FlowStep.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).
- [ ] `CMD-007` **Access de Command** — `PARTIAL`; somente `public` · `P1` · `L` · Area: `Security`
  - Evidence: [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java), [`UnsupportedFeatureDetector`](src/main/java/dev/harpia/parse/UnsupportedFeatureDetector.java).
- [ ] `CMD-008` **Política de transação de Command** — `PARTIAL`; inferência por espécie CRUD, sem configuração declarativa · `P1` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java).
- [ ] `CMD-009` **Idempotency de Command** — `TODO` · `P1` · `L` · Area: `Runtime`
  - Depends on: `IDEMP-001`.
- [ ] `CMD-010` **Events de Command** — `TODO` · `P1` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `FLOW-015`.
- [x] `CMD-011` **Exposure opcional por bindings** — `DONE`; Command/Query V1 podem permanecer internos ou ser expostos por binding HTTP externo; capability/controller/teste web só existem quando há binding · `P0` · `L` · Area: `API`
  - Depends on: `BIND-001`.
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).
- [ ] `CMD-012` **Implementação custom de Command** — `TODO` · `P1` · `M` · Area: `Extensibility`
  - Depends on: `CUSTOM-001`.

## EPIC — Queries

- [x] `QUERY-001` **Query como símbolo explícito independente de HTTP** — `DONE`; `## Query` é kind V1, rejeita mutação (`HRP2120`) e pode existir sem capability ou adapter HTTP · `P0` · `L` · Area: `API`
  - Evidence: [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java), [`OperationNatureTest`](src/test/java/dev/harpia/validate/OperationNatureTest.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java).
- [x] `QUERY-002` **Input/output/Flow de Query CRUD V0** — `DONE` para load-by-id e list-all · `P0` · `L` · Area: `API`
  - Evidence: [`Resolver`](src/main/java/dev/harpia/model/Resolver.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `QUERY-003` **Filters** — `TODO` · `P1` · `L` · Area: `Persistence`
- [ ] `QUERY-004` **Sorting declarativo** — `PARTIAL`; list-all impõe ordem estável por id, sem DSL de sort · `P1` · `M` · Area: `Persistence`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).
- [ ] `QUERY-005` **Pagination offset/page** — `TODO` · `P1` · `M` · Area: `API`
- [ ] `QUERY-006` **Cursor pagination** — `TODO` · `P2` · `L` · Area: `API`
  - Depends on: `QUERY-005`.
- [ ] `QUERY-007` **Projection** — `TODO` · `P1` · `L` · Area: `Persistence`
  - Depends on: `CORE-009`, `TYPE-021`.
- [ ] `QUERY-008` **Cache de Query** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `CACHE-001`.
- [ ] `QUERY-009` **Access de Query** — `PARTIAL`; somente `public` · `P1` · `L` · Area: `Security`
  - Evidence: [`AccessRule`](src/main/java/dev/harpia/model/AccessRule.java), [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java).
- [ ] `QUERY-010` **Count e Exists** — `TODO` · `P1` · `M` · Area: `Persistence`
- [ ] `QUERY-011` **Distinct** — `TODO` · `P2` · `M` · Area: `Persistence`
- [ ] `QUERY-012` **Aggregation e Grouping** — `TODO` · `P2` · `XL` · Area: `Persistence`
  - Depends on: `LOGIC-021`.
- [ ] `QUERY-013` **Nested filters e optional filters** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `QUERY-003`, `TYPE-022`.
- [ ] `QUERY-014` **Query DSL tipada** — `TODO` · `P1` · `XL` · Area: `Persistence`
  - Depends on: `QUERY-001`, `QUERY-003`, `CORE-009`.
- [ ] `QUERY-015` **Full-text search** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `QUERY-014`.

## EPIC — REST / HTTP API

- [x] `API-001` **Endpoint HTTP V0** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`EndpointParser`](src/main/java/dev/harpia/parse/EndpointParser.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).
- [x] `API-002` **GET, POST, PUT e DELETE** — `DONE` · `P0` · `M` · Area: `API`
  - Evidence: [`HttpBinding`](src/main/java/dev/harpia/model/HttpBinding.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `API-003` **PATCH** — `TODO` · `P1` · `M` · Area: `API`
- [ ] `API-004` **Path params gerais** — `PARTIAL`; somente `{id}` no último segmento · `P1` · `M` · Area: `API`
  - Evidence: [`EndpointParser`](src/main/java/dev/harpia/parse/EndpointParser.java), [`SemanticValidator`](src/main/java/dev/harpia/validate/SemanticValidator.java).
- [ ] `API-005` **Query params e headers** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `BIND-002`.
- [x] `API-006` **Request body DTO e response body DTO** — `DONE` para CRUD V0 · `P0` · `L` · Area: `API`
  - Evidence: [`JavaSpringDtoTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringDtoTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [x] `API-007` **Status codes 2xx e errors V0** — `DONE` para o conjunto fechado atual · `P0` · `M` · Area: `API`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java).
- [ ] `API-008` **Authentication e authorization HTTP** — `TODO` · `P1` · `XL` · Area: `Security`
  - Depends on: `SEC-002`, `SEC-003`.
- [x] `API-009` **Validação HTTP** — `DONE` para o recorte atual; `validate input` vale na fronteira HTTP inteira, não só no corpo: parâmetros de path/query/header carregam as constraints declaradas, `ConstraintViolationException` responde o status declarado, e o teste de controller gerado envia valores de requisição em texto puro e um valor realmente inválido · `P0` · `M` · Area: `API`
  - Evidence: [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).
- [ ] `API-010` **Pagination HTTP** — `TODO` · `P1` · `M` · Area: `API`
  - Depends on: `QUERY-005`.
- [ ] `API-011` **Multipart e file upload/download** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`, `BIND-002`.
- [ ] `API-012` **Versionamento e deprecation de API** — `TODO` · `P2` · `L` · Area: `API`
- [ ] `API-013` **OpenAPI generation** — `TODO` · `P1` · `L` · Area: `API`
  - Depends on: `CMD-011`, `QUERY-001`, `BIND-002`.
- [ ] `API-014` **OpenAPI import** — `RESEARCH` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `API-013`, `BROWN-001`.
- [x] `API-015` **Error envelope V0** — `DONE` para status/error/message · `P0` · `M` · Area: `API`
  - Evidence: [`JavaSpringErrorTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringErrorTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `API-016` **Conditional requests / ETag** — `RESEARCH` · `P3` · `L` · Area: `API`
- [ ] `API-017` **Async / HTTP 202** — `RESEARCH` · `P3` · `L` · Area: `API`
  - Depends on: `FLOW-021`, `EVENT-001`.

## EPIC — Persistence

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
- [ ] `PERSIST-006` **JDBC provider** — `TODO` · `P2` · `L` · Area: `Persistence`
- [ ] `PERSIST-007` **R2DBC provider** — `RESEARCH` · `P3` · `XL` · Area: `Persistence`
- [ ] `PERSIST-008` **Transactions declarativas** — `PARTIAL`; transação Spring é inferida para mutações CRUD · `P1` · `M` · Area: `Persistence`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [ ] `PERSIST-009` **Relationships persistentes** — `TODO` · `P1` · `XL` · Area: `Persistence`
  - Depends on: `DOM-012`.
- [ ] `PERSIST-010` **Constraints e índices gerais** — `PARTIAL`; PK, nullability e unique existem, índices configuráveis não · `P1` · `L` · Area: `Persistence`
  - Evidence: [`JavaSpringMigrationTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformer.java), [`PersistenceEmitterTest`](src/test/java/dev/harpia/target/javaspring/PersistenceEmitterTest.java).
- [ ] `PERSIST-011` **Optimistic locking** — `TODO` · `P2` · `M` · Area: `Persistence`
- [ ] `PERSIST-012` **Pessimistic locking** — `RESEARCH` · `P3` · `L` · Area: `Persistence`
- [ ] `PERSIST-013` **Soft delete** — `TODO` · `P2` · `L` · Area: `Persistence`
- [ ] `PERSIST-014` **Audit de entidade** — `TODO` · `P2` · `L` · Area: `Persistence`
- [x] `PERSIST-015` **Schema inicial determinístico** — `DONE` · `P0` · `L` · Area: `Persistence`
  - Evidence: [`SqlMigrationModel`](src/main/java/dev/harpia/target/javaspring/model/SqlMigrationModel.java), [`JavaSpringMigrationTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformerTest.java).
- [x] `PERSIST-016` **Flyway V1 init migration** — `DONE` · `P0` · `M` · Area: `Persistence`
  - Evidence: [`MigrationEmitter`](src/main/java/dev/harpia/target/javaspring/MigrationEmitter.java), [`migration.sql.mustache`](src/main/resources/targets/java-spring/templates/migration.sql.mustache).
- [ ] `PERSIST-017` **Liquibase provider** — `TODO` · `P3` · `L` · Area: `Persistence`
- [ ] `PERSIST-018` **MySQL provider** — `TODO` · `P3` · `L` · Area: `Persistence`
- [ ] `PERSIST-019` **MongoDB provider** — `TODO` · `P3` · `XL` · Area: `Persistence`
- [ ] `PERSIST-020` **Redis data provider** — `TODO` · `P3` · `L` · Area: `Persistence`
- [ ] `PERSIST-021` **Cassandra/Couchbase providers** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
- [ ] `PERSIST-022` **Neo4j provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
- [ ] `PERSIST-023` **Elasticsearch/OpenSearch provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`

## EPIC — Database Evolution

- [ ] `DBEV-001` **Schema snapshot versionado** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `PERSIST-015`, `CORE-020`.
- [ ] `DBEV-002` **Schema diff e migration planning** — `TODO` · `P2` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-001`.
- [ ] `DBEV-003` **Rename detection** — `RESEARCH` · `P3` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`, `CORE-016`.
- [ ] `DBEV-004` **Destructive/breaking change detection** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`.
- [ ] `DBEV-005` **Nullable transition e default backfill** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `DBEV-002`, `DBEV-004`.
- [ ] `DBEV-006` **Expand/contract e zero-downtime analysis** — `RESEARCH` · `P3` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-004`, `DBEV-005`.
- [ ] `DBEV-007` **Online migration provider** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `DBEV-006`.

## EPIC — Binding System

Regra alvo: **Spec = what software does; Binding = how software connects; Config = target/provider/environment.**

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
- [ ] `BIND-006` **HTTP binding: base URL, method/path, path/query/header/body/response mapping** — `PARTIAL`; base URL, method/path e mappings explícitos `path`/`query`/`header`/`body` mais `output: body`/`none` atravessam parser, resolver e controller gerado, provados por compilação real; falta content negotiation (`BIND-007`) e mapping de response além do corpo inteiro · `P0` · `XL` · Area: `Integration`
  - Evidence: [`HttpBinding`](src/main/java/dev/harpia/model/HttpBinding.java), [`BindingParser`](src/main/java/dev/harpia/binding/BindingParser.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`ExternalHttpBindingTest`](src/test/java/dev/harpia/binding/ExternalHttpBindingTest.java).
- [ ] `BIND-007` **HTTP status/content-type/accept mapping** — `PARTIAL`; status é mapeado, media types não · `P1` · `M` · Area: `Integration`
  - Evidence: [`OutputParser`](src/main/java/dev/harpia/parse/OutputParser.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java).
- [ ] `BIND-008` **HTTP auth: bearer, API key e OAuth** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `SEC-002`, `SEC-005`.
- [ ] `BIND-009` **HTTP mTLS** — `RESEARCH` · `P4` · `L` · Area: `Security`
- [ ] `BIND-010` **HTTP timeout/retry/proxy/multipart/response validation** — `TODO` · `P2` · `XL` · Area: `Integration`
  - Depends on: `RELY-001`, `RELY-002`, `API-011`.
- [ ] `BIND-011` **Messaging binding: event/topic/queue/routing/partition/serialization/headers** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-001`, `MSG-001`.
- [ ] `BIND-012` **Messaging retry e DLQ binding** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `BIND-011`, `RELY-002`.
- [ ] `BIND-013` **Persistence binding: capability/provider/datasource/schema** — `PARTIAL`; provider PostgreSQL e config estão no `harpia.yaml`, sem binding próprio · `P1` · `L` · Area: `Persistence`
  - Evidence: [`CapabilityResolver`](src/main/java/dev/harpia/capability/CapabilityResolver.java), [`HarpiaConfig`](src/main/java/dev/harpia/config/HarpiaConfig.java).

## EPIC — Integrations

- [ ] `INTEG-001` **Integration e Operation como portas tipadas** — `TODO` · `P1` · `XL` · Area: `Integration`
  - Depends on: `CORE-009`, `BIND-002`.
- [ ] `INTEG-002` **Typed Input/Output/Errors de integração** — `TODO` · `P1` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `CMD-004`.
- [ ] `INTEG-003` **Flow call integration** — `TODO` · `P1` · `M` · Area: `Integration`
  - Depends on: `INTEG-001`, `FLOW-014`.
- [ ] `INTEG-004` **HTTP/REST client provider** — `TODO` · `P1` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `BIND-006`.
- [ ] `INTEG-005` **SOAP provider** — `TODO` · `P3` · `XL` · Area: `Integration`
  - Depends on: `INTEG-001`.
- [ ] `INTEG-006` **gRPC provider** — `RESEARCH` · `P4` · `XL` · Area: `Integration`
- [ ] `INTEG-007` **Inbound/outbound Webhook** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `API-001`, `EVENT-001`.
- [ ] `INTEG-008` **Custom integration provider** — `TODO` · `P2` · `M` · Area: `Extensibility`
  - Depends on: `INTEG-001`, `CUSTOM-001`.
- [ ] `INTEG-009` **Timeout/retry/backoff/circuit breaker/fallback/rate limit** — `TODO` · `P2` · `XL` · Area: `Integration`
  - Depends on: `INTEG-001`, `RELY-001`–`RELY-006`.
- [ ] `INTEG-010` **Authentication e response validation de integração** — `TODO` · `P1` · `L` · Area: `Integration`
  - Depends on: `INTEG-001`, `TYPE-019`, `BIND-008`.

## EPIC — Events

- [ ] `EVENT-001` **Event e payload tipado** — `TODO`; seção Events é rejeitada em V0 · `P1` · `L` · Area: `Messaging`
  - Depends on: `CORE-009`, `CORE-018`.
- [ ] `EVENT-002` **Versionamento e compatibilidade de Event** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `INTEL-006`.
- [ ] `EVENT-003` **Emit Event** — `TODO` · `P1` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `FLOW-015`.
- [ ] `EVENT-004` **Handler / On Event** — `TODO` · `P1` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `CMD-001`.
- [ ] `EVENT-005` **Local events provider** — `TODO` · `P1` · `L` · Area: `Messaging`
  - Depends on: `EVENT-001`, `EVENT-004`.
- [ ] `EVENT-006` **Inbound/outbound events** — `TODO` · `P2` · `M` · Area: `Messaging`
  - Depends on: `EVENT-001`, `BIND-011`.
- [ ] `EVENT-007` **Contratos de Event gerados** — `TODO` · `P1` · `M` · Area: `Java/Spring Target`
  - Depends on: `EVENT-001`.

## EPIC — Messaging

- [ ] `MSG-001` **Capability e modelo lógico de messaging** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-001`, `BIND-011`.
- [ ] `MSG-002` **Producer/consumer, topic/queue e consumer group** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`.
- [ ] `MSG-003` **Routing, partition key, ordering, headers e correlation ID** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `EVENT-002`.
- [ ] `MSG-004` **Acknowledgement, retry e DLQ** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-012`.
- [ ] `MSG-005` **Delivery at-least-once, deduplication e replay** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `MSG-004`, `IDEMP-003`, `DIST-006`.
- [ ] `MSG-006` **Message versioning** — `TODO` · `P2` · `M` · Area: `Messaging`
  - Depends on: `EVENT-002`.
- [ ] `MSG-007` **Kafka provider** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-011`, `IDEMP-003`.
- [ ] `MSG-008` **RabbitMQ provider** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `BIND-011`.
- [ ] `MSG-009` **JMS/ActiveMQ/Artemis providers** — `TODO` · `P3` · `XL` · Area: `Messaging`
  - Depends on: `MSG-001`.
- [ ] `MSG-010` **Pulsar provider** — `RESEARCH` · `P4` · `XL` · Area: `Messaging`
- [ ] `MSG-011` **SQS provider** — `RESEARCH` · `P4` · `L` · Area: `Messaging`

## EPIC — Distributed Consistency

- [ ] `DIST-001` **Transactional Outbox** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `EVENT-003`, `PERSIST-008`, `PERSIST-015`.
- [ ] `DIST-002` **Inbox e idempotent consumer** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `MSG-001`, `IDEMP-003`.
- [ ] `DIST-003` **After-commit event** — `TODO` · `P2` · `M` · Area: `Messaging`
  - Depends on: `EVENT-005`, `PERSIST-008`.
- [ ] `DIST-004` **Eventual consistency** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `DIST-001`, `DIST-002`.
- [ ] `DIST-005` **Optimistic concurrency e locks** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `PERSIST-011`.
- [ ] `DIST-006` **Event replay** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `EVENT-002`, `MSG-005`.
- [ ] `DIST-007` **Distributed locks** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `RELY-007`.
- [ ] `DIST-008` **Consistência transaction/event** — `TODO` · `P2` · `XL` · Area: `Messaging`
  - Depends on: `DIST-001`, `DIST-003`.

## EPIC — Idempotency

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

## EPIC — Security

- [x] `SEC-001` **Access `public`** — `DONE` · `P0` · `XS` · Area: `Security`
  - Evidence: [`AccessParser`](src/main/java/dev/harpia/parse/AccessParser.java), [`AccessRule`](src/main/java/dev/harpia/model/AccessRule.java).
- [ ] `SEC-002` **Access `authenticated`** — `TODO`; rejeitado explicitamente em V0 · `P1` · `L` · Area: `Security`
- [ ] `SEC-003` **Role e scope** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `SEC-002`, `RULE-003`.
- [ ] `SEC-004` **Ownership/object-level/field-level authorization** — `TODO` · `P2` · `XL` · Area: `Security`
  - Depends on: `SEC-003`, `DOM-016`.
- [ ] `SEC-005` **JWT** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `SEC-002`, `BIND-008`.
- [ ] `SEC-006` **OAuth2/OIDC Resource Server** — `TODO` · `P2` · `XL` · Area: `Security`
  - Depends on: `SEC-005`.
- [ ] `SEC-007` **Spring Security provider** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `SEC-002`, `SEC-003`.
- [ ] `SEC-008` **LDAP provider** — `RESEARCH` · `P4` · `L` · Area: `Security`
- [ ] `SEC-009` **Secret handling** — `PARTIAL`; datasource usa referências de environment, sem tipo Secret/redaction geral · `P1` · `L` · Area: `Security`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache).
- [ ] `SEC-010` **Sensitive fields, masking e redacted logging** — `TODO` · `P1` · `L` · Area: `Security`
  - Depends on: `DOM-010`, `OBS-001`.
- [ ] `SEC-011` **Encrypted fields** — `TODO` · `P2` · `XL` · Area: `Security`
  - Depends on: `TYPE-019`, `SEC-010`.
- [ ] `SEC-012` **Audit de acesso** — `TODO` · `P2` · `L` · Area: `Security`
  - Depends on: `SEC-003`, `OBS-001`.
- [ ] `SEC-013` **PII semantics/data governance** — `RESEARCH` · `P4` · `XL` · Area: `Security`
  - Depends on: `SEC-010`, `SEC-012`.

## EPIC — Reliability

- [ ] `RELY-001` **Timeout e failure mapping** — `TODO` · `P1` · `M` · Area: `Runtime`
  - Depends on: `INTEG-001`, `CMD-004`.
- [ ] `RELY-002` **Retry, conditions e backoff** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `RELY-001`, `IDEMP-001`.
- [ ] `RELY-003` **Circuit breaker e fallback** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `RELY-001`, `RELY-002`.
- [ ] `RELY-004` **Bulkhead e concurrency limits** — `TODO` · `P3` · `L` · Area: `Runtime`
- [ ] `RELY-005` **Rate limiting** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `SEC-002`, `CACHE-001`.
- [ ] `RELY-006` **Política de idempotência antes de retry inseguro** — `TODO` · `P1` · `M` · Area: `Runtime`
  - Depends on: `RELY-002`, `IDEMP-001`.
- [ ] `RELY-007` **Distributed lock** — `TODO` · `P3` · `L` · Area: `Runtime`
  - Depends on: `PERSIST-001`.

## EPIC — Email

- [ ] `EMAIL-001` **Email como operação/capability** — `TODO`; a seção é rejeitada em V0 · `P1` · `L` · Area: `Integration`
  - Depends on: `CORE-009`, `TYPE-008`.
- [ ] `EMAIL-002` **Subject, recipients, template e inputs tipados** — `TODO` · `P1` · `L` · Area: `Integration`
  - Depends on: `EMAIL-001`.
- [ ] `EMAIL-003` **CC/BCC e attachments** — `TODO` · `P3` · `M` · Area: `Integration`
  - Depends on: `EMAIL-002`, `FILE-001`.
- [ ] `EMAIL-004` **SMTP provider** — `TODO` · `P1` · `M` · Area: `Integration`
  - Depends on: `EMAIL-001`, `TYPE-019`.
- [ ] `EMAIL-005` **SES provider** — `RESEARCH` · `P3` · `L` · Area: `Integration`
  - Depends on: `EMAIL-001`.

## EPIC — Cache

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

## EPIC — Scheduling

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

## EPIC — Jobs / Batch

- [ ] `BATCH-001` **Job e Step** — `TODO` · `P2` · `XL` · Area: `Runtime`
  - Depends on: `CMD-001`, `TEST-001`.
- [ ] `BATCH-002` **Source/Processor/Writer** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `BATCH-001`, `INTEG-001`.
- [ ] `BATCH-003` **Chunk e batch size** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `BATCH-002`.
- [ ] `BATCH-004` **Retry/skip/restart/checkpoint** — `TODO` · `P2` · `XL` · Area: `Runtime`
  - Depends on: `BATCH-001`, `IDEMP-001`, `RELY-002`.
- [ ] `BATCH-005` **Spring Batch provider** — `TODO` · `P2` · `XL` · Area: `Java/Spring Target`
  - Depends on: `BATCH-001`–`BATCH-004`.

## EPIC — State Machine

- [ ] `STATE-001` **States e transitions** — `TODO` · `P2` · `L` · Area: `Runtime`
  - Depends on: `TYPE-020`, `CMD-001`, `RULE-001`.
- [ ] `STATE-002` **Transition Command e validation** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-001`.
- [ ] `STATE-003` **Diagnostics de transição inválida** — `TODO` · `P2` · `M` · Area: `Runtime`
  - Depends on: `STATE-002`, `CORE-017`.
- [ ] `STATE-004` **Testes gerados e transition events** — `TODO` · `P2` · `L` · Area: `Testing`
  - Depends on: `STATE-002`, `EVENT-001`, `TEST-001`.

## EPIC — Workflow / Saga

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

## EPIC — File / Storage

- [ ] `FILE-001` **File, upload e download** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `TYPE-015`, `BIND-002`.
- [ ] `FILE-002` **Validação de tamanho/content type/formato/checksum** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`, `RULE-001`.
- [ ] `FILE-003` **Storage capability e filesystem provider** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-001`.
- [ ] `FILE-004` **S3 provider e signed URLs** — `TODO` · `P2` · `L` · Area: `Integration`
  - Depends on: `FILE-003`.
- [ ] `FILE-005` **Azure Blob provider** — `RESEARCH` · `P4` · `L` · Area: `Integration`
  - Depends on: `FILE-003`.
- [ ] `FILE-006` **Retention** — `TODO` · `P3` · `M` · Area: `Integration`
  - Depends on: `FILE-003`, `SCHED-001`.
- [ ] `FILE-007` **Virus scanning** — `CUSTOM` · `P4` · `L` · Area: `Extensibility`
  - Path: custom Integration/provider after `CUSTOM-001`.

## EPIC — Multi-Tenancy

- [ ] `TENANT-001` **TenantScoped e tenant context** — `RESEARCH` · `P3` · `XL` · Area: `Runtime`
  - Depends on: `CORE-009`, `SEC-002`.
- [ ] `TENANT-002` **Tenant filtering e authorization** — `RESEARCH` · `P3` · `XL` · Area: `Security`
  - Depends on: `TENANT-001`, `SEC-004`.
- [ ] `TENANT-003` **Column/schema/database strategies e isolation** — `RESEARCH` · `P4` · `XL` · Area: `Persistence`
  - Depends on: `TENANT-001`, `PERSIST-001`, `DBEV-001`.

## EPIC — Observability

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
- [ ] `OBS-007` **Datadog/New Relic/OTLP exporters** — `RESEARCH` · `P4` · `L` · Area: `Operations`
  - Depends on: `OBS-005`.
- [ ] `OBS-008` **Instrumentação automática de Command/Query/Integration/Event/retry/Job/Schedule/DB** — `TODO` · `P2` · `XL` · Area: `Operations`
  - Depends on: `OBS-002`, `OBS-003` e os modelos de operação correspondentes.

## EPIC — Runtime Operations / Actuator

- [ ] `OPS-001` **Health/readiness/liveness e dependency health** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `OBS-002`, `CONFIG-001`.
- [ ] `OPS-002` **Graceful shutdown e consumer draining** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `MSG-001`.
- [ ] `OPS-003` **Actuator metrics/Prometheus/info/loggers/management endpoints** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
  - Depends on: `OBS-005`, `SEC-003`.
- [ ] `OPS-004` **Runtime diagnostics** — `TODO` · `P2` · `L` · Area: `Operations`
  - Depends on: `CORE-017`, `OBS-001`.

## EPIC — Configuration

- [ ] `CONFIG-001` **Externalized YAML configuration** — `PARTIAL`; gera `application.yaml` para datasource, sem modelo geral de propriedades · `P1` · `L` · Area: `Operations`
  - Evidence: [`AppConfigEmitter`](src/main/java/dev/harpia/target/javaspring/AppConfigEmitter.java), [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache).
- [ ] `CONFIG-002` **Environment variables e secrets** — `PARTIAL`; placeholders de datasource existem, sem Secret/redaction · `P1` · `M` · Area: `Security`
  - Evidence: [`JavaSpringDependencyResolver`](src/main/java/dev/harpia/target/javaspring/JavaSpringDependencyResolver.java), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).
- [ ] `CONFIG-003` **Profiles/environments dev/test/staging/production** — `TODO` · `P2` · `L` · Area: `Operations`
- [ ] `CONFIG-004` **Property binding tipado** — `TODO` · `P2` · `M` · Area: `Operations`
- [x] `CONFIG-005` **Validação estrutural de `harpia.yaml`** — `DONE` · `P0` · `L` · Area: `Language Core`
  - Evidence: [`ConfigValidator`](src/main/java/dev/harpia/config/ConfigValidator.java), [`ConfigLoaderTest`](src/test/java/dev/harpia/config/ConfigLoaderTest.java).
- [ ] `CONFIG-006` **Provider/config validation geral** — `PARTIAL`; PostgreSQL, paths e opções Java/Spring atuais são validados · `P1` · `L` · Area: `Operations`
  - Evidence: [`ProviderValidation`](src/main/java/dev/harpia/application/ProviderValidation.java), [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java).

## EPIC — Testing

- [ ] `TEST-001` **Scenario / Given / When / Then** — `TODO` · `P1` · `XL` · Area: `Testing`
  - Depends on: `CORE-009`, `CMD-001`, `QUERY-001`, `RULE-001`.
- [x] `TEST-002` **Testes unitários de service gerados para CRUD V0** — `DONE` · `P0` · `L` · Area: `Testing`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).
- [x] `TEST-003` **Testes REST/controller gerados para status e erros V0** — `DONE` · `P0` · `L` · Area: `Testing`
  - Evidence: [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java), [`GeneratedTestTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/GeneratedTestTransformerTest.java).
- [ ] `TEST-004` **Testes de persistência/integration gerados** — `TODO` · `P1` · `L` · Area: `Testing`
  - Depends on: `TEST-001`, `PERSIST-005`.
- [ ] `TEST-005` **Testes de security/event/integration contract** — `TODO` · `P2` · `XL` · Area: `Testing`
  - Depends on: `SEC-007`, `EVENT-005`, `INTEG-001`.
- [x] `TEST-006` **Testes de Logic e equivalência Java** — `DONE` para L1/L2 · `P1` · `L` · Area: `Testing`
  - Evidence: [`LogicAnalyzerTest`](src/test/java/dev/harpia/logic/LogicAnalyzerTest.java), [`GeneratedLogicCompilesTest`](src/test/java/dev/harpia/target/javaspring/GeneratedLogicCompilesTest.java).
- [ ] `TEST-007` **Testes de Formula/Decision** — `TODO` · `P2` · `M` · Area: `Testing`
  - Depends on: `LOGIC-001`, `LOGIC-004`.
- [x] `TEST-008` **Mocks derivados da especificação V0** — `DONE` para service/controller CRUD · `P0` · `M` · Area: `Testing`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java).
- [ ] `TEST-009` **Contract test suite de providers/targets** — `PARTIAL`; existem testes do provider atual e golden de um target, sem suite SPI reutilizável · `P1` · `L` · Area: `Testing`
  - Evidence: [`ProviderContributionsTest`](src/test/java/dev/harpia/target/javaspring/ProviderContributionsTest.java), [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java).
- [ ] `TEST-010` **Testcontainers e Service Connections** — `TODO` · `P2` · `L` · Area: `Testing`
- [ ] `TEST-011` **Fixtures geradas de Scenario** — `TODO` · `P2` · `L` · Area: `Testing`
  - Depends on: `TEST-001`.
- [x] `TEST-012` **Golden tests versionados** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`customer golden`](src/test/resources/fixtures/targets/java-spring/customer/pom.xml).
- [x] `TEST-013` **Determinism tests** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).
- [x] `TEST-014` **Build/test Maven offline do projeto gerado** — `DONE` · `P0` · `M` · Area: `Testing`
  - Evidence: [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

## EPIC — Custom Java Escape Hatch

- [x] `CUSTOM-001` **Custom implementation como feature oficial** — `DONE` na V1 para Logic; `### Implementation` com `custom <Contrato>` substitui o corpo, o compilador mantém assinatura e type checking e gera a interface, sem gerar implementação. Scenario sobre uma Logic custom é recusado (`HRP2124`), porque Harpia teria de executar código que não é dela · `P1` · `L` · Area: `Extensibility`
  - Evidence: [`LogicDeclarationParser`](src/main/java/dev/harpia/parse/LogicDeclarationParser.java), [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`ScenarioAnalyzer`](src/main/java/dev/harpia/validate/ScenarioAnalyzer.java), [`CustomImplementationTest`](src/test/java/dev/harpia/logic/CustomImplementationTest.java).
- [ ] `CUSTOM-002` **Interface/contract gerado e dependency injection** — `PARTIAL`; a interface é gerada com a assinatura tipada; falta injetar o bean no chamador, o que depende de `FLOW-014` (`call` Logic a partir de Flow) · `P1` · `L` · Area: `Extensibility`
  - Depends on: `CUSTOM-001` (pronto), `FLOW-014`.
- [ ] `CUSTOM-003` **Diretório custom protegido e preservado** — `PARTIAL`; a garantia de propriedade está provada (EP21): um arquivo que Harpia não escreveu sobrevive byte a byte a `--clean --force`, nunca entra no manifesto, e `--clean` sem `--force` recusa em vez de decidir pelo usuário. Falta **escolher o layout**: onde a implementação custom vive de modo a compilar junto com a interface gerada — ver nota abaixo · `P1` · `M` · Area: `Ownership`
  - Evidence: [`CustomCodeOwnershipTest`](src/test/java/dev/harpia/emit/CustomCodeOwnershipTest.java), [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java).
  - Aberto: `docs/roadmap.md` prevê `custom/` como irmão de `generated/`, o que exigiria um source root extra no pom. `build-helper-maven-plugin` não resolve offline, então essa escolha quebraria o gate `mvn -o test` do projeto gerado. A alternativa é a implementação viver dentro da árvore de fontes gerada (compila e é component-scanned sem plugin nenhum), ao custo de `rm -rf generated` destruir o trabalho do usuário. Decisão do dono do produto.
- [ ] `CUSTOM-004` **Custom Maven dependencies/proprietary SDKs** — `TODO` · `P2` · `M` · Area: `Extensibility`
  - Depends on: `CUSTOM-001`, `TARGET-016`.
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

## EPIC — Multi-Target Architecture

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
- [ ] `TARGET-013` **Conformance suite reutilizável de target** — `PARTIAL`; golden/compile/build cobrem Java/Spring, sem contrato genérico plugável · `P1` · `L` · Area: `Multi-target`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).
- [x] `TARGET-014` **Descoberta de targets e stable DTOs** — `DONE` via CLI/core · `P0` · `M` · Area: `Multi-target`
  - Evidence: [`TargetInfo`](src/main/java/dev/harpia/target/TargetInfo.java), [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java).
- [ ] `TARGET-015` **Target SDK e community target loading** — `TODO` · `P2` · `XL` · Area: `Extensibility`
  - Depends on: `TARGET-003`, `TARGET-013`, `CORE-018`.
- [ ] `TARGET-016` **Provider SPI e community providers** — `TODO` · `P2` · `XL` · Area: `Extensibility`
  - Depends on: segundo provider real, `BIND-013`.
- [ ] `TARGET-017` **Plugin architecture** — `RESEARCH` · `P4` · `XL` · Area: `Extensibility`
  - Depends on: `TARGET-015`, `TARGET-016`.
- [x] `TARGET-018` **Capability catalog V0** — `DONE` para HTTP/persistence/events/custom como IDs lógicos · `P1` · `M` · Area: `Multi-target`
  - Evidence: [`Capability`](src/main/java/dev/harpia/capability/Capability.java), [`CapabilityRequirementSet`](src/main/java/dev/harpia/capability/CapabilityRequirementSet.java).

## EPIC — Supported Targets

- [x] `TGT-JAVA` **`java-spring`** — `DONE` como único target executável, no recorte V0 · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`JavaSpringTargetTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTargetTest.java).
- [ ] `TGT-KOTLIN` **`kotlin-spring`** — `NOT_SUPPORTED` · `P3` · `XL` · Area: `Multi-target`
- [ ] `TGT-CSHARP` **`csharp-aspnet`** — `NOT_SUPPORTED` · `P3` · `XL` · Area: `Multi-target`
- [ ] `TGT-TS` **`typescript-nestjs`** — `NOT_SUPPORTED` · `P3` · `XL` · Area: `Multi-target`
- [ ] `TGT-PYTHON` **`python-fastapi`** — `NOT_SUPPORTED` · `P3` · `XL` · Area: `Multi-target`
- [ ] `TGT-GO` **`go`** — `NOT_SUPPORTED` · `P3` · `XL` · Area: `Multi-target`
- [ ] `TGT-CLOJURE` **`clojure-jvm`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`
- [ ] `TGT-PHP` **`php-laravel`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`
- [ ] `TGT-RUST` **`rust`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`
- [ ] `TGT-ELIXIR` **`elixir-phoenix`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`
- [ ] `TGT-RUBY` **`ruby-rails`** — `NOT_SUPPORTED` · `P4` · `XL` · Area: `Multi-target`

Nenhum target `NOT_SUPPORTED` possui generator falso ou capabilities fictícias.

## EPIC — Java/Spring Transformer and Target Model

- [x] `JAVA-001` **JavaSpringTarget e project transformer** — `DONE` · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTarget`](src/main/java/dev/harpia/target/javaspring/JavaSpringTarget.java), [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java).
- [x] `JAVA-002` **Entity transformer** — `DONE` para Entity V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringEntityTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformer.java), [`JavaSpringEntityTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformerTest.java).
- [ ] `JAVA-003` **ValueObject e Enum transformers** — `TODO` · `P1` · `L` · Area: `Java/Spring Target`
  - Depends on: `TYPE-020`, `TYPE-024`.
- [ ] `JAVA-004` **Command/Query transformers explícitos** — `PARTIAL`; a natureza declarada (`Command`/`Query`/inferida) atravessa a Application IR e chega ao target, que a documenta no serviço gerado; falta especializar a geração além da forma CRUD · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`ApplicationOperation`](src/main/java/dev/harpia/application/ApplicationOperation.java), [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`JavaSpringControllerTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTransformer.java), [`UnboundOperationTest`](src/test/java/dev/harpia/target/javaspring/UnboundOperationTest.java), [`OperationNatureTest`](src/test/java/dev/harpia/validate/OperationNatureTest.java).
- [x] `JAVA-005` **Logic transformer** — `DONE` para L1/L2 · `P1` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringLogicTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringLogicTransformer.java), [`LogicEmitterTest`](src/test/java/dev/harpia/target/javaspring/LogicEmitterTest.java).
- [x] `JAVA-006` **Flow CRUD transformer** — `DONE` para oito comandos V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringServiceTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java), [`ApplicationLayerTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/ApplicationLayerTransformerTest.java).
- [x] `JAVA-007` **Persistence e validation transformers/mappers** — `DONE` para V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`SpringPersistenceMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringPersistenceMapper.java), [`SpringValidationMapper`](src/main/java/dev/harpia/target/javaspring/mapping/SpringValidationMapper.java).
- [ ] `JAVA-008` **Security/event/integration/email/scheduling transformers** — `TODO` · `P2` · `XL` · Area: `Java/Spring Target`
  - Depends on: modelos semânticos correspondentes.
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

## EPIC — Java/Spring Templates

- [x] `TPL-001` **Target template loader e set default versionado** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTemplates`](src/main/java/dev/harpia/target/javaspring/JavaSpringTemplates.java), [`TargetDescriptor`](src/main/java/dev/harpia/target/TargetDescriptor.java).
- [x] `TPL-002` **pom.xml template** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`EmitterPipelineTest`](src/test/java/dev/harpia/target/javaspring/EmitterPipelineTest.java).
- [x] `TPL-003` **application.yaml template** — `DONE` · `P0` · `S` · Area: `Java/Spring Target`
  - Evidence: [`application.yaml.mustache`](src/main/resources/targets/java-spring/templates/application.yaml.mustache), [`AppConfigEmitter`](src/main/java/dev/harpia/target/javaspring/AppConfigEmitter.java).
- [ ] `TPL-004` **application-test.yml** — `TODO` · `P1` · `S` · Area: `Java/Spring Target`
- [x] `TPL-005` **Flyway migration template** — `DONE` · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`migration.sql.mustache`](src/main/resources/targets/java-spring/templates/migration.sql.mustache), [`JavaSpringMigrationTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringMigrationTransformerTest.java).
- [ ] `TPL-006` **Dockerfile/config adicionais** — `TODO` · `P2` · `M` · Area: `Java/Spring Target`
- [x] `TPL-007` **Bootstrap, repository, controller, error handler e testes Java** — `DONE` via Target Model/renderer, deliberadamente não via templates semânticos · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringProjectTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringProjectTransformer.java), [`JavaSpringTemplateRulesTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTemplateRulesTest.java).
- [x] `TPL-008` **Regra: template não interpreta required/unique/Rule/Flow/dependencies/transações** — `DONE` como gate arquitetural · `P0` · `M` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringTemplateRulesTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringTemplateRulesTest.java), [`JavaSpringEntityTransformerTest`](src/test/java/dev/harpia/target/javaspring/transformer/JavaSpringEntityTransformerTest.java).

## EPIC — Java/Spring Target Delivery

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
- [ ] `SPRING-006` **Spring Security/JWT** — `TODO` · `P1` · `XL` · Area: `Java/Spring Target`
  - Depends on: `SEC-005`, `SEC-007`.
- [ ] `SPRING-007` **Spring Mail** — `TODO` · `P1` · `L` · Area: `Java/Spring Target`
  - Depends on: `EMAIL-004`.
- [ ] `SPRING-008` **Local Spring Events** — `TODO` · `P1` · `L` · Area: `Java/Spring Target`
  - Depends on: `EVENT-005`.
- [x] `SPRING-009` **Spring/JUnit/Mockito test baseline gerada** — `DONE` para CRUD V0 · `P0` · `L` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringServiceTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTestTransformer.java), [`JavaSpringControllerTestTransformer`](src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringControllerTestTransformer.java).
- [ ] `SPRING-010` **Testcontainers** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
- [x] `SPRING-011` **Projeto convencional sem Harpia runtime** — `DONE` para o recorte gerado · `P0` · `M` · Area: `Ownership`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java).
- [ ] `SPRING-012` **Executable JAR verificado** — `PARTIAL`; plugin é emitido e `mvn test` passa, mas o gate `mvn package` do projeto gerado não é automatizado · `P1` · `S` · Area: `Java/Spring Target`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).

## EPIC — CLI / Developer Experience

- [ ] `CLI-001` **`harpia init`** — `TODO` · `P1` · `M` · Area: `CLI/DX`
- [x] `CLI-002` **`harpia validate`** — `DONE` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`ValidateCommand`](src/main/java/dev/harpia/cli/ValidateCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).
- [x] `CLI-003` **`harpia build`** — `DONE` · `P0` · `L` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).
- [ ] `CLI-004` **`harpia test`** — `TODO` · `P1` · `M` · Area: `CLI/DX`
- [ ] `CLI-005` **`harpia clean`** — `PARTIAL`; existe `build --clean [--force]`, não comando próprio · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).
- [ ] `CLI-006` **`harpia fmt`** — `TODO` · `P1` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-024`.
- [ ] `CLI-007` **`harpia lint`** — `TODO` · `P2` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-025`.
- [x] `CLI-008` **`harpia inspect`** — `DONE` para `ast`, `symbols`, `business-ir` e `application-ir` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`InspectCommand`](src/main/java/dev/harpia/cli/InspectCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).
- [x] `CLI-009` **`harpia version`** — `DONE` · `P1` · `XS` · Area: `CLI/DX`
  - Evidence: [`VersionCommand`](src/main/java/dev/harpia/cli/VersionCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).
- [ ] `CLI-010` **`harpia capabilities`** — `PARTIAL`; `harpia targets <id>` exibe capabilities, sem comando global próprio · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java), [`TargetCatalogTest`](src/test/java/dev/harpia/target/TargetCatalogTest.java).
- [x] `CLI-011` **`harpia targets`** — `DONE` · `P0` · `M` · Area: `CLI/DX`
  - Evidence: [`TargetsCommand`](src/main/java/dev/harpia/cli/TargetsCommand.java), [`CliExitCodeTest`](src/test/java/dev/harpia/cli/CliExitCodeTest.java).
- [ ] `CLI-012` **`harpia diff` / `impact` / `explain` / `why`** — `TODO` · `P2` · `XL` · Area: `CLI/DX`
  - Depends on: `INTEL-001`–`INTEL-004`.
- [ ] `CLI-013` **`harpia migrate diff`** — `TODO` · `P2` · `L` · Area: `CLI/DX`
  - Depends on: `DBEV-002`.
- [ ] `CLI-014` **`harpia import` / `reverse`** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-001`.
- [ ] `CLI-015` **`harpia mcp`** — `TODO` · `P1` · `S` · Area: `MCP`
  - Depends on: `MCP-001`.

## EPIC — Semantic Intelligence

- [ ] `INTEL-001` **Semantic diff** — `TODO` · `P2` · `XL` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-016`.
- [ ] `INTEL-002` **Impact analysis** — `TODO` · `P2` · `XL` · Area: `CLI/DX`
  - Depends on: `CORE-009`, `INTEL-001`.
- [ ] `INTEL-003` **Explain** — `TODO` · `P1` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-026`, `CORE-017`.
- [ ] `INTEL-004` **Why / provenance de inferências** — `TODO` · `P2` · `L` · Area: `CLI/DX`
  - Depends on: `INTEL-003`, `CORE-016`.
- [ ] `INTEL-005` **Breaking API analysis** — `TODO` · `P2` · `L` · Area: `API`
  - Depends on: `API-013`, `INTEL-001`.
- [ ] `INTEL-006` **Breaking Event analysis** — `TODO` · `P2` · `L` · Area: `Messaging`
  - Depends on: `EVENT-002`, `INTEL-001`.
- [ ] `INTEL-007` **Breaking schema analysis** — `TODO` · `P2` · `L` · Area: `Persistence`
  - Depends on: `DBEV-004`, `INTEL-001`.
- [ ] `INTEL-008` **Architecture rules e module dependency analysis** — `PARTIAL`; testes protegem fronteiras internas do compiler, sem DSL/regra de projeto gerado · `P2` · `XL` · Area: `CLI/DX`
  - Evidence: [`ArchitectureBoundaryTest`](src/test/java/dev/harpia/ArchitectureBoundaryTest.java), [`ApplicationModelBuilderTest`](src/test/java/dev/harpia/application/ApplicationModelBuilderTest.java).
- [ ] `INTEL-009` **Semantic/project search** — `TODO` · `P2` · `L` · Area: `CLI/DX`
  - Depends on: `CORE-008`, `CORE-009`.

## EPIC — MCP Agent API

- [ ] `MCP-001` **MCP server** — `TODO`; não existe dependência, transporte ou adapter MCP no repositório · `P1` · `XL` · Area: `MCP`
  - Depends on: `CORE-008`, `CORE-026`, `CORE-027`.
- [ ] `MCP-002` **STDIO transport** — `TODO` · `P1` · `M` · Area: `MCP`
  - Depends on: `MCP-001`.
- [ ] `MCP-003` **HTTP transport** — `RESEARCH` · `P4` · `XL` · Area: `MCP`
  - Depends on: STDIO estável, threat model, authentication e rate limit.
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
- [ ] `MCP-014` **Semantic mutations add_entity/add_field/add_value_object/add_enum** — `TODO` · `P2` · `XL` · Area: `MCP`
  - Depends on: `CORE-024`, `CORE-016`, atomic writer.
- [ ] `MCP-015` **Semantic mutations add_command/add_query/add_rule/add_formula/add_decision/add_logic** — `TODO` · `P2` · `XL` · Area: `MCP`
  - Depends on: construções correspondentes, `MCP-014`.
- [ ] `MCP-016` **Semantic mutations add_invariant/add_event/add_email/add_integration/add_scenario** — `TODO` · `P3` · `XL` · Area: `MCP`
  - Depends on: construções correspondentes, `MCP-014`.
- [ ] `MCP-017` **`apply_spec_patch` versionado e semantic diff** — `TODO` · `P2` · `XL` · Area: `MCP`
  - Depends on: `MCP-014`, `INTEL-001`, `INTEL-002`.
- [ ] `MCP-018` **Structured outputs e progressive disclosure** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-001`, schemas versionados.
- [ ] `MCP-019` **Atomic mutations, dry-run, digest e rollback** — `TODO` · `P1` · `XL` · Area: `MCP`
  - Depends on: `CORE-016`, `CORE-024`, workspace lock.
- [ ] `MCP-020` **Workspace safety e proibição de shell arbitrário** — `TODO` no adapter MCP; primitives de path/output já existem no core · `P0` · `L` · Area: `MCP`
  - Depends on: `MCP-001`; reuse `CORE-027` e guards de [`SpecDiscovery`](src/main/java/dev/harpia/source/SpecDiscovery.java).
- [ ] `MCP-021` **MCP API version, schemas e handshake de versões** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `CORE-018`, `CORE-019`, `TARGET-002`.
- [ ] `MCP-022` **Prompts progressivos sobre tools/resources estáveis** — `TODO` · `P3` · `L` · Area: `MCP`
  - Depends on: `MCP-009`–`MCP-019`.
- [ ] `MCP-023` **Project locking e controle de concorrência** — `TODO` · `P1` · `L` · Area: `MCP`
  - Depends on: `MCP-013`, `MCP-019`.

## EPIC — Brownfield / Harpia Reverse

Princípio: **Reverse engineering may be probabilistic. Compilation remains deterministic.** O
objetivo é reconstrução semântica, não round-trip para Java idêntico.

- [ ] `BROWN-001` **`harpia reverse` / `harpia import code` pipeline** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: cobertura V1 estável, `CORE-008`, `CORE-009`, `BIND-002`, `CORE-022`.
- [ ] `BROWN-002` **Java AST e Spring annotation scanning** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-001`.
- [ ] `BROWN-003` **Controller/service/JPA/repository analysis** — `TODO` · `P3` · `XL` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `CMD-001`, `QUERY-001`, `PERSIST-005`.
- [ ] `BROWN-004` **Spring Security analysis** — `TODO` · `P4` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `SEC-007`.
- [ ] `BROWN-005` **Kafka/Rabbit analysis** — `TODO` · `P4` · `L` · Area: `Brownfield`
  - Depends on: `BROWN-002`, `MSG-007`, `MSG-008`.
- [ ] `BROWN-006` **RestClient/WebClient integration analysis** — `TODO` · `P4` · `L` · Area: `Brownfield`
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

## EPIC — Deterministic Compilation

Esta visão referencia itens canônicos para não duplicar implementação:

| Garantia | Item canônico | Estado |
|---|---|---|
| mesma spec/config/compiler/target produz os mesmos bytes | `CORE-020`, `CORE-021` | `DONE` |
| ordem de arquivos/imports/dependencies/métodos | `CORE-020`, `JAVA-010`, `JAVA-014` | `DONE` |
| sem timestamp/random | `CORE-021` | `DONE` |
| golden tests | `TEST-012` | `DONE` |
| output hashing exposto ao usuário | `DET-001` | `PARTIAL` |
| build fingerprint | `DET-002` | `TODO` |
| clean + rebuild equality em disco | `DET-003` | `DONE` |

- [ ] `DET-001` **Output hashing** — `PARTIAL`; testes calculam hash, o produto não o publica · `P1` · `S` · Area: `CLI/DX`
  - Evidence: [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java).
- [ ] `DET-002` **Build fingerprint versionado** — `TODO` · `P1` · `M` · Area: `CLI/DX`
  - Depends on: `CORE-018`, `TARGET-002`, `DET-001`.
- [x] `DET-003` **Clean + rebuild equality test em filesystem** — `DONE`; um diretório reconstruído com `--clean` sobre um build antigo é byte a byte idêntico a um build feito do zero, manifest incluído · `P0` · `S` · Area: `Testing`
  - Evidence: [`RebuildEqualityTest`](src/test/java/dev/harpia/RebuildEqualityTest.java), [`DeterminismTest`](src/test/java/dev/harpia/DeterminismTest.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).

## EPIC — Developer Ownership / Bootstrap Mode

- [x] `OWN-001` **Código gerado é fonte comum, sem runtime Harpia** — `DONE` no target atual · `P0` · `M` · Area: `Ownership`
  - Evidence: [`pom.xml.mustache`](src/main/resources/targets/java-spring/templates/pom.xml.mustache), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).
- [x] `OWN-002` **Projeto pode ser commitado, modificado e destacado do Harpia** — `DONE` como propriedade do bootstrap atual · `P0` · `XS` · Area: `Ownership`
  - Evidence: saída não referencia `dev.harpia`; ver [`customer golden`](src/test/resources/fixtures/targets/java-spring/customer/pom.xml).
- [x] `OWN-003` **Manifesto limita ownership do writer** — `DONE` · `P0` · `M` · Area: `Ownership`
  - Evidence: [`OutputWriter`](src/main/java/dev/harpia/emit/OutputWriter.java), [`OutputWriterTest`](src/test/java/dev/harpia/emit/OutputWriterTest.java).
- [ ] `OWN-004` **Managed Generation Mode explícito e opcional** — `RESEARCH` · `P4` · `XL` · Area: `Ownership`
  - Depends on: `CORE-016`, `INTEL-001`, estratégia de merge.

## EPIC — Greenfield Bootstrap

- [ ] `GREEN-001` **Project starter completo** — `PARTIAL`; `build` requer config/specs existentes, não há `init` · `P1` · `L` · Area: `CLI/DX`
  - Evidence: [`BuildCommand`](src/main/java/dev/harpia/cli/BuildCommand.java), [`README`](README.md).
- [x] `GREEN-002` **Baseline domain/API/persistence/config/test** — `DONE` para Customer CRUD V0 · `P0` · `XL` · Area: `Java/Spring Target`
  - Evidence: [`JavaSpringGoldenTest`](src/test/java/dev/harpia/target/javaspring/JavaSpringGoldenTest.java), [`GeneratedMavenProjectTest`](src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java).
- [ ] `GREEN-003` **Baseline integration/messaging/security** — `TODO` · `P2` · `XL` · Area: `Java/Spring Target`
  - Depends on: `INTEG-001`, `MSG-001`, `SEC-007`.
- [ ] `GREEN-004` **Docker baseline** — `TODO` · `P2` · `M` · Area: `Java/Spring Target`
  - Depends on: `SPRING-012`.
- [ ] `GREEN-005` **Observability baseline** — `TODO` · `P2` · `L` · Area: `Java/Spring Target`
  - Depends on: `OBS-005`, `OPS-001`.
- [x] `GREEN-006` **Developer ownership após geração** — `DONE` no modo bootstrap atual · `P0` · `XS` · Area: `Ownership`
  - Evidence: `OWN-001`–`OWN-003`.

## EPIC — Spring Boot Coverage

Esta é uma visão de cobertura; cada linha aponta para um item canônico e não duplica sua contagem.

| Área Spring | Feature | Estado real | Item |
|---|---|---|---|
| Web | Spring MVC / REST CRUD | `DONE` no recorte V0 | `SPRING-003` |
| Web | WebFlux | `RESEARCH` | `ADV-001` |
| Web | GraphQL | `TODO` | `ADV-002` |
| Web | WebSocket / SSE | `TODO` | `ADV-003` |
| Web | Static resources / server-side templates | `WONT_DO` como objetivo da DSL atual | `SCOPE-002` |
| Data | JPA / PostgreSQL / Flyway | `DONE` no recorte V0 | `SPRING-004` |
| Data | JDBC / R2DBC | `TODO` / `RESEARCH` | `PERSIST-006`, `PERSIST-007` |
| Data | MongoDB / Redis / Elasticsearch / Cassandra / Couchbase / Neo4j / LDAP | `TODO` / `RESEARCH` | `PERSIST-019`–`PERSIST-023`, `SEC-008` |
| Messaging | Spring local events | `TODO` | `SPRING-008` |
| Messaging | Kafka / RabbitMQ / JMS / ActiveMQ / Artemis / Pulsar | `TODO` / `RESEARCH` | `MSG-007`–`MSG-010` |
| Messaging | STOMP | `TODO` | `ADV-004` |
| Security | Spring Security / JWT / OAuth2 / OIDC / Resource Server | `TODO` | `SEC-005`–`SEC-007` |
| Integration | RestClient / WebClient / SOAP / Email | `TODO` | `INTEG-004`, `INTEG-005`, `EMAIL-004`, `ADV-005` |
| Runtime | scheduling / Quartz / Batch | `TODO` / `RESEARCH` | `SCHED-004`, `SCHED-005`, `BATCH-005` |
| Runtime | transactions | `PARTIAL` | `PERSIST-008` |
| Runtime | JTA/XA | `RESEARCH` | `ADV-006` |
| Runtime | cache | `TODO` | `CACHE-005` |
| Observability | Actuator / Micrometer / OpenTelemetry / Prometheus | `TODO` | `OBS-005`, `OBS-006`, `OPS-003` |
| Testing | Spring Test unit/controller baseline | `DONE` no recorte V0 | `SPRING-009` |
| Testing | test slices / Testcontainers / Service Connections | `TODO` | `TEST-004`, `TEST-010` |
| Packaging | Maven | `DONE` | `SPRING-002` |
| Packaging | executable JAR | `PARTIAL` | `SPRING-012` |
| Packaging | Docker / Buildpacks / AOT / GraalVM | `TODO` / `RESEARCH` | `GREEN-004`, `ADV-007`–`ADV-009` |

## EPIC — Spring Ecosystem

- [ ] `SPR-EC-001` **Spring Batch** — `TODO` · `P2` · `XL` · Area: `Java/Spring Target`
  - Depends on: `BATCH-001`; canonical provider: `BATCH-005`.
- [ ] `SPR-EC-002` **Spring Security** — `TODO` · `P1` · `XL` · Area: `Java/Spring Target`
  - Depends on: `SEC-007`; canonical delivery: `SPRING-006`.
- [ ] `SPR-EC-003` **Spring Integration** — `RESEARCH` · `P3` · `XL` · Area: `Java/Spring Target`
  - Depends on: `INTEG-001`, evidência de providers menores.
- [ ] `SPR-EC-004` **Spring Cloud Gateway** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `API-012`, `SEC-003`.
- [ ] `SPR-EC-005` **Spring Cloud Stream** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `MSG-001`, ao menos um broker estável.
- [ ] `SPR-EC-006` **Spring Cloud Config / discovery** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: `CONFIG-003`, modelo de serviços.
- [ ] `SPR-EC-007` **Spring Cloud Circuit Breaker** — `RESEARCH` · `P3` · `L` · Area: `Java/Spring Target`
  - Depends on: `RELY-003`.
- [ ] `SPR-EC-008` **Spring Modulith** — `RESEARCH` · `P3` · `XL` · Area: `Java/Spring Target`
  - Depends on: `DOM-018`, `EVENT-005`, `INTEL-008`.
- [ ] `SPR-EC-009` **Spring Authorization Server** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: modelo de segurança maduro.
- [ ] `SPR-EC-010` **Spring AI em aplicações geradas** — `RESEARCH` · `P4` · `XL` · Area: `Java/Spring Target`
  - Depends on: caso de uso runtime recorrente; não é MCP.

## EPIC — Advanced / Future

- [ ] `ADV-001` **Reactive execution model / WebFlux** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: modelo de efeitos, `PERSIST-007`.
- [ ] `ADV-002` **GraphQL** — `TODO` · `P3` · `XL` · Area: `API`
  - Depends on: `CMD-011`, `QUERY-001`, `BIND-002`.
- [ ] `ADV-003` **WebSocket e SSE** — `TODO` · `P3` · `XL` · Area: `API`
  - Depends on: `EVENT-001`, `BIND-002`.
- [ ] `ADV-004` **STOMP messaging** — `TODO` · `P3` · `L` · Area: `Messaging`
  - Depends on: `ADV-003`, `MSG-001`.
- [ ] `ADV-005` **WebClient provider** — `RESEARCH` · `P3` · `L` · Area: `Integration`
  - Depends on: `ADV-001`, `INTEG-004`.
- [ ] `ADV-006` **JTA/XA distributed transactions** — `RESEARCH` · `P4` · `XL` · Area: `Runtime`
  - Depends on: evidência de que Saga/outbox não atende o caso.
- [ ] `ADV-007` **Buildpacks** — `TODO` · `P3` · `M` · Area: `Java/Spring Target`
  - Depends on: `SPRING-012`, `GREEN-004`.
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

## EPIC — Compression Benchmarks

- [ ] `BENCH-001` **SCR compiler-only corpus e runner** — `TODO`; protocolo existe em documentação, sem medição executável · `P1` · `L` · Area: `CLI/DX`
  - Depends on: baseline V0 congelada, corpus versionado.
- [ ] `BENCH-002` **CCR/TCR agent benchmark** — `TODO` · `P2` · `XL` · Area: `MCP`
  - Depends on: `MCP-009`–`MCP-019`.

## EPIC — AI-Native Documentation

- [ ] `DOC-001` **Documentação compacta e exemplos para humanos/LLMs** — `PARTIAL`; README bilíngue e specs existem, sem catálogo estruturado único · `P1` · `L` · Area: `CLI/DX`
  - Evidence: [`README`](README.md), [`language spec`](docs/spec/harpia-language.md), [`Logic spec`](docs/spec/harpia-logic-v1-draft.md).
- [ ] `DOC-002` **Agent skill derivada da mesma fonte canônica** — `TODO` · `P2` · `M` · Area: `MCP`
  - Depends on: `MCP-004`–`MCP-006`, catálogo estruturado de coverage.

## EPIC — Open Source Extensibility

| Capacidade | Item canônico | Estado |
|---|---|---|
| Target API e catálogo | `TARGET-001`, `TARGET-002`, `TARGET-014` | `DONE` |
| Registro externo completo | `TARGET-003` | `DONE` |
| Target SDK/community targets/conformance | `TARGET-013`, `TARGET-015` | `PARTIAL` / `TODO` |
| Provider SPI/community providers | `TARGET-016` | `TODO` |
| Plugin architecture | `TARGET-017` | `RESEARCH` |
| Capability catalog | `TARGET-018` | `DONE` no V0 |

## Custom / Not Planned

Os casos `CUSTOM-005`–`CUSTOM-014` são deliberadamente resolvidos pelo escape hatch. Estes itens
registram os limites de escopo atuais:

- [ ] `SCOPE-001` **Frontend: React/Vue/Angular e UI generation** — `WONT_DO` no foco atual · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-002` **Server-side UI/static resource generation** — `WONT_DO` no foco atual · `P4` · `L` · Area: `Scope`
- [ ] `SCOPE-003` **Mobile** — `WONT_DO` · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-004` **Desktop** — `WONT_DO` · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-005` **Games** — `WONT_DO` · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-006` **DSL annotation-by-annotation ou nomes de framework na Business IR** — `WONT_DO` · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-007` **LLM dentro de validate/build** — `WONT_DO` · `P4` · `L` · Area: `Scope`
- [ ] `SCOPE-008` **Round-trip brownfield para Java idêntico** — `WONT_DO` · `P4` · `XL` · Area: `Scope`
- [ ] `SCOPE-009` **MCP com shell arbitrário ou edição direta de generated/** — `WONT_DO` · `P4` · `L` · Area: `Scope`

---

## Coverage Summary

As contagens abaixo incluem somente os 546 itens canônicos com checkbox. Linhas das tabelas de
referência Spring, Determinism e Extensibility não são contadas novamente.

| Status | Items |
|---|---:|
| `DONE` | 142 |
| `PARTIAL` | 34 |
| `TODO` | 284 |
| `BLOCKED` | 0 |
| `RESEARCH` | 56 |
| `NOT_SUPPORTED` | 10 |
| `CUSTOM` | 11 |
| `WONT_DO` | 9 |
| **Total** | **546** |

- Completion bruto: **142 / 546 = 26,0%**.
- Universo implementável atual, excluindo `NOT_SUPPORTED`, `CUSTOM` e `WONT_DO`: **516 itens**.
- Completion estrito nesse universo: **142 / 516 = 27,5%**.
- Progresso ponderado nesse universo, contando `PARTIAL` como 0,5: **30,8%**.

Esses percentuais medem apenas este backlog enumerado; não representam uma porcentagem universal
de tudo que um backend ou o ecossistema Spring pode fazer.

## Coverage by Area

`Todo` abaixo conta somente `TODO`; Research/Not Supported/Custom/Won't Do permanecem separados.

| Area | Total | Done | Partial | Todo | Research | Not Supported | Custom | Won't Do |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Language Core | 39 | 32 | 3 | 4 | 0 | 0 | 0 | 0 |
| Domain | 31 | 7 | 0 | 24 | 0 | 0 | 0 | 0 |
| Logic | 26 | 11 | 1 | 14 | 0 | 0 | 0 | 0 |
| API | 41 | 17 | 3 | 18 | 3 | 0 | 0 | 0 |
| Persistence | 51 | 11 | 7 | 22 | 11 | 0 | 0 | 0 |
| Integration | 34 | 5 | 2 | 23 | 4 | 0 | 0 | 0 |
| Messaging | 30 | 0 | 0 | 28 | 2 | 0 | 0 | 0 |
| Security | 25 | 1 | 4 | 15 | 5 | 0 | 0 | 0 |
| Java/Spring Target | 58 | 26 | 2 | 20 | 10 | 0 | 0 | 0 |
| Multi-target | 25 | 13 | 2 | 0 | 0 | 10 | 0 | 0 |
| Testing | 19 | 7 | 2 | 10 | 0 | 0 | 0 | 0 |
| CLI/DX | 28 | 6 | 6 | 16 | 0 | 0 | 0 | 0 |
| MCP | 26 | 0 | 0 | 25 | 1 | 0 | 0 | 0 |
| Brownfield | 17 | 0 | 0 | 15 | 2 | 0 | 0 | 0 |
| Runtime | 44 | 0 | 0 | 31 | 13 | 0 | 0 | 0 |
| Operations | 15 | 0 | 2 | 11 | 2 | 0 | 0 | 0 |
| Ownership | 8 | 6 | 0 | 1 | 1 | 0 | 0 | 0 |
| Extensibility | 20 | 0 | 0 | 7 | 2 | 0 | 11 | 0 |
| Scope | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 9 |
| **Total** | **546** | **142** | **34** | **284** | **56** | **10** | **11** | **9** |

## Top 10 Next Features

Ordem baseada em alavancagem arquitetural, compressão semântica, frequência, experiência de
humanos/agentes e dependências desbloqueadas — não apenas em interesse técnico.

Concluídos desta lista: HTTP binding com base URL e os quatro mappings (`BIND-006`, parcial),
`RULE-001`, `TYPE-020`, `TYPE-024`, `CORE-016`, `CMD-004`, `API-009` e `DET-003`.

1. **Layout do código custom** (`CUSTOM-003`) — a garantia de propriedade está provada; falta decidir onde a implementação vive para compilar com a interface gerada.
2. **`Reference<T>` e relacionamentos** (`TYPE-023`, `DOM-012`) — primeiro caso em que um campo aponta para outra entidade; `Page<T>` (`TYPE-025`) depende de `QUERY-005`.
4. **MCP M1 read-only/STDIO** (`MCP-001`, `MCP-002`, `MCP-007`–`MCP-012`) — prova o fluxo agent-native sobre o mesmo core e é o único desbloqueio de `MCP-020`, o último P0 aberto.
5. **Flow `fail`, `call` e `require`** (`FLOW-013`, `FLOW-014`, `FLOW-012`) — `fail` fecha `CMD-004`, cujo tipo já é gerado e mapeado.
6. **Content negotiation e response mapping** (`BIND-007`) — fecha o que resta de `BIND-006`.
7. **Especialização Command/Query no target** (`JAVA-004`) — a natureza declarada já chega ao gerador; falta usá-la além da forma CRUD.
8. **Generalização de escopos/referências** (`CORE-010`, `CORE-011`) — estende a resolução pronta aos novos kinds V1.

Depois desses itens: Scenario, Event local e schema evolution são os próximos slices naturais,
respectivamente `TEST-001`, `EVENT-005` e `DBEV-001`.

## Most Important Architectural Gaps

1. O Binding System formal cobre HTTP externo com base URL e mappings de path/query/header/body; falta content negotiation e outros kinds.
3. O type system de campo é álgebra selada com Enum e ValueObject; faltam containers (`List`/`Optional`/`Page`) e referências tipadas.
4. `SourceRef` e related locations têm ranges, mas ainda falta source mapping por símbolo/linha gerada.
5. `TargetRegistry` aceita instâncias externas, mas `TargetResolver` ainda consulta o catálogo estático.
6. Migrations geram apenas `V1__init.sql`; não há snapshot, diff ou evolução segura.
7. O escape hatch Custom Java está documentado, mas não possui contrato/preservação/DI implementados.
8. MCP e brownfield não possuem implementação; são backlog, não capacidades atuais.

## Features Already Strong

- Ingestão UTF-8, descoberta segura multi-file, CommonMark estrutural e diagnostics determinísticos.
- Versionamento explícito e separado para schema de config, linguagem Harpia e linguagem do target.
- Entity/CRUD V0 completo até DTO, service, controller, error mapping, JPA, repository e Flyway.
- Harpia Logic L1/L2 com AST tipada, pureza, escopos, inferência, chamadas e Java executado em teste.
- Business IR → Application IR → target transformer → Java Target Model → renderer.
- Templates declarativos pequenos, com gate que impede semântica de negócio.
- Geração determinística, golden byte a byte, compilação Java e `mvn -o test` do projeto gerado.
- Writer idempotente com manifesto, `--clean`, proteção de arquivos desconhecidos e path/symlink guards.
- Catálogo/CLI de targets honesto: somente `java-spring` gera; nenhum target futuro é simulado.

## Features Mostly Missing

- Rules/Invariants/Policies executáveis, ValueObject, Enum, relações, aggregates e lifecycle.
- Queries reais, filtros, paginação, projections e schema evolution.
- Bindings avançados, integrations, events, messaging, email, cache e files/storage.
- Authentication/authorization, sensitive data, audit, observability e Actuator.
- Idempotency, reliability distribuída, scheduling, batch, state machine e workflows.
- Formatter/linter, semantic diff/impact/explain/why e benchmarks executáveis.
- Custom Java completo, MCP Agent API e brownfield semantic reconstruction.
- Todos os targets além de Java/Spring.

## Verification Record

```text
Command: mvn -o clean test
Result: BUILD SUCCESS
Tests run: 330
Failures: 0
Errors: 0
Skipped: 0
Audited production files: 195 Java files / 15,364 lines
Audited test files: 59 Java files / 7,058 lines
```
