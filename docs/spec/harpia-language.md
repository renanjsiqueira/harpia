# Harpia Language Specification — V0

Status: normativa para o MVP. Esta versão descreve somente CRUD de entidade única.

A evolução proposta está em [`harpia-language-v1-draft.md`](harpia-language-v1-draft.md). O draft
não é aceito pelo compiler até ser promovido explicitamente para a especificação normativa.

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
- `### Rules` é documentação no V0. Regras executáveis devem ser representadas por input, flow e
  errors; nenhuma frase livre é interpretada.

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

## 9. Errors

Cada item diretamente sob `### Errors` declara uma condição e seu status:

```ebnf
error-item      = bullet, sp, error-condition, sp, "->", sp, status ;
error-condition = "invalid input"
                | "duplicate", sp, field-name
                | "not found" ;
```

Mapeamentos V0 obrigatórios:

| Condição | Status |
|---|---|
| `invalid input` | `400` |
| `duplicate <field>` | `409` |
| `not found` | `404` |

O campo de `duplicate` deve existir e ser `unique`. Uma condição só é declarada quando pode ocorrer:
`invalid input` exige `validate input`; `not found` exige `load ... by id`; e `duplicate` exige
criação/atualização seguida de `save`.

## 10. Exemplo completo

A fixture normativa está em
[`examples/customer/specs/customer.harpia.md`](../../examples/customer/specs/customer.harpia.md).
Ela cobre os oito comandos por meio de Create, Get, List, Update e Delete.

## 11. Compatibilidade

Adicionar novo tipo, comando, modifier ou condição altera a linguagem e exige atualização desta
especificação, parser, IR, validação, emissores e goldens no mesmo change set. Texto documental
continua livre, mas nunca adquire semântica implicitamente.
