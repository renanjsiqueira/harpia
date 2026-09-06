<!-- Gerado pelo Playground do Loop Code — documento "requirements".
     Objetivo: # Harpia — MVP Open Source

Quero que você projete e implemente o MVP de um novo projeto open source chamado **Harpia**.

Antes de escrever código, leia toda esta especificação, analise criticamente a proposta, identifique ambiguidades e riscos técnicos, proponha a arquitetura mínima e só depois com
     Editar aqui é permitido: a etapa seguinte lê o arquivo, não a memória do agente. -->
A escrita de `REQUIREMENTS.md` precisa da sua aprovação — o documento está pronto, mas não foi gravado. Aprove o write (ou me diga outro caminho, ex. `docs/requirements.md`) que eu persisto.

Resumo do que ele contém:

**Propósito** — Harpia é uma linguagem de especificação executável em Markdown cujo compilador determinístico e offline transforma `*.harpia.md` + `harpia.yaml` em um projeto Java 21 / Spring Boot 3 que compila e roda.

**Premissas** (workspace vazio, projeto novo): compilador em Java 21 + Maven; CLI como JAR + wrapper shell; layout `harpia.yaml` + `specs/` + `generated/`; uma entidade por arquivo; e um corte de escopo em duas faixas — M1 (CRUD, JPA, Flyway, validação, erros, testes) obrigatório, e Security/JWT, SMTP e eventos rejeitados com erro explícito em vez de geração parcial.

**Fora de escopo** — a lista proibida do enunciado, mais exclusões minhas: round-trip/merge de código editado, expressões livres no `flow`, plugins de template, LSP/IDE, migrações incrementais.

**9 histórias de usuário** e **41 critérios EARS** agrupados em: CLI e exit codes; determinismo e offline (build duas vezes → bytes idênticos, sem timestamp/hostname/aleatório, sem rede); parsing e validação (tipos, duplicidade, `flow` de vocabulário fechado, todos os erros reportados de uma vez); `harpia.yaml`; código gerado (entity, DTOs, repository, service, controller, advice, Flyway `V1__init.sql`, testes); e testes golden do compilador.

**Decisões que dependem de você** — as três que travam a etapa de arquitetura:

1. **Linguagem do compilador.** Assumi Java 21. TypeScript daria distribuição trivial (`npx harpia`) e Go daria binário único. É irreversível depois da próxima etapa.
2. **Escopo do MVP.** Confirma o corte M1/M2? Tentar Security + SMTP + eventos em 6 iterações tem risco alto de nada fechar de ponta a ponta.
3. **Superfície do `flow`.** Congelei 6 comandos. Falta decidir se o MVP cobre só `create` ou o CRUD completo (`update ... from input`, `list` com paginação, condicionais).

E mais: relacionamentos entre entidades entram no MVP? Qual métrica "prova a tese" (sugiro razão ≥20× spec→Java + build reprodutível)? O `generated/` é versionado? Licença/nome confirmados?

Por fim, um ponto de entrada: **o enunciado chegou truncado** — a lista de não-escopo termina em `* sinc` no meio da palavra. Assumi "sincronização bidirecional", coerente com a proibição de `Harpia ↔ Java`. Se havia itens depois disso, me envie.
