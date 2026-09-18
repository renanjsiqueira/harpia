# Harpia Core V1 — critérios de aceite fixados

Profile: standard
Plan: [plan.md](plan.md)
Baseline: `3c62a18`
Data do congelamento: **2026-09-17**.

**87 checks em 9 slices; 65 critérios do plano rastreados; 61 conjuntos com 252 membros atribuídos; 4 portas do plano; 4 gates técnicos ainda abertos.**

O pedido de fixar os critérios autoriza este desdobramento do plano. O usuário escolheu explicitamente o perfil **standard**; o layout custom permanece o da decisão AD-001. Escopo e resultados observáveis ficam fixados por este documento. Os contratos técnicos novos continuam sujeitos a S0 antes da implementação dependente.

## Status

**C1–C4 fechados por S0 e C5–C15 por S1**, com C13 e C15 incompletos por construções que ainda não existem; os 72 restantes continuam **Pending**. Nenhum resultado do Commerce foi executado. Os 613 testes verdes da baseline são evidência anterior do repositório; não encerram os checks novos.

| Check | Seletor executado | Relatório | Asserção localizada |
| --- | --- | --- | --- |
| C1 | `mvn -o '-Dtest=CoreV1PlanningTest#inventoryDistinguishesCanonicalStatusFromExistingEvidence' test` | 1 caso, 0 failures, 0 errors, 0 skipped | os 14 IDs do inventário, cada status conferido contra a linha do `BACKLOG.md` e cada seletor citado localizado em `src/test/java` |
| C2 | `mvn -o '-Dtest=CoreV1PlanningTest#spikeRecordsInputsObservedResultsAndOwningSlices' test` | 1 caso, 0 failures, 0 errors, 0 skipped | as 7 fixtures de [`mvp-core-v1-spike.md`](../../../.design/mvp-core-v1-spike.md) recompiladas; `input`→`HRP2011`, `member-access`→`HRP2114`, `command-result`→`HRP2102`, `owned`→`HRP2010`, com a tabela do relatório conferida contra o código observado |
| C3 | `mvn -o '-Dtest=CoreV1PlanningTest#everyNewContractHasLiteralExamplesAndMechanism' test` | 1 caso, 0 failures, 0 errors, 0 skipped | os 4 contratos de [`mvp-core-v1-contracts.md`](../../../.design/mvp-core-v1-contracts.md) com as 5 partes cada; códigos reutilizados existem em `ErrorCodes` e os 5 reservados estão livres |
| C4 | `mvn -o '-Dtest=CoreV1PlanningTest#localEventsDoNotPromoteUpstreamDependencies' test` | 1 caso, 0 failures, 0 errors, 0 skipped | `EVENT-005` e `GREEN-003` lidos do `BACKLOG.md` após a correção de escopo; `DOM-015`, `DOM-016`, `EVENT-004` e `MSG-001` declarados depois do horizonte Next |

| C5 | `FlowCallTest#aPriorScalarResultCanFeedTheNextCall`, `FlowCompositionRuntimeTest#secondCallConsumesTheFirstResult` | 2 casos, 0 failures | input 100.00 → primeira Logic 10.00 → segunda recebe 10.00 e devolve 1.00; a asserção recusa também 10.00, que seria reler o input |
| C6 | `FlowCallTest#aCommandCanBeCalledWithNamedTypedArguments`, `FlowCompositionRuntimeTest#namedCommandArgumentsReachTheCallee` | 2 casos, 0 failures | o Command chamado grava `orderNumber="A-1"` e `amount=100.00` nas duas ordens de escrita dos argumentos |
| C7 | `FlowCompositionRuntimeTest#setConsumesTheVisibleLocalResult` | 1 caso, 0 failures | `total` observado 10.00 e não 100.00 |
| C8 | `FlowCompositionRuntimeTest#requireUsesTheLocalBoolean` | 1 caso, 0 failures | true → marcador 1.00; false → `RejectedOrderException` e zero gravações |
| C9 | `FlowCompositionRuntimeTest#failUsesTheLocalBoolean` | 1 caso, 0 failures | true → erro e zero gravações; false → marcador 1.00 |
| C10 | `FlowCompositionRuntimeTest#ifUsesTheLocalBoolean` | 1 caso, 0 failures | marcador 1.00 no ramo verdadeiro e 2.00 no falso |
| C11 | `FlowScopeTest#invalidReferencesPointToTheirExactSourceRange` | 1 caso, 0 failures | `HRP2102` na linha e **coluna** exatas do nome, nos dois usos: antes da declaração e fora do bloco |
| C12 | `CommandCallGraphTest#cyclesAreRejectedAndAnAcyclicChainIsAccepted` | 1 caso, 0 failures | A→B→C aceito; A→B→C→A recusado com o caminho na mensagem; A→A recusado onde foi escrito |
| C13 | `OperationNatureTest#queriesRejectTransitiveCommandEffectsAndEmit` | 1 caso, 0 failures | **parcial**: `call` direto e transitivo provados (`HRP2120` nomeando `RecordVisit -> ArchiveVisit`); o membro `emit` fecha em S5, quando a instrução existir |
| C14 | `ComposedCommandTest#transientInputAndResultDoNotRequireFakeEntityFields` | 1 caso, 0 failures | `discountRate` atravessa Business IR e Application IR, chega ao request e ao call; ausente da entidade e da migration; árvore compilada por javac |
| C15 | `JavaSpringGoldenTest#customerProjectMatchesTheVersionedTargetGolden`, `FlowCallTest#versionZeroDoesNotSilentlyAcquireCalls`, `CoreV1CompatibilityTest#v0RejectsCoreV1OnlyConstructs` | 10 casos, 0 failures | **parcial**: 8 construções aceitas sob V1 e recusadas sob V0 pela mesma fixture; `for each`, `emit`, política transacional e failure mapping entram nas slices que as criam |

A execução completa em 2026-09-17 sobre esta árvore: `mvn -o test` — **635 testes, 0 failures, 0 errors, 0 skipped** (613 na baseline). Injeção de falha exercitada nas seis superfícies de asserção de C1–C4 (código esperado, status canônico, seletor citado, parte do contrato, código reservado e dependência revertida) e nas seis de S1 (visibilidade dos locais, coluna da referência, detecção de ciclo, efeito de Query, cópia do input e gate da V0); cada uma falhou como devia antes de ser restaurada.

Há **23 seletores existentes** e **94 seletores a criar**, todos identificados nas linhas Proof. Um seletor a criar é a assinatura prevista do teste que será escrito a partir do check; não é uma alegação de que o teste existe ou passou. A fase de implementação deve criar a prova antes do código que satisfaz o comportamento.

O comando real é Maven/Surefire do [pom.xml](../../../pom.xml): `mvn -o '-Dtest=Classe#metodo' test`, a partir da raiz do repositório. Não há workflow de CI configurado em `.github/` nesta baseline. Não usar `-DskipTests`, `-DfailIfNoTests=false` ou `-Dsurefire.failIfNoSpecifiedTests=false`. Cada seletor deve existir, executar pelo menos um caso e terminar sem failure, error ou skipped. Os testes que geram aplicações executam o Maven do projeto gerado por dentro da prova específica, como o precedente GeneratedMavenProjectTest.

**O que pode começar:** S2, sobre a parte de iteração do contrato 1 do RFC. **O que continua aberto:** TG-03 e TG-04, provados em execução por S7, e a pendência do nome da entidade `Order` registrada no spike. O documento congela o resultado exigido; não inventa uma gramática, um mecanismo transacional ou um provider já decidido.

| Gate | Kind | Obrigação / condição de saída |
| --- | --- | --- |
| TG-01 | ~~blocks~~ **resolvido em S0** | C2 demonstrou o recorte mínimo: `HRP2011`, `HRP2114`, `HRP2102` e `HRP2010`; C3 registrou a extensão no contrato 1 do RFC |
| TG-02 | ~~blocks~~ **resolvido em S0** | C3 registrou sintaxe, semântica e mecanismo dos quatro contratos; as portas entraram em `Landing` do plano antes de qualquer código dependente |
| TG-03 | blocks | C63 e C68 atravessam JWT real e sua conversão de roles; não substituir por Authentication injetada no teste |
| TG-04 | blocks go-live | C69, C76 e C86 exercitam PostgreSQL/HTTP/JWKS locais e as três montagens da aplicação; falta de infraestrutura não vira teste ignorado |

Os checks C1–C4 têm provas a criar para verificar os artefatos e executar as fixtures do spike. Essas provas verificam dados e resultados observados; a revisão do RFC continua necessária para julgar a escolha de arquitetura. Sua existência não encerra uma questão de produto por conta própria.

Onde estes checks dizem `Order` — C51 entre as 15 declarações, e a representação de C52/C55 — a entidade é a declarada como **`SalesOrder`**, por AD-005: `order` é palavra reservada do PostgreSQL e `HRP2016` a recusa antes da geração. As rotas de Surface continuam `/orders` e nenhum resultado observável destes checks muda; o que muda é o identificador da declaração e o nome dos tipos gerados a partir dele.

Os nomes `Unavailable`, `TimedOut` e `InvalidResponse` são variantes das fixtures de aceite. A sintaxe para declará-las/mapear gatilhos e sua representação Java serão registradas em S0; os checks não escolhem entre subclasses e outro modelo tipado.

## Checks

### S0 — Evidências e contratos do recorte Core

**C1** — *fechado por S0.* O inventário contém exatamente os 14 IDs abertos listados no plano e separa status canônico de evidência existente; nenhum item é marcado DONE por este congelamento. (MVP-001; AC 1)
Proof: `mvn -o '-Dtest=CoreV1PlanningTest#inventoryDistinguishesCanonicalStatusFromExistingEvidence' test` — **verde**. Artefato: [`mvp-core-v1-inventory.md`](../../../.design/mvp-core-v1-inventory.md).

**C2** — *fechado por S0.* O relatório de S0 registra os quatro casos executados — input de Command, acesso a campo, resultado de Command e associação owned — com spec de entrada, diagnóstico ou saída observada e slice responsável pela lacuna. (MVP-001, JAVA-004; AC 2)
Proof: `mvn -o '-Dtest=CoreV1PlanningTest#spikeRecordsInputsObservedResultsAndOwningSlices' test` — **verde**. Artefato: [`mvp-core-v1-spike.md`](../../../.design/mvp-core-v1-spike.md).

**C3** — *fechado por S0.* O RFC registra os quatro contratos — Flow, transação, falha outbound e evento — com forma literal, exemplo válido, exemplo recusado, mecanismo e alternativa rejeitada em cada um. (MVP-001, JAVA-004; AC 3)
Proof: `mvn -o '-Dtest=CoreV1PlanningTest#everyNewContractHasLiteralExamplesAndMechanism' test` — **verde**. Artefato: [`mvp-core-v1-contracts.md`](../../../.design/mvp-core-v1-contracts.md).

**C4** — *fechado por S0.* As dependências reconciliadas de EVENT-005 e GREEN-003 apontam para entrega local; DOM-015, DOM-016, EVENT-004 e MSG-001 continuam fora do Core. (MVP-001; AC 4)
Proof: `mvn -o '-Dtest=CoreV1PlanningTest#localEventsDoNotPromoteUpstreamDependencies' test` — **verde**. Artefato: `BACKLOG.md`, correção de escopo de `EVENT-005` e `GREEN-003`.


### S1 — Composição de operações e valores locais

**C5** — *fechado por S1.* Uma primeira Logic retorna 10.00 e a segunda recebe 10.00, embora o input original seja 100.00; a saída observada da segunda é a calculada com 10.00. (FLOW-014; AC 5)
Proof: `mvn -o '-Dtest=FlowCallTest#aPriorScalarResultCanFeedTheNextCall' test` — **existente**.
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#secondCallConsumesTheFirstResult' test` — **verde**.

**C6** — *fechado por S1.* Um Command interno recebe os argumentos pelos nomes declarados; inverter sua ordem escrita mantém os valores observados pelo Command chamado. (FLOW-014, JAVA-004; AC 6)
Proof: `mvn -o '-Dtest=FlowCallTest#aCommandCanBeCalledWithNamedTypedArguments' test` — **existente**.
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#namedCommandArgumentsReachTheCallee' test` — **verde**.

**C7** — *fechado por S1.* Depois de uma chamada produzir 10.00, set grava 10.00 no campo total da entidade; o input original 100.00 não é usado nessa atribuição. (CORE-010, FLOW-014; AC 7)
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#setConsumesTheVisibleLocalResult' test` — **verde**.

**C8** — *fechado por S1.* require avalia o Boolean local: true continua até o marcador seguinte; false levanta o erro declarado e o marcador seguinte permanece não executado. (CORE-010, FLOW-014; AC 7)
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#requireUsesTheLocalBoolean' test` — **verde**.

**C9** — *fechado por S1.* fail avalia o Boolean local: true levanta o erro declarado; false alcança o marcador seguinte. (CORE-010, FLOW-014; AC 7)
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#failUsesTheLocalBoolean' test` — **verde**.

**C10** — *fechado por S1.* if avalia o Boolean local: true produz apenas o marcador do ramo verdadeiro e false apenas o do ramo falso. (CORE-010, FLOW-014; AC 7)
Proof: `mvn -o '-Dtest=FlowCompositionRuntimeTest#ifUsesTheLocalBoolean' test` — **verde**.

**C11** — *fechado por S1.* Nome usado antes da declaração e nome usado fora do seu bloco são recusados com código do livro-razão e range exato da referência; nenhum dos dois chega à geração Java. (CORE-010, FLOW-014; AC 8)
Proof: `mvn -o '-Dtest=FlowScopeTest#invalidReferencesPointToTheirExactSourceRange' test` — **verde**.

**C12** — *fechado por S1.* Os ciclos de Commands A→A e A→B→A são recusados; a cadeia A→B sem retorno a A é aceita. (FLOW-014; AC 9)
Proof: `mvn -o '-Dtest=CommandCallGraphTest#cyclesAreRejectedAndAnAcyclicChainIsAccepted' test` — **verde**.

**C13** — *parcial em S1; fecha em S5.* Query que chama Command, diretamente ou por chamada intermediária, e Query que executa emit são recusadas por efeito de mutação. (JAVA-004; AC 10)
Proof: `mvn -o '-Dtest=OperationNatureTest#queriesRejectTransitiveCommandEffectsAndEmit' test` — **verde para `call` direto e transitivo; o membro `emit` fecha em S5**.

**C14** — *fechado por S1.* O input transitório e o resultado composto da fixture aprovada em S0 atravessam os IRs e geram Java compilável sem acrescentar um campo persistido fictício. (JAVA-004; AC 11)
Proof: `mvn -o '-Dtest=ComposedCommandTest#transientInputAndResultDoNotRequireFakeEntityFields' test` — **verde**.

**C15** — *parcial em S1; fecha em S5.* As fixtures V0 preservam seus goldens e continuam recusando cada extensão V1 exercitada pelo Core; rejeição antiga não é apagada para acomodar a nova semântica. (JAVA-004; AC 12)
Proof: `mvn -o '-Dtest=JavaSpringGoldenTest#customerProjectMatchesTheVersionedTargetGolden' test` — **existente**.
Proof: `mvn -o '-Dtest=FlowCallTest#versionZeroDoesNotSilentlyAcquireCalls' test` — **existente**.
Proof: `mvn -o '-Dtest=CoreV1CompatibilityTest#v0RejectsCoreV1OnlyConstructs' test` — **verde para as 8 construções que existem; `for each`, `emit`, política transacional e failure mapping entram em S2–S5**.


### S2 — Iteração tipada

**C16** - Coleções com 0, 1 e 3 itens executam o corpo do loop exatamente 0, 1 e 3 vezes, respectivamente. (FLOW-020; AC 13, 14)
Proof: `mvn -o '-Dtest=FlowIterationRuntimeTest#bodyCountMatchesCollectionSize' test` — **a criar**.

**C17** - A coleção de entrada [A, B, C] produz a sequência de observação [A, B, C], sem reordenar os itens. (FLOW-020; AC 13)
Proof: `mvn -o '-Dtest=FlowIterationRuntimeTest#iterationPreservesCollectionOrder' test` — **a criar**.

**C18** - Ao iterar itens com quantity 2 e 3, a leitura do campo tipado entrega 2 e 3 à operação consumidora. (CORE-010, FLOW-020; AC 15)
Proof: `mvn -o '-Dtest=FlowIterationTest#itemMemberTypeComesFromTheDeclaredElement' test` — **a criar**.
Proof: `mvn -o '-Dtest=FlowIterationRuntimeTest#itemMembersReachTheConsumer' test` — **a criar**.

**C19** - Campo inexistente e campo de tipo incompatível com o argumento esperado geram diagnóstico no acesso inválido antes da geração. (CORE-010, FLOW-020; AC 16)
Proof: `mvn -o '-Dtest=FlowIterationTest#invalidMemberAccessIsRejectedBeforeGeneration' test` — **a criar**.

**C20** - O item de iteração e uma variável criada no corpo não podem ser referenciados após o loop; ambos os usos externos são recusados. (CORE-010, FLOW-020; AC 17)
Proof: `mvn -o '-Dtest=FlowIterationTest#loopBindingsDoNotEscapeTheirBlock' test` — **a criar**.

**C21** - O recorte Core recusa loop aninhado, break, continue, async, add à coleção iterada e remove da coleção iterada. (FLOW-020; AC 18)
Proof: `mvn -o '-Dtest=FlowIterationTest#unsupportedIterationFormsAreRejected' test` — **a criar**.

**C22** - CalculateTotal, executada pelo Flow sobre itens de 20.00 e 30.00, produz Decimal numericamente igual a 50.00; os operandos e o resultado usam BigDecimal no Java gerado. (FLOW-020; AC 19)
Proof: `mvn -o '-Dtest=FlowIterationTest#totalExpressionsPreserveDecimalType' test` — **a criar**.
Proof: `mvn -o '-Dtest=FlowIterationRuntimeTest#calculateTotalUsesExactDecimalArithmetic' test` — **a criar**.


### S3 — Atomicidade local

**C23** - A política explícita de Command e a política read-only de Query chegam à Application IR com os dois significados distintos definidos no RFC, sem inferência pelo verbo HTTP. (CMD-008, PERSIST-008, JAVA-004; AC 20)
Proof: `mvn -o '-Dtest=TransactionPolicyTest#declaredPoliciesSurviveTheApplicationIr' test` — **a criar**.

**C24** - Um Command bem-sucedido com 1 Order e 2 OrderItems deixa 1 pedido e 2 itens confirmados, visíveis por uma conexão posterior ao término da transação. (CMD-008, PERSIST-008; AC 21)
Proof: `mvn -o '-Dtest=CommandTransactionRuntimeTest#successfulOrderCommitsAllItsItems' test` — **a criar**.

**C25** - Um erro declarado injetado após a primeira gravação do Command deixa 0 pedidos e 0 itens novos, consultados depois do rollback. (CMD-008, PERSIST-008; AC 22)
Proof: `mvn -o '-Dtest=CommandTransactionRuntimeTest#declaredFailureRollsBackEarlierWrites' test` — **a criar**.

**C26** - A falha do Command chamador reverte também a gravação do Command chamado, tanto para chamada no mesmo service quanto entre dois services. (CMD-008, PERSIST-008, JAVA-004; AC 23)
Proof: `mvn -o '-Dtest=CommandTransactionRuntimeTest#nestedCommandsShareTheCallersTransaction' test` — **a criar**.

**C27** - O método gerado para Query declara @Transactional(readOnly = true). (CMD-008, PERSIST-008, JAVA-004; AC 24)
Proof: `mvn -o '-Dtest=TransactionPolicyTest#queryGenerationUsesReadOnlyTransaction' test` — **a criar**.

**C28** - Política read-only declarada em operação com gravação é recusada antes do target; o erro aponta a política incompatível. (CMD-008, PERSIST-008, JAVA-004; AC 25)
Proof: `mvn -o '-Dtest=TransactionPolicyTest#readOnlyPolicyCannotAdmitWrites' test` — **a criar**.

**C29** - Falha HTTP injetada depois de uma gravação local deixa 0 novas linhas dessa execução, sem afirmar reversão de qualquer efeito no sistema remoto. (CMD-008, PERSIST-008; AC 26)
Proof: `mvn -o '-Dtest=CommandTransactionRuntimeTest#outboundFailureRollsBackLocalWrites' test` — **a criar**.


### S4 — Antifraude e falhas nomeadas

**C30** - Com binding que associa HTTP 503 à variante Unavailable declarada na porta, o client expõe Unavailable ao chamador e conserva a identificação da operação. (RELY-001; AC 27)
Proof: `mvn -o '-Dtest=IntegrationFailureMappingTest#httpStatusMapsToTheDeclaredPortVariant' test` — **a criar**.
Proof: `mvn -o '-Dtest=IntegrationFailureRuntimeTest#httpStatusRaisesTheNamedPortFailure' test` — **a criar**.

**C31** - Mapping para variante inexistente e dois mappings para o mesmo gatilho são recusados no range da declaração de binding inválida. (RELY-001; AC 28)
Proof: `mvn -o '-Dtest=IntegrationFailureMappingTest#unknownAndConflictingFailureMappingsAreRejected' test` — **a criar**.

**C32** - Com read timeout de 100ms e servidor que não responde durante 2s, a chamada termina pela variante declarada TimedOut; a prova exige término antes de 2s e não apenas inspeção da configuração. (RELY-001; AC 29)
Proof: `mvn -o '-Dtest=IntegrationFailureRuntimeTest#readDeadlineRaisesTheTimeoutVariant' test` — **a criar**.

**C33** - Sem configuração específica, os timeouts gerados permanecem connect=2s e read=10s, aplicados por RestClientCustomizer. (RELY-001; AC 29)
Proof: `mvn -o '-Dtest=IntegrationHttpClientTest#everyCallCarriesADeadlineWithoutMakingTheClientUntestable' test` — **existente**.

**C34** - Uma resposta que omite o Boolean obrigatório de aprovação termina pela variante declarada InvalidResponse, sem entregar null ao Flow. (RELY-001; AC 30)
Proof: `mvn -o '-Dtest=IntegrationFailureRuntimeTest#missingRequiredResponseFieldRaisesInvalidResponse' test` — **a criar**.

**C35** - FraudService com aprovação true faz o Flow alcançar a gravação; a prova observa o resultado da chamada consumido pela guarda. (RELY-001; AC 31)
Proof: `mvn -o '-Dtest=FraudDecisionRuntimeTest#approvalReachesTheWrite' test` — **a criar**.

**C36** - A recusa de negócio do FraudService produz HTTP 422 em POST /orders, com ApiError contendo status=422, error não vazio e message não vazia. (RELY-001; AC 32)
Proof: `mvn -o '-Dtest=FraudDecisionTest#businessRefusalMapsTo422' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#fraudRefusalReturns422' test` — **a criar**.

**C37** - Timeout, conexão indisponível, HTTP 503 mapeado e resposta inválida do FraudService produzem HTTP 503 em POST /orders com ApiError.status=503. (RELY-001; AC 33)
Proof: `mvn -o '-Dtest=FraudDecisionTest#dependencyFailuresMapTo503' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#fraudDependencyFailuresReturn503' test` — **a criar**.


### S5 — Eventos locais

**C38** - Emit com OrderCreated declarado e payload compatível atravessa AST, Business IR, Application IR e inspect, e gera publicação de um record OrderCreated compilável. (FLOW-015, EVENT-003; AC 34)
Proof: `mvn -o '-Dtest=EventEmitTest#typedEmissionCrossesThePipeline' test` — **a criar**.

**C39** - Event inexistente, campo obrigatório ausente e campo de tipo incompatível são recusados no ponto de emit. (FLOW-015, EVENT-003; AC 35)
Proof: `mvn -o '-Dtest=EventEmitTest#invalidEventOrPayloadIsRejected' test` — **a criar**.

**C40** - Uma execução de CreateOrder com uma instrução emit entrega exatamente um OrderCreated ao listener local depois do commit; a observação anterior ao commit contém zero eventos. (EVENT-005, SPRING-008; AC 36)
Proof: `mvn -o '-Dtest=LocalEventPolicyTest#dispatchesOnCommitOnly' test` — **a criar**.
Proof: `mvn -o '-Dtest=LocalEventRuntimeTest#oneEmissionIsObservedOnlyAfterCommit' test` — **a criar**.

**C41** - Uma execução de CreateOrder que reverte a transação entrega zero OrderCreated ao listener, inclusive quando o Flow já alcançou emit. (EVENT-005, SPRING-008; AC 37)
Proof: `mvn -o '-Dtest=LocalEventPolicyTest#dispatchesOnCommitOnly' test` — **a criar**.
Proof: `mvn -o '-Dtest=LocalEventRuntimeTest#rollbackDiscardsThePendingEmission' test` — **a criar**.

**C42** - Declarar Event sem emit gera o record e compila sem dependência de Kafka ou RabbitMQ. (EVENT-005, SPRING-008; AC 38)
Proof: `mvn -o '-Dtest=EventContractTest#anEventBecomesAnImmutableRecordInItsOwnPackage' test` — **existente**.
Proof: `mvn -o '-Dtest=LocalEventProviderTest#unusedEventContractsRequireNoBroker' test` — **a criar**.

**C43** - Falha do listener após commit conserva o pedido confirmado e a resposta 201 de CreateOrder; não transforma o sucesso persistido em erro HTTP. (EVENT-005, SPRING-008; AC 39)
Proof: `mvn -o '-Dtest=LocalEventPolicyTest#listenerFailureIsReportedWithoutEscaping' test` — **a criar**.
Proof: `mvn -o '-Dtest=LocalEventRuntimeTest#listenerFailureDoesNotChangeTheCommittedCommand' test` — **a criar**.

**C44** - A falha do listener produz um registro de erro que contém o nome OrderCreated; a prova captura o registro emitido pelo mecanismo aprovado em S0. (EVENT-005, SPRING-008; AC 39)
Proof: `mvn -o '-Dtest=LocalEventPolicyTest#listenerFailureIsReportedWithoutEscaping' test` — **a criar**.
Proof: `mvn -o '-Dtest=LocalEventRuntimeTest#listenerFailureNamesTheEventInTheErrorLog' test` — **a criar**.


### S6 — Custom executado e preservado

**C45** - Um bean do usuário no pacote custom retorna 83.50 e o Flow consome 83.50 por injeção do contrato em logic, sem gerar uma implementação substituta. (CUSTOM-002, FLOW-014; AC 40)
Proof: `mvn -o '-Dtest=FlowCallTest#aCustomLogicContractIsInjectedAsTheTargetAdapter' test` — **existente**.
Proof: `mvn -o '-Dtest=CustomLogicRuntimeTest#generatedCallerExecutesTheUsersBean' test` — **a criar**.

**C46** - O projeto com o bean em generated/src/main/java/<pacote>/custom passa mvn -o test com exit code 0, sem build-helper-maven-plugin nem source root extra. (CUSTOM-002, CUSTOM-003; AC 41)
Proof: `mvn -o '-Dtest=CustomLogicRuntimeTest#customBeanBuildsOfflineInTheConventionalSourceRoot' test` — **a criar**.

**C47** - Dois rebuilds com --clean --force mantêm byte a byte o arquivo do bean no pacote custom. (CUSTOM-003; AC 42)
Proof: `mvn -o '-Dtest=CustomCodeOwnershipTest#aCustomImplementationSurvivesACleanRebuildByteForByte' test` — **existente**.
Proof: `mvn -o '-Dtest=CustomLogicRuntimeTest#cleanRebuildPreservesCustomBeanBytes' test` — **a criar**.

**C48** - O manifesto lista a interface gerada e exclui a implementação do usuário; a implementação não é reclassificada como arquivo de propriedade Harpia no rebuild. (CUSTOM-003; AC 43)
Proof: `mvn -o '-Dtest=CustomCodeOwnershipTest#theManifestNeverClaimsAFileHarpiaDidNotWrite' test` — **existente**.
Proof: `mvn -o '-Dtest=CustomLogicRuntimeTest#customBeanStaysOutsideTheGeneratedManifest' test` — **a criar**.

**C49** - --clean sem --force recusa a limpeza quando há arquivo desconhecido e preserva o conteúdo desse arquivo. (CUSTOM-003; AC 44)
Proof: `mvn -o '-Dtest=CustomCodeOwnershipTest#cleanWithoutForceRefusesRatherThanDecidingForTheUser' test` — **existente**.

**C50** - O handoff enumera o contrato custom e o caminho generated/src/main/java/<pacote>/custom; a presença de um .java nesse caminho não muda a obrigação para implementação verificada. (CUSTOM-003, HARNESS-003; AC 45)
Proof: `mvn -o '-Dtest=HandoffManifestTest#itNamesTheWorkHarpiaDeliberatelyDidNotDo' test` — **existente**.
Proof: `mvn -o '-Dtest=HandoffManifestTest#customLayoutDoesNotClaimJavaImplementationVerification' test` — **a criar**.


### S7 — Commerce integrado

**C51** - As 15 declarações canônicas do Commerce listadas em Coverage são resolvidas no mesmo projeto e estão presentes nos estágios aplicáveis do pipeline. (GREEN-003; AC 46)
Proof: `mvn -o '-Dtest=CommerceCompilationTest#allReferenceDeclarationsResolveTogether' test` — **a criar**.

**C52** - POST /orders com entrada válida, JWT válido e antifraude aprovado responde 201 com os sete campos Order definidos em Surface e status CREATED. (GREEN-003; AC 47)
Proof: `mvn -o '-Dtest=CommerceHttpTest#createOrderReturns201AndTheDeclaredRepresentation' test` — **a criar**.

**C53** - POST /orders recusa input obrigatório ausente e JSON malformado com 400 e ApiError.status=400. (GREEN-003; AC 47)
Proof: `mvn -o '-Dtest=CommerceHttpTest#invalidCreateInputReturns400' test` — **a criar**.

**C54** - POST /orders com orderNumber já existente responde 409 e ApiError.status=409. (GREEN-003; AC 47)
Proof: `mvn -o '-Dtest=CommerceHttpTest#duplicateOrderNumberReturns409' test` — **a criar**.

**C55** - GET /orders/{id} para pedido persistido responde 200 com a representação desse pedido, incluindo seus itens e total. (GREEN-003; AC 48)
Proof: `mvn -o '-Dtest=CommerceHttpTest#getOrderReturnsThePersistedRepresentation' test` — **a criar**.

**C56** - GET /orders/{id} para id ausente responde 404 e ApiError.status=404. (GREEN-003; AC 49)
Proof: `mvn -o '-Dtest=CommerceHttpTest#getMissingOrderReturns404' test` — **a criar**.

**C57** - POST /orders/{id}/cancel com ADMIN e id ausente responde 404 e ApiError.status=404. (GREEN-003; AC 49)
Proof: `mvn -o '-Dtest=CommerceHttpTest#cancelMissingOrderReturns404' test` — **a criar**.

**C58** - GET /orders para status sem pedidos responde 200 com content=[], page=0, size=10, totalElements=0 e totalPages=0. (GREEN-003; AC 50)
Proof: `mvn -o '-Dtest=CommerceHttpTest#emptySearchReturnsAnEmptyPage' test` — **a criar**.

**C59** - Para três pedidos do status solicitado, page=0 e size=2 retornam os dois primeiros ids em ordem ascendente, totalElements=3 e totalPages=2; page=1 retorna o terceiro. (GREEN-003; AC 51)
Proof: `mvn -o '-Dtest=CommerceQueryTest#statusFilteringAndIdOrderingDefineThePage' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#searchReturnsStablePages' test` — **a criar**.

**C60** - GET /orders com page=-1, size=0 ou size=101 responde 400 em cada caso e ApiError.status=400. (GREEN-003; AC 52)
Proof: `mvn -o '-Dtest=CommerceQueryTest#paginationRejectsOutOfRangeValues' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#invalidPaginationReturns400' test` — **a criar**.

**C61** - GET /orders aceita as bordas page=0, size=1 e size=100 e responde 200 em cada caso. (GREEN-003; AC 51, 52)
Proof: `mvn -o '-Dtest=CommerceQueryTest#paginationAcceptsItsBoundaryValues' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#paginationBoundaryValuesReturn200' test` — **a criar**.

**C62** - GET /orders com ausência de status, page ou size responde 400, conforme os três parâmetros obrigatórios de Surface. (GREEN-003; AC 51, 52)
Proof: `mvn -o '-Dtest=CommerceHttpTest#missingSearchParametersReturn400' test` — **a criar**.

**C63** - POST /orders/{id}/cancel com JWT real contendo ADMIN e pedido CREATED responde 200 com status CANCELLED, também lido por uma consulta posterior ao banco. (GREEN-003; AC 53)
Proof: `mvn -o '-Dtest=OrderStateTest#createdOrderCanBeCancelled' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceSecurityHttpTest#adminTokenCanCancelACreatedOrder' test` — **a criar**.

**C64** - POST /orders/{id}/cancel para pedido CANCELLED responde 409 e mantém CANCELLED no banco. (GREEN-003; AC 54)
Proof: `mvn -o '-Dtest=OrderStateTest#cancelledOrderCannotBeCancelledAgain' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceHttpTest#cancellingAnAlreadyCancelledOrderReturns409' test` — **a criar**.

**C65** - Cada uma das quatro rotas de Surface responde 401 e ApiError.status=401 quando o token está ausente. (GREEN-003; AC 55)
Proof: `mvn -o '-Dtest=CommerceSecurityHttpTest#everyProtectedRouteRejectsMissingTokens' test` — **a criar**.

**C66** - Cada uma das quatro rotas de Surface responde 401 e ApiError.status=401 quando o JWT está expirado. (GREEN-003; AC 55)
Proof: `mvn -o '-Dtest=CommerceSecurityHttpTest#everyProtectedRouteRejectsExpiredTokens' test` — **a criar**.

**C67** - Cada uma das quatro rotas de Surface responde 401 e ApiError.status=401 quando a assinatura do JWT é inválida. (GREEN-003; AC 55)
Proof: `mvn -o '-Dtest=CommerceSecurityHttpTest#everyProtectedRouteRejectsInvalidSignatures' test` — **a criar**.

**C68** - POST /orders/{id}/cancel com JWT válido sem ADMIN responde 403 e ApiError.status=403; não altera o estado do pedido. (GREEN-003; AC 56)
Proof: `mvn -o '-Dtest=JwtRoleMappingTest#rolesClaimProducesTheExpectedRoleAuthorities' test` — **a criar**.
Proof: `mvn -o '-Dtest=CommerceSecurityHttpTest#validTokenWithoutAdminCannotCancel' test` — **a criar**.

**C69** - A fixture integrada inicia PostgreSQL descartável e aplica a migration Flyway gerada; o ciclo criar/ler usa repositories reais e observa os dados por uma conexão posterior. (GREEN-003; AC 57)
Proof: `mvn -o '-Dtest=CommercePersistenceTest#generatedMigrationAndRepositoriesRunOnPostgres' test` — **a criar**.

**C70** - Os dois OrderItems do pedido permanecem associados somente ao seu Order por relationship owned; a Application IR preserva lifecycle DEPENDENT. (GREEN-003; AC 46, 57)
Proof: `mvn -o '-Dtest=OwnedRelationshipTest#ownedToOneAndToManyRelationshipsHaveDependentLifecycle' test` — **existente**.
Proof: `mvn -o '-Dtest=CommercePersistenceTest#orderItemsBelongToTheirOwningOrder' test` — **a criar**.

**C71** - Address é embedded de Order e não recebe tabela própria na migration Commerce. (GREEN-003; AC 46, 57)
Proof: `mvn -o '-Dtest=DeclaredValueTest#aDeclaredValueIsEmbeddedRatherThanGivenATableOfItsOwn' test` — **existente**.
Proof: `mvn -o '-Dtest=CommercePersistenceTest#shippingAddressIsEmbeddedInTheOrder' test` — **a criar**.

**C72** - As referências a Customer e Product persistem as identidades fornecidas e retornam essas identidades na representação Order, sem exigir carregamento de associações. (GREEN-003; AC 46, 48, 57)
Proof: `mvn -o '-Dtest=ReferenceFieldTest#aReferenceIsAForeignKeyAndNotAnAssociation' test` — **existente**.
Proof: `mvn -o '-Dtest=CommercePersistenceTest#customerAndProductReferencesPreserveTheirIdentities' test` — **a criar**.


### S8 — Gates e encerramento

**C73** - Os quatro gates validate, build, Maven test e Maven package do Commerce retornam exit code 0; a prova guarda um resultado para cada comando, sem editar arquivos gerados para corrigir falhas. (GREEN-003, HARNESS-004; AC 58)
Proof: `mvn -o '-Dtest=GeneratedCommerceProjectTest#everyRequiredGateReturnsZero' test` — **a criar**.

**C74** - Duas gerações do Commerce em diretórios temporários distintos produzem bytes idênticos para arquivos Harpia, manifesto de ownership e handoff; custom não entra no conjunto de arquivos do compiler. (DET-003; AC 59)
Proof: `mvn -o '-Dtest=HandoffManifestTest#twoIdenticalBuildsLeaveIdenticalBytes' test` — **existente**.
Proof: `mvn -o '-Dtest=CommerceDeterminismTest#independentBuildsProduceIdenticalOwnedTreesAndHandoffs' test` — **a criar**.

**C75** - O jar Commerce contém Main-Class=org.springframework.boot.loader.launch.JarLauncher, Start-Class da aplicação gerada e entradas BOOT-INF/lib/. (GREEN-003; AC 60)
Proof: `mvn -o '-Dtest=GeneratedCommerceProjectTest#packageContainsTheExecutableSpringBootLayout' test` — **a criar**.

**C76** - O jar Commerce iniciado com PostgreSQL, HTTP e JWKS locais responde ao fluxo criar/consultar/cancelar; suas dependências de runtime não incluem Harpia. (GREEN-003; AC 61)
Proof: `mvn -o '-Dtest=GeneratedCommerceProjectTest#packagedApplicationRunsWithoutHarpiaRuntime' test` — **a criar**.

**C77** - O handoff mantém validate/build como runBy=harpia,status=passed e test/package como runBy=you,status=pending; a execução posterior do runner não reescreve esses fatos do compiler. (HARNESS-003, HARNESS-004; AC 62)
Proof: `mvn -o '-Dtest=HandoffManifestTest#itSaysWhichGatesPassedAndWhichAreStillYours' test` — **existente**.
Proof: `mvn -o '-Dtest=GeneratedCommerceProjectTest#externalGateResultsDoNotRewriteCompilerClaims' test` — **a criar**.

**C78** - validate e build em modo JSON, diante de erro de compilação, emitem um único objeto em stdout com ok=false, exitCode=1 e diagnostics com código/range, sem resumo textual. (HARNESS-002; AC 63)
Proof: `mvn -o '-Dtest=CliJsonReportTest#aRefusalCarriesItsCodeLocationAndSpan' test` — **existente**.
Proof: `mvn -o '-Dtest=CliJsonReportTest#aBuildThatNeverCompiledReportsInTheSameShape' test` — **existente**.

**C79** - Falha de I/O ao sincronizar output em build --json emite um único objeto com ok=false, exitCode=2 e diagnóstico da falha. (HARNESS-002; AC 63)
Proof: `mvn -o '-Dtest=CliJsonReportTest#outputWriteFailureIsOneJsonReport' test` — **a criar**.

**C80** - Falha de I/O ao escrever o handoff depois do output em build --json emite um único objeto com ok=false, exitCode=2 e diagnóstico que identifica .harpia/handoff.json. (HARNESS-002; AC 63)
Proof: `mvn -o '-Dtest=CliJsonReportTest#handoffWriteFailureIsOneJsonReport' test` — **a criar**.

**C81** - Cada exemplo do catálogo é compilado; cada exemplo de recusa produz o código declarado pela entrada, incluindo as construções novas do Core. (DOC-001; AC 64)
Proof: `mvn -o '-Dtest=CatalogTest#everyExampleMeansWhatTheCatalogueSaysItMeans' test` — **existente**.

**C82** - O catálogo contém exemplos aceitos e recusados de composição, iteração, transação, failure mapping, emit e custom usados no fechamento; nenhum desses seis tópicos fica sem entrada exercitada. (DOC-001; AC 64)
Proof: `mvn -o '-Dtest=CatalogTest#coreV1TopicsHaveExecutableExamples' test` — **a criar**.

**C83** - A declaração de conclusão exige os 14 IDs originalmente abertos como DONE com evidência e GREEN-003 ligado aos resultados reais do gate Commerce; ausência de resultado, failure, error ou skipped impede o encerramento. (GREEN-003; AC 65)
Proof: `mvn -o '-Dtest=CoreV1ReleaseTest#closureRequiresEvidenceForEveryOriginalBlocker' test` — **a criar**.

**C84** - Os onze pacotes independentes verificados por ArchitectureBoundaryTest continuam sem vocabulário e literais de tipos exclusivos do target; a Business IR permanece sem decisões de arquitetura Java/Spring. (JAVA-004, GREEN-003; AC 12, 46)
Proof: `mvn -o '-Dtest=ArchitectureBoundaryTest#noLayerAboveTheTargetBoundaryNamesALanguageOrAFramework' test` — **existente**.
Proof: `mvn -o '-Dtest=ArchitectureBoundaryTest#theBusinessIrDescribesIntentRatherThanArchitecture' test` — **existente**.

**C85** - API Java, contrato JSON e livro-razão de diagnósticos preservam os membros já prometidos; adições seguem o versionamento do contrato e não removem códigos existentes. (JAVA-004, HARNESS-002; AC 12, 63)
Proof: `mvn -o '-Dtest=HarpiaContractTest#thePromisedSurfaceIsTheOneThatWasPromised' test` — **existente**.
Proof: `mvn -o '-Dtest=HarpiaContractTest#everyCodeEverPromisedStillMeansWhatItMeant' test` — **existente**.

**C86** - Os testes gerados, o runner integrado e o jar executável recebem sua configuração de teste antes da inicialização; nenhuma dessas três montagens usa placeholder de credencial ou JWKS não resolvido. (CUSTOM-002, GREEN-003; AC 40, 57, 61)
Proof: `mvn -o '-Dtest=GeneratedCommerceProjectTest#eachAssemblyReceivesResolvedTestConfiguration' test` — **a criar**.

**C87** - As linhas Java geradas para call, for each e emit conservam source mappings para o arquivo e range correspondentes da spec; a prova compara os três casos com posições conhecidas da fixture. (CORE-016, GREEN-003, HARNESS-001; AC 34, 46, 63)
Proof: `mvn -o '-Dtest=CoreV1SourceMappingTest#generatedOperationsRetainSpecSourceRanges' test` — **a criar**.

## Coverage

Cada membro tem uma obrigação atribuída. **Unproven = `-` significa ausência de lacuna de atribuição**, não prova executada: o estado de execução continua Pending. A coluna Member é também o inventário dos casos que o teste parametrizado deve afirmar individualmente, com nomes de casos identificáveis no relatório. Quando o mesmo check aparece em várias posições, sua prova deve cobrir todas elas.

| Set (size) | Member -> proof | Unproven |
| --- | --- | --- |
| Blockers canônicos originais (14) | `CORE-010` C11 · `FLOW-014` C5 · `FLOW-015` C38 · `FLOW-020` C16 · `CMD-008` C24 · `PERSIST-008` C25 · `EVENT-003` C38 · `EVENT-005` C40 · `RELY-001` C30 · `JAVA-004` C14 · `SPRING-008` C41 · `GREEN-003` C73 · `CUSTOM-002` C45 · `CUSTOM-003` C47 | - |
| S0 — casos do spike (4) | `input` C2 · `member access` C2 · `resultado de Command` C2 · `owned` C2 | - |
| S0 — contratos novos (4) | `Flow` C3 · `transação` C3 · `falha outbound` C3 · `Event` C3 | - |
| S0 — itens que permanecem Next (4) | `DOM-015` C4 · `DOM-016` C4 · `EVENT-004` C4 · `MSG-001` C4 | - |
| Consumidores de valor local (6) | `call Logic` C5 · `call Command` C6 · `set` C7 · `require` C8 · `fail` C9 · `if` C10 | - |
| require Boolean (2) | `true` C8 · `false` C8 | - |
| fail Boolean (2) | `true` C9 · `false` C9 | - |
| if Boolean (2) | `true` C10 · `false` C10 | - |
| Referências inválidas (2) | `antes da declaração` C11 · `fora do bloco` C11 | - |
| Grafo de chamadas de Command (3) | `A→A` C12 · `A→B→A` C12 · `A→B` C12 | - |
| Efeitos recusados em Query (3) | `call direto de Command` C13 · `call transitivo de Command` C13 · `emit` C13 | - |
| V1 recusado sob V0 (8) | `call` C15 · `if/else V1` C15 · `require/fail V1` C15 · `Command/Query explícitos` C15 · `Event/emit` C15 · `for each` C15 · `política transacional nova` C15 · `failure mapping novo` C15 | - |
| Cardinalidade da iteração (3) | `0` C16 · `1` C16 · `3` C16 | - |
| Ordem da iteração (3) | `A na posição 0` C17 · `B na posição 1` C17 · `C na posição 2` C17 | - |
| Leitura do item (4) | `quantity=2` C18 · `quantity=3` C18 · `campo inexistente` C19 · `tipo incompatível` C19 | - |
| Bindings que não escapam do loop (2) | `item` C20 · `variável do corpo` C20 | - |
| Limites do loop (6) | `aninhamento` C21 · `break` C21 · `continue` C21 · `async` C21 · `add à coleção iterada` C21 · `remove da coleção iterada` C21 | - |
| Políticas transacionais (3) | `Command obrigatório` C23 · `Query read-only` C23 · `read-only com gravação recusado` C28 | - |
| Persistência da execução (3) | `sucesso confirma pedido e itens` C24 · `erro declarado reverte` C25 · `falha HTTP reverte` C29 | - |
| Command aninhado (2) | `mesmo service` C26 · `services distintos` C26 | - |
| Binding de falha (3) | `503→Unavailable` C30 · `variante inexistente` C31 · `gatilho duplicado` C31 | - |
| Timeouts (3) | `connect default 2s` C33 · `read default 10s` C33 · `read efetivo 100ms` C32 | - |
| Resultado do antifraude (2) | `aprovado` C35 · `recusado` C36 | - |
| Falhas de dependência → HTTP 503 (4) | `timeout` C37 · `conexão indisponível` C37 · `status 503 mapeado` C37 · `resposta inválida` C37 | - |
| Emit inválido (3) | `Event inexistente` C39 · `campo obrigatório ausente` C39 · `tipo incompatível` C39 | - |
| Evento e transação (3) | `antes do commit zero` C40 · `após commit um` C40 · `rollback zero` C41 | - |
| Listener falha (3) | `pedido confirmado` C43 · `HTTP 201 preservado` C43 · `erro nomeia OrderCreated` C44 | - |
| Custom — ownership (7) | `bean executado` C45 · `Maven offline` C46 · `primeiro rebuild` C47 · `segundo rebuild` C47 · `fora do manifesto` C48 · `clean sem force recusa` C49 · `handoff não verifica Java` C50 | - |
| Declarações Commerce (15) | `Customer` C51 · `Address` C51 · `Product` C51 · `Order` C51 · `OrderItem` C51 · `CustomerTier` C51 · `OrderStatus` C51 · `CalculateDiscount` C51 · `CalculateTotal` C51 · `CreateOrder` C51 · `CancelOrder` C51 · `GetOrder` C51 · `SearchOrders` C51 · `FraudService` C51 · `OrderCreated` C51 | - |
| POST /orders — statuses (6) | `201` C52 · `400` C53 · `401` C65 · `409` C54 · `422` C36 · `503` C37 | - |
| GET /orders/{id} — statuses (3) | `200` C55 · `401` C65 · `404` C56 | - |
| GET /orders — statuses (3) | `200` C59 · `400` C60 · `401` C65 | - |
| POST /orders/{id}/cancel — statuses (5) | `200` C63 · `401` C65 · `403` C68 · `404` C57 · `409` C64 | - |
| Order — campos de resposta (7) | `id` C52 · `customerId` C52 · `orderNumber` C52 · `shippingAddress` C52 · `items` C52 · `total` C52 · `status` C52 | - |
| OrderItem — campos de resposta (3) | `productId` C55 · `quantity` C55 · `unitPrice` C55 | - |
| ApiError — campos (3) | `status` C36 · `error` C36 · `message` C36 | - |
| Input de criação recusado (3) | `obrigatório ausente` C53 · `JSON malformado` C53 · `orderNumber duplicado` C54 | - |
| PageResponse — campos (5) | `content` C58 · `page` C58 · `size` C58 · `totalElements` C58 · `totalPages` C58 | - |
| Paginação — bordas (6) | `page=-1` C60 · `page=0` C61 · `size=0` C60 · `size=1` C61 · `size=100` C61 · `size=101` C60 | - |
| Busca — parâmetros obrigatórios (3) | `status ausente` C62 · `page ausente` C62 · `size ausente` C62 | - |
| Busca — páginas (3) | `primeira com 2 itens` C59 · `segunda com 1 item` C59 · `vazia` C58 | - |
| OrderStatus — transições (3) | `criar em CREATED` C52 · `CREATED→CANCELLED` C63 · `CANCELLED→CANCELLED recusada` C64 | - |
| JWT ausente — rotas (4) | `POST /orders` C65 · `GET /orders/{id}` C65 · `GET /orders` C65 · `POST /orders/{id}/cancel` C65 | - |
| JWT expirado — rotas (4) | `POST /orders` C66 · `GET /orders/{id}` C66 · `GET /orders` C66 · `POST /orders/{id}/cancel` C66 | - |
| JWT assinatura inválida — rotas (4) | `POST /orders` C67 · `GET /orders/{id}` C67 · `GET /orders` C67 · `POST /orders/{id}/cancel` C67 | - |
| JWT válido — role (4) | `roles=[ADMIN]` C63 · `roles=[USER]` C68 · `roles=[]` C68 · `claim roles ausente` C68 | - |
| Relations — entidades/valor (5) | `Customer` C72 · `Product` C72 · `Order` C69 · `OrderItem` C70 · `Address` C71 | - |
| Relations — ligações (4) | `Customer→Order` C72 · `Order→Address embedded` C71 · `Order→OrderItem owned` C70 · `Product→OrderItem` C72 | - |
| Gates executados (4) | `validate` C73 · `build` C73 · `Maven test` C73 · `Maven package` C73 | - |
| Determinismo (3) | `árvore Harpia` C74 · `manifesto ownership` C74 · `handoff` C74 | - |
| Jar executável (5) | `Main-Class` C75 · `Start-Class` C75 · `BOOT-INF/lib` C75 · `execução HTTP` C76 · `sem runtime Harpia` C76 | - |
| Handoff — responsabilidade por gate (4) | `validate harpia/passed` C77 · `build harpia/passed` C77 · `test you/pending` C77 · `package you/pending` C77 | - |
| CLI JSON — falhas (4) | `validate compilação exit 1` C78 · `build compilação exit 1` C78 · `output I/O exit 2` C79 · `handoff I/O exit 2` C80 | - |
| CLI JSON — envelope de falha (5) | `ok=false` C78 · `exitCode` C78 · `diagnostics` C78 · `código` C78 · `range` C78 | - |
| Catálogo — tópicos (6) | `composição` C82 · `iteração` C82 · `transação` C82 · `failure mapping` C82 · `emit` C82 · `custom` C82 | - |
| Catálogo — tipos de exemplo (2) | `válido` C81 · `recusa com código` C81 | - |
| Landing — portas do plano (4) | `door 1 layout custom` C45 · `door 2 ownership` C47 · `door 3 fronteira do target` C84 · `door 4 harness` C77 | - |
| Startup — assemblies (3) | `testes gerados` C86 · `runner integrado` C86 · `jar executável` C86 | - |
| Startup — configuração (5) | `PostgreSQL local` C86 · `FraudService local` C86 · `JWKS local` C86 · `credencial de teste resolvida` C86 · `bean custom` C86 | - |
| Source mapping das novas instruções (3) | `call` C87 · `for each` C87 · `emit` C87 | - |
| Contrato existente (3) | `API Java` C85 · `JSON versionado` C85 · `códigos publicados` C85 | - |

As quatro rotas têm **17 pares rota/status** atribuídos. Os testes de token ausente, expirado e assinatura inválida expandem cada uma das quatro rotas separadamente: são **12 casos de autenticação**, além dos casos de role. Não usar apenas o status de uma rota como evidência de todas as outras.

Os checks C36–C37, C52–C68 e C76 fazem afirmações HTTP e exigem prova que atravesse a fronteira HTTP da aplicação gerada. Asserção sobre string de controller ou anotação não substitui essa prova. C69–C72 exigem PostgreSQL; C40–C44 exigem o momento transacional do evento; C45–C46 exigem execução do bean e do build gerado.

Fixtures obrigatórias das provas:
- JWTs são assinados localmente e passam pelo decoder/JWKS; controlar a expiração por relógio de teste ou instantes fixados na fixture, sem depender de token de terceiros.
- Na prova de paginação, incluir ao menos um pedido de outro status, para que a ordenação sozinha não faça a prova de filtro passar.
- Observar rollback por uma conexão posterior; não consultar apenas o persistence context que fez a gravação.
- No loop, usar valores distinguíveis e observar os efeitos; contar trechos de Java não prova número de execuções.
- Timeout começa a ser medido depois da inicialização do client; servidor controlado e sem retry. Medir com relógio monotônico.
- Servidores, PostgreSQL e processos do jar pertencem ao teste e devem ser encerrados também na falha; usar diretórios temporários para outputs e custom de demonstração.

## Traceability

Esta tabela deriva dos ACs, não da implementação. `In checks` significa que a obrigação foi escrita; não significa teste verde.

| AC | Checks | Status |
| --- | --- | --- |
| 1 | C1 | In checks |
| 2 | C2 | In checks |
| 3 | C3 | In checks |
| 4 | C4 | In checks |
| 5 | C5 | In checks |
| 6 | C6 | In checks |
| 7 | C7, C8, C9, C10 | In checks |
| 8 | C11 | In checks |
| 9 | C12 | In checks |
| 10 | C13 | In checks |
| 11 | C14 | In checks |
| 12 | C15, C84, C85 | In checks |
| 13 | C16, C17 | In checks |
| 14 | C16 | In checks |
| 15 | C18 | In checks |
| 16 | C19 | In checks |
| 17 | C20 | In checks |
| 18 | C21 | In checks |
| 19 | C22 | In checks |
| 20 | C23 | In checks |
| 21 | C24 | In checks |
| 22 | C25 | In checks |
| 23 | C26 | In checks |
| 24 | C27 | In checks |
| 25 | C28 | In checks |
| 26 | C29 | In checks |
| 27 | C30 | In checks |
| 28 | C31 | In checks |
| 29 | C32, C33 | In checks |
| 30 | C34 | In checks |
| 31 | C35 | In checks |
| 32 | C36 | In checks |
| 33 | C37 | In checks |
| 34 | C38, C87 | In checks |
| 35 | C39 | In checks |
| 36 | C40 | In checks |
| 37 | C41 | In checks |
| 38 | C42 | In checks |
| 39 | C43, C44 | In checks |
| 40 | C45, C86 | In checks |
| 41 | C46 | In checks |
| 42 | C47 | In checks |
| 43 | C48 | In checks |
| 44 | C49 | In checks |
| 45 | C50 | In checks |
| 46 | C51, C70, C71, C72, C84, C87 | In checks |
| 47 | C52, C53, C54 | In checks |
| 48 | C55, C72 | In checks |
| 49 | C56, C57 | In checks |
| 50 | C58 | In checks |
| 51 | C59, C61, C62 | In checks |
| 52 | C60, C61, C62 | In checks |
| 53 | C63 | In checks |
| 54 | C64 | In checks |
| 55 | C65, C66, C67 | In checks |
| 56 | C68 | In checks |
| 57 | C69, C70, C71, C72, C86 | In checks |
| 58 | C73 | In checks |
| 59 | C74 | In checks |
| 60 | C75 | In checks |
| 61 | C76, C86 | In checks |
| 62 | C77 | In checks |
| 63 | C78, C79, C80, C85, C87 | In checks |
| 64 | C81, C82 | In checks |
| 65 | C83 | In checks |

Os 14 IDs canônicos abertos aparecem nos checks correspondentes e em Coverage. C4 resolve dependências editoriais antigas; C83 exige a evidência de conclusão. Status e evidência de BACKLOG continuam sendo a autoridade de produto, sem uma segunda contagem de entrega baseada no número de checks.

## Test policy

Política desta feature, confirmada pelo perfil standard. Não cria nem altera AGENTS.md ou diretrizes globais do repositório. Uma prova vale no nível em que **afirma** o resultado, não em todos os níveis pelos quais passou.

| Code | Required proofs | Coverage expectation |
| --- | --- | --- |
| Parser/analyzer/resolver que decide sobre construção, tipo, escopo ou efeito | Prova própria pelo compilador/estágio; execução do código gerado quando houver promessa runtime | Cada alternativa nomeada em Coverage tem caso afirmado; código/range de diagnóstico são comparados, não apenas presença de erro |
| Mapeamento de política, falha, estado ou authority | Prova na camada da decisão e prova do efeito na fronteira consumidora | Cada entrada/saída da tabela é afirmada no nível próprio; status HTTP e efeitos persistidos são afirmados na fronteira |
| Transação e evento local | Prova da política onde ela decide; prova runtime com commit/rollback e listener real | Sucesso, falha antes de commit, chamada aninhada e falha do listener; mecanismos de framework não são reimplementados no teste |
| DTO, migration e configuração que variam por tipo ou capability | Prova da transformação e compilação/execução consumidora | Cada entidade/relação e cada assembly de Coverage é alcançado; não substituir PostgreSQL por um banco com semântica diferente |
| CLI, writer e handoff | Prova pela CLI/filesystem e prova própria quando existir decisão de ownership ou erro | JSON, exit code, preservação de bytes e responsabilidades dos gates afirmados separadamente |
| Encaminhamento sem decisão e renderer puramente mecânico | Prova do consumidor suficiente, salvo contrato próprio já existente | Não criar teste que apenas repita uma chamada ao framework |
| Artefatos de S0 e encerramento | Prova da estrutura/ligação das evidências e execução das fixtures referidas | Links sozinhos não demonstram comportamento; o relatório registra resultado de execução e a revisão julga escolhas do RFC |

Evidence, contagem lexical da baseline de produção (ocorrências de `if (`, `switch (` e rótulos `case`; **não** é complexidade ciclomática nem meta de cobertura global):
- FlowBlockParser: **23 if, 0 switch, 0 case** — construção/blocos; o precedente FlowConditionalTest exercita ramos válidos, aninhamento, tipos e recusas.
- LogicAnalyzer: **105 if, 2 switch, 16 case** — tipos, nomes, chamadas e ciclos; precedentes LogicAnalyzerTest e FlowCallTest.
- JavaSpringServiceTransformer: **20 if, 5 switch, 31 case** — natureza/resultado/instrução e montagem de dependências; precedentes ApplicationLayerTransformerTest e GeneratedMavenProjectTest. Strings compiláveis não provam runtime.
- JavaSpringIntegrationClientTransformer: **12 if, 1 switch, 2 case** — forma de chamada/resposta; IntegrationHttpClientTest prova transformação, IntegrationAuthTest inclui chamada em execução.
- JavaSpringSecurityTransformer: **4 if, 1 switch, 5 case** — access e mecanismo; RoleAccessTest/SecurityProviderTest são precedentes de geração, não substitutos da prova JWT + ADMIN.
- OutputWriter: **12 if, 0 switch, 0 case** — escrita, arquivos desconhecidos e limpeza; CustomCodeOwnershipTest afirma bytes e manifesto no filesystem.

Para os arquivos novos, enumerar os membros da decisão a partir do contrato de S0 **antes** de escrever a implementação; depois confrontar a enumeração com o código. Não transformar essa contagem lexical dos arquivos inteiros em obrigação de reescrever todos os testes existentes. O escopo são os comportamentos tocados e suas regressões.

**Custo:** 94 novos seletores específicos, além de 23 existentes. “Seletor” não é número de arquivos ou de métodos finais: métodos parametrizados representam vários membros de Coverage. O custo de PostgreSQL/processos/HTTP pertence às provas de integração; testes de sintaxe ou política não ganham infraestrutura por associação. As obrigações novas de decisão já têm provas próprias, por exemplo C30/C36/C37 (falhas), C40/C43 (evento), C59/C60/C61 (busca) e C63/C68 (estado/roles).

Na verificação standard, o Verifier recompõe os conjuntos a partir de plan/contratos e confronta cada linha desta política. Aplica ao menos uma falha por superfície de asserção relevante, por exemplo: consumir o input antigo no call, deixar uma variável escapar, omitir rollback, entregar evento antes de commit, ignorar ADMIN, sobrescrever custom ou marcar Maven como passed no handoff. Os exemplos orientam o tipo de falha; não substituem a enumeração exigida pelo perfil. Mutante sobrevivente, prova que não executou ou caso omitido impedem PASS.

## Swept

- validation: C11, C19, C20, C21, C28, C31, C39, C53, C60, C61, C62 — nomes/ranges, tipos, limites e entradas HTTP.
- failure modes: C25, C29, C41, C43, C79, C80 — rollback, evento e I/O parcial; não prometer filesystem atômico que o writer não implementa.
- idempotency: C47, C48, C49, C74 — repetição de geração e ownership. Idempotência de requisições de negócio e deduplicação de eventos ficam fora do Core; C54 é unicidade de orderNumber, não protocolo de idempotência HTTP.
- authorization: C63, C65, C66, C67, C68 — JWT e ADMIN no runtime. Rate limiting é n/a porque está explicitamente no Next.
- concurrency: n/a — o plano não promete resolução de corrida entre requisições, locking, exactly-once ou entrega distribuída; esses itens são Next. C17/C59 provam ordenação de coleções/resultados e não são usados como prova de concorrência.
- data lifecycle: C25, C47, C48, C64, C70, C71 — rollback, ownership de arquivos, estado cancelado conservado e relações da fixture. TTL, backfill e schema evolution são n/a no bootstrap.
- dependency failure: C29, C30, C32, C34, C37, C86 — dependência HTTP e configuração real de inicialização; indisponibilidade do ambiente de teste bloqueia a execução.
- state transitions: C52, C63, C64 — criação CREATED, cancelamento e recusa de repetição. Não introduzir DSL de lifecycle.
- observability: C44, C77, C78, C79, C80, C83, C87 — falha do listener nomeia evento, JSON e handoff mostram resultados reais, encerramento exige evidência. Métricas/traces e SLO são n/a neste recorte.

## Handoff

Ordem preservada do plano: S0, S1, S2, S3, S4, S5, S6, S7, S8. S6 pode antecipar depois de S1. Não há autorização para implementar o MVP nesta solicitação de critérios; a próxima etapa é executar S0 quando o desenvolvimento for iniciado.

O inventário por diretórios mostrou que entregar S0–S3 inteiro a um builder exigiria cerca de **991.421 / 4 = 247.855 tokens** só de fontes existentes, acima do orçamento logístico padrão de 150k. S4–S6 por diretórios completos também excede: **740.740 / 4 = 185.185 tokens**. O corte deve respeitar slices completas e terminar apenas com suas provas verdes.

Intenção de divisão: **S0 → S1 → S2 → S3 → S4–S6 → S7–S8**. Leituras mais específicas já identificadas, antes dos novos testes:
- conjunto compartilhado de S0/S1: **372.788 bytes / 4 = 93.197 tokens** em 23 arquivos Java;
- conjunto compartilhado de S2/S3: **355.184 bytes / 4 = 88.796 tokens** em 18 arquivos Java;
- conjunto S4/S5/S6: **199.760 bytes / 4 = 49.940 tokens** em 21 arquivos Java;
- conjunto S7/S8: **180.678 bytes / 4 ≈ 45.170 tokens** em 19 arquivos Java.

São estimativas de leitura de fontes selecionadas, não garantia de caber toda a execução. Acrescentar o custo dos artefatos e testes novos antes de iniciar cada lote; leitura integral de BACKLOG + plan + discovery nesta baseline já representa aproximadamente **225.032 / 4 = 56.258 tokens**. Se um lote ultrapassar 150k, manter o corte por slice; se uma slice sozinha ultrapassar, explicitar a necessidade de dividi-la por resultado observável antes de começar. Não esconder o excesso em uma estimativa que ignore os documentos.

**Boundary atual:** nenhum check fechado; não há diff de implementação a transferir.
**Settled nesta fase:** perfil standard confirmado; critérios derivados do plano; cada prova tem seletor e estado de disponibilidade.
**A resolver:** TG-01/TG-02 por S0, TG-03/TG-04 pelas provas integradas. S0 acrescenta as portas aprovadas a Landing/STATE antes do código dependente; não remove nem enfraquece estas obrigações.

## Maintenance

Checks, provas e linhas de Test policy são a baseline de aceite. Alteração que enfraqueça um resultado exige renegociação explícita; esclarecimento aditivo deve apontar o AC e o check afetados. Nomes de provas previstos não podem ser trocados silenciosamente por uma suite ampla.

Quando um check for cumprido, registrar seu commit, cada seletor executado, os casos/sucessos/erros/skips do relatório e a asserção localizada. Um check com duas linhas Proof exige as duas. Não marcar como fechado enquanto uma prova estiver apenas “a criar”. No encerramento da implementação, o Verifier independente exigido pela skill deve verificar todos os checks e escrever verification.md; este documento não é esse relatório.
