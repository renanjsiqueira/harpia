# Harpia Language Specification — V0

Status: normativa para o MVP. Esta versão descreve somente CRUD de entidade única.

A evolução incremental está em [`harpia-language-v1-draft.md`](harpia-language-v1-draft.md).
Somente os slices marcados como implementados são aceitos em `languageVersion: 1`; bindings HTTP
externos possuem sua especificação normativa em
[`harpia-bindings-v1.md`](harpia-bindings-v1.md).

Um projeto declara esta gramática sem inferência a partir do conteúdo Markdown:

```yaml
harpia:
  schemaVersion: 1
  languageVersion: 0
```

`schemaVersion` pertence ao formato de configuração. `languageVersion` pertence à gramática e à
semântica descritas aqui. A versão em `target.language.version` pertence ao target e, no
`java-spring`, seleciona Java; nenhuma dessas versões substitui outra.

## 1. Convenções

Arquivos de linguagem terminam em `.harpia.md`, são UTF-8 e ficam sob o diretório `specs/`. O
compilador remove um BOM UTF-8 inicial e normaliza CRLF/CR para LF antes do parsing. Linhas e colunas
de diagnóstico começam em 1 e contam code points Unicode.

As palavras-chave e os nomes das seções executáveis são case-sensitive. Espaços mostrados nas
produções são um ou mais espaços ASCII; espaços no início e no fim de uma linha são ignorados.
Linhas vazias dentro de `flow` são ignoradas.

Neste documento, `?`, `*`, `+`, `|` e parênteses são metassintaxe EBNF e não caracteres literais.

```ebnf
lower       = "a" | "b" | ... | "z" ;
upper       = "A" | "B" | ... | "Z" ;
digit       = "0" | "1" | ... | "9" ;
identifier  = lower, { lower | upper | digit } ;
entity-name = upper, { lower | upper | digit } ;
variable    = identifier ;
field-name  = identifier ;
sp          = " ", { " " } ;
bullet      = "-" | "*" | "+" ;
```

`identifier` é lowerCamelCase e `entity-name` é PascalCase. Palavras reservadas Java e PostgreSQL
são rejeitadas semanticamente mesmo quando casam essas produções.

## 2. Estrutura Markdown

Cada arquivo declara **zero ou uma** entidade. Um arquivo sem `## Data` é um módulo puramente
declarativo — hoje, um módulo de `## Logic`:

```ebnf
spec          = entity-heading, [ data-section ],
                { use-case-section | logic-section | documentation } ;
entity-heading = h1, sp, entity-name ;
data-section   = h2, sp, "Data", newline, { field-item | documentation } ;
use-case-section = h2, sp, use-case-title, newline,
                   endpoint-section, access-section, [ input-section ],
                   [ rules-section ], flow-section, output-section,
                   [ errors-section ] ;
```

- Deve existir exatamente um H1 (`#`) no arquivo.
- Deve existir no máximo uma seção `## Data`. Sem ela o módulo não declara entidade e não pode
  declarar casos de uso, porque um caso de uso V0 opera sobre a entidade do arquivo.
- Um H2 com o prefixo `Logic` é uma declaração de computação pura, especificada em
  [`harpia-logic-v1-draft.md`](harpia-logic-v1-draft.md).
- Todo outro H2 é título de caso de uso, salvo `## Events`, `## Email` e `## Security`, rejeitados
  como recursos fora do V0.
- Um caso de uso exige exatamente uma seção de cada: `### Endpoint`, `### Access`, `### Flow` e
  `### Output`. `### Input`, `### Rules` e `### Errors` são opcionais e não podem se repetir.
- A ordem canônica é a mostrada na produção. O parser identifica as seções por heading, não por
  posição, mas o formatter futuro pode normalizar para essa ordem.
- Parágrafos fora de construções executáveis são documentação e não têm semântica.
- `### Rules` é documentação na V0. Na V1 um **item de lista** sob `### Rules` é uma condição
  executável (§9.1); parágrafos continuam documentação em qualquer versão, e nenhuma frase livre é
  interpretada.

Títulos de caso de uso contêm palavras ASCII separadas por um espaço, cada palavra iniciando por
maiúscula. Eles são concatenados para formar um identificador PascalCase: `Create Customer` vira
`CreateCustomer`.

```ebnf
title-word     = upper, { lower | upper | digit } ;
use-case-title = title-word, { sp, title-word } ;
```

## 3. Tipos

Os dez tipos V0 são:

| Harpia | Java | PostgreSQL |
|---|---|---|
| `String` | `java.lang.String` | `varchar(255)` |
| `Text` | `java.lang.String` | `text` |
| `Int` | `java.lang.Integer` | `integer` |
| `Long` | `java.lang.Long` | `bigint` |
| `Decimal` | `java.math.BigDecimal` | `numeric(19, 2)` |
| `Boolean` | `java.lang.Boolean` | `boolean` |
| `UUID` | `java.util.UUID` | `uuid` |
| `Email` | `java.lang.String` | `varchar(320)` |
| `Date` | `java.time.LocalDate` | `date` |
| `DateTime` | `java.time.Instant` | `timestamp with time zone` |

```ebnf
type = "String" | "Text" | "Int" | "Long" | "Decimal" | "Boolean"
     | "UUID" | "Email" | "Date" | "DateTime" ;
```

`Enum`, `List<T>`, `T?`, nomes Java e referências `-> Entity` não pertencem ao V0. `List<Entity>`
existe apenas como forma de output; não é um tipo de campo.

## 4. Campos de entidade

Cada item de lista diretamente sob `## Data` declara um campo:

```ebnf
field-item = bullet, sp, field-name, ":", sp, type, { sp, field-modifier } ;

field-modifier = "required"
               | "unique"
               | "generated"
               | "default", sp, literal ;

literal = boolean-literal | integer-literal | decimal-literal
        | string-literal | uuid-literal | date-literal | datetime-literal ;
```

Os modificadores podem aparecer em qualquer ordem, mas cada um aparece no máximo uma vez. `default`
consome o restante da linha como um único literal. Literais são:

- `Boolean`: `true` ou `false`;
- `Int` e `Long`: inteiro decimal com sinal opcional, sem separador;
- `Decimal`: inteiro ou decimal com sinal opcional e ponto;
- `String`, `Text` e `Email`: string JSON entre aspas duplas;
- `UUID`: forma canônica minúscula `8-4-4-4-12`;
- `Date`: ISO-8601 `YYYY-MM-DD`;
- `DateTime`: instante RFC 3339 em UTC terminado em `Z`.

Um default de tipo incompatível gera `HRP2004`. `required` e `default` podem coexistir; o default é
usado quando o campo não foi enviado, enquanto valor `null` continua inválido.

Toda entidade possui exatamente este campo:

```md
- id: UUID generated
```

O nome deve ser `id`, o tipo deve ser `UUID`, e `generated` não pode ser usado em outro campo no V0.
Campos duplicados e nomes reservados são erros semânticos.

## 5. Endpoint e acesso

O conteúdo de `### Endpoint` é exatamente uma linha:

```ebnf
endpoint = method, sp, path ;
method   = "GET" | "POST" | "PUT" | "DELETE" ;
path     = "/", path-segment, { "/", path-segment }, [ "/{id}" ] ;
path-segment = lower, { lower | digit | "-" } ;
```

`{id}` é a única variável de path permitida, aparece no máximo uma vez e somente como último
segmento. Query parameters não fazem parte da sintaxe V0.

O conteúdo de `### Access` é exatamente:

```text
public
```

`authenticated`, listas de roles ou qualquer outro valor geram `HRP4001`.

## 6. Input

Cada item diretamente sob `### Input` referencia um campo da entidade:

```ebnf
input-item = bullet, sp, field-name, ":", sp, type, [ sp, "required" ] ;
```

O campo deve existir em `## Data` e repetir exatamente seu tipo. `id` e campos `generated` não podem
ser input. `unique` e `default` são propriedades do dado e não são repetidos no input.

Ausência de `### Input` representa input vazio. `validate input` é permitido somente quando a seção
existe e possui ao menos um campo.

## 7. Flow

`### Flow` contém exatamente um fenced code block com info string `flow`. Cada linha não vazia casa
um e somente um dos oito comandos abaixo:

```ebnf
flow          = flow-step, { newline, [ flow-step ] } ;

flow-step     = validate-input
              | create-from
              | load-by-id
              | update-from
              | list-all
              | save
              | delete
              | return ;

validate-input = "validate", sp, "input" ;
create-from    = variable, sp, "=", sp, "create", sp, entity-name, sp, "from", sp, "input" ;
load-by-id     = variable, sp, "=", sp, "load", sp, entity-name, sp, "by", sp, "id" ;
update-from    = "update", sp, variable, sp, "from", sp, "input" ;
list-all       = variable, sp, "=", sp, "list", sp, entity-name ;
save           = "save", sp, variable ;
delete         = "delete", sp, variable ;
return         = "return", sp, ( variable | "nothing" ) ;
```

Semântica fixa:

| Comando | Efeito e variável resultante |
|---|---|
| `validate input` | valida o DTO; não define variável. |
| `x = create E from input` | cria `x: E` sem persistir. |
| `x = load E by id` | carrega `x: E` ou aciona `not found`. |
| `update x from input` | copia campos presentes no input para `x`. |
| `xs = list E` | define `xs: List<E>` com todos os registros, em ordem de `id`. |
| `save x` | persiste uma variável de entidade. |
| `delete x` | remove uma variável de entidade. |
| `return x` / `return nothing` | encerra o flow com o valor indicado. |

Cada variável deve ser definida antes do uso e só pode ser definida uma vez. `E` deve ser a entidade
do arquivo. Todo flow termina em um único `return`; nenhum passo pode sucedê-lo. Os comandos
`find`, `require`, `fail`, `set`, `if`, `else`, `transaction`, `call`, `store`, `send` e `emit` não
pertencem ao V0.

## 8. Output

O conteúdo de `### Output` é uma linha:

```ebnf
output       = status, sp, output-shape ;
status       = digit, digit, digit ;
output-shape = entity-name | "List<", entity-name, ">" | "nothing" ;
```

O status deve estar entre 200 e 299. O valor retornado pelo flow deve ter a mesma forma do output.
`return nothing` exige `nothing`; uma variável `E` exige `E`; e uma variável `List<E>` exige
`List<E>`. Status 204 exige `nothing` e `nothing` exige status 204.

## 7.0 Coleções

Na V1, um campo pode ser `List<T>`, onde `T` é um escalar ou um `Enum` declarado:

```markdown
- tags: List<String> required
- channels: List<Channel>
```

Uma coleção tem tantas linhas por dono quantos elementos tiver, então **não cabe na linha do dono**.
Esse único fato decide o mapeamento inteiro: ela ganha tabela própria, `<tabela>_<campo>`, ligada ao
dono por chave estrangeira, e a entidade que a declara não tem coluna para ela.

`required` numa coleção significa **não-vazia**: uma coleção que precisa existir mas pode estar
vazia é o mesmo que uma coleção ausente.

Fora do recorte: `List<Entity>` é relacionamento (`DOM-012`); `List<Value>` e `List<List<...>>` são
recusados. Coleção como parâmetro ou retorno de Logic é `LOGIC-006`.

### 7.0.2 Reference

Na V1, `Reference<Entidade>` declara que o campo aponta para outra entidade:

```markdown
- buyer: Reference<Customer> required
```

É um **tipo**, não um relacionamento. Ele diz qual entidade é a alvo e qual linha, e não diz nada
sobre carregá-la, cascatear até ela ou possuir seu ciclo de vida — isso é `DOM-012`.

| | Resultado |
|---|---|
| coluna | `<campo>_id`, com o tipo do id do alvo |
| migração | `FOREIGN KEY (<campo>_id) REFERENCES <alvo> (id)` |
| campo JPA | o escalar do id (`UUID`), sem `@ManyToOne` nem `@JoinColumn` |

O alvo precisa ser uma entidade declarada: uma referência aponta para algo que tem identidade.
Integridade referencial é a única promessa que ela faz, e por isso o esquema a declara enquanto o
mapeamento não importa estratégia de fetch nenhuma.

### 7.0.1 Optional

Na V1, `Optional<T>` declara que o valor pode não estar lá:

```markdown
- nickname: Optional<String>
```

Um campo sem `required` já é nullable, mas nada no contrato gerado diz isso — quem consome recebe um
`String` e descobre em tempo de execução. `Optional<T>` move esse fato para o tipo entregue ao
chamador, **sem mudar como o valor é armazenado**:

| | Tipo |
|---|---|
| campo JPA e coluna | `String`, nullable — JPA mapeia pelo tipo declarado e não entende `Optional` |
| getter e response DTO | `Optional<String>` |
| setter | `String` — recebe o valor, não a possibilidade de um |

`Optional<T> required` é contradição e é recusada (`HRP2126`). Esse é o ganho de tornar a
opcionalidade um tipo: as duas formas de dizê-la agora podem se contradizer, e uma contradição que o
compilador resolvesse em silêncio seria um bug atribuído ao gerador.

## 7.1 Enum

Na V1, `## Enum <Nome>` declara um conjunto fechado de valores que o projeto nomeia:

```markdown
## Enum TicketStatus

- open
- in_progress
- closed
```

- o nome é PascalCase e vive no mesmo namespace de tipos das entidades;
- cada valor é uma lista direta em `lower_snake_case`, no vocabulário da especificação, não de uma
  linguagem-alvo;
- valores duplicados e um enum sem valores são recusados (`HRP1108`).

Um campo referencia o enum pelo nome: `- status: TicketStatus required`. Um nome PascalCase que
nenhuma declaração do projeto fornece é `HRP2123` — a decisão é semântica, não sintática, porque o
parser enxerga um módulo e a declaração pode estar em qualquer um.

O target Java/Spring materializa o enum no pacote de domínio, com as constantes em maiúsculas
(`IN_PROGRESS`), e armazena o **nome**, não o ordinal: um ordinal codifica posição e muda quando a
especificação reordena seus valores.

## 7.2 Value

Na V1, `## Value <Nome>` declara um grupo de campos comparado pelo que contém, não por uma
identidade:

```markdown
## Value Address

- street: String required
- city: String required
- zip: String required
```

- os campos usam a mesma gramática de `## Data`, mas `generated` e `unique` são recusados
  (`HRP1109`): identidade pertence a uma entidade, não a um valor;
- um valor sem campos é recusado;
- um valor não contém outro valor neste recorte — aninhamento é `DOM-014`.

Um campo referencia o valor pelo nome: `- destination: Address required`.

O target Java/Spring gera uma classe `@Embeddable` no pacote de domínio — classe, e não record,
porque JPA embute por acesso a campo e exige construtor sem argumentos; o construtor completo
permanece. O valor **não ganha tabela**: seus campos viram colunas da entidade que o contém,
prefixadas pelo nome do campo (`destination_street`), com `@AttributeOverride` correspondente. O
prefixo existe para que dois valores na mesma tabela não colidam.

## 7.3 Invariants

Na V1, `## Invariants` declara condições que a entidade do módulo satisfaz sempre:

```markdown
## Data

- id: UUID generated
- total: Decimal required
- discount: Decimal required

## Invariants

- discount <= total
- total > 0
```

A diferença para `### Rules` (§8.1) é de escopo e de momento:

| | Escopo | Quando vale |
|---|---|---|
| `### Rules` | o input de **uma** operação | onde o flow declara `validate input` |
| `## Invariants` | os campos da **entidade** | antes de cada `save`, em qualquer operação |

- a condição é tipada contra os campos da entidade pelo mesmo analisador de expressões de Logic;
- o módulo precisa declarar `## Data`, senão não há o que restringir (`HRP2125`);
- violar um invariante responde **422**, não 400: uma requisição bem formada pedindo um estado que
  a entidade não permite não é input inválido.

A verificação acontece antes do `save` porque depois dele o estado proibido já está armazenado.

## 8.1 Rules

Na V1, cada item de lista sob `### Rules` é uma condição booleana sobre o input da operação:

```markdown
### Rules

O desconto nunca ultrapassa o total.

- total > 0
- discount <= total
```

A condição usa a mesma linguagem de expressão dos corpos de Logic e é verificada pelo mesmo
analisador — uma regra não pode discordar de uma computação sobre o que um operador significa.

- o escopo é o input da operação e nada mais. Uma regra que lesse a entidade armazenada estaria
  perguntando algo que a operação ainda não carregou; alcance maior é `RULE-004`;
- a condição precisa ser `Boolean`;
- a operação precisa declarar `### Input`, senão não há o que restringir;
- o flow precisa declarar `validate input`, porque é ali que a regra roda. Sem isso a regra seria
  escrita, compilada e nunca executada — `HRP2122`;
- violar uma regra é input inválido: responde o status declarado para `invalid input`, e a mensagem
  cita a regra como ela foi escrita.

Regra é a parte da validação de input que nenhuma anotação de campo consegue expressar, porque
relaciona dois valores. O que um campo diz sobre si mesmo continua em `### Input`.

## 8.3 find

Na V1, o Flow pode buscar um registro por um campo que não é o id:

```flow
account = find Account by email
return account
```

- o campo precisa ser `unique` na entidade (`HRP2128`). `load ... by id` sempre responde no máximo
  um registro porque um id é único; um `find` precisa merecer a mesma promessa, senão o flow
  atribuiria uma pergunta de muitas respostas a uma variável só;
- o campo precisa existir no `### Input` da operação, que é de onde vem o valor buscado;
- não encontrar é a mesma falha que um `load ... by id` reporta: `not found`.

O target gera um finder derivado no repositório (`Optional<Account> findByEmail(String email)`) —
Spring Data lê o nome do método, então o nome **é** a consulta.

Busca por campo não-único, com filtros e mais de um resultado, é `QUERY-003`.

## 8.4 list ... by

Na V1, o Flow pode listar só os registros que casam com um valor:

```flow
tickets = list Ticket by status and owner
return tickets
```

É a contrapartida de `find` (§8.3):

| | Responde | Campo |
|---|---|---|
| `find Entity by campo` | no máximo um | precisa ser `unique` |
| `list Entity by campo` | muitos | não precisa ser único |

Muitos registros compartilharem um valor é exatamente o que uma lista filtrada pede, então a
exigência de unicidade não se aplica aqui. Cada campo precisa existir na entidade e no `### Input`.

O target gera um finder derivado multi-campo (`findByStatusAndOwner(...)`) e mantém a **mesma ordem
estável por id** que uma listagem sem filtro já tem — filtrar muda quais registros voltam, não a
ordem em que voltam.

Fora do recorte: operadores além de igualdade, filtro opcional e combinação com `or`.

## 8.5 sorted by

Na V1, uma listagem pode declarar a ordem em que devolve os registros:

```flow
tickets = list Ticket sorted by priority desc and title
tickets = list Ticket by status sorted by priority desc
```

- vale para `list` com e sem filtro;
- direção omitida é ascendente;
- cada campo precisa existir na entidade.

**O id permanece como desempate final.** Ordenar por um campo que muitos registros compartilham
deixaria os empates na ordem que o banco preferisse, e Harpia promete os mesmos bytes para a mesma
entrada. Então a ordem declarada **refina** a ordem estável em vez de substituí-la:

```java
Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("title"), Sort.Order.asc("id"))
```

## 8.6 paged

Na V1, uma listagem pode devolver só uma fatia:

```flow
tickets = list Ticket by status sorted by title paged
```

`page` e `size` vêm do `### Input` da própria operação e precisam estar declarados como `Int`
(`HRP2129`):

```markdown
### Input

- status: String required
- page: Int required
- size: Int required
```

Eles são a **única exceção** à regra de que todo input nomeia um campo da entidade — descrevem a
requisição, não a entidade. Declará-los em vez de inventá-los é o que faz o request model gerado
mostrar ao chamador o que ele precisa enviar.

Uma página é uma fatia de uma ordem, então a paginação se apoia na ordem estável (§8.5) em vez de
substituí-la:

```java
PageRequest.of(request.page(), request.size(),
        Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")))
```

Fora do recorte: o envelope com total de registros e de páginas é `TYPE-025`/`API-010`. Hoje a
operação devolve `List<Entity>` com os registros da página.

## 8.2 fail

Na V1, o Flow pode levantar um erro de domínio declarado:

```markdown
### Flow

```flow
validate input
fail insufficient balance when amount > balance
payment = create Payment from input
save payment
return payment
```

### Errors

- insufficient balance -> 422
```

- a guarda é obrigatória. Um `fail` incondicional encerraria toda execução da operação, então a
  condição é o que faz dele uma instrução e não um beco sem saída;
- a condição é booleana sobre o input, tipada pelo mesmo analisador de `### Rules` e
  `## Invariants`;
- o erro precisa estar declarado em `### Errors` da própria operação (`HRP2127`). O status vive lá,
  e levantar um erro não declarado deixaria o compilador escolhendo um status que ninguém escreveu;
- o erro é levantado exatamente onde o flow o posiciona.

A diferença para `### Rules`: uma rule diz que o input é inválido e responde o status de
`invalid input`. Um `fail` nomeia **qual** erro de negócio ocorreu e responde o status daquele erro.

## 8.7 Page como saída

Na V1, `### Output` aceita `Page<Entidade>`:

```markdown
### Output

200 Page<Ticket>
```

`List<Ticket>` sobre uma consulta paginada entrega ao chamador os registros sem nenhuma forma de
pedir os próximos. `Page<Ticket>` carrega o que ele precisa para continuar:

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
```

- o envelope é genérico e emitido **uma vez por projeto**: o que uma página reporta sobre si mesma
  não depende do que ela contém;
- o finder passa a devolver `Page` para trazer a contagem da **mesma** consulta que fatiou, em vez
  de uma segunda consulta que poderia discordar;
- declarar `Page<...>` sem uma listagem `paged` no flow é recusado — não haveria página a reportar;
- uma listagem paginada pode continuar respondendo `List<...>` quando o chamador não precisa dos
  metadados, e aí nenhum envelope é gerado.

## 9. Errors

Cada item diretamente sob `### Errors` declara uma condição e seu status:

```ebnf
error-item       = bullet, sp, error-condition, sp, "->", sp, status ;
error-condition  = detected-condition | domain-error ;
detected-condition = "invalid input"
                   | "duplicate", sp, field-name
                   | "not found" ;
domain-error     = lower-word, { sp, lower-word } ;
```

Há duas espécies de condição, e a diferença é quem sabe quando ela ocorre.

**Condições detectadas** são reconhecidas pelo runtime, então o compilador sabe exatamente quando
acontecem e fixa o status:

| Condição | Status |
|---|---|
| `invalid input` | `400` |
| `duplicate <field>` | `409` |
| `not found` | `404` |

O campo de `duplicate` deve existir e ser `unique`. Uma condição detectada só é declarada quando
pode ocorrer: `invalid input` exige `validate input`; `not found` exige `load ... by id`; e
`duplicate` exige criação/atualização seguida de `save`.

**Erros de domínio** são nomeados pelo negócio — `insufficient balance -> 422`. Só a especificação
sabe o que significam, então ela declara nome e status. O nome é uma frase em minúsculas, vira o
símbolo canônico (`InsufficientBalance`) e o target gera o tipo correspondente mais o mapeamento
para o status declarado. O status precisa ser 4xx ou 5xx: uma falha que responde 2xx não é falha.

Um erro de domínio é um tipo só no projeto inteiro, portanto responde com um status só. Declarar
`insufficient balance` com 422 em uma operação e 409 em outra é `HRP2121`.

A instrução de Flow que levanta um erro de domínio (`fail`) ainda não existe. O que a declaração
entrega hoje é o contrato: o tipo, o status e o handler.

## 10. Exemplo completo

A fixture normativa está em
[`examples/customer/specs/customer.harpia.md`](../../examples/customer/specs/customer.harpia.md).
Ela cobre os oito comandos por meio de Create, Get, List, Update e Delete.

## 11. Compatibilidade

Adicionar novo tipo, comando, modifier ou condição altera a linguagem e exige atualização desta
especificação, parser, IR, validação, emissores e goldens no mesmo change set. Texto documental
continua livre, mas nunca adquire semântica implicitamente.
