# Harpia Core V1 — spike de CreateOrder (S0)

> Plano: [`.specs/features/mvp-core-v1/plan.md`](../.specs/features/mvp-core-v1/plan.md) · Checks: [`checks.md`](../.specs/features/mvp-core-v1/checks.md), check **C2**, AC 2.
> Executado em 2026-09-17 sobre `3c62a18`, com `validate` do compiler desta árvore e `harpia.languageVersion: 1`, `database.vendor: postgres`.
> Prova: `CoreV1PlanningTest#spikeRecordsInputsObservedResultsAndOwningSlices` recompila cada fixture deste documento e confere o resultado registrado.

## Resultado

O recorte mínimo de CreateOrder com itens owned **não compila hoje**, e as quatro recusas são
distintas — não são uma só limitação vista de quatro ângulos. Duas são de escopo de valores
(`HRP2102`, `HRP2114`), uma é da forma do input (`HRP2011`) e uma é da fronteira de entidade da
operação (`HRP2010`). Nenhuma delas é contornável escrevendo a spec de outro jeito dentro da
linguagem atual, então as quatro viram extensão declarada, cada uma na slice abaixo.

O spike encerra TG-01 no que ele perguntava: a extensão mínima é **input não projetado na entidade**,
**member access tipado sobre variáveis de Flow**, **valores locais visíveis às expressões** e
**gravação de filhos owned pela operação do dono**. O spike não implementa nenhuma delas.

## Casos exigidos por TG-01

A coluna **Estado hoje** é mantida viva pela prova: enquanto a lacuna estiver aberta, a recusa
registrada tem de continuar acontecendo; quando uma slice a fecha, a linha diz qual slice foi e o
que o compiler passou a responder. Um relatório de spike que envelhece em silêncio é pior que
nenhum, porque a próxima slice constrói em cima dele.

| Caso | O que a spec pede | Observado em S0 | Estado hoje | Lacuna | Slice |
| --- | --- | --- | --- | --- | --- |
| `input` — input de Command | um campo de input que não existe na entidade (`couponCode`) | `HRP2011` — *input field 'couponCode' must name one non-generated entity field exactly once* | aberto | input de Command é hoje uma projeção obrigatória da entidade; um input composto ou transitório não tem forma | S1 (C14) |
| `member-access` — acesso a campo | ler `order.total` numa expressão de Flow | `HRP2114` — *member access '.total' requires a nominal type; Logic parameters are scalar* | aberto | a expressão tipada só conhece escalares; variável de Flow não tem tipo nominal | S2 (C18) |
| `command-result` — resultado de Command | consumir em `set` o valor devolvido por `call RecordAudit(...)` | `HRP2102` — *unknown value 'audited'; it is not a parameter of Logic 'audited' and was not assigned before this line* | fechado em S1 → `HRP2103` | o nome agora resolve e carrega o tipo da entidade que o Command devolve; pedir um Decimal dele é erro de tipo, e vira acesso a membro quando S2 der membros aos valores | S1 (C7–C10) |
| `owned` — associação owned | criar o `OrderItem` dentro do Command do `Order` | `HRP2010` — *operation 'CreateOrder' works on more than one entity: [SalesOrder, OrderItem]* | aberto | a operação pertence a uma entidade; escrever o filho owned no mesmo Command é recusado antes da geração | S2 (C16–C22), S3 (C24) |

O caso `command-result` era o único em que **parte** do caminho já existia: a chamada resolvia,
atravessava os IRs e gerava `AuditEntryResponse audited = auditEntryService.recordAudit(...)` no
serviço. O que faltava era o valor ser visível para a instrução seguinte — por isso a linha estava
em S1 e não numa entrega nova, e por isso ela foi a primeira a fechar.

### `input` — campo de input fora da entidade

````harpia case=input expect=HRP2011 state=open
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- total: Decimal required default 0

## Command CreateOrder

### Input

- orderNumber: String required
- couponCode: String required

### Flow

```flow
validate input
order = create SalesOrder from input
save order
return order
```

### Output

201 SalesOrder
````

### `member access` — ler um campo de uma variável de Flow

````harpia case=member-access expect=HRP2114 state=open
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- total: Decimal required default 0

## Command CreateOrder

### Input

- orderNumber: String required

### Flow

```flow
validate input
order = create SalesOrder from input
set order.total = order.total
save order
return order
```

### Output

201 SalesOrder
````

### `command-result` — consumir o valor devolvido

*Fechado em S1.* A fixture abaixo é a mesma; o que mudou foi o compiler. `audited` agora está em
escopo com o tipo `AuditEntry`, e a recusa passou a ser `HRP2103` — *'audited' must be Decimal but
is AuditEntry* — que é a verdade sobre esta spec: o valor existe e não é um Decimal.

````harpia case=command-result expect=HRP2103 state=closed
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- total: Decimal required default 0

## Command CreateOrder

### Input

- orderNumber: String required
- total: Decimal required

### Flow

```flow
validate input
order = create SalesOrder from input
audited = call RecordAudit(orderNumber = orderNumber, amount = total)
set order.total = audited
save order
return order
```

### Output

201 SalesOrder

<!-- specs/audit.harpia.md -->
# AuditEntry

## Data

- id: UUID generated
- orderNumber: String required
- amount: Decimal required

## Command RecordAudit

### Input

- orderNumber: String required
- amount: Decimal required

### Flow

```flow
validate input
entry = create AuditEntry from input
save entry
return entry
```

### Output

201 AuditEntry
````

### `owned` — escrever o filho owned no Command do dono

````harpia case=owned expect=HRP2010 state=open
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- items: List<OrderItem> owned required

## Command CreateOrder

### Input

- orderNumber: String required

### Flow

```flow
validate input
order = create SalesOrder from input
item = create OrderItem from input
add item to order.items
save order
return order
```

### Output

201 SalesOrder

<!-- specs/item.harpia.md -->
# OrderItem

## Data

- id: UUID generated
- quantity: Int required

## Query GetOrderItem

### Flow

```flow
item = load OrderItem by id
return item
```

### Output

200 OrderItem
````

## Capacidades que o spike encontrou prontas

Estas não viram extensão. Foram observadas na mesma execução e existem para impedir que uma slice
reimplemente o que já funciona:

- `items: List<OrderItem> owned required` em `## Data` valida, gera `@OneToMany(cascade = ALL, orphanRemoval = true)` e a join table com FK de alvo única;
- `- items: List<OrderItem> required` em `### Input` valida e gera `@NotEmpty List<OrderItem> items` no request record — **o DTO expõe a entidade JPA**, com o `id` gerado dentro dela. Aceitar não é o mesmo que ser a forma certa: o contrato de entrada dos itens é parte do que S1 deve resolver junto com `HRP2011`;
- `call` de Command entre serviços resolve e injeta o serviço do outro agregado; no mesmo serviço a chamada sai como `this.<operação>(...)`, sem novo proxy — é o caminho que C26 precisa provar em S3;
- `@Transactional` em Command e `@Transactional(readOnly = true)` em Query **já são gerados**, por inferência da natureza declarada. O que falta a CMD-008/PERSIST-008 é a política ser **declarada** e preservada na Application IR, não a anotação.

## Recusas das superfícies que TG-02 ainda vai declarar

Executadas no mesmo spike para fixar o ponto de partida. Cada uma é a ausência da construção, não
um defeito: hoje a linha nem chega ao analisador.

| Construção | Resultado observado | Slice |
| --- | --- | --- |
| `for each item in items` | `HRP1007` — *unknown flow command* | S2 |
| `emit OrderCreated(orderId = order.id)` | `HRP1007` — *unknown flow command* | S5 |

````harpia case=for-each expect=HRP1007 state=open
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- total: Decimal required default 0

## Command CreateOrder

### Input

- orderNumber: String required

### Flow

```flow
validate input
order = create SalesOrder from input
for each item in items
    save order
return order
```

### Output

201 SalesOrder
````

````harpia case=emit expect=HRP1007 state=open
# SalesOrder

## Data

- id: UUID generated
- orderNumber: String required
- total: Decimal required default 0

## Command CreateOrder

### Input

- orderNumber: String required

### Flow

```flow
validate input
order = create SalesOrder from input
save order
emit OrderCreated(orderNumber = orderNumber)
return order
```

### Output

201 SalesOrder
````

## Achado fora dos quatro casos: a entidade não pode se chamar `Order`

O plano e os checks nomeiam a entidade `Order`. Sobre `database.vendor: postgres` o compiler recusa:

````harpia case=reserved-name expect=HRP2016 state=open
# Order

## Data

- id: UUID generated
- orderNumber: String required

## Command CreateOrder

### Input

- orderNumber: String required

### Flow

```flow
validate input
order = create Order from input
save order
return order
```

### Output

201 Order
````

`order` está em `src/main/resources/reserved-postgres.txt` e `ProviderValidation` recusa a tabela
antes da geração. `order_item`, `customer` e `product` não estão na lista — só o dono do agregado.

Isso não é lacuna de capacidade do Core: é o nome da fixture contra uma regra que o compiler já
promete e testa. Resolver mudando o compiler (quoting de identificador reservado, ou override de
nome de tabela na linguagem) é trabalho de persistência que nenhum critério do plano pede. A saída
barata é a fixture usar outro nome de entidade e conservar as rotas `/orders` de Surface. **Fica
registrado como decisão pendente do usuário**, porque os checks nomeiam `Order` entre as 15
declarações de C51; o spike não escolhe por conta própria.

## O que o spike não decidiu

- nenhuma sintaxe: a forma literal das construções novas está em [`mvp-core-v1-contracts.md`](mvp-core-v1-contracts.md);
- nenhuma mudança de status no backlog: o inventário está em [`mvp-core-v1-inventory.md`](mvp-core-v1-inventory.md);
- nada sobre transação, evento, timeout ou JWT em execução: TG-03 e TG-04 continuam abertos e são provados por S3–S7.
