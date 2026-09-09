# Harpia Targets

> O status e a prioridade canônicos dos targets vivem em [`BACKLOG.md`](BACKLOG.md). Este documento
> detalha o contrato e a arquitetura de targets.

> **Harpia compila intenção de backend. Java/Spring é o seu primeiro target.**

> **Portable semantics stay in Harpia. Target-specific behavior stays behind the target boundary.**

> **Generated code belongs to the target. Software intent belongs to Harpia.**

Última revisão: 2026-09-06.

## 1. Filosofia

A semântica da linguagem Harpia é **independente de target**. Java e Spring Boot são o primeiro —
e hoje o único — target suportado, não a definição da linguagem.

A mesma especificação, sem alterar um caractere, deve preservar a **mesma semântica de aplicação**
em qualquer target suportado. O código gerado muda; o significado, não.

```harpia
save customer
```

| Target | Implementação plausível |
|---|---|
| `java-spring` | repositório Spring Data, fronteira transacional, entidade JPA |
| `csharp-aspnet` (futuro) | Entity Framework, transação, DbContext |
| `typescript-nestjs` (futuro) | repositório TypeORM/Prisma, transação |

A intenção — *este dado passa a existir de forma durável* — é a mesma nos três.

## 2. Determinismo

> **AI is probabilistic. Harpia compilation is deterministic.**

Para um target fixo:

```text
mesma spec
+ mesmo harpia.yaml
+ mesma languageVersion
+ mesma compilerVersion
+ mesma configuração de target
= mesma implementação gerada
```

O compilador não usa LLM, `random`, relógio, iteração não ordenada nem qualquer entrada dependente
do ambiente. `DeterminismTest` compila cada exemplo duas vezes e compara o hash SHA-256 da árvore
gerada; ele também verifica que nenhum arquivo gerado contém ano corrente, usuário ou diretório da
máquina.

`validate` e `build` compilam exatamente a mesma árvore. A única diferença entre eles é a
severidade de um target indisponível.

## 3. Target suportado hoje

```text
java-spring   Java 21+   Spring Boot   Maven   SUPPORTED
```

Capabilities que o generator realmente implementa:

```text
http
persistence
```

Providers realmente existentes:

```text
persistence -> postgresql
```

Nada além disso é anunciado. `events` e `custom` existem como capability no modelo, não têm
generator, e por isso **não** aparecem nas capabilities do target.

## 4. Catálogo

`harpia targets` é a fonte da verdade em tempo de execução:

```text
Harpia targets

SUPPORTED
  ✓ java-spring        Java + Spring Boot

NOT SUPPORTED
  - clojure-jvm        Clojure on the JVM
  - csharp-aspnet      C# + ASP.NET Core
  - elixir-phoenix     Elixir + Phoenix
  - go                 Go
  - kotlin-spring      Kotlin + Spring Boot
  - php-laravel        PHP + Laravel
  - python-fastapi     Python + FastAPI
  - ruby-rails         Ruby + Rails
  - rust               Rust
  - typescript-nestjs  TypeScript + NestJS
```

Estar no catálogo é documentação de intenção, **nunca** compromisso. Identificadores de targets
ainda não suportados podem mudar antes de serem oficialmente suportados.

O catálogo também não é a lista do que o compilador consegue gerar: essa é o `TargetRegistry`. Um
target registrado carrega o próprio `TargetDescriptor` e resolve mesmo que nenhum identificador
correspondente exista no catálogo, que responde apenas por identificadores sem generator. É o que
permite a um futuro plugin loader montar outro conjunto de targets sem editar o core.

`harpia targets <id>` descreve um target:

```text
Target: Java + Spring Boot
Id: java-spring
Status: SUPPORTED
Language: java
Framework: spring-boot
Language requirement: Java >= 21
Target version: 1
Template set: default (version 1)
Capabilities:
  http
  persistence
```

## 5. Ciclo de vida

```text
NOT_SUPPORTED  →  EXPERIMENTAL  →  SUPPORTED
```

| Status | Significado |
|---|---|
| `NOT_SUPPORTED` | está no catálogo, **não existe generator** |
| `EXPERIMENTAL` | existe generator, sem garantia de produção |
| `SUPPORTED` | generator completo, conformidade provada, documentado |

`DEPRECATED` está previsto para o futuro e ainda não existe.

Hoje nenhum target é experimental: `java-spring` é `SUPPORTED` e todos os demais são
`NOT_SUPPORTED`, sem nenhuma linha de generator.

Um `TargetDescriptor` sem generator **não pode declarar capabilities** — isso é verificado no
construtor. Não existe "REST suportado em teoria".

## 6. Um target é linguagem + framework

`java` sozinho não descreve arquitetura suficiente para gerar uma aplicação; `java-spring`
descreve. O mesmo vale para `typescript-nestjs` e não `typescript`.

Isso deixa espaço natural para `java-quarkus` ou `java-micronaut` no futuro, sem redefinir nada.

Compartilhar linguagem ou plataforma **não** implica compartilhar generator:

- `kotlin-spring` compartilha Spring com `java-spring`, mas precisa gerar Kotlin idiomático;
- `clojure-jvm` roda na JVM e **não** reaproveita o generator Java: Clojure idiomático não é Java
  com parênteses.

## 7. Escopo: backend

> **Harpia is backend-focused.**

Todos os targets do catálogo são backend. React, Vue, Angular, mobile, desktop e jogos estão fora
do produto, mesmo com arquitetura multi-target.

## 8. Configuração

Forma canônica:

```yaml
target:
  id: java-spring
  language:
    version: 21
  options:
    springBootVersion: "3.3.2"
```

- `id` seleciona um target registrado, ou um identificador catalogado sem generator;
- `language.version` é a versão da linguagem **daquele** target;
- `options` carrega valores que só aquele target entende. O core nunca os interpreta: ele valida
  apenas que são escalares. `java-spring` exige `springBootVersion` e reporta `HRP7005` se faltar.

Futuro, quando existir:

```yaml
target:
  id: csharp-aspnet
  language:
    version: 12
```

```text
NOT SUPPORTED YET
```

### 8.1 Compatibilidade

A forma V0 continua funcionando:

```yaml
target:
  type: spring
  javaVersion: 21
  springBootVersion: 3.3.2
```

Ela é mapeada internamente para `java-spring` e produz **um** warning de deprecação (`HRP3009`)
com a forma canônica na hint. Misturar as duas formas é erro.

## 9. Target indisponível

```yaml
target:
  id: csharp-aspnet
```

```bash
harpia build
```

```text
harpia.yaml: error[HRP7001]: target `csharp-aspnet` is not supported by this compiler;
target status: NOT_SUPPORTED; no code was generated
  hint: supported targets: [java-spring]; planned targets: [clojure-jvm, csharp-aspnet,
  elixir-phoenix, go, kotlin-spring, php-laravel, python-fastapi, ruby-rails, rust,
  typescript-nestjs]
```

Sem fallback. Sem gerar Java. Sem chamar IA. Nenhum arquivo é criado. Exit code 1.

`validate` separa as duas perguntas — a spec está correta? o target existe? — e responde as duas:

```text
harpia.yaml: warning[HRP7001]: target `csharp-aspnet` is not supported by this compiler;
target status: NOT_SUPPORTED; the specification was still validated
Validation succeeded.
```

Exit code 0: a especificação é válida; apenas este compilador não sabe gerá-la.

## 10. Diagnostics

| Código | Situação |
|---|---|
| `HRP7001` | target catalogado sem generator neste compilador |
| `HRP7002` | identificador que nenhum target registrado nem catalogado reconhece |
| `HRP7003` | a spec exige uma capability que o target não implementa |
| `HRP7004` | versão de linguagem abaixo do exigido pelo target |
| `HRP7005` | opção obrigatória do target ausente ou inválida |
| `HRP7006` | template/renderer do target falhou com diagnostic estável |
| `HRP7007` | transformação Application IR → Target Model falhou |
| `HRP7008` | construção válida ainda não possui mapping no target |
| `HRP3009` | bloco `target` na forma legada (warning) |

## 11. Capabilities

```text
Business IR
      ↓ requirements inferidos
CapabilityRequirementSet
      ↓ o target implementa isso?
TargetDescriptor.capabilities        → HRP7003
      ↓ qual provider lógico?
CapabilityResolver                   → HRP6001 / HRP6002
      ↓
Application IR
      ↓
Target generation
```

A separação entre **target** e **provider** é deliberada:

```text
Target:      java-spring
Capability:  persistence
Provider:    postgresql
```

PostgreSQL é conceitualmente cross-target: a mesma escolha lógica valeria para `csharp-aspnet`,
mudando só a implementação (JPA aqui, EF Core lá). Por isso a seleção de provider vive na camada
de capabilities, independente de target, e a **contribuição** dessa escolha (dependências Maven,
chaves `spring.*`) vive dentro de `java-spring`.

HTTP não tem provider selecionável: o target expõe endpoints por conta própria. Isso é modelado
como `ResolvedCapability.provider() == empty`, e não com um provider fictício.

## 12. Target SPI

```java
public interface HarpiaTarget {
    TargetDescriptor descriptor();

    void validate(ApplicationProject application,
                  TargetConfiguration configuration,
                  DiagnosticCollector diagnostics);

    TargetGenerationResult generate(ApplicationProject application,
                                    TargetConfiguration configuration);
}
```

`validate` reporta restrições que **só** aquele target impõe — palavras reservadas da sua
linguagem, opções obrigatórias. Quando essa validação roda, a Application IR já é Harpia válida.

`TargetRegistry` é uma instância, não um `switch` estático espalhado pelo compilador:

```java
TargetRegistry.standard();                    // built-in: apenas java-spring
new TargetRegistry(List.of(meuTarget));       // um futuro plugin loader monta a sua
```

Um target sem generator **não** é representado por uma implementação que lança exceção. Ele existe
apenas como `TargetDescriptor` no catálogo, e `TargetResolver` produz o diagnostic.

Plugin loading não está implementado. A arquitetura apenas não impede: nada no core precisa mudar
para um target de comunidade existir.

### 12.1 Transformer, Target Model e renderer

O target Java/Spring implementa o pipeline:

```text
ApplicationProject
      ↓
JavaSpringProjectTransformer
      ↓
JavaProjectModel
      ├── JavaSourceFile
      ├── JavaTypeModel (class/interface/record)
      ├── JavaFieldModel
      ├── JavaConstructorModel
      ├── JavaMethodModel
      ├── JavaParameterModel
      ├── JavaAnnotationModel
      └── JavaImportModel
      ↓
JavaSourceRenderer
      ↓
GeneratedFile
```

`JavaTypeMapper`, `SpringValidationMapper`, `SpringPersistenceMapper` e
`JavaDefaultValueMapper` fazem as decisões target-specific. O renderer recebe essas decisões
prontas e não lê Application IR.

### 12.2 Templates do target Java/Spring

Os templates oficiais e versionados ficam em:

```text
src/main/resources/targets/java-spring/templates/
├── pom.xml.mustache
├── application.yaml.mustache
└── migration.sql.mustache
```

| Template | Modelo que o alimenta |
|---|---|
| `pom.xml` | coordenadas e dependências já resolvidas por `JavaSpringDependencyResolver` |
| `application.yaml` | propriedades finais, ordenadas e com secrets referenciados por ambiente |
| migration | tabelas, colunas e constraints já inferidas |

Templates podem formatar; não podem interpretar `required`, `unique`, Rule ou Flow, escolher
annotations/dependencies/packages, consultar filesystem, executar lógica arbitrária ou gerar
timestamps/random. Source Java não usa Mustache: passa pelo Java Target Model e renderer.

### 12.3 Generated files e source mapping

O target devolve `TargetGenerationResult` com uma lista ordenada de `GeneratedFile`. Cada arquivo
carrega `relativePath`, `content`, categoria e, quando aplicável, o `SourceRef` Harpia que o
originou. O core converte o resultado para `GeneratedTree`; somente `OutputWriter` grava em disco.

## 13. Portabilidade semântica

Cada construção da linguagem tem semântica Harpia oficial, e o target deve preservá-la.

Exemplo — `required`:

| Camada | O que diz |
|---|---|
| Harpia | o valor deve estar presente; ausência é inválida |
| `java-spring` | Jakarta Validation na entrada, `NOT NULL` na coluna |
| outro target | os mecanismos equivalentes da sua plataforma |

A documentação de linguagem descreve a primeira linha; a documentação do target descreve as
demais. Nunca o contrário — a especificação da linguagem não diz "Entity vira JPA Entity".

## 14. Conformidade de target

Para um target futuro sair de `NOT_SUPPORTED`, ele precisará provar:

```text
Parser/semantic conformance      a mesma spec produz a mesma Business IR
Core construct mapping           cada construção tem semântica preservada
Golden generation                árvore gerada estável e revisável
Generated compilation            o projeto gerado compila
Generated tests                  os testes gerados passam
Capability coverage              declara só o que implementa
Determinism tests                dois builds, mesmos bytes
Documentation                    target documentado neste arquivo
```

A fundação já existe: `fixtures/semantic/customer/` guarda a Business IR e a Application IR
esperadas, **target-independentes**. O golden atual vive em
`fixtures/targets/java-spring/customer/`; um segundo target ganha seu próprio diretório e não muda
as fixtures semânticas.

## 15. Custom implementation e portabilidade

Custom Java é, por natureza, específico de target. A IR representa apenas o conceito genérico —
um contrato com nome — e o target resolve o que isso significa. Uma aplicação pode então ser
descrita como, por exemplo, 90% semântica portável e 10% específica de target.

O core **não** assume `custom == Java`. Essa métrica de portabilidade ainda não é calculada; a
arquitetura apenas não a impede.

## 16. Roadmap de targets

Isto é intenção, não compromisso. Nada aqui está em desenvolvimento.

```text
P0   java-spring                                   SUPPORTED

P1   kotlin-spring, csharp-aspnet, typescript-nestjs
P2   python-fastapi, go
P3   clojure-jvm, php-laravel

Research   rust, elixir-phoenix, ruby-rails
```

Todos aparecem no catálogo como `NOT_SUPPORTED`, sem generator e sem capabilities anunciadas.
Pesquisa no roadmap não significa suporte executável.

## 17. Descoberta por agentes

`harpia targets` já permite que um agente descubra o que é real antes de propor uma mudança de
target. Um servidor MCP **não existe** neste repositório; quando existir, `harpia://targets` e
`get_targets` devem servir exatamente o mesmo `TargetCatalog`, sem uma segunda fonte da verdade.
