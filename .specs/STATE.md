# Project state

## Decisions

| ID | Decision | Rationale | Status | Date |
| --- | --- | --- | --- | --- |
| AD-001 | O MVP usa `generated/src/main/java/<pacote>/custom/` para implementações do usuário; interfaces continuam no pacote `logic`; arquivos custom ficam fora do manifesto do writer. | Escolha explícita do usuário nesta conversa. Compila no source root Maven existente, sem plugin adicional. `generated/` com custom deixa de ser descartável como diretório inteiro. | active | 2026-09-17 |
| AD-002 | A execução e verificação de `mvp-core-v1` usam perfil `standard` da skill tlc-spec-lean. | Escolha explícita do usuário ao fixar os critérios: Coverage recomposta, revisão da política de testes e injeção de falhas por superfície de asserção. Não altera o perfil de outras features nem diretrizes globais do repositório. | active | 2026-09-17 |
| AD-003 | Os quatro contratos novos do Core V1 — Flow (input próprio, valores locais, `for each` de um nível), `### Transaction` com `required`/`read only`, `### Failures` no binding com variantes tipadas, e `emit` com entrega local adiada ao commit pelo publisher gerado — ficam fixados por [`.design/mvp-core-v1-contracts.md`](../.design/mvp-core-v1-contracts.md). | S0 respondeu TG-01/TG-02. Cada contrato traz forma literal, exemplo válido, exemplo recusado, mecanismo e alternativa rejeitada; as portas 5–8 entraram em `Landing` antes de qualquer código dependente. | active | 2026-09-17 |
| AD-004 | `EVENT-005` deixa de depender de `EVENT-004` e `GREEN-003` deixa de depender de `MSG-001`; as duas passam a depender da entrega local (`EVENT-003`, `EVENT-005`). | Dependência herdada estava puxando Handler DSL e messaging distribuído para dentro do Core sem decisão. A correção foi escrita no `BACKLOG.md` antes de qualquer trabalho dessas dependências, como AC 4 exige. | active | 2026-09-17 |
| AD-005 | A entidade do Commerce é declarada `SalesOrder`; as rotas de Surface continuam `/orders`. | Escolha explícita do usuário em 2026-09-17, diante do achado do spike: `order` é palavra reservada do PostgreSQL e `HRP2016` recusa a tabela antes da geração. Renomear a entidade custa zero trabalho de compiler; quoting de identificador e override de nome de tabela cresceriam o escopo de persistência sem critério que os peça. Onde o plano e os checks dizem `Order`, a entidade é esta. | active | 2026-09-17 |

## Handoff

**Feature**: `mvp-core-v1`.
**Where**: S0 fechado. Checks **C1–C4 verdes**; C5–C87 continuam Pending. Artefatos em [`.design/mvp-core-v1-inventory.md`](../.design/mvp-core-v1-inventory.md), [`mvp-core-v1-spike.md`](../.design/mvp-core-v1-spike.md) e [`mvp-core-v1-contracts.md`](../.design/mvp-core-v1-contracts.md); prova em `src/test/java/dev/harpia/CoreV1PlanningTest.java`.
**In progress**: nada. S1 é a próxima slice e nenhum código dela foi escrito.
**Next step**: S1 pelos checks C5–C15, sobre o contrato 1 do RFC — escopo empilhado no `LogicAnalyzer`, tipo nominal em `TypedExpression` e input que deixa de ser projeção obrigatória da entidade.
**Blockers**: TG-03 e TG-04 continuam abertos e são provados em execução por S7. O nome da entidade foi resolvido por AD-005: a fixture declara `SalesOrder` e conserva as rotas `/orders`.
**Settled nesta fase**: os quatro contratos (AD-003); a correção de escopo das duas dependências herdadas (AD-004); os códigos `HRP2145`–`HRP2149` reservados pelo RFC e ainda não declarados em `ErrorCodes`.
**Abandonado**: nada foi tentado e descartado nesta slice; as recusas observadas pelo spike são o estado do compiler, não caminhos abandonados.
**Baseline**: `3c62a18`; `mvn -o test` em 2026-09-17 após S0: **617 testes, 0 failures, 0 errors, 0 skipped** (613 anteriores + 4 de `CoreV1PlanningTest`).
**Plan validation**: `validate_plan.py mvp-core-v1` terminou com exit code 0, zero erros e um aviso pelas questões técnicas abertas; TG-01 e TG-02 agora estão resolvidos em `checks.md` e o aviso permanece por TG-03/TG-04.
**Uncommitted**: verificar antes de seguir; já existiam alterações em `.gitignore` e diretórios não rastreados `.agents/`, `.claude/`, `.cursor/`, `.windsurf/` e `tools/`.
**Branch**: `codex/harness-2521fbd0-624e7b04-d42f09e2`.
