# Harpia Core V1 — contratos novos (RFC de S0)

> Plano: [`.specs/features/mvp-core-v1/plan.md`](../.specs/features/mvp-core-v1/plan.md) · Checks: [`checks.md`](../.specs/features/mvp-core-v1/checks.md), check **C3**, AC 3. Entrada: [`mvp-core-v1-spike.md`](mvp-core-v1-spike.md).
> Escrito em 2026-09-17 sobre `3c62a18`. Fecha TG-02 e a parte de TG-01 que depende de sintaxe.
> Prova: `CoreV1PlanningTest#everyNewContractHasLiteralExamplesAndMechanism`.

Quatro contratos, um por superfície nova. Cada um traz **forma literal**, **exemplo válido**,
**exemplo recusado**, **mecanismo** e **alternativa rejeitada** — as cinco partes que AC 3 exige.
Nenhum está implementado; este documento é o que as slices S1–S5 constroem, e é o que uma revisão
pode recusar antes de existir código.

Os exemplos válidos deste documento **não compilam na árvore atual**, por construção: se
compilassem, não haveria contrato novo. Os exemplos recusados nomeiam o código do livro-razão que a
recusa deve trazer. Cinco códigos são reservados aqui e nenhum código existente muda de significado:

A coluna **Estado** é mantida viva pela prova: um código ainda reservado não pode estar declarado
em `ErrorCodes`, e um código em uso tem de estar. Reservar um número já tomado redefiniria uma
recusa publicada, e é isso que a verificação impede.

| Código | Constante | Significado | Estado |
| --- | --- | --- | --- |
| `HRP2145` | `SEMANTIC_TRANSACTION_POLICY` | a política transacional declarada contraria os efeitos da operação | reservado |
| `HRP2146` | `SEMANTIC_ITERATION` | forma de iteração fora do recorte Core | em uso desde S2 |
| `HRP2147` | `SEMANTIC_EMIT_TARGET` | `emit` nomeia um Event que não existe | reservado |
| `HRP2148` | `SEMANTIC_EMIT_PAYLOAD` | payload de `emit` omite campo obrigatório ou usa tipo incompatível | reservado |
| `HRP2149` | `SEMANTIC_FLOW_MEMBER` | acesso a membro inexistente ou incompatível de um valor tipado do Flow | em uso desde S2 |

Reutilizados sem mudança de sentido: `HRP2007` (nome não declarado), `HRP2130` (binding que escapa
do seu bloco), `HRP2011`/`HRP2012` (input contra a entidade), `HRP2203` (mapping que não corresponde
ao contrato da operação vinculada), `HRP2120` (Query que muta).

---

## Contrato 1 — Flow: input próprio, valores locais e iteração tipada

Responde TG-01 e a parte de TG-02 sobre iteração. Slices S1 e S2; checks C5–C22.

### Forma literal

```ebnf
for-each      = "for each", sp, variable, sp, "in", sp, expression, newline,
                indented-block ;
indented-block = { four-spaces, flow-command, newline } ;
member-access = ( variable | "input" ), ".", field-name ;
```

Três regras semânticas, e nenhuma sintaxe nova além de `for each`:

1. **Input próprio.** Em `languageVersion: 1`, `### Input` declara o contrato de entrada da
   operação e deixa de ser obrigatoriamente uma projeção da entidade. Um campo de input que nomeia
   um campo não gerado da entidade continua sendo atribuído por `create E from input` e continua
   sujeito a `HRP2012` quando o tipo não bate; um campo que não nomeia campo nenhum passa a ser um
   **valor local** do Flow, visível pelo seu nome. `HRP2011` continua valendo em
   `languageVersion: 0` e continua recusando um campo que nomeia um campo **gerado** ou que aparece
   duas vezes. Isso generaliza a exceção que `page`/`size` já têm hoje, em vez de criar uma segunda.
2. **Valores locais visíveis.** Um nome produzido por `x = call ...`, `x = create ...`,
   `x = load ...`, `x = find ...`, `x = list ...` ou por `for each x in ...` fica visível da linha
   seguinte até o fim do bloco onde foi declarado, e é visível para **todas** as expressões —
   argumentos de `call`, `set`, `require`, `fail` e `if` — e não só para argumentos de `call`, que é
   o recorte de hoje. O valor é de atribuição única, como em Logic.
3. **Member access tipado.** `x.campo` é válido quando `x` tem tipo nominal — entidade, Value ou
   elemento de coleção declarado — e o tipo do resultado é o tipo declarado do campo.

O recorte da iteração é deliberadamente pequeno: **um nível**, ordem da coleção, corpo indentado por
quatro espaços, sem `break`, sem `continue`, sem forma assíncrona, e sem `add`/`remove` sobre a
coleção que está sendo iterada.

### Exemplo válido

```markdown
## Command CreateOrder

### Input

- customerId: Reference<Customer> required
- orderNumber: String required
- items: List<OrderItemInput> required

### Flow

```flow
validate input
order = create SalesOrder from input
for each line in items
    item = create OrderItem from line
    add item to order.items
total = call CalculateTotal(items = order.items)
set order.total = total
save order
return order
```
```

`items` não é campo de `SalesOrder`: é input próprio da operação (regra 1). `line` existe apenas
dentro do corpo (regra 2). `order.items` é acesso tipado (regra 3).

### Exemplo recusado

```flow
for each line in items
    for each part in line.parts
        save order
```

`HRP2146`: loop aninhado está fora do recorte Core. As outras cinco recusas da mesma família são
`break`, `continue`, iteração assíncrona, `add` e `remove` sobre a coleção iterada.

```flow
for each line in items
    item = create OrderItem from line
set order.total = item.unitPrice
```

`HRP2130`: `item` é binding do corpo e não escapa do bloco — o mesmo código que hoje recusa uma
variável que escapa de um ramo de `if`.

```flow
set order.total = line.precoUnitario
```

`HRP2149`: `precoUnitario` não é campo do tipo de `line`.

### Mecanismo

`FlowLineParser` ganha a linha `for each`, e `FlowBlockParser` reaproveita a leitura de bloco
indentado que `if`/`else` já usa — inclusive a recusa de corpo não indentado, que hoje sai como
`HRP1007` com a mensagem de quatro espaços. `LogicAnalyzer` passa a receber um **escopo empilhado**
em vez do conjunto de parâmetros escalares: cada bloco empurra seus bindings e os descarta ao
fechar, que é o que `HRP2130` já pressupõe para ramos. `TypedExpression` ganha um tipo nominal, e é
ele que faz `.campo` deixar de cair em `HRP2114`. `FlowStep` ganha `ForEach(variable, collection,
body, where)`; Business IR e Application IR carregam o passo com o tipo do elemento;
`JavaSpringServiceTransformer` renderiza um `for (T line : ...)` Java, sem biblioteca de coleções.

### Alternativa rejeitada

Funções universais de coleção — `map`, `filter`, `sum` — resolveriam o total do pedido sem laço
nenhum e em menos linhas. Foram rejeitadas porque transformam o recorte numa linguagem funcional
geral: cada função vira contrato público, pede um sistema de tipos para lambdas e não tem fim
natural. O laço de um nível processa os itens do pedido, que é o que o Core precisa demonstrar, e é
recusável de volta se a linguagem crescer noutra direção. Também foi rejeitado o laço por índice
(`for i in 0..n`), que traz aritmética de índice e erro de limite para dentro da spec.

---

## Contrato 2 — Transação declarada

Responde a parte transacional de TG-02. Slice S3; checks C23–C29.

### Forma literal

```ebnf
transaction-section = h3, sp, "Transaction", newline, transaction-policy ;
transaction-policy  = "required" | "read only" ;
```

Seção opcional de `## Command` e `## Query`. Ausente, o default é o de hoje e nada muda: Command é
`required`, Query é `read only`. Presente, a política é **declarada** e atravessa os IRs como
declaração, não como inferência — a diferença que `CMD-008` e `PERSIST-008` esperam.

Semântica das duas políticas:

- `required` — a operação executa numa transação; uma operação chamada por ela participa da
  transação do chamador, sem abrir uma nova;
- `read only` — a operação não grava; declarar isso numa operação que grava é recusado.

A unidade transacional é o banco local. Propagação por chamada, `requires new`, isolamento,
timeout transacional e transação distribuída ficam fora, como o plano já registra.

### Exemplo válido

```markdown
## Command CreateOrder

### Transaction

required

### Flow

```flow
validate input
order = create SalesOrder from input
save order
return order
```
```

### Exemplo recusado

```markdown
## Command CancelOrder

### Transaction

read only

### Flow

```flow
order = load SalesOrder by id
set order.status = "CANCELLED"
save order
return order
```
```

`HRP2145`: a política declarada contraria os efeitos validados da operação. O diagnóstico aponta a
linha da política, não a do `save` — quem escreveu `read only` escolheu ali.

### Mecanismo

`SpecParser` lê a seção; o modelo de operação ganha `TransactionPolicy`; `SemanticValidator`
compara a política declarada com os efeitos que ele já calcula para decidir `HRP2120`, e a mesma
travessia produz `HRP2145`. A Application IR expõe a política explicitamente, e o `Inspector` a
mostra. No target, `required` continua saindo como `@Transactional` e `read only` como
`@Transactional(readOnly = true)` — a anotação não muda, o que muda é de onde ela vem.

A participação da chamada aninhada não precisa de mecanismo novo e é por isso que ela é provável em
S3: uma chamada para operação do mesmo serviço já é gerada como `this.<operação>(...)`, que não
passa por proxy e portanto roda na transação do chamador; uma chamada para outro serviço é gerada
como invocação do bean injetado, cujo `@Transactional` tem propagação `REQUIRED` por default e
adere à transação corrente. C26 prova os dois caminhos em execução, não pela anotação.

### Alternativa rejeitada

Atributos de propagação por chamada (`call X(...) requires new`) foram rejeitados: dariam ao
Core um vocabulário transacional inteiro — propagação, isolamento, timeout — para demonstrar
atomicidade de um pedido, e cada atributo publicado é um contrato que sobrevive à demonstração.
Também foi rejeitado inferir a política dos passos do Flow, que é exatamente o que existe hoje e o
que os dois registros abertos pedem para deixar de ser implícito.

---

## Contrato 3 — Falha outbound nomeada

Responde a parte de falha de TG-02. Slice S4; checks C30–C37.

### Forma literal

No arquivo de binding, dentro de `## Bind <Integration>.<Operation>`:

```ebnf
failures-section = h3, sp, "Failures", newline, failure-mapping, { failure-mapping } ;
failure-mapping  = "- ", trigger, ": ", variant-name ;
trigger          = "status", sp, http-status
                 | "timeout" | "unavailable" | "invalid response" ;
```

`variant-name` deve estar declarada em `#### Errors` da operação. Cada gatilho aparece no máximo uma
vez por binding. Os quatro gatilhos são os quatro modos que o chamador distingue: o outro lado
respondeu com erro, não respondeu a tempo, não foi alcançado, ou respondeu algo que o contrato da
porta não aceita.

Sem `### Failures`, nada muda: a falha continua saindo como a exceção da porta, sem variante.

### Exemplo válido

```markdown
## Bind FraudService.CheckOrder

### Endpoint

POST /checks/{orderId}

### Request

- orderId: path orderId

### Response

output: body

### Failures

- status 503: Unavailable
- timeout: TimedOut
- invalid response: InvalidResponse
```

### Exemplo recusado

```markdown
### Failures

- status 503: Unavailable
- status 503: TimedOut
```

`HRP2203`: dois mappings para o mesmo gatilho — o client teria de descartar um em silêncio. O mesmo
código recusa `- status 500: NaoDeclarada`, variante que `#### Errors` não declara.

### Mecanismo

`IntegrationBindingValidator` valida os mappings contra as variantes da operação, reusando o
caminho que já produz `HRP2203` para request/response. A Application IR já carrega
`ApplicationIntegration.Failure` — hoje nenhum target a lê — e passa a carregar também o gatilho.
`JavaSpringIntegrationClientTransformer` gera cada variante como **subclasse aninhada estática** da
exceção da porta (`FraudServiceException.Unavailable`), e o client lança a variante: o `onStatus`
que hoje lança `FraudServiceException` escolhe pelo status mapeado, o `catch` de
`ResourceAccessException` distingue timeout de conexão recusada, e a checagem de contrato de
resposta que hoje chama `check(...)` lança `InvalidResponse`.

A subclasse é o ponto do mecanismo: quem já captura `FraudServiceException` continua capturando
tudo, inclusive variante nova — a adição é compatível — e quem precisa distinguir captura a
variante, sem `switch` sobre string. Os defaults `connect=2s`/`read=10s` e o `RestClientCustomizer`
permanecem como estão.

### Alternativa rejeitada

Um campo `variant` (enum ou string) na exceção única foi rejeitado: obriga todo chamador a capturar
a exceção genérica e ramificar dentro do `catch`, o que o compilador não verifica, e uma variante
nova passa despercebida por um `switch` sem `default`. Também foi rejeitado mapear falha no `spec/`
em vez do binding: status HTTP é como o outro lado é alcançado, e colocá-lo na spec quebraria a
fronteira que o door 3 do plano protege.

---

## Contrato 4 — Evento local

Responde a parte de evento de TG-02. Slice S5; checks C38–C44.

### Forma literal

```ebnf
emit      = "emit", sp, event-name, "(", [ arguments ], ")" ;
arguments = argument, { ",", sp, argument } ;
argument  = field-name, sp, "=", sp, expression ;
```

Argumentos nomeados, como em `call`. O payload deve cobrir exatamente os campos obrigatórios do
Event declarado, com tipos compatíveis. `emit` é efeito de mutação: uma Query que o executa é
recusada pelo contrato de Query, com `HRP2120`.

Política de entrega, que é a parte que não se lê na gramática:

- a emissão é observada **depois do commit** da transação da operação que a executou;
- uma execução que reverte entrega **zero** eventos, mesmo tendo alcançado o `emit`;
- uma instrução `emit` executada uma vez entrega uma publicação. Isso não é exactly-once entre
  tentativas: sem outbox, um crash entre o commit e a publicação perde o evento, e o contrato diz
  isso em vez de prometer o contrário;
- a falha de um listener **depois** do commit não desfaz o resultado já confirmado nem muda a
  resposta da operação; ela é registrada como erro nomeando o Event.

### Exemplo válido

```markdown
## Event OrderCreated

### Payload

- orderId: UUID
- total: Decimal

## Command CreateOrder

### Flow

```flow
validate input
order = create SalesOrder from input
save order
emit OrderCreated(orderId = order.id, total = order.total)
return order
```
```

### Exemplo recusado

```flow
emit OrderShipped(orderId = order.id)
```

`HRP2147`: `OrderShipped` não é um Event declarado.

```flow
emit OrderCreated(orderId = order.id)
```

`HRP2148`: o payload omite `total`, que o Event declara como obrigatório. O mesmo código recusa um
campo cujo tipo não é compatível com o declarado.

### Mecanismo

`FlowLineParser` ganha a linha; `FlowStep` ganha `Emit(event, arguments, where)`;
`SemanticValidator` resolve o Event no namespace `events` que `EVENT-001` já criou e compara o
payload. No target, o serviço recebe um `LocalEventPublisher` gerado e chama
`events.publish(new OrderCreated(...))`.

O `LocalEventPublisher` é onde a política vive: quando há transação ativa, ele registra uma
`TransactionSynchronization` e publica em `afterCommit`; sem transação, publica na hora. A
publicação usa o `ApplicationEventPublisher` do Spring, e o bloco de publicação captura a exceção
do listener e a registra com o nome do Event, para que a falha do listener não volte pela pilha da
operação que já confirmou.

A escolha de o **publisher** adiar até o commit — e não o listener declarar
`@TransactionalEventListener` — é o núcleo do mecanismo: o listener é código do usuário, e uma
garantia que depende de o usuário lembrar de uma anotação não é garantia. Com o publisher adiando,
um listener escrito com o `@EventListener` comum já observa depois do commit.

### Alternativa rejeitada

Publicar direto por `ApplicationEventPublisher` no ponto do `emit`, deixando o commit por conta do
`@TransactionalEventListener` de quem escuta, foi rejeitado pelo motivo acima: um listener comum
veria um evento de uma transação que ainda pode reverter, e C41 falharia por causa da anotação que
o usuário não escreveu. Broker, outbox e entrega durável foram rejeitados por escopo: são Next, e o
evento local não promete sobreviver a um crash.

---

## O que este RFC não decide

- o nome da entidade do Commerce: `Order` é recusado por `HRP2016` sobre Postgres, e a decisão está
  registrada em [`mvp-core-v1-spike.md`](mvp-core-v1-spike.md) como pendência do usuário;
- TG-03 e TG-04: JWT real com claim de roles e o ambiente do gate integrado são provados em
  execução por S7, não por declaração aqui.
