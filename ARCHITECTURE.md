# Harpia Architecture

> Status e prioridades de evolução estão consolidados em [`BACKLOG.md`](BACKLOG.md). Este documento
> descreve a arquitetura atual.

> **Harpia language semantics are target-independent.**

> **Java/Spring is the first supported target, not the definition of the Harpia language.**

> **The same Harpia specification should preserve the same application semantics across supported
> targets.**

Última revisão: 2026-09-06. Estado real do working tree, não aspiração.

Documentos relacionados: [TARGETS.md](TARGETS.md), [LANGUAGE_COVERAGE.md](LANGUAGE_COVERAGE.md),
[docs/roadmap.md](docs/roadmap.md), [docs/current-architecture.md](docs/current-architecture.md).

## 1. Pipeline

```text
harpia.yaml ──► ConfigLoader ─────────────┐
                                          │
specs/*.harpia.md ──► SourceFile          │
        │                                 │
        ▼                                 │
  MarkdownStructure                       │
        │                                 │
        ▼                                 │
     SpecParser ──► SpecAst / LogicAst    │   TARGET-INDEPENDENT
        │                                 │
        ▼                                 │
  SemanticValidator + LogicAnalyzer       │
        │                                 │
        ▼                                 │
  Resolver ──► Business IR                │
        │                                 │
        ▼                                 │
  CapabilityAnalyzer ──► requirements     │
        │                                 │
        ▼                                 │
  TargetResolver ◄──────────────────────┘   ← A FRONTEIRA
        │
        ▼
  CapabilityResolver ──► providers lógicos
        │
        ▼
  ApplicationModelBuilder ──► Application IR
        │
        ▼
  ProviderValidation
        │
        ▼
  HarpiaTarget.validate                       TARGET-SPECIFIC
        │
        ▼
  HarpiaTarget.generate ──► TargetGenerationResult / GeneratedFile[]
        │
        ▼
  GeneratedTree ──► OutputWriter ──► disco + manifesto
```

Tudo acima de `TargetResolver` produz exatamente os mesmos artefatos qualquer que seja o target
configurado. É isso que `fixtures/semantic/customer/` congela e `ArchitectureBoundaryTest` protege.

## 2. Camadas e direção de dependência

```text
source ──► ast ──► parse ──► logic ──► validate ──► model ──► capability ──► application
                                                                                  │
                                                                                  ▼
                                                                              target
                                                                                  │
                                                                                  ▼
                                                                    target.javaspring
                                                                                  │
                                                                                  ▼
                                                                                cli
```

| Pacote | Papel | Conhece um target? |
|---|---|---|
| `dev.harpia.source` | leitura UTF-8, descoberta ordenada | não |
| `dev.harpia.ast` | estrutura CommonMark e texto cru | não |
| `dev.harpia.parse` | AST Harpia (`SpecAst`, `LogicAst`) | não |
| `dev.harpia.logic` | álgebra de tipos, expressões e efeitos | não |
| `dev.harpia.validate` | análise semântica, escopo, tipos, pureza | não |
| `dev.harpia.model` | **Business IR** | não |
| `dev.harpia.capability` | capability, requirement, provider lógico | não |
| `dev.harpia.application` | **Application IR** e naming relacional | não |
| `dev.harpia.emit` | infraestrutura de geração (árvore, normalização, templates) | não |
| `dev.harpia.inspect` | renderização de cada estágio para `harpia inspect` | não |
| `dev.harpia.target` | **Target API**: id, status, descriptor, catálogo, registry, resolver | não |
| `dev.harpia.target.javaspring` | target pack Java/Spring: dependências, layout e orquestração | sim, é o target |
| `dev.harpia.target.javaspring.transformer` | Application IR → Java Target Model | sim |
| `dev.harpia.target.javaspring.model` | modelo estruturado de source Java | sim |
| `dev.harpia.target.javaspring.mapping` | tipos, validação, JPA e defaults | sim |
| `dev.harpia.target.javaspring.renderer` | Java Target Model → `.java` | sim |
| `dev.harpia.cli` | comandos e exit codes | só para exibir |

Módulos Maven separados foram **avaliados e recusados por ora**: a divisão não reduziria acoplamento
real, e um teste de arquitetura entrega a mesma garantia sem o custo de build. A direção de
dependência acima já é a do desenho `harpia-language → … → harpia-target-java-spring`.

## 3. O que cada IR pode conter

### Business IR (`dev.harpia.model`)

Permitido: `EntityModel`, `FieldModel`, `TypeRef`, `UseCaseModel`, `FlowStep`, `LogicModel`,
`ErrorMapping`, `OutputModel`.

Proibido, e verificado por teste: qualquer nome de arquitetura ou plataforma. A Business IR também
**não** carrega mais `tableName`/`columnName` — nome de tabela é decisão de aplicação, não de
negócio.

### Application IR (`dev.harpia.application`)

Permitido: `ApplicationEntity` (entidade persistente, tabela, response model), `ApplicationField`
(coluna, escalar, obrigatoriedade, unicidade), `ApplicationOperation` (kind, `HttpOperation`,
`RequestModel`, `Result`, fronteira transacional, falhas), `ApplicationLogic`, `ProjectSettings`
(nome, namespace, intenções de geração), `ResolvedCapabilities`.

Proibido: `repositoryTypeName`, `serviceTypeName`, `controllerTypeName`, `javaType`, `JavaLayout`,
`springBootVersion`. Tudo isso foi removido nesta refatoração e vive hoje em
`target.javaspring`.

`ApplicationScalarType` continua existindo, mas sem `javaType()`: o vocabulário escalar é da
aplicação, o tipo concreto é do target.

### Target (`dev.harpia.target.javaspring`)

`JavaTypeMapper` (Harpia → Java), `JavaLayout` (namespace → package, nome → classe),
`JavaReservedWords`, `JavaSpringDependencyResolver`, transformers, Java Target Model,
`JavaSourceRenderer`, `EmitterPipeline` e os emissores declarativos. Templates em
`resources/targets/java-spring/templates/`.

## 4. Target API

```java
public interface HarpiaTarget {
    TargetDescriptor descriptor();
    void validate(ApplicationProject, TargetConfiguration, DiagnosticCollector);
    TargetGenerationResult generate(ApplicationProject, TargetConfiguration);
}

public record TargetDescriptor(
        TargetId id, String displayName, String language, String framework,
        TargetStatus status, String languageRequirement, int minimumLanguageVersion,
        Set<Capability> capabilities,
        String targetVersion, String templateSet, int templateVersion) {}

public enum TargetStatus { SUPPORTED, EXPERIMENTAL, NOT_SUPPORTED }
```

`TargetGenerationResult` contém `GeneratedFile` ordenado com path, conteúdo, tipo e a origem Harpia
mais próxima. O core o adapta para `GeneratedTree`; só o `OutputWriter` toca o filesystem.

`TargetId` é value object, não enum, para que um target de comunidade não exija alterar o core.
`TargetCatalog` guarda metadata de todos os identificadores conhecidos; `TargetRegistry` guarda
apenas os que têm generator. Detalhes e ciclo de vida em [TARGETS.md](TARGETS.md).

## 5. Onde ficam as restrições

Uma restrição pertence à camada que a origina:

| Restrição | Antes | Agora |
|---|---|---|
| palavra reservada do Java | `SemanticValidator` (semântica) | `JavaSpringTarget.validate` |
| palavra reservada do PostgreSQL | `SemanticValidator` (semântica) | `application.ProviderValidation` |
| versão mínima da linguagem | `ConfigValidator` (`javaVersion != 21`) | `TargetResolver` + descriptor |
| versão do Spring Boot | `HarpiaConfig.TargetConfig` | opção do target |
| dependências Maven | `capability` | `target.javaspring` |
| chaves `spring.*` | `capability` | `target.javaspring` |
| nome de tabela/coluna | Business IR | Application IR |

Um nome ilegal em Java pode ser perfeitamente legal em outro target; PostgreSQL reserva palavras
independentemente de quem gera o código. As duas restrições existem — em lugares diferentes.

## 6. Determinismo

Ver [TARGETS.md §2](TARGETS.md). Em resumo: sem LLM, sem relógio, sem random, sem iteração não
ordenada. `GeneratedTree` é um `TreeMap` com paths validados; `OutputNormalizer` canonicaliza
CRLF, espaços finais e newline final; `DeterminismTest` compara hashes de dois builds.

## 7. Arquitetura de testes

| Camada | Testes |
|---|---|
| Language/Parser | `SpecParserTest`, `SpecParserDiagnosticsTest`, `LineGrammarTest`, `LogicGrammarTest` |
| Semantic | `SemanticValidatorTest`, `LogicAnalyzerTest` |
| Business IR | `ResolverTest`, `SemanticFixtureTest` (golden) |
| Application IR | `ApplicationModelBuilderTest`, `SqlNamingTest`, `SemanticFixtureTest` |
| Capability | `CapabilityResolverTest` |
| Target contract | `TargetCatalogTest`, `TargetRegistryTest`, `TargetCapabilityTest`, `UnsupportedTargetTest` |
| Java/Spring transformer | `JavaSpringEntityTransformerTest` (tipo e annotations antes do rendering) |
| Java renderer | `JavaSourceRendererTest`, `GeneratedSourcesCompileTest` |
| Templates | `JavaSpringTemplateRulesTest` |
| Golden target | `JavaSpringGoldenTest` |
| Projeto gerado | `GeneratedMavenProjectTest` executa `mvn -o test` |
| Java/Spring regressão | `JavaSpringTargetTest`, `EmitterPipelineTest`, `LogicEmitterTest`, `GeneratedLogicCompilesTest`, `PersistenceEmitterTest` |
| Arquitetura | `ArchitectureBoundaryTest`, e `harpia inspect` como prova externa |
| Inspeção | `InspectorTest`, `SemanticFixtureTest` (golden de cada estágio) |
| Determinismo | `DeterminismTest` |
| CLI | `CliExitCodeTest` |

Regra: um teste de Business IR **nunca** afirma "gerou um repositório JPA". Ele afirma
"`PersistentEntity` com restrição de unicidade e operação transacional". A afirmação sobre JPA
pertence aos testes do target.

`ArchitectureBoundaryTest` lê o código de produção e falha se um pacote acima da fronteira
mencionar linguagem, framework ou build tool. Ele encontrou quatro vazamentos reais quando foi
escrito, incluindo um diagnostic do parser que citava Spring Security.

## 8. Lowering Java/Spring implementado

Se um dia o generator precisar de um estágio intermediário, o caminho previsto é:

```text
Application IR
      │
      ▼
JavaSpringProjectTransformer
      ├── JavaSpringBootstrapTransformer
      ├── JavaSpringEntityTransformer
      ├── JavaSpringRepositoryTransformer
      └── JavaSpringLogicTransformer
      │
      ▼
JavaProjectModel / JavaSourceFile / JavaTypeModel
      │
      ▼
JavaSourceRenderer
      │
      ▼
GeneratedFile(JAVA_SOURCE)
```

O transformer decide significado: `Email required unique`, por exemplo, vira tipo `String` e as
annotations já resolvidas `@Email`, `@NotBlank` e `@Column(nullable = false, unique = true)`.
O renderer nunca vê `required`, `unique`, Rule ou Flow; apenas escreve o modelo Java.

O modelo não tenta reproduzir a JLS. Ele cobre somente os elementos que a Harpia gera hoje:
classes/interfaces, tipos, imports, annotations, campos, construtores, métodos e parâmetros.

## 9. Estratégia híbrida e regras de template

Source Java é produzido pelo modelo estruturado e por `JavaSourceRenderer`. Mustache fica restrito
a arquivos declarativos e padronizados:

```text
pom.xml.mustache
application.yaml.mustache
migration.sql.mustache
```

O `JavaSpringDependencyResolver` entrega dependências e propriedades prontas. A migration recebe
tabelas, colunas e constraints já inferidas. Template pode ordenar/formar texto; não pode interpretar
semântica Harpia, escolher annotation/dependency/package, ler filesystem, usar relógio ou random.

## 10. Limites conhecidos

- o generator ainda não emite request/response records, service, controller e error handling
  (E0.7) — sem eles a aplicação gerada compila mas não expõe comportamento;
- não existe servidor MCP neste repositório; a descoberta de targets hoje é via CLI;
- `GeneratedFile` preserva a origem principal, mas ainda não existe source map por símbolo/linha;
- `harpia explain`, `why`, `fmt`, `lint` e `inspect` não existem.
