# Fechamento do MVP — Harpia Core V1

> Plano correspondente: [`.specs/features/mvp-core-v1/plan.md`](../.specs/features/mvp-core-v1/plan.md), no formato de [tlc-spec-lean / plan](../.cursor/skills/tlc-spec-lean/references/plan.md).
> Discovery em 2026-09-17 sobre `3c62a18`. Decisões confirmadas, propostas e gates técnicos estão separados.

## Situation

- Project: em construção; há compiler e exemplos executáveis, mas não há evidência de conclusão do Commerce Service exigido pelo Core.
- Decision: o usuário solicitou o plano até finalizar o MVP; o compromisso de construir já está no `BACKLOG.md`. A escolha do layout custom foi confirmada pelo usuário nesta conversa.
- In flight: a implementação de `FLOW-014` avançou além da descrição canônica. Não foram encontradas instruções `AGENTS.md` nem decisões anteriores em `.specs/STATE.md`. O workspace contém alterações preexistentes fora deste planejamento.
- At stake: expandir a linguagem além do bootstrap atrasaria o fechamento; fixar sintaxe, semântica transacional ou ownership errados criaria contratos difíceis de mudar.

## Problem

O developer ainda não consegue demonstrar, em uma única aplicação de referência, a composição de domínio, fluxo de aplicação, antifraude HTTP, evento local, autenticação, testes, jar e handoff. Testes separados de capacidades não comprovam que elas funcionam juntas. Isso impede concluir o compromisso do Core V1 e deixa indefinido o ponto em que o desenvolvimento segue em Java comum.

É um problema de construção. Não há métricas de usuários, receita ou horas perdidas no backlog; não se atribui urgência numérica a essa ausência. A unidade de progresso é um comportamento integrado demonstrável e o fechamento dos blockers canônicos com evidência.

## Evidence

A medida que altera a sequência é quantos blockers ainda carecem de implementação versus quantos já têm código e precisam de validação integrada.

- Recontagem dos itens canônicos do Core, incluindo `Core V1 Escape Hatch`: **213 itens; 199 DONE, 8 PARTIAL, 6 TODO**. São **14 abertos**. A tabela agregada ainda diz 176/16/21 e não representa os registros atuais. A fórmula do backlog daria 95,3%; isso é contagem editorial, não percentual comprovado de prontidão.
- `mvn -o test` executado nesta discovery: **BUILD SUCCESS; 613 testes; 0 failures, 0 errors, 0 skipped**. O snapshot de 520 testes é histórico. A execução inclui o package gate do Customer, não o futuro Commerce.
- `FlowCallTest` já cobre resultado escalar anterior, `call` de Command e injeção de contrato custom, com Java compilável. `FLOW-014` e `CUSTOM-002` precisam ser reconciliados antes de virarem implementação repetida.
- `SemanticValidator.validateInput` ainda prende inputs a campos da entidade; `target` recusa mais de uma entidade; `TypedExpression` não tem member access tipado. Esse é o risco real de `JAVA-004`/`CORE-010`, além da sintaxe de loop.
- O provider JWT gera `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))`, enquanto role exige `ROLE_ADMIN`. A prova conjunta com token real e claim de roles ainda precisa ser construída; testes isolados não a substituem.
- Event e record já existem. `emit`, entrega local e vínculo com commit ainda faltam. `RELY-001` já tem timeout e exceção da porta; faltam variantes nomeadas reconhecidas pelo binding.

## Journey

O developer escreve specs e bindings, valida o projeto, inspeciona os modelos, gera a aplicação, fornece implementações custom declaradas, executa testes e package, recebe o handoff e continua em Java. Essa sequência vem do contrato do backlog.

Estados a cobrir: diagnóstico de spec inválida sem escrita; lista vazia; chamada cuja dependência falha; transação revertida; chamada sem identidade ou role; custom declarado sem bean; reconstrução com arquivos do usuário; gates Maven ainda pendentes no handoff. As respostas propostas estão nos critérios do plano, sem presumir aprovação das novas regras de negócio do exemplo.

## Verdict

Already committed — see Situation. Fechar o Core V1 sobre a implementação existente; o pedido é de planejamento, e este documento não declara o MVP entregue.

Cheaper paths considered: implementar o Commerce inteiro à mão em Java demonstraria um backend, mas não provaria o compiler; manter só Customer CRUD não exercita os blockers; usar código custom apenas nos pontos de extensão declarados preserva o bootstrap e é parte do caminho escolhido.

## Success

- Worked if: um developer parte do Commerce canônico e obtém uma aplicação testada, empacotada e editável, com as pendências custom explícitas, sem corrigir manualmente arquivos de propriedade do compiler para passar o gate.
- Early signal: o primeiro incremento consome um resultado de Logic em outro passo e executa uma implementação custom; se exigir refazer capacidades já existentes ou ampliar a DSL sem relação com o exemplo, a sequência saiu do recorte.
- Review: ao concluir cada slice e no gate final, o mantenedor confronta a demonstração com os critérios; prazo de calendário não foi informado.

## Boundary

In: os 14 itens abertos do Core, a composição das capacidades já prontas e a cadeia automática `parse → validate → build → compile → test → package → handoff` do Commerce.

Out: Aggregate/Aggregate Root, lifecycle genérico, Formula/Decision, Money, query engine avançado, Kafka, Handler DSL, messaging distribuído, retries, idempotência de negócio, outbox, MCP, brownfield, outro target e Managed Mode. Os registros `DOM-015/016`, `EVENT-004`, `MSG-001` e `RULE-004` pertencem ao Next; dependências antigas nas tabelas não os promovem silenciosamente ao Core. O plano inclui uma reconciliação explícita dessas referências.

## Prior art

- `GeneratedMavenProjectTest`: reaproveitar o gate offline e a inspeção de jar, ampliando a fixture para Commerce.
- `FlowCallTest` e `CustomCodeOwnershipTest`: preservar chamadas tipadas e ownership existentes; completar a execução Spring com bean real.
- `EventContractTest` e `IntegrationHttpClientTest`: estender contratos/providers já presentes, sem uma segunda infraestrutura de geração.
- Comparação limitada ao repositório: o escopo é concluir o target existente, sem seleção de fornecedores ou bibliotecas externas.

## Shape

Incrementar o pipeline existente e fazer o Commerce crescer junto com cada slice. O target continua `java-spring`; código custom usa o source root Maven convencional já escolhido. Contratos novos de linguagem precisam do gate S0 antes de se tornarem sintaxe publicada.

### Adds

- `examples/commerce/`: fixture canônica com Customer, Address, Product, Order, OrderItem, CustomerTier, OrderStatus, CalculateDiscount, CalculateTotal, CreateOrder, CancelOrder, GetOrder, SearchOrders, FraudService e OrderCreated.
- Iteração tipada em Flow, reconhecimento de erros outbound e emissão local; nomes exatos e semântica nova serão registrados na saída de S0.
- Provas integradas do banco, antifraude, JWT com ADMIN, evento após sucesso e implementação custom no layout acordado.

### Changes

- `LogicAnalyzer`, `SemanticValidator`, `Resolver` e IRs: completar uso dos resultados e escopos no recorte Commerce, sem perpetuar a limitação incidental de CRUD.
- `JavaSpringServiceTransformer` e transformers de testes: executar a composição e os novos efeitos com Java compilável.
- `JavaSpringIntegrationClientTransformer`: mapear variantes declaradas, preservando timeouts e a fronteira da porta.
- Provider/security e handoff: comprovar a composição e registrar as extensões necessárias, preservando os contratos existentes.
- `BACKLOG.md`: reconciliar evidências, contagem, dependências e resumos durante a execução; este planejamento não marca itens como DONE.

### Leaves

As exclusões de Boundary permanecem fora do fechamento. `examples/ecommerce` continua como exemplo existente de PurchaseOrder; o Commerce novo não renomeia sua API.

A alternativa de uma nova arquitetura de aplicação/provider só venceria se o spike mostrasse que o caminho existente não consegue representar o Commerce sem semântica incorreta. Não há evidência atual para reescrever o pipeline. Para custom, a alternativa de diretório irmão exigiria resolver um source root adicional e o gate offline; o usuário escolheu o layout interno.

## Roadmap

| Block | Delivers | Clarity |
| --- | --- | --- |
| S0 — recorte executável | Fixture mínima e resposta sobre as limitações de inputs, resultados e ownership | spike |
| S0 — contratos novos | Registro literal de iteração, transação, falhas e momento de emissão | rfc |
| S1 — composição | Calls existentes reconciliados e valores de Flow utilizáveis | clear |
| S2 — iteração | Itens de um pedido processados na ordem declarada | rfc |
| S3 — atomicidade | Command composto com rollback demonstrado | rfc |
| S4 — antifraude | Falhas HTTP nomeadas e resultado consumido pelo Flow | rfc |
| S5 — evento local | OrderCreated observado apenas na condição de sucesso definida | rfc |
| S6 — custom | Bean do usuário executado e preservado no layout escolhido | clear |
| S7 — Commerce | Uma aplicação reúne todos os comportamentos; spike adicional para JWT + ADMIN | spike |
| S8 — fechamento | Gates, documentação, handoff e backlog consistentes | clear |

## Decisions

| Decision | Choice | Why this | Alternative, and what would make it win | Reversibility |
| --- | --- | --- | --- | --- |
| Fonte de escopo | Itens individuais de `BACKLOG.md`, com evidência no código | Resumos e contagens estão desatualizados | Usar tabelas agregadas somente depois de reconciliadas | reversible |
| Produto e target | Greenfield bootstrap; `java-spring`; Java `21` | Compromisso oficial do Core | Novo target quando houver objetivo posterior específico | costly |
| Custom — confirmado pelo usuário | `generated/src/main/java/<pacote>/custom/`; contrato no pacote `logic`; bean do usuário fora do manifesto | Maven e component scan existentes, sem plugin extra | `custom/` irmão se isolamento físico justificar resolver o build offline | costly |
| Aplicação canônica | Novo `examples/commerce/` | Evita renomear PurchaseOrder e mantém a regressão atual | Evoluir ecommerce se houver decisão explícita de substituir seu contrato | reversible |
| Event do MVP | Provider local no processo; consumo de demonstração em Java custom | Event local é obrigatório, broker e Handler DSL são Next | Broker quando houver requisito de entrega entre processos | costly |
| Entrega | Gates executados no projeto gerado e handoff honesto sobre quem os executou | O compiler não deve inventar sucesso Maven | Orquestrar Maven no compiler só com novo contrato de produto | costly |

## Needs an RFC

S0 produz um registro curto em `.design/mvp-core-v1-contracts.md`, com exemplos válidos/recusados e alternativas. O documento ainda não existe. Ele bloqueia o congelamento das superfícies novas de S2–S5, não a validação das capacidades existentes.

1. **Flow/Command além de CRUD:** tipos de input e resultados, leitura de campos de valores locais, associação owned e compatibilidade V0/V1. Recomendação: extensão mínima demonstrada pelo Commerce; não adotar modelo genérico de aggregate.
2. **Iteração:** expressão de coleção, escopo do item, ordem, limite de aninhamento e alterações permitidas. Recomendação: um nível, coleção finita, sem async/break/continue e sem alterar a coleção iterada; alterar outras entidades/coleções continua possível.
3. **Transação e eventos:** política declarativa e composição entre Commands. Recomendação: Command participa de uma transação local obrigatória, Query read-only, evento observado após commit e nenhum evento em rollback. Definir mecanismo, comportamento de listener que falha e ausência de garantia de entrega durável antes de publicar a promessa.
4. **Falhas de Integration:** gramática no binding para status/timeout/resposta inválida → variante declarada; tradução da variante para erro de operação. Recomendação: mapeamento explícito, sem retry automático e sem incorporar status HTTP à assinatura semântica da porta.

Cada tópico termina quando há assinatura literal, caso de sucesso, caso de recusa e mecanismo compatível com o pipeline. Se a alternativa mudar o escopo do Core, registrar a questão de produto antes de adotá-la.

## Needs a spike

1. **S0: o menor CreateOrder atravessa o pipeline sem falsificar o domínio?** Exercitar uma coleção de itens, um resultado de Logic usado no total, resultado de Command e `OrderCreated` com identidade do pedido. Inventariar as recusas atuais. Termina com um exemplo mínimo e uma tabela restrição → extensão mínima → slice; não com o Commerce implementado. Se a adaptação pequena bastar, preservar a arquitetura; se exigir mudança de contrato externo, resolver no RFC antes do código de produção.
2. **S7: JWT real chega à regra ADMIN?** Token assinado localmente, JWKS de teste e `roles: ["ADMIN"]`, com sucesso, 401 e 403. Termina quando o teste distingue autenticação de conversão de authorities; se faltar conversão, acrescentar a configuração necessária no target e sua prova. Não transformar isso em plataforma de identidade.

## Open

Defaults propostos para o exemplo, ainda não confirmados pelo usuário: Address como ValueObject embedded; itens owned de Order; `CREATED → CANCELLED` com repetição recusada por 409; recusas de fraude por 422 e indisponibilidade por 503. O plano registra esses defaults e não os apresenta como exigências originais do backlog.

Esforços relativos e dependências constam no plano; não há estimativa de calendário sem capacidade da equipe e sem o resultado do spike.

## Sources

- [BACKLOG.md](../BACKLOG.md): produto, IDs, Core, exclusões e Definition of Done.
- [FlowCallTest](../src/test/java/dev/harpia/validate/FlowCallTest.java): avanços de calls além do texto do backlog.
- [SemanticValidator](../src/main/java/dev/harpia/validate/SemanticValidator.java) e [TypedExpression](../src/main/java/dev/harpia/logic/TypedExpression.java): limites atuais de inputs, entidade e expressões.
- [JavaSpringServiceTransformer](../src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringServiceTransformer.java): composição, injeção e transações atuais.
- [JavaSpringSecurityTransformer](../src/main/java/dev/harpia/target/javaspring/transformer/JavaSpringSecurityTransformer.java): JWT e regras de role existentes.
- [CustomCodeOwnershipTest](../src/test/java/dev/harpia/emit/CustomCodeOwnershipTest.java): preservação de custom.
- [GeneratedMavenProjectTest](../src/test/java/dev/harpia/target/javaspring/GeneratedMavenProjectTest.java): package do projeto gerado.
- [HandoffManifest](../src/main/java/dev/harpia/cli/HandoffManifest.java): contrato atual de ownership, custom e gates.
- Conversa de 2026-09-17: pedido do plano e escolha explícita de custom dentro de generated.
