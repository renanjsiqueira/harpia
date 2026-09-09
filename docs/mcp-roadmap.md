# Harpia MCP Roadmap

> **Referência de design não normativa.** O status e a prioridade canônicos do MCP vivem em
> [`../BACKLOG.md`](../BACKLOG.md), seção **MCP Agent API**.

Status: planejamento; nenhuma implementação MCP existe em 2026-09-06.

MCP será a API oficial para agentes. CLI continuará a interface humana. Ambas chamam o mesmo
`HarpiaCompiler` e os mesmos serviços de inspeção, escrita e teste; o servidor MCP não terá parser,
validador ou generator próprios.

## 1. Princípios

1. **AI-native does not mean AI-dependent.** O compiler continua local e determinístico.
2. **Context should be requested, not preloaded.** Resources pequenos e progressivos.
3. Read-only precede mutation; mutation precede execution remota.
4. Nenhuma tool aceita shell arbitrário, path fora do workspace ou edição de `generated/`.
5. Toda resposta é estruturada, versionada e inclui diagnostics quando aplicável.
6. A tool de build usa o mesmo core e writer da CLI.
7. Uma mutation é dry-run → parse → validate → atomic write; falha implica rollback.

## 2. Versionamento

Versões independentes:

| Version | Responsabilidade | Compatibilidade |
|---|---|---|
| Compiler version | comportamento do binário/generator | saída determinística para tuple completa de versões |
| Language version | grammar e semântica de `*.harpia.md` | selecionada explicitamente em `harpia.yaml` |
| MCP API version | schemas de resources/tools/results | breaking changes exigem nova major da API |

O handshake MCP deve publicar as três. O servidor rejeita mutation quando a language version do
projeto não é compreendida; resources de documentação continuam disponíveis.

## 3. Arquitetura alvo

```text
MCP STDIO transport
        ↓
workspace guard + request validation
        ↓
MCP facade
        ↓
Compiler / Inspector / SpecEditor / OutputWriter / TestRunner
        ↓
structured result + diagnostics + changed paths
```

O facade depende de interfaces do core. Picocli e classes MCP são adapters irmãos e não se chamam.

## 4. Security model

| Control | Requirement |
|---|---|
| Workspace guard | canonicalizar root; rejeitar absoluto, `..`, symlink escape e path externo |
| Read separation | tools read-only não escrevem cache, source ou generated output |
| Mutation scope | somente `harpia.yaml` e `specs/**/*.harpia.md` autorizados pela tool |
| Generated protection | mutations nunca editam `generated/`; build usa manifesto Harpia |
| Custom protection | MCP não edita `custom/` sem uma futura tool explicitamente autorizada |
| Atomicity | temp file + atomic move quando suportado; rollback em qualquer falha |
| Locking | uma mutation/build por project root; conflito retorna erro estruturado |
| Execution | allowlist de ações Harpia/Maven; nunca recebe comando shell livre |
| Diagnostics | paths relativos, sem secrets; valores sensíveis são redacted |
| Network | STDIO inicial não abre rede; HTTP só após threat model/ADR |

## 5. Resources

### Language resources — M1

```text
harpia://language/overview
harpia://language/version
harpia://language/grammar
harpia://language/types
harpia://language/entities
harpia://language/value-objects
harpia://language/rules
harpia://language/formulas
harpia://language/decisions
harpia://language/logic
harpia://language/flow
harpia://language/commands
harpia://language/queries
harpia://language/events
harpia://language/integrations
harpia://language/scenarios
harpia://language/capabilities
harpia://language/custom-java
harpia://language/coverage
harpia://language/examples
harpia://language/skill
```

Resources de feature incluem `status`, `sinceLanguageVersion`, grammar mínima, um exemplo válido,
diagnostics relevantes e links relacionados. Feature `PLANNED` nunca aparece como grammar aceita.

### Target resources — M1

```text
harpia://targets
harpia://target/java-spring
```

Eles serializam DTOs estáveis (`TargetInfo`, `TargetCapabilityInfo`) derivados do mesmo
`TargetCatalog` usado por `harpia targets`. Nunca expõem classes internas do transformer, renderer
ou Java Target Model. Targets `NOT_SUPPORTED` aparecem como metadata, sem capabilities fictícias.

### Project resources — M1/M2

```text
harpia://project/info
harpia://project/config
harpia://project/model
harpia://project/specs
harpia://project/entities
harpia://project/commands
harpia://project/queries
harpia://project/events
harpia://project/logics
harpia://project/integrations
harpia://project/capabilities
harpia://project/diagnostics
harpia://project/custom
```

`project/model` é paginado/filtrável. Não serializa toda a AST por default. Source text só é
retornado por resource/tool que o solicite explicitamente.

## 6. Tools

### Read-only — M1

| Tool | Input mínimo | Output | Gate |
|---|---|---|---|
| `project_info` | root | versions, modules, status | mesmo project summary em duas chamadas |
| `project_validate` | root | diagnostics + exit class | equivalente à CLI, sem escrita |
| `entity_list` / `entity_get` | root, optional name | summaries/detail | símbolos globais, paths relativos |
| `command_list/get` | root, optional name | contract/effects/bindings | depois de Command explícito |
| `query_list/get` | root, optional name | contract/effects/bindings | depois de Query explícita |
| `logic_list/get` | root, optional name | signature/typed summary | usa L1/L2 atual após Project AST |
| `formula_get` / `decision_get` | root, name | typed computation | só quando L3/L5 estiverem supported |
| `event_list/get` | root, optional name | payload/handlers | depois de S1 |
| `integration_list/get` | root, optional name | operations/errors/provider | depois de S5 |
| `diagnostics_get/explain` | root, optional code | structured diagnostics/docs | ranges/related locations em F1 |
| `project_inspect` | root, stage, filter | AST/symbols/IR view | mesma serialização de CLI inspect |
| `project_search` | root, structured query | symbols/references | lexical/structured primeiro |
| `get_language_capabilities` | optional filter | real coverage statuses | derivado da fonte canônica |
| `get_language_example` | feature/version | minimal valid source | exemplos executados em testes |
| `get_targets` | nenhum | ids, status e versões | mesmo `TargetCatalog` da CLI |
| `get_target` | target id | metadata estável | não expõe implementação interna |
| `get_target_capabilities` | target id | capabilities realmente geráveis | vazio para `NOT_SUPPORTED` |
| `analyze_impact` | root, symbol | dependency graph | depende de source maps/I2 |

### Build and execution — M2

| Tool | Safety | Gate |
|---|---|---|
| `project_build` | project lock + Harpia writer only | igual à CLI e retorna `WriteReport` |
| `project_test` | fixed Maven goal/profile, timeout e output bounded | generated project gate already stable |
| `project_clean` | only manifest-owned paths | unknown files survive unless explicit safe policy |
| `project_format` | source-only atomic rewrite | formatter is idempotent and preserves prose |
| `project_lint` | read-only | does not silently change validate semantics |

### Semantic mutation — M3

```text
add_entity        add_field          add_value_object
add_enum          add_command        add_query
add_rule          add_invariant      add_formula
add_decision      add_logic          add_event
add_email         add_integration    add_scenario
```

Cada tool recebe identificadores e valores tipados, nunca fragmento Markdown arbitrário. A primeira
mutation deve ser **uma** operação pequena (`add_field`) para provar o pipeline antes de ampliar o
catálogo.

### Semantic patch — M3

`apply_spec_patch` usa operações versionadas (`add`, `replace`, `remove`) sobre símbolos/fields,
não offsets frágeis. Response:

```text
dryRun
baseDigest
resultDigest
changedFiles
semanticDiff
diagnostics
applied
```

O digest impede lost update. `remove` exige impact analysis para referências existentes. A escrita
só acontece quando o resultado completo valida.

### Prompts — M4

```text
create-feature  add-entity  add-command  add-query  add-integration
add-event       add-authentication       fix-spec   explain-project
```

Prompts são conveniência sobre tools/resources já estáveis. Eles não carregam toda a linguagem nem
substituem schemas estruturados.

## 7. Fases e gates

| Phase | Scope | Depends | Acceptance |
|---|---|---|---|
| M0 | ADR, API version, schemas, workspace guard | F1 design | threat model e fixtures de path aprovados |
| M1 | STDIO + target/language/project resources + read tools | F1 Project AST/inspect | agente descobre target/capability e valida sem ler docs inteiras |
| M2 | build/test/clean/fmt/lint | E0.8/E0.9 + tooling | CLI/MCP produzem resultado equivalente |
| M3 | `add_field`, semantic patch e safe fixes | formatter + source ranges + locks | dry-run/applied/rollback e concurrent digest testados |
| M4 | prompts, search, impact/explain | semantic intelligence | respostas usam resources progressivos |
| M5 | HTTP transport | security evidence from STDIO | authn/authz, isolation, rate limit e audit aprovados |

## 8. AI-native MVP

O menor fluxo que prova a tese agent-native:

1. agente lê `harpia://language/coverage`;
2. chama `project_info` e `entity_get`;
3. executa `project_validate`;
4. faz dry-run de `add_field`;
5. inspeciona semantic diff e diagnostics;
6. aplica com digest;
7. chama `project_build`.

Gate: o agente não lê nem edita Java gerado, não recebe toda a documentação e não executa shell
arbitrário.

## 9. Deferred / not planned

- HTTP antes de STDIO estabilizar: `RESEARCH`.
- mutation textual sem schema: `NOT_PLANNED`.
- tool de shell arbitrário: `NOT_PLANNED`.
- edição de generated Java: `NOT_PLANNED`.
- LLM dentro do compiler/MCP core: `NOT_PLANNED`.
