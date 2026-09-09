# Harpia HTTP Binding Specification — V1

Status: normativa para o primeiro slice executável de bindings externos em
`harpia.languageVersion: 1`.

## 1. Separação

```text
spec/       = o que o software faz
bindings/   = como operações são expostas
harpia.yaml = target, providers, ambiente e paths
```

`spec/` é obrigatório e `bindings/` é opcional. Os defaults são configuráveis:

```yaml
paths:
  specs: spec
  bindings: bindings
  output: generated
```

Os diretórios de spec e binding devem ser diferentes. Ambos aceitam arquivos UTF-8 terminados em
`.harpia.md`, são descobertos em ordem determinística e não seguem links simbólicos.

## 2. Gramática implementada

Um arquivo de binding HTTP possui exatamente um H1 `# HTTP Bindings` e uma ou mais declarações:

```ebnf
binding-file       = http-bindings-heading, [ base-url-section ],
                     http-binding, { http-binding } ;
http-bindings-heading = h1, sp, "HTTP Bindings" ;
base-url-section   = h2, sp, "Base URL", newline, path-prefix ;
http-binding       = h2, sp, "Bind", sp, operation-symbol, newline,
                     endpoint-section, access-section,
                     request-section, response-section ;
endpoint-section   = h3, sp, "Endpoint", newline, endpoint ;
access-section     = h3, sp, "Access", newline, "public" ;
request-section    = h3, sp, "Request", newline, ( "none" | request-mapping,
                     { request-mapping } ) ;
request-mapping    = "- ", input-name, ": ", ( "body"
                     | ( "path" | "query" | "header" ), sp, external-name ) ;
response-section   = h3, sp, "Response", newline, ( "output: body" | "none" ) ;
endpoint           = http-method, sp, path ;
http-method        = "GET" | "POST" | "PUT" | "DELETE" ;
operation-symbol   = upper, { letter | digit } ;
```

`## Base URL` é opcional, aparece no máximo uma vez e prefixa o path de todo binding do arquivo.
Cada item de `### Request` liga um campo de `### Input` da operação a uma posição do protocolo; o
nome externo é o nome no protocolo, não no domínio, e é por isso que ele é escrito explicitamente.
`body` não aceita nome externo e os demais o exigem.

O símbolo é o nome canônico da operação: `## Query Get Customer` é referenciado como
`GetCustomer`.

```markdown
# HTTP Bindings

## Bind GetCustomer

### Endpoint

GET /customers/{id}

### Access

public
```

## 3. Regras semânticas

- o símbolo referenciado deve existir no namespace global de operações;
- uma operação possui no máximo um binding HTTP;
- método e path formam uma rota única no projeto;
- `{id}` e `load <Entity> by id` aparecem juntos ou ambos ficam ausentes;
- um binding externo e um binding inline para a mesma operação são duplicados;
- bindings externos exigem `languageVersion: 1`.

Sem binding, `Command` e `Query` continuam sendo comportamento válido e geram serviço. Com
binding, a capability HTTP é inferida e o target Java/Spring gera controller e teste web.

O compilador preserva o range da declaração e do endpoint. No resultado de geração, o método do
controller possui um `GeneratedSourceMapping` que liga seu range Java exclusivo à linha do endpoint
no Markdown. Esse índice permanece disponível em memória mesmo que a aplicação use apenas a visão
de conteúdo da árvore gerada.

## 4. Geração

Um mapping `body` vira um parâmetro anotado do controller e o resolvedor de imports o enxerga pelo
tipo. Um binding sem `body` monta a request a partir dos parâmetros individuais, e aí o tipo é
nomeado apenas dentro de um statement: o import correspondente é declarado explicitamente pelo
transformer, porque nenhum nó tipado do modelo o revela. `ExternalHttpBindingTest` compila a árvore
gerada com `javac` justamente para que um import ausente falhe aqui, e não na máquina de quem roda
o projeto gerado.

| Mapping | Java/Spring |
| --- | --- |
| `x: path <nome>` | `@PathVariable("<nome>")` |
| `x: query <nome>` | `@RequestParam("<nome>")` |
| `x: header <Nome>` | `@RequestHeader("<Nome>")` |
| `x: body` | `@RequestBody` (com `@Valid` quando o Flow declara `validate input`) |
| `output: body` | corpo da `ResponseEntity` no status declarado em `### Output` |
| `none` | `ResponseEntity` sem corpo |

Quando o Flow declara `validate input`, a validação vale para a fronteira inteira, não só para o
corpo: cada parâmetro de path, query ou header recebe as constraints declaradas para aquele campo e
o controller ganha `@Validated`. Um corpo inválido falha como erro de binding; um parâmetro inválido
falha como violação de constraint dentro do método. São exceções diferentes, mas a especificação
declarou **um** status para `invalid input`, então ambas são mapeadas para ele — sem isso um 400
declarado apareceria como 500 apenas por causa de onde o valor entrou.

## 5. Limite atual

Este slice implementa base URL, method, path, access `public`, os quatro mappings de request e
`output: body`/`none`. Content negotiation, mapping de response abaixo do corpo inteiro, auth,
timeout, retry, multipart, messaging e persistence bindings permanecem no backlog (`BIND-007` em
diante).
