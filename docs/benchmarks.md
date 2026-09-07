# Harpia Semantic Compression Benchmarks

Status: protocolo planejado; nenhum resultado oficial foi publicado ainda.

Este documento impede que “economia de tokens” vire uma afirmação sem método. Linhas e caracteres
são proxies separados; qualquer métrica de tokens identifica tokenizer, versão do corpus,
compiler version, language version e configuração.

## 1. Perguntas

1. Quanto source convencional uma especificação Harpia representa?
2. Quanto contexto um agente precisa ler para realizar a mesma mudança?
3. Qual é o consumo total de tokens de um workflow completo, incluindo correções?
4. O resultado compila, passa testes e preserva comportamento equivalente?

Uma razão alta sem build/test equivalente não é sucesso.

## 2. Métricas

### SCR — Semantic Compression Ratio

```text
generated application tokens
────────────────────────────
Harpia source tokens
```

Reportar também arquivos, linhas não vazias e caracteres, mas nunca chamá-los de tokens. O D4
legado de `20×` por linhas continua como gate do V0 até existir baseline tokenizado; não será
silenciosamente reinterpretado como SCR de tokens.

### CCR — Context Compression Ratio

```text
traditional code context tokens read by the agent
─────────────────────────────────────────────────
Harpia semantic context tokens read by the agent
```

Inclui arquivos de documentação e tool results necessários. Cache invisível ou contexto
pré-carregado precisa ser declarado.

### TCR — Token Consumption Reduction

```text
1 - (Harpia workflow input+output tokens / traditional workflow input+output tokens)
```

Valores negativos são publicados. Repetições, diagnostics e tentativas falhas contam.

## 3. Métricas obrigatórias por run

```text
benchmark id and revision
scenario/task id
compiler version
language version
MCP API version, when applicable
Java/Spring/provider versions
tokenizer name and version
model and agent configuration, when an agent is measured
source tokens
generated tokens
context input tokens
agent output tokens
files read
files written
compiler diagnostics count
agent iterations/tool calls
wall-clock time (secondary, environment-labelled)
build success
test success
behavioral assertions passed
SCR / CCR / TCR
```

## 4. Corpus

| ID | Fixture | Semantic focus | Prerequisite | Status |
|---|---|---|---|---|
| B01 | customer | Entity/CRUD/REST/JPA/Flyway | E0.9 | current source exists; baseline pending |
| B02 | pricing | typed Logic/calls/conditionals | L1/L2 + writer | current source exists; baseline pending |
| B03 | order | Value/Enum/relationship/transaction | S2/S3 | planned |
| B04 | discount | Formula/Decision/Money/Percentage | L3–L5 | planned |
| B05 | auth | Access/roles/Policy/JWT | S4 | planned |
| B06 | payment | Command/idempotency/state | S4/S7 | planned |
| B07 | integration | typed HTTP integration/errors/retry | S5 | planned |
| B08 | messaging | Event/Handler/local/broker | S1/S8 | planned |
| B09 | workflow | orchestration/compensation | S9 | planned |
| B10 | spring-rest | MVC/controller/contracts/tests | E0/S4 | planned |
| B11 | spring-data | JPA plus second provider comparison | E0/S8 | planned |
| B12 | spring-messaging | local vs broker provider | S8 | planned |

Cada fixture contém:

```text
spec/
traditional-baseline/
expected-behavior/
benchmark.yaml
results/raw/
results/summary/
```

O baseline tradicional deve ser convencional, idiomático e revisado; um baseline artificialmente
verboso invalida a comparação.

## 5. Protocolos

### Compiler-only SCR

1. fixar commit, versions e tokenizer;
2. gerar duas vezes e exigir bytes idênticos;
3. compilar/testar o projeto gerado;
4. tokenizar `*.harpia.md` + partes semânticas necessárias de `harpia.yaml`;
5. tokenizar os artefatos convencionais equivalentes;
6. publicar contagens brutas e razão.

Generated tests podem ser reportados em duas visões: production-only e full-project. Nunca misturar
as duas séries.

### Agent CCR/TCR

1. escrever a mesma tarefa e critérios de aceite para Harpia e baseline Java;
2. usar modelo, budget, ferramentas e limite de tempo equivalentes;
3. começar de snapshots limpos;
4. registrar todo input/output e toda leitura/escrita;
5. executar build e behavioral tests independentes;
6. repetir no mínimo cinco vezes antes de publicar média, mediana e dispersão.

O avaliador de comportamento não pode depender do texto gerado pelo agente.

## 6. Result schema

Formato recomendado, planejado para `benchmarks/results/*.json`:

```json
{
  "benchmark": "B01-customer",
  "revision": "git-commit",
  "versions": {
    "compiler": "0.1.0-SNAPSHOT",
    "language": "legacy-v0",
    "mcpApi": null
  },
  "tokenizer": "name@version",
  "tokens": {
    "harpiaSource": 0,
    "generatedProduction": 0,
    "traditionalContext": 0,
    "harpiaContext": 0,
    "traditionalWorkflow": 0,
    "harpiaWorkflow": 0
  },
  "quality": {
    "build": false,
    "tests": false,
    "behavioralAssertions": 0
  }
}
```

Zeros acima são schema placeholders, não resultados.

## 7. Gates

| Milestone | Gate |
|---|---|
| B0 | script determinístico conta linhas/caracteres e registra tokenizer |
| B1 | Customer fecha Maven test e publica primeiro SCR bruto |
| B2 | Pricing mede Logic separadamente |
| B3 | AI-native MVP compara workflow MCP e Java por CCR/TCR |
| B4 | corpus ≥ 5 fixtures e repetição estatística |

Não definir meta universal de CCR/TCR antes de B3. O primeiro objetivo é reprodutibilidade e
equivalência; metas são propostas somente com baseline observado.
