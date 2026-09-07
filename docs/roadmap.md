# Roadmap histórico da Harpia

> **Snapshot não normativo.** O status e a prioridade oficiais foram consolidados em
> [`../BACKLOG.md`](../BACKLOG.md). Este arquivo permanece como registro das ondas e decisões do
> planejamento anterior e não deve ser usado para concluir que uma feature está pronta.

Status do snapshot: 2026-09-06. Cobertura detalhada da época vive em
[`../LANGUAGE_COVERAGE.md`](../LANGUAGE_COVERAGE.md) e
[`../SPRING_COVERAGE.md`](../SPRING_COVERAGE.md).

## 1. Visão

Harpia é uma **Semantic Software Specification Language for Humans and AI**: uma camada semântica
entre intenção e implementação.

```text
Humans / AI Agents
        ↓
*.harpia.md + harpia.yaml
        ↓
Project AST + Symbol Table
        ↓
Business IR
        ↓
Capability Resolution
        ↓
Application IR
        ↓
Target Registry + Target Transformer
        ↓
Deterministic Target Model / Renderer
        +
Target-owned templates / Custom implementation
        ↓
Application
```

As duas interfaces oficiais compartilham o mesmo core:

```text
              Harpia Core
             /           \
       Harpia CLI     Harpia MCP
```

## 2. Princípios protegidos

1. **Harpia covers software semantics. Java covers implementation freedom.**
2. **The intent is the source. Generated code is an artifact.**
3. **AI-native does not mean AI-dependent.** Nenhuma IA participa de `validate` ou `build`.
4. **Compile intent, do not prompt boilerplate.**
5. Densidade semântica importa mais que sintaxe curta ou críptica.
6. A Business IR não conhece Spring, JPA, Maven, brokers ou SDKs.
7. **Java/Spring é o primeiro target, não a linguagem Harpia.**
8. Transformers traduzem significado; templates apenas renderizam dados prontos.
9. Providers implementam capabilities; não criamos uma keyword por annotation.
10. Custom Java é a saída para comportamento específico, low-level ou sem compressão recorrente.
11. **Formula computes. Decision chooses. Logic reasons. Flow acts.**
12. Uma feature só está pronta quando fecha todos os estágios aplicáveis e possui teste.

Antes de adicionar uma construção, responder: representa intenção recorrente, comprime semântica,
é legível, determinística, tipável e melhor que Custom Java? Se apenas abrevia Java, não entra.

## 3. Estado atual comprovado

| Área | Estado | Evidência principal |
|---|---|---|
| Compilador | `PARTIAL` | 152 arquivos de produção; `HarpiaCompiler` conecta todos os estágios atuais |
| Testes | `DONE` para o escopo existente | 42 classes, 215 testes verdes em `mvn -o test` |
| Fonte | `DONE` | UTF-8 estrito, BOM/CRLF, descoberta ordenada e proteção de path/symlink |
| Markdown/AST V0 | `DONE` | CommonMark estrutural + raw spans; uma entidade opcional por módulo |
| Entity/CRUD V0 | `PARTIAL` | entity JPA, repository e Flyway gerados; DTO/service/controller ainda ausentes |
| Harpia Logic L1/L2 | `PARTIAL` | parser, tipos, pureza, IR, Java puro gerado, escrito, compilado e executado |
| Multi-target foundation | `DONE` | descriptor/catalog/registry/resolver; somente `java-spring` tem generator |
| Capabilities | `PARTIAL` | HTTP nativo do target e persistence/postgresql resolvidos separadamente |
| Providers | `PARTIAL` | dependências/configuração e mapping PostgreSQL no target; novos providers não existem |
| Generator | `PARTIAL` | transformer, Java Target Model, renderer e templates geram o slice atual |
| Escrita | `DONE` | `OutputWriter`, manifesto, ownership, `--clean` e `--force` testados |
| CLI | `PARTIAL` | `validate`, `build`, `targets`, `version`; demais comandos planejados |
| MCP | `TODO` | nenhum servidor, resource ou tool implementado |
| Custom Java | `TODO` | fronteira documentada, nenhum contrato/writer implementado |

Conclusão: a arquitetura target e a fatia persistence passam por golden, determinismo, `javac` e
`mvn -o test`. A tese CRUD completa ainda depende de DTOs, services, controllers e testes gerados.

## 4. Status, horizonte e esforço

| Status | Uso neste roadmap |
|---|---|
| `DONE` | gate do item passou no estágio aplicável |
| `PARTIAL` | existe implementação útil, mas o gate end-to-end não fechou |
| `TODO` | planejado, sem implementação comprovada |
| `BLOCKED` | depende de uma decisão ou fundação ainda inexistente |
| `RESEARCH` | precisa de spike/ADR antes de compromisso de sintaxe ou provider |
| `CUSTOM` | caminho oficial é contrato + Java do usuário |
| `WONT_DO` | contrário aos princípios do produto |

Esforço: `XS`, `S`, `M`, `L`, `XL`. Os targets abaixo são horizontes de planejamento, não promessa
de data: `0.1` fecha o compilador V0; `0.2` estabiliza a fundação semântica; `0.3` prova o MVP
semântico; `0.4` prova o fluxo agent-native; `1.x` amplia a superfície Spring e inteligência.

## 5. Modelo de prioridade e impacto

Cada épico recebe sete notas de 1–5:

- `SC`: Semantic Compression;
- `DX`: Developer Experience;
- `AX`: AI/Agent Experience;
- `SP`: Spring Coverage;
- `BE`: Business Expressiveness;
- `CC`: Compiler Complexity, onde 5 é mais caro;
- `AR`: Architecture Risk, onde 5 é mais arriscado.

Prioridade não é a soma cega das notas. Favorecemos alta `SC`, frequência e alavancagem com esforço
baixo/médio; `CC` e `AR` altos exigem fundações, ADRs e slices menores.

## 6. Gaps arquiteturais

| ID | Gap | Evidência | Decisão |
|---|---|---|---|
| G01 | Aplicação Spring incompleta | persistence compila, mas não há DTO/service/controller | fechar E0.6/E0.7 antes de ampliar providers |
| G03 | `SpecAst` não é Project AST | compilador mantém `List<SpecAst>` | adapter incremental para `ModuleAst`/`ProjectAst` |
| G04 | SymbolTable parcial | somente namespace de Logic possui declare/resolve | tabela global determinística em duas passagens |
| G05 | Localização só inicial | `SourceRef` não possui fim/related | `SourcePosition`/`SourceRange`, mantendo adapter |
| G06 | Versões misturadas | `harpia: 1` é schema marker | separar compiler, language e MCP API version |
| G07 | Parser central cresce por `if` | `SpecParser` despacha Data/use case/Logic | registry interno por kind, sem SPI prematura |
| G08 | HTTP ainda é obrigatório no use case V0 | `UseCaseModel.http` não é opcional | Command/Query independentes de protocolo em F1/S1 |
| G09 | Provider artifact contract é interno ao target | PostgreSQL já contribui deps/config/schema | generalizar somente quando surgir segundo provider real |
| G10 | Type system global é enum escalar | `TypeRef` não representa nominal/container | álgebra selada; reaproveitar `LogicType` sem fundir camadas |
| G11 | Source map ainda é por arquivo | `GeneratedFile` carrega `SourceRef`, não ranges/símbolos | índice de origem por símbolo e linha em F2 |
| G12 | Sem fonte estruturada única de coverage | matrizes Markdown são manuais | adotar catálogo estruturado depois de `inspect`, não antes |
| G13 | CLI é a única interface | nenhum MCP | MCP fino sobre APIs do core, nunca sobre parsing duplicado |
| G14 | Logic avançou antes do backend V0 | L1/L2 existem, service CRUD não | congelar novos slices Logic até E0 e F1 fecharem |

## 7. Milestones imediatos — fechar E0

| Milestone | Entrega | Gate |
|---|---|---|
| E0.1 | Business IR + requirements HTTP/persistence | `DONE` |
| E0.2 | Application IR, provider inputs e emissores básicos | `DONE` |
| MT0 | Target API, catálogo/registry, Java Target Model, transformer, renderer e templates | `DONE` |
| E0.3 | mappings Harpia → Java/JPA/PostgreSQL dentro do target | `DONE`; IR genérica não conhece Java |
| E0.4 | entity JPA generator-ready | `DONE`; transformer e golden cobrem constraints |
| E0.5 | repository + Flyway | `DONE`; schema/repository derivam da mesma Application IR |
| E0.6 | DTOs + error contracts | requests/responses/errors compilam isoladamente |
| E0.7 | services + controllers para os oito flows | CRUD completo compila; desbloqueia Flow → Logic |
| E0.8 | `OutputWriter`, manifesto e `build` real | `DONE`; idempotência/ownership testados |
| E0.9 | testes gerados, goldens, determinismo, Maven e SCR baseline | `PARTIAL`; golden/determinismo/Maven verdes, faltam testes gerados e SCR |

```text
harpia build --dir examples/customer
cd examples/customer/generated
mvn -o test
```

Enquanto E0.6/E0.7/E0.9 não passarem, CRUD/REST completo permanece `PARTIAL`.

### 7.1 Harpia Logic lane

L1/L2 já entregam `## Logic`, expressão tipada, `if`/`else`, atribuição única, pureza, chamadas
nomeadas acíclicas, built-ins mínimos e Java puro executado em teste. A sequência planejada é L3
Formula, L4 Money/Percentage, L5 Decision, L6 collections, L7 date/time, L8 Flow → Logic e L9
Scenario. Novos slices ficam depois de E0/F1; L8 depende diretamente do service generator E0.7.

O V0 atual fixa Java 21. A política futura será `Java >= 21` com compatibilidade validada, não uma
enum fechada com versões futuras hardcoded.

## 8. Ondas de implementação

| Wave | Conteúdo | Entrada | Saída/gate |
|---|---|---|---|
| W0 — Audit | arquitetura, grammar, coverage, riscos | working tree | `DONE`: roadmap e matrizes oficiais |
| W0.5 — Multi-target | target boundary + `java-spring` pack | E0.2 | `DONE`: modelo/renderer/templates/golden/Maven |
| W1 — V0 end-to-end | E0.6, E0.7 e restante de E0.9 | W0.5/E0.5/E0.8 | Customer CRUD compilado e testado |
| W2 — Foundation | languageVersion, ranges, Project AST, SymbolTable, inspect | E0 | modelo global e diagnóstico estruturado |
| W3 — Semantic core | Value, Enum, Command, Query, Rule, Invariant, Event local | W2 | primeiro slice além de CRUD |
| W4 — Logic | Formula, Money, Percentage, Decision, collections, dates | W2 + L1/L2 | computação rica ainda pura |
| W5 — Scenarios | Given/When/Then e testes derivados | W3/W4 | specs executáveis de comportamento |
| W6 — Security/events | JWT, roles, Policy, Email, audit, sensitive | W3/W5 | application core seguro |
| W7 — Integrations | contract HTTP, errors, timeout, retry, webhook | W3 + idempotency | integração testada por mock |
| W8 — Custom Java | contracts, DI, dependencies e preservação | W2 | escape hatch seguro e completo |
| W9 — MCP | STDIO, resources, leitura, validate/build, mutation segura | W2 + E0.8 | fluxo agent-native demonstrável |
| W10 — Intelligence | diff, impact, explain, why, breaking changes | Project AST + source maps | inteligência sem ler Java gerado |
| W11 — Spring expansion | WebFlux, GraphQL, data/providers, messaging, Actuator | abstrações semânticas maduras | providers adicionados por demanda |
| W12 — Advanced | state, workflow, saga, batch, tenancy, parallel | events/idempotency/integration | slices independentes com gates |
| W13 — Ecosystem | Cloud, Modulith, Authorization Server, Spring AI | core estável + evidência | decisões por ADR, não por catálogo |

## 9. Catálogo dos 36 épicos

### 9.1 Planejamento e dependências

| ID | Epic | Descrição/categoria | Status | P | Effort | Depends | Blocks | Milestone | Target |
|---|---|---|---|---|---|---|---|---|---|
| EP01 | Language Foundation | AST, symbols, versions, locations; A/tooling | `PARTIAL` | P0 | XL | E0 | EP02–EP21, EP32–EP35 | W2 | 0.2 |
| EP01.1 | ~~Pipeline inspection~~ | `harpia inspect --stage`; tooling | `SUPPORTED` | P0 | M | E0 | EP01 restante | W2 | 0.2 |
| EP02 | Type System | escalares, nominais e containers; A | `PARTIAL` | P1 | XL | EP01 | EP03–EP06, EP16 | W3/W4 | 0.3 |
| EP03 | Domain Modeling | Entity, Value, Enum, Aggregate, relações; A | `PARTIAL` | P0 | XL | EP01/EP02/EP08 | EP04/EP06/EP16 | W1/W3 | 0.1–0.3 |
| EP04 | Rules / Invariants / Policies | predicados reutilizáveis tipados; A | `TODO` | P1 | XL | EP01/EP02 | EP06/EP10/EP16 | W3/W6 | 0.3 |
| EP05 | Harpia Logic | Formula/Decision/Logic pura; A | `PARTIAL` | P1 | XL | EP01/EP02 | EP06/EP18 | W4 | 0.3 |
| EP06 | Commands / Queries / Flow | comportamento e efeitos; A | `PARTIAL` | P0 | XL | EP01–EP05 | EP07–EP18 | W1/W3 | 0.1–0.3 |
| EP07 | API / Transport | exposição desacoplada do comportamento; A+B | `PARTIAL` | P0 | XL | EP06 | EP22 | W1/W3 | 0.1–0.3 |
| EP08 | Persistence | intenção e provider persistente; A+B | `PARTIAL` | P0 | L | EP02/EP03 | EP09/EP23 | W1 | 0.1 |
| EP09 | Database Evolution | snapshots, diff e migrations; B | `TODO` | P2 | XL | EP08/EP32 | imports/migrate diff | W10/W11 | 1.x |
| EP10 | Security | authn, authz, roles e policies; A+B | `TODO` | P1 | XL | EP04/EP06/EP07 | EP25 | W6 | 0.3 |
| EP11 | Events / Messaging | Event, Handler, delivery capability; A+B | `TODO` | P1 | XL | EP01/EP02/EP06 | EP17/EP24 | W3/W6 | 0.3 |
| EP12 | Integrations | ports, operations, errors e clients; A+B+C | `TODO` | P1 | XL | EP02/EP06/EP13 | EP26 | W7 | 0.3 |
| EP13 | Reliability | timeout, retry, breaker, rate limit; B | `TODO` | P2 | L | EP06/EP12/idempotency | providers distribuídos | W7 | 0.4 |
| EP14 | Cache / Storage | semântica e providers local/cloud; A+B | `TODO` | P2 | L | EP02/EP06 | EP23/EP26 | W11/W12 | 1.x |
| EP15 | Scheduling / Batch | Schedule, Job e execução; A+B | `TODO` | P2 | XL | EP06/EP13/EP18 | runtime avançado | W12 | 1.x |
| EP16 | State Machines | states, transitions e guards; A | `TODO` | P2 | L | EP02/EP04/EP06 | EP17 | W12 | 1.x |
| EP17 | Workflows / Sagas | orquestração, compensação e recovery; A+B | `TODO` | P3 | XL | EP11–EP16 | — | W12 | 1.x |
| EP18 | Scenarios / Testing | Given/When/Then e testes gerados; A+B | `PARTIAL` | P1 | XL | EP03–EP07 | qualidade dos slices | W5 | 0.3 |
| EP19 | Sensitive Data / Audit | lifecycle, mask, crypto e trilha; A+B | `TODO` | P2 | L | EP03/EP10/EP11 | compliance | W6/W12 | 0.4–1.x |
| EP20 | Multi-tenancy | intenção tenant + estratégias; A+B | `RESEARCH` | P3 | XL | EP08/EP10/EP19 | providers tenant | W12 | 1.x |
| EP21 | Custom Java | contratos, DI e dependencies; C | `TODO` | P1 | L | EP01/EP02/E0.8 | cobertura especializada | W8 | 0.3 |
| EP22 | Spring Boot Web Coverage | MVC e transports futuros; B | `PARTIAL` | P0 | XL | EP07 | cobertura Spring web | W1/W11 | 0.1–1.x |
| EP23 | Spring Boot Data Coverage | JPA/JDBC/R2DBC/NoSQL; B | `PARTIAL` | P0 | XL | EP08 | cobertura Spring data | W1/W11 | 0.1–1.x |
| EP24 | Spring Messaging Coverage | local/JMS/AMQP/Kafka/etc.; B | `TODO` | P2 | XL | EP11 | coverage messaging | W11 | 1.x |
| EP25 | Spring Security Coverage | JWT/OAuth/OIDC/LDAP; B | `TODO` | P1 | XL | EP10 | coverage security | W6/W11 | 0.3–1.x |
| EP26 | Spring Integration / IO | clients, mail, files e protocols; B+C | `TODO` | P2 | XL | EP12/EP14 | IO providers | W7/W11 | 0.4–1.x |
| EP27 | Observability / Actuator | logs, metrics, traces e management; B | `TODO` | P2 | L | EP06/EP11/EP12 | operations | W11 | 1.x |
| EP28 | Spring Testing | slices, containers e service connections; B | `TODO` | P1 | L | EP18/EP22–EP27 | gates providers | W5/W11 | 0.3–1.x |
| EP29 | Packaging / Runtime | JAR, image, AOT e runtime controls; B | `PARTIAL` | P2 | L | E0/EP27 | distribuição | W11 | 1.x |
| EP30 | Spring Ecosystem | Cloud, Modulith, Integration, AI; B+C | `RESEARCH` | P3 | XL | core + evidência | — | W13 | 1.x+ |
| EP31 | CLI / Developer Experience | init/validate/build/inspect/fmt/etc.; tooling | `PARTIAL` | P0 | L | core de cada comando | adoção local | W1/W2 | 0.1–0.2 |
| EP32 | Semantic Diff / Impact / Explain | inteligência sobre modelos; tooling | `TODO` | P2 | XL | EP01/source maps | MCP intelligence | W10 | 0.4 |
| EP33 | Architecture Rules / Modules | contexts, visibility e constraints; A/tooling | `TODO` | P2 | XL | EP01/EP32 | Modulith | W10/W13 | 1.x |
| EP34 | MCP Agent API | interface STDIO/HTTP para agentes; tooling | `TODO` | P1 | XL | EP01/EP31/E0.8 | agent-native MVP | W9 | 0.4 |
| EP35 | AI-native Documentation | recursos compactos e fonte única; tooling | `PARTIAL` | P1 | L | coverage/versioning | MCP disclosure | W0/W9 | 0.2–0.4 |
| EP36 | Compression Benchmarks | SCR/CCR/TCR e corpus; tooling | `TODO` | P1 | L | E0/EP18 | tese mensurável | W1/W5 | 0.1–0.3 |

### 9.2 Impacto por épico

Formato: `SC/DX/AX/SP/BE | CC/AR`. As cinco primeiras notas medem valor; as duas últimas, custo e
risco. As notas servem para ordenação relativa, não para alegar precisão estatística.

| ID | Impacto | ID | Impacto | ID | Impacto |
|---|---|---|---|---|---|
| EP01 | 4/5/5/3/4 \| 5/5 | EP13 | 3/3/3/4/3 \| 4/4 | EP25 | 4/4/4/5/4 \| 5/5 |
| EP02 | 5/4/4/3/5 \| 5/4 | EP14 | 3/3/3/4/3 \| 4/4 | EP26 | 4/4/3/5/4 \| 5/4 |
| EP03 | 5/4/4/4/5 \| 5/4 | EP15 | 4/3/3/4/4 \| 5/5 | EP27 | 3/4/3/5/3 \| 4/3 |
| EP04 | 5/4/4/3/5 \| 5/4 | EP16 | 5/4/4/3/5 \| 4/4 | EP28 | 4/5/4/5/4 \| 4/3 |
| EP05 | 5/4/5/2/5 \| 5/5 | EP17 | 5/3/3/3/5 \| 5/5 | EP29 | 2/4/2/5/2 \| 4/3 |
| EP06 | 5/5/5/4/5 \| 5/5 | EP18 | 5/5/5/4/5 \| 5/4 | EP30 | 2/3/2/5/2 \| 5/5 |
| EP07 | 4/5/4/5/4 \| 4/4 | EP19 | 4/4/3/4/4 \| 4/4 | EP31 | 3/5/5/2/2 \| 3/2 |
| EP08 | 5/5/4/5/5 \| 4/4 | EP20 | 4/3/2/4/4 \| 5/5 | EP32 | 5/5/5/2/4 \| 5/4 |
| EP09 | 4/4/3/4/4 \| 5/5 | EP21 | 4/5/4/4/5 \| 4/4 | EP33 | 4/4/4/3/5 \| 5/5 |
| EP10 | 5/5/4/5/5 \| 5/5 | EP22 | 4/5/4/5/4 \| 5/4 | EP34 | 4/5/5/2/3 \| 5/5 |
| EP11 | 5/4/5/5/5 \| 5/5 | EP23 | 4/5/4/5/4 \| 5/4 | EP35 | 3/4/5/1/3 \| 3/2 |
| EP12 | 5/4/4/5/5 \| 5/5 | EP24 | 4/4/4/5/4 \| 5/5 | EP36 | 4/4/5/1/3 \| 4/3 |

### 9.3 Notas arquiteturais e critérios de aceite

| ID | Arquitetura protegida | Critério de aceite do épico |
|---|---|---|
| EP01 | adapters V0, duas passagens, ranges estruturados | ordem de arquivos não altera símbolos/IR; diagnostics têm range e related |
| EP02 | tipos Harpia nunca são nomes Java | referência nominal/container é resolvida antes do generator |
| EP03 | ownership/lifecycle sem annotations | modelo com duas entidades gera mapping e teste coerentes |
| EP04 | expressão tipada comum, sem inglês livre | regra inválida falha no compiler; invariants protegem mutações |
| EP05 | pureza e AST tipada; sem runtime interpreter | Logic/Formula/Decision geram Java puro e testes equivalentes |
| EP06 | comportamento independente de protocolo | Command/Query têm efeito validado e endpoint opcional |
| EP07 | bindings são adapters | uma operação pode ser internal ou exposta sem duplicar o comportamento |
| EP08 | flow usa intenção, provider decide storage | save/load/delete geram app compilável sem JPA na Business IR |
| EP09 | diff sem depender do Java gerado | snapshot reproduzível produz migration revisável e breaking diagnostics |
| EP10 | policy sem `@PreAuthorize` na spec | autorização negada/permitida possui cenários gerados |
| EP11 | Event/Handler antes de broker | provider local passa antes de Kafka/AMQP entrar |
| EP12 | Integration é port tipado | client, DTO, config, failure mapping e mock compilam |
| EP13 | política conhece idempotência | retry nunca é aplicado a operação insegura sem diagnóstico |
| EP14 | cache/storage são capabilities | provider local prova contrato antes de Redis/S3 |
| EP15 | Schedule reutiliza Command | job reiniciável e testável sem expor APIs Spring Batch |
| EP16 | transition é validada no modelo | transição estática inválida falha antes do Java |
| EP17 | compensações são explícitas | falha intermediária executa compensação idempotente em cenário |
| EP18 | Scenario usa símbolos reais | testes são derivados e falham com source location da spec |
| EP19 | sensitive influencia todas as saídas | DTO/log/diagnostic não expõe dado marcado; audit é verificável |
| EP20 | tenancy permanece provider-neutral | mesma intenção prova ao menos duas estratégias em fixtures separadas |
| EP21 | writer nunca possui `custom/` | dois builds preservam byte a byte a implementação do usuário |
| EP22 | não clonar annotations/controllers | MVC fecha primeiro; cada transport reutiliza a mesma operação |
| EP23 | não vazar repository types | JPA fecha primeiro; provider alternativo mantém Business IR |
| EP24 | delivery semantics explícitas | provider local e um broker passam contract tests comuns |
| EP25 | Security implementa Access/Policy | JWT fecha primeiro com testes web/security |
| EP26 | IO nasce de Integration/Email/Storage | cada provider tem timeout, erros e mock determinísticos |
| EP27 | instrumentação é automática | command/query/integration expõem métricas sem DSL de logging |
| EP28 | testes seguem capabilities | suite gerada não exige rede; containers são opt-in por provider |
| EP29 | packaging não muda semântica | JAR roda; AOT/native são gates separados e reproduzíveis |
| EP30 | integrações por ADR e demanda | nenhum módulo Spring entra só para aumentar checklist |
| EP31 | CLI fina sobre core | CLI e MCP observam o mesmo resultado para a mesma request |
| EP32 | inteligência opera sobre modelos/source maps | diff/impact/explain são estáveis e nunca precisam parsear Java |
| EP33 | boundaries são símbolos, não packages Spring | violação cross-module aponta as duas localizações |
| EP34 | MCP não duplica compiler | STDIO read-only passa segurança antes de mutation/execution |
| EP35 | progressive disclosure | agente obtém somente grammar/capability solicitada e status real |
| EP36 | protocolo reprodutível | tokenizer, versões, corpus e resultados brutos são publicados |

## 10. Dependências críticas

```text
E0 end-to-end
  ├─► F1 Project AST + SymbolTable + SourceRange
  │     ├─► Value/Enum/relationships
  │     ├─► Command/Query/Event/Rules
  │     ├─► Semantic Diff/Impact/Explain
  │     └─► MCP semantic reads/mutations
  ├─► Scenarios + generated tests
  └─► Custom Java safe writer boundary

Command + Event + idempotency
  ├─► messaging providers
  ├─► integrations with retry
  └─► workflows/sagas
```

Providers seguem sempre: **semantic abstraction → capability contract → local/default provider →
distributed/alternative provider**.

## 11. Definições de MVP

### Proof MVP — target 0.1

Entity/CRUD V0 + Harpia Logic atual + Spring MVC/JPA/PostgreSQL/Flyway + tests + writer. Prova que
Markdown semântico pequeno produz uma aplicação convencional determinística.

### Semantic MVP — target 0.3

Project AST, SymbolTable, Value, Enum, Rules/Invariants, Command, Query, Flow, Event local,
Scenario e Custom Java. JWT/SMTP entram em slices próprios se os gates anteriores estiverem verdes.

### AI-native MVP — target 0.4

MCP STDIO com `project_info`, resources compactos de linguagem, `entity_get`, `project_validate`,
`project_build` e uma mutação atômica `add_field`/`apply_spec_patch` com dry-run e rollback.

## 12. Must Build Next — Top 10

1. E0.6: request/response records e contratos de erro no Java Target Model.
2. E0.7: services/controllers cobrindo os oito comandos Flow V0.
3. E0.9: testes gerados e baseline SCR (golden/determinismo/Maven já estão verdes).
4. F1: languageVersion, SourceRange, Project AST e SymbolTable global.
5. F2: source map por símbolo/linha, `inspect`, `why` e `explain` básicos.
6. S1: Command explícito + Event + `emit` + provider local.
7. S2: Value, Enum e relações básicas.
8. S3: Rules, Invariants e transaction boundaries.
9. S6: Custom implementation por contrato do target, DI e preservação.
10. M1: MCP read-only com target discovery vindo do mesmo `TargetCatalog`.

MCP vem imediatamente depois da fundação global; o writer já existe, mas Project AST, SymbolTable
e SourceRange ainda precisam estabilizar a API semântica exposta aos agentes.

## 13. Important Later

- Formula, Money/Percentage, Decision, collections e date/time sobre a fundação Logic existente;
- Scenario e property testing derivados de invariants;
- JWT, roles, Policy, SMTP, idempotency e OpenAPI;
- Integration HTTP, error mapping, timeout e retry seguro;
- cache/storage/scheduling/observability com primeiro provider local;
- MCP read-only, build e semantic mutation;
- semantic diff, impact, explain, why e breaking-change analysis.

## 14. Long-term

- brokers, NoSQL, reactive, GraphQL, WebSocket e SSE;
- database import e migration intelligence;
- state machines, batch, workflows, sagas e multi-tenancy;
- advanced packaging, AOT, native image e checkpoint/restore;
- Spring Cloud, Modulith, Authorization Server, Integration e AI somente após ADR/evidência.

## 15. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| grammar crescer como Java reduzido | checklist de keyword + Custom Java |
| provider vazar para Business IR | contract tests e dependency rule |
| matriz declarar suporte inexistente | gate end-to-end e evidência por path/teste |
| Logic avançar sobre fundação parcial | pausar L3+ até E0/F1 estabilizarem |
| generated sobrescrever código humano | manifesto possui só `generated/`; `custom/` fora da raiz |
| MCP ampliar superfície de ataque | read-only primeiro, workspace guard, sem shell arbitrário |
| percentuais virarem marketing | denominador versionado e fórmula explícita nas matrizes |
| expansão Spring virar checklist de annotations | medir categorias de aplicação, não classes/APIs |

## 16. ADRs recomendados

| ADR | Decisão |
|---|---|
| ADR-001 | Business IR vs Application IR e regras de dependência |
| ADR-002 | Flow vs Formula vs Decision vs Logic |
| ADR-003 | Provider contribution model e momento de SPI pública |
| ADR-004 | Project AST, namespaces e SymbolTable em duas passagens |
| ADR-005 | Generated vs Custom Java e ownership do writer |
| ADR-006 | Versionamento separado: compiler/language/MCP API |
| ADR-007 | MCP transport, segurança e mutações atômicas |
| ADR-008 | Modelo de execução sync/reactive sem `Mono`/`Flux` no negócio |
| ADR-009 | Boundary do target Spring e critérios para Spring Ecosystem |

## 17. Dashboard executivo

```text
CURRENT STATE
Frontend V0 e Logic L1/L2 reais; target boundary completo; entity/repository/Flyway e bootstrap
gerados pelo java-spring pack; writer, golden, determinismo, javac e Maven offline verdes.

NEXT MILESTONE
E0.6 — records de request/response e contratos de erro no Java Target Model.

TOP 10 PRIORITIES
E0.6, E0.7, E0.9 restante, F1, F2, S1, S2, S3, S6, M1.

ARCHITECTURAL BLOCKERS
Generator CRUD sem DTO/service/controller; sem Project AST/SymbolTable global/SourceRange;
versionamento de linguagem misturado; source map ainda só por arquivo.

QUICK WINS
DTO records no modelo existente; contracts de erro; source map de tipo; inspect read-only após F1.

LONG-TERM ROADMAP
Semantic core → scenarios/security/events → integrations/custom → MCP/intelligence → Spring expansion.

SPRING COVERAGE %
5.8% strict (8/138); 8.7% weighted progress. Método em ../SPRING_COVERAGE.md.

LANGUAGE COVERAGE %
9.3% strict (20/214); 17.5% weighted progress. Método em ../LANGUAGE_COVERAGE.md.
```
