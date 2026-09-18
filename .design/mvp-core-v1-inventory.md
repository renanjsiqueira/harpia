# Harpia Core V1 — inventário dos 14 registros canônicos abertos (S0)

> Plano: [`.specs/features/mvp-core-v1/plan.md`](../.specs/features/mvp-core-v1/plan.md) · Checks: [`checks.md`](../.specs/features/mvp-core-v1/checks.md), checks **C1** e **C4**, AC 1 e AC 4.
> Reconciliado em 2026-09-17 sobre `3c62a18`. Fonte do status canônico: [`BACKLOG.md`](../BACKLOG.md).
> Provas: `CoreV1PlanningTest#inventoryDistinguishesCanonicalStatusFromExistingEvidence` e `CoreV1PlanningTest#localEventsDoNotPromoteUpstreamDependencies`.

## O que este documento faz e o que ele não faz

Ele separa três coisas que o backlog hoje mistura em uma linha só: o **status canônico**, a
**evidência que já existe na árvore** e o **restante efetivo** depois dessa evidência. A separação
importa porque quatro dos registros abertos descrevem como faltante algo que já está implementado e
testado — construir de novo por causa da descrição seria trabalho pago duas vezes.

Ele **não muda status**. Nenhum item vira `DONE` aqui: `DONE` exige a evidência do recorte, que é o
que as slices S1–S8 produzem, e o encerramento é o check C83. A coluna de status canônico é cópia
verificável do backlog, não uma segunda contagem.

## Inventário

| ID | Status canônico | Evidência que já existe | Restante efetivo | Slice |
| --- | --- | --- | --- | --- |
| `CORE-010` | `PARTIAL` | escopo léxico em Logic: `LogicAnalyzerTest#aValueDeclaredInsideAConditionalDoesNotEscapeIt`, `LogicAnalyzerTest#valuesAreSingleAssignment`; escopo de ramo no Flow: `FlowConditionalTest#aBranchCannotDefineAVariableThatEscapesItsJavaScope` | o escopo não alcança valores produzidos por `call` nem o corpo de iteração; `LogicAnalyzerTest#memberAccessIsGrammarValidButSemanticallyUnavailable` fixa o limite que o spike reobservou como `HRP2114` | S1, S2 |
| `FLOW-014` | `PARTIAL` | `FlowCallTest#aPriorScalarResultCanFeedTheNextCall`, `FlowCallTest#aCommandCanBeCalledWithNamedTypedArguments`, `FlowCallTest#aCustomLogicContractIsInjectedAsTheTargetAdapter` — as três lacunas que o texto canônico ainda lista como faltantes já estão implementadas e provadas | o resultado do `call` não é visível para `set`/`require`/`fail`/`if` (`HRP2102` no spike); reconciliar o texto do registro em vez de reimplementar | S1, S6 |
| `FLOW-015` | `TODO` | nenhuma: `emit` é `HRP1007` no spike | instrução de emissão no parser, semântica e IRs | S5 |
| `FLOW-020` | `TODO` | nenhuma: `for each` é `HRP1007` no spike | iteração tipada com escopo próprio, no recorte que C21 delimita | S2 |
| `JAVA-004` | `PARTIAL` | natureza declarada atravessa os IRs e chega ao target: `OperationNatureTest#aDeclaredCommandIsTransactionalWhateverItsStepsAre`, `OperationNatureTest#aDeclaredQueryIsNeverTransactional`, `ApplicationLayerTransformerTest#theTransactionBoundaryFollowsTheOperationNotTheCode` | input e resultado ainda presumem a projeção CRUD da entidade (`HRP2011` no spike); a fronteira de uma entidade por operação recusa o agregado (`HRP2010`) | S0, S1, S3 |
| `CMD-008` | `PARTIAL` | `@Transactional` é gerado para Command hoje, por inferência da natureza — mesma evidência de `JAVA-004` | a política ser **declarada** na spec e preservada explicitamente na Application IR; recusa de política incompatível | S3 |
| `PERSIST-008` | `PARTIAL` | idem `CMD-008`: a anotação existe, a declaração não | mesma entrega transacional; não duplicar trabalho com `CMD-008` | S3 |
| `RELY-001` | `PARTIAL` | `IntegrationHttpClientTest#everyCallCarriesADeadlineWithoutMakingTheClientUntestable` — prazo `2s`/`10s` por `RestClientCustomizer` e `<Integration>Exception` com operação e status | as variantes de `#### Errors` chegam à Application IR (`ApplicationIntegration.Failure`) e **nenhum target as usa**; falta a regra de reconhecimento no binding e o tipo por variante | S4 |
| `EVENT-003` | `TODO` | nenhuma para emissão; o contrato do Event existe por `EVENT-001`/`EVENT-007` | mesma entrega tipada de `FLOW-015` | S5 |
| `EVENT-005` | `TODO` | nenhuma | provider local de entrega, ligado ao commit | S5 |
| `SPRING-008` | `TODO` | `EventContractTest#anEventBecomesAnImmutableRecordInItsOwnPackage` gera o record, sem publicar nada | implementação Java/Spring da mesma entrega local | S5 |
| `CUSTOM-002` | `PARTIAL` | `FlowCallTest#aCustomLogicContractIsInjectedAsTheTargetAdapter` — o texto canônico diz que falta injetar o bean, e a injeção já é gerada e compilada | executar um bean real do usuário no contexto, não apenas compilar a injeção | S6 |
| `CUSTOM-003` | `PARTIAL` | `CustomCodeOwnershipTest#aCustomImplementationSurvivesACleanRebuildByteForByte`, `#theManifestNeverClaimsAFileHarpiaDidNotWrite`, `#cleanWithoutForceRefusesRatherThanDecidingForTheUser` | o layout foi escolhido em AD-001; falta a prova Maven/Spring do layout e a documentação | S6 |
| `GREEN-003` | `TODO` | capacidades isoladas verdes; nenhuma aplicação as exercita junta | baseline integrada do Commerce e o gate do jar | S7, S8 |

Quatro registros — `FLOW-014`, `JAVA-004`, `CUSTOM-002`, `CUSTOM-003` — descrevem como faltante
algo que a árvore já prova. Reconciliar o texto desses registros é parte do encerramento (C83) e
não autoriza reimplementar a capacidade.

## Dependências reconciliadas

Dois registros abertos do Core dependiam de itens que vivem no horizonte Next. Deixar a dependência
como estava faria o Core puxar Handler DSL e messaging distribuído para dentro de si por herança
editorial, sem ninguém decidir isso. A correção foi escrita no `BACKLOG.md` antes de qualquer
trabalho dessas dependências, como AC 4 exige.

| Registro | Dependência anterior | Dependência reconciliada | Por quê |
| --- | --- | --- | --- |
| `EVENT-005` | `EVENT-001`, `EVENT-004` | `EVENT-001`, `EVENT-003` | entrega local precisa da emissão, não do consumidor declarativo; o consumidor da demonstração é Java custom |
| `GREEN-003` | `INTEG-001`, `MSG-001`, `SEC-007` | `INTEG-001`, `EVENT-005`, `SEC-007` | a baseline integrada precisa do evento **local**; messaging distribuído não é gate do bootstrap |

Continuam fora do Core, no horizonte Next do backlog, e nenhuma entrega deste plano os promove:

| ID | Onde continua | Por quê |
| --- | --- | --- |
| `DOM-015` | Next | Aggregate/Aggregate Root; o Commerce usa Entity, Value, Reference e relationships owned existentes |
| `DOM-016` | Next | ownership explícito de aggregate, que depende de `DOM-015` |
| `EVENT-004` | Next | Handler/On Event DSL; o consumidor de demonstração é Java custom |
| `MSG-001` | Next | capability e modelo lógico de messaging; evento local não promete entrega durável |
