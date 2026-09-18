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
**Where**: S0 e S1 fechados. **C1–C15 verdes**, com C13 e C15 parciais por construções que ainda não existem (`emit`, `for each`, política transacional, failure mapping); C16–C87 Pending. Commits `be46222` (S0), `c5d1234` e `1e3347b` (S1).
**In progress**: nada. S2 é a próxima slice e nenhum código dela foi escrito.
**Next step**: S2 pelos checks C16–C22 — `for each` de um nível, member access tipado sobre o item, escopo do corpo e as seis formas recusadas do recorte. A porta 5 do plano já fixa a forma literal.
**Blockers**: TG-03 e TG-04 continuam abertos e são provados em execução por S7.
**Settled nesta fase**: AD-003 (contratos), AD-004 (dependências), AD-005 (`SalesOrder`). Dois defeitos de geração corrigidos porque a fixture da slice os expôs: exceção de erro declarado não gerada para operação sem endpoint, e teste gerado afirmando sobre `response` num Command que responde `nothing`. Um terceiro ficou registrado no backlog em vez de corrigido: sob V0 o parser ainda aceita `set`, `add`, `remove` e `find`, e fechar isso muda o sentido de specs já escritas.
**Abandonado**: nada. As recusas do spike são estado do compiler, não caminhos tentados e descartados.
**Baseline**: `3c62a18`; `mvn -o test` em 2026-09-17 após S1: **635 testes, 0 failures, 0 errors, 0 skipped** (613 na baseline, 617 após S0).
**Verificação**: a skill exige um Verifier independente sobre `<base>..HEAD` depois da última slice. Ele ainda **não** foi despachado; S0 e S1 têm injeção de falha por superfície de asserção, que é o piso do perfil standard, não o relatório final.
**Uncommitted**: verificar antes de seguir; já existiam alterações em `.gitignore` e diretórios não rastreados `.agents/`, `.claude/`, `.cursor/`, `.windsurf/` e `tools/`.
**Branch**: `codex/harness-2521fbd0-624e7b04-d42f09e2`.
