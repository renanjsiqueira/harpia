# Harpia Logic — Grammar V1 (draft normativo)

Status: **§1–§12 são implementados** (slices L1 e L2). §13 em diante é proposta arquitetural e não
é aceita pelo compilador. A especificação normativa geral continua em
[`harpia-language.md`](harpia-language.md).

A análise que fundamenta este documento está em [`../logic/analysis.md`](../logic/analysis.md).

> **Formula computa. Decision escolhe. Logic raciocina. Flow age.**

## 0. Princípios

1. **Flow descreve o que acontece. Logic descreve como valores de negócio são calculados.**
2. **Lógica pura permanece pura. Efeitos colaterais permanecem explícitos.**
3. **Preferir operações semânticas a mecânica imperativa.**
4. **Preferir fórmulas e decisões a laços de baixo nível.**
5. **Lógica de negócio é compilada, não interpretada.**
6. **Algoritmos complexos continuam disponíveis por Custom Java.**
7. **Harpia Logic aumenta densidade semântica sem virar linguagem de propósito geral.**

Consequência operacional dos princípios 5 e 7: não existe interpretador Harpia em runtime, não
existe `eval`, e a gramática só cresce quando uma construção é recorrente em negócio e produz
compressão semântica mensurável.

## 1. Camadas

```text
Structure   Entity / Value / Enum
Rules       Constraint / Invariant / Policy
Logic       Formula / Decision / Logic        <- esta especificação
Flow        Command / Query / orquestração
Workflow    composição de commands
```

`Formula`, `Decision` e `Logic` formam a camada **Business Computation**. As três são puras. A
diferença é de forma, não de poder:

| Construção | Forma | Uso |
|---|---|---|
| `Formula` | uma única expressão | cálculo direto |
| `Decision` | tabela condição → resultado | política/escolha |
| `Logic` | sequência de atribuições, condicionais e retornos | raciocínio composto |

O V1 implementa **apenas `Logic`**. `Formula` e `Decision` estão especificados em §13–§14 e entram
como slices próprios.

## 2. Declaração

````ebnf
logic-declaration = h2, sp, "Logic", sp, logic-name, newline,
                    input-section, output-section, logic-block ;

logic-name        = upper, { lower | upper | digit } ;
input-section     = h3, sp, "Input", newline, { parameter-item | documentation } ;
parameter-item    = bullet, sp, identifier, ":", sp, type ;
output-section    = h3, sp, "Output", newline, type ;
logic-block       = fenced-code-block with info string "logic" ;
````

Exemplo canônico:

````md
## Logic CalculateDiscount

### Input

- total: Decimal
- vip: Boolean

### Output

Decimal

```logic
if vip
    return total * 0.20

if total >= 1000
    return total * 0.10

return 0
```
````

Regras:

- `### Input` e `### Output` são **obrigatórios**; uma Logic sem parâmetros declara `### Input` vazio;
- `### Output` contém exatamente um tipo — não um status HTTP. Logic não tem binding HTTP;
- a declaração contém **exatamente um** fenced block com info string `logic`;
- o bloco é identificado pela info string, não pela posição: **um bloco executável pertence à
  declaração cujo kind corresponde à sua info string.** A ordem canônica é Input, Output, bloco, e
  `harpia fmt` normalizará para ela;
- parâmetros não repetem nome; nomes reservados Java são rejeitados;
- `## Logic X` pertence a um namespace de projeto: dois arquivos não podem declarar `Logic X`.

### 2.1 Módulo sem entidade

Um `*.harpia.md` passa a declarar **zero ou uma** seção `## Data`. Um módulo sem `## Data` não
declara entidade e deve declarar ao menos uma outra construção. Isso permite arquivos puramente
computacionais sem inventar uma entidade artificial:

```md
# Pricing

## Logic CalculateDiscount
...
```

Isso é a única mudança de compatibilidade da V0: nenhuma spec V0 válida deixa de ser válida.

## 3. Léxico do bloco `logic`

O bloco é lido linha a linha a partir do `RawSpan` do fenced block, portanto cada token conhece
`file:line:column` reais.

```ebnf
identifier      = lower, { lower | upper | digit } ;
type-name       = upper, { lower | upper | digit } ;
integer-literal = digit, { digit } ;
decimal-literal = digit, { digit }, ".", digit, { digit } ;
string-literal   = '"', { character - '"' | escape }, '"' ;
boolean-literal = "true" | "false" ;
```

- Linhas em branco são ignoradas e não afetam indentação.
- **Tabs são rejeitados** dentro do bloco (`HRP1102`). A indentação é sempre espaço ASCII.
- Comentários não existem no V1. Documentação vive em Markdown, fora do bloco.
- Um literal decimal exige dígito antes e depois do ponto: `0.20`, nunca `.20` nem `20.`.
- Sinal negativo é o operador unário `-`, não parte do literal.

### 3.1 Palavras reservadas

```text
if  else  return  and  or  not  true  false
```

Reservadas para uso futuro e rejeitadas como identificador:

```text
when  otherwise  match  for  each  in  where  exists  null  let  const
```

Reservadas por serem **operações com efeito colateral** (§7):

```text
save  delete  update  create  load  find  list  emit  send  call  store  transaction
```

### 3.2 Caracteres de operador

```text
+  -  *  /  ==  !=  <  <=  >  >=  (  )  ,  .  =
```

`%` **não** é operador de módulo. Ele é reservado exclusivamente como sufixo do literal
`Percentage` (§16). Módulo, quando houver demanda, entra como built-in `mod(a, b)`. Isso elimina por
construção a ambiguidade entre `a % b` e `10%`.

`&&`, `||` e `!` são rejeitados com uma mensagem que aponta `and`, `or` e `not`.

## 4. Indentação e blocos

A indentação é significativa e **estritamente quatro espaços por nível**.

```ebnf
logic-body  = { statement } ;
statement   = assignment | conditional | return-statement ;
assignment  = identifier, sp, "=", sp, expression ;
return-statement = "return", sp, expression ;
conditional = "if", sp, expression, newline,
              indented-block,
              [ dedent, "else", newline, indented-block ] ;
indented-block = one or more statements at exactly parent indent + 4 ;
```

- indentação que não seja múltiplo de 4 → `HRP1102`;
- salto de mais de um nível → `HRP1102`;
- `else` fica na mesma coluna do seu `if`;
- `else` sem `if` correspondente → `HRP1104`;
- profundidade máxima de aninhamento é 3 (acima disso, `harpia lint` recomendará `Decision`).

Um statement ocupa exatamente uma linha. Não há continuação de linha no V1: se uma expressão não
cabe confortavelmente em uma linha, ela deve ser quebrada em atribuições nomeadas — o que aumenta a
legibilidade de negócio em vez de reduzi-la.

## 5. Expressões

Precedência, do menor para o maior. Todos os níveis binários são associativos à esquerda, exceto
comparação, que é **não associativa** (`a < b < c` é erro).

| Nível | Operadores | Associatividade |
|---|---|---|
| 1 | `or` | esquerda |
| 2 | `and` | esquerda |
| 3 | `not` | prefixo |
| 4 | `==` `!=` `<` `<=` `>` `>=` | não associativa |
| 5 | `+` `-` | esquerda |
| 6 | `*` `/` | esquerda |
| 7 | `-` unário | prefixo |
| 8 | `.` (acesso a membro), chamada | pós-fixo |

```ebnf
expression   = or-expression ;
or-expression  = and-expression, { sp, "or", sp, and-expression } ;
and-expression = not-expression, { sp, "and", sp, not-expression } ;
not-expression = [ "not", sp ], comparison ;
comparison   = additive, [ sp, comparison-op, sp, additive ] ;
additive     = multiplicative, { sp, ( "+" | "-" ), sp, multiplicative } ;
multiplicative = unary, { sp, ( "*" | "/" ), sp, unary } ;
unary        = [ "-" ], postfix ;
postfix      = primary, { ".", identifier } ;
primary      = literal | identifier | call | "(", expression, ")" ;
call         = type-name, "(", [ argument-list ], ")" ;
argument-list = argument, { ",", sp, argument } ;
argument     = identifier, sp, "=", sp, expression ;
```

O uso de `and`/`or`/`not` em vez de `&&`/`||`/`!` é deliberado: a spec é lida por pessoas de negócio
e por LLMs, e palavras produzem menos erro de leitura que pontuação.

### 5.1 Chamadas

Argumentos são **sempre nomeados**. Posicional não é aceito no V1.

```logic
discount = CalculateDiscount(total = order.total, vip = customer.vip)
```

Justificativa: posicional economiza caracteres mas é frágil a reordenação e ambíguo quando dois
parâmetros compartilham tipo — exatamente o caso de `(Money, Money)`. Uma única forma canônica
também simplifica formatter, diff e geração por LLM. Todos os parâmetros declarados devem ser
fornecidos exatamente uma vez; não há valores default.

### 5.2 Acesso a membro

`customer.vip` é acesso a campo de um valor nominal. No V1 apenas parâmetros de tipo escalar
existem, portanto `.` é aceito pela gramática e rejeitado pela análise semântica até `Entity` e
`Value` serem tipos de parâmetro válidos (slice L5). A produção existe agora para que a mensagem de
erro seja semântica, e não um erro de sintaxe genérico.

## 6. Escopo e atribuição

- cada Logic tem **um escopo próprio**; não há variáveis globais nem estado compartilhado;
- parâmetros de `### Input` são as ligações iniciais do escopo;
- uma atribuição **declara** a variável no bloco corrente;
- **atribuição é única**: reatribuir um nome é `HRP2112`;
- sombrear um parâmetro ou uma variável de bloco externo é `HRP2112`;
- variáveis declaradas dentro de um bloco `if`/`else` **não escapam** do bloco;
- variável declarada e não usada é warning `HRP2110`.

Reatribuição (`discount = discount + 5%`) e a forma composta (`discount += 5%`) são **deliberadamente
excluídas do V1**. Motivos:

1. com atribuição única, cada nome tem exatamente um tipo e uma definição — a inferência é
   trivialmente sólida e não exige SSA nem nós phi;
2. o padrão de acumulação que motiva a reatribuição é melhor expresso por `Decision` (§14), que é
   justamente a construção de maior densidade semântica do brief;
3. duas formas equivalentes (`x = x + y` e `x += y`) violam o princípio de forma canônica única.

Quando `Money`/`Percentage` e `Decision` existirem (slices L4/L5), a reatribuição será reavaliada
com evidência real de uso. Se entrar, entrará **apenas** na forma `x = x + y`.

## 7. Modelo de efeitos

O compilador classifica toda operação por efeito:

```text
PURE
PERSISTENCE_READ
PERSISTENCE_WRITE
INTEGRATION
EVENT
EMAIL
STORAGE
```

Regra: **`Formula`, `Decision` e `Logic` aceitam somente `PURE`. `Flow` aceita os demais conforme a
capability resolvida.**

O modelo é interno. Não é exposto na spec, não tem sintaxe e não aparece em `harpia.yaml`. Ele
existe para que a fronteira de pureza seja verificada pelo compilador e para que um effect system
futuro seja adição, não reescrita.

No V1 a pureza é garantida por construção — a gramática de `logic` não tem produção com efeito. A
verificação que resta é de **diagnóstico**: usar uma palavra de efeito dentro de `logic` produz uma
mensagem que ensina a fronteira em vez de um erro de sintaxe:

```text
HRP2106 spec/pricing.harpia.md:12:5
Operation `save` is not allowed inside Logic.

Logic must be pure.

Move side-effecting operations to Flow.
```

## 8. Sistema de tipos

`LogicType` é uma **álgebra selada**, nunca uma enum crescente:

```java
sealed interface LogicType {
    record Scalar(TypeRef kind) implements LogicType {}   // V1
    // L4: record MoneyType(...) / record PercentageType()
    // L6: record ListOf(LogicType element)
    // L5: record Named(SymbolId symbol, NamedKind kind)
}
```

Escalares do V1 são os dez da V0: `String Text Int Long Decimal Boolean UUID Email Date DateTime`.

### 8.1 Torre numérica e alargamento

```text
Int  <  Long  <  Decimal
```

Alargamento é **implícito e apenas para cima**, nas posições: operando binário, argumento de
chamada, valor de `return`. Estreitamento nunca é implícito. Não há conversão entre numérico e
`String`, `Boolean`, `UUID`, `Date` ou `DateTime`.

`Text` e `Email` são distintos de `String` na declaração, mas unificam com `String` em comparação e
igualdade — são refinamentos de domínio do mesmo valor textual.

### 8.2 Regras dos operadores

| Operador | Operandos | Resultado |
|---|---|---|
| `+` `-` `*` | numérico, numérico | o mais largo dos dois |
| `/` | numérico, numérico | **sempre `Decimal`** |
| `-` unário | numérico | o mesmo tipo |
| `<` `<=` `>` `>=` | numérico, numérico | `Boolean` |
| `==` `!=` | mesmo tipo (após alargamento) | `Boolean` |
| `and` `or` | `Boolean`, `Boolean` | `Boolean` |
| `not` | `Boolean` | `Boolean` |

`+` **não** concatena texto. Concatenação será um built-in explícito (`concat`), porque sobrecarregar
`+` é a porta de entrada mais comum para uma DSL virar Java simplificado.

`/` sempre produzir `Decimal` elimina a divisão inteira truncante, que é uma das fontes mais comuns
de defeito silencioso em cálculo de negócio. A divisão é feita com `MathContext.DECIMAL128`; a
política de arredondamento configurável entra com `Money` (§16).

### 8.3 Inferência

A inferência é local, ascendente e sem unificação global:

```logic
subtotal = 100          -> Int
rate = 0.10             -> Decimal
discount = subtotal * rate   -> Decimal
eligible = vip and total >= 1000  -> Boolean
```

O tipo de uma variável é o tipo inferido da sua expressão. Não existe anotação de tipo em variável
local: se o tipo não é óbvio, o nome está errado ou a expressão deve ser quebrada.

### 8.4 Diagnóstico de tipo

```text
HRP2103 spec/pricing.harpia.md:7:9
Cannot apply `+` to String and Decimal.
```

O compilador Harpia nunca delega a descoberta de erro da linguagem ao compilador Java.

## 9. Retorno

- toda Logic tem `### Output`, portanto **todo caminho deve retornar**;
- o tipo do valor retornado deve ser o tipo de saída, ou alargar para ele;
- um bloco retorna definitivamente se termina em `return`, ou se contém um `if/else` em que **ambos**
  os ramos retornam definitivamente;
- statement após um retorno definitivo é inalcançável e é erro;
- caminho sem retorno é `HRP2105`.

```text
HRP2105 spec/pricing.harpia.md:3:1
Logic `CalculateDiscount` may finish without returning a value.
```

## 10. Recursão

Recursão **não é suportada** — nem direta nem indireta. O grafo de chamadas entre Logics deve ser
acíclico (`HRP2109`). Algoritmos que exigem recursão pertencem a Custom Java (§18), e essa fronteira
é permanente: ela é o que impede Harpia de se tornar Turing-completa por acidente.

## 11. Built-ins

Registry formal, fechado, sem reflexão e sem plugin system. Cada built-in declara nome, assinatura,
tipo de retorno e pureza.

V1:

| Nome | Assinatura | Retorno |
|---|---|---|
| `min` | (numérico, numérico) | o mais largo dos dois |
| `max` | (numérico, numérico) | o mais largo dos dois |

O restante do catálogo do brief (`round`, `sum`, `count`, `any`, `all`, `trim`, `lower`, `upper`,
`length`, datas) entra junto com o tipo que o justifica: `round` com `Money`, os agregadores com
`List<T>`, os de data com `Date`/`Duration`. Adicionar uma função sem o tipo que ela serve produz
API morta.

Built-ins são chamados com argumentos **posicionais**, ao contrário de Logic (§5.1): `min(a, b)` não
tem ambiguidade de ordem e nomear seria ruído.

## 12. Geração Java

Uma Logic pura gera **uma classe final, sem Spring, sem anotação, sem injeção**:

```java
// src/main/java/<pkg>/logic/CalculateDiscount.java
public final class CalculateDiscount {

    private CalculateDiscount() {
    }

    public static BigDecimal apply(BigDecimal total, Boolean vip) {
        if (vip) {
            return total.multiply(new BigDecimal("0.20"));
        }
        if (total.compareTo(new BigDecimal("1000")) >= 0) {
            return total.multiply(new BigDecimal("0.10"));
        }
        return new BigDecimal("0");
    }
}
```

Decisões:

- **classe final + construtor privado + método `static apply`**: uma função pura não tem estado,
  não precisa de bean e não deve ser mockada. Nada de `@Service`;
- pacote `<packageName>.logic`, um arquivo por Logic, isolado do service gerado;
- `Decimal` é `java.math.BigDecimal` sempre — **nunca** `double` ou `float`. Literais decimais usam
  o construtor de `String`, que é exato;
- comparação de `BigDecimal` usa `compareTo`, nunca `equals`, porque `equals` compara escala;
- alargamento `Int → Decimal` vira `BigDecimal.valueOf(...)`;
- o corpo é escrito por um writer recursivo sobre a árvore tipada, não por template Mustache:
  template logic-less não expressa aninhamento. O template cobre apenas o invólucro da classe;
- a expressão gerada é parentizada por precedência, para que o Java saia idiomático.

Não há runtime Harpia. O artefato final é Java convencional compilado pelo `javac`:

```text
Harpia Logic → Expression AST → typed Logic IR → Java source → bytecode
```

### 12.1 Determinismo

A mesma spec produz os mesmos bytes. Sem LLM, sem relógio, sem hash de ordem de iteração, sem
inferência em runtime.

## 12.2 Scenario

Uma computação pura não tem comportamento observável do qual o compilador possa derivar uma
expectativa. Ele prova os tipos de `CalculateDiscount`; ele não sabe qual número está certo. Essa
informação só existe na cabeça de uma pessoa, e `## Scenario` é onde ela entra no pipeline.

````md
## Scenario VIP discount

### Given

- total: 100
- vip: true

### When

CalculateDiscount

### Then

- result: 20.00
````

```ebnf
scenario-declaration = h2, sp, "Scenario", sp, scenario-title, newline,
                       given-section, when-section, then-section ;
given-section = h3, sp, "Given", newline, { binding } ;
when-section  = h3, sp, "When", newline, logic-name ;
then-section  = h3, sp, "Then", newline, "-", sp, "result", ":", sp, literal ;
binding       = bullet, sp, identifier, ":", sp, literal ;
```

Regras:

- `### When` nomeia uma computação declarada; a resolução usa a SymbolTable, então o cenário pode
  viver em qualquer arquivo;
- `### Given` liga **cada** input exatamente uma vez; faltar ou sobrar é erro;
- literais seguem as mesmas regras de `default` em `## Data`, com o mesmo alargamento numérico do
  resto da Logic: `20` é aceito onde se espera `Decimal`;
- `### Then` declara exatamente um `result`;
- títulos são únicos no projeto e viram nome de método: `VIP discount` → `vipDiscount`.

O compilador **não avalia** a Logic para conferir o valor esperado. Fazer isso exigiria uma segunda
implementação da semântica, mantida em paralelo com o generator para sempre. O valor esperado é
declarado, e o teste gerado é quem confronta os dois.

Um cenário no limite exato de uma condição — `total: 1000` para `if total >= 1000` — é o que
transforma `>=` virando `>` em uma falha visível. É o mesmo valor dos boundary tests, sem precisar
gerar entradas automaticamente.

### 12.3 Quando não há cenário

Se `generation.tests` está ligado e uma computação não declara cenário, o compilador **avisa**
(`HRP2119`) em vez de silenciosamente não gerar nada. Gerar um teste sem oráculo — que só verifica
que a função executa — daria uma falsa sensação de cobertura, e por isso não é feito.

## 13. Formula (proposta, slice L3)

````md
## Formula OrderSubtotal

### Input
- items: List<CartItem>

### Output
Money

```formula
sum(items.price * items.quantity)
```
````

`Formula` é `Logic` restrita a **uma única expressão** e sem statements. Ela não adiciona poder;
adiciona intenção. O analisador é o mesmo, com duas regras extras: exatamente uma expressão e
nenhuma atribuição. `harpia lint` recomendará converter uma `Logic` de expressão única em `Formula`.

Depende de `List<T>` e agregadores, portanto vem depois de L6.

## 14. Decision (proposta, slice L5)

Duas formas foram avaliadas.

**Forma A — bloco `decision`:**

```decision
20% when customer.vip
10% when subtotal >= BRL 1000
5% when subtotal >= BRL 500
0% otherwise
```

**Forma B — tabela Markdown:**

| Condition | Result |
|---|---:|
| customer.vip | 20% |
| subtotal >= BRL 1000 | 10% |
| otherwise | 0% |

| Critério | A (bloco) | B (tabela) |
|---|---|---|
| Parser | reusa o lexer de expressão; uma linha = uma regra | exige parser de tabela GFM, alinhamento, escape de `\|` |
| Diagnóstico | coluna exata do token | coluna dentro de célula, com offset artificial |
| Legibilidade | ótima até ~2 condições | ótima para matriz multidimensional |
| Confiabilidade para LLM | alta: formato livre de alinhamento | média: LLMs erram alinhamento e contagem de colunas |
| Semântica determinística | ordem textual = prioridade | idem, mas a coluna `any` precisa de significado próprio |

**Decisão: a forma canônica é o bloco `decision` (A).** A tabela (B) permanece planejada como
**Decision Table multidimensional** — uma construção distinta, para quando houver mais de uma coluna
de condição (`CreditRisk` com `Score` e `Income`), onde ela realmente vence.

Semântica formal, em ambos os casos:

- **a primeira regra que casa vence** (`first matching rule wins`), na ordem textual;
- as condições **não** precisam ser mutuamente exclusivas; a ordem resolve;
- se a Decision produz valor obrigatório, `otherwise` é **exigido**, salvo quando o analisador
  provar exaustividade (hoje, apenas para `Boolean` e `Enum` completos);

```text
HRP2xxx
Decision `DiscountRate` may produce no result.

Add an `otherwise` rule.
```

## 15. Condicional declarativa

O brief levanta uma segunda forma para condicional:

```logic
discount =
    20% when customer.vip
    0% otherwise
```

**Decisão: não adotar.** Ela é `Decision` inline com outra sintaxe. Duas formas equivalentes para a
mesma ideia custam parser, formatter, lint e ensino, e violam o princípio de forma canônica única. A
forma canônica para escolher um valor por condição é `Decision`; a forma canônica para ramificar o
raciocínio é `if`/`else`.

## 16. Money, Percentage e Currency (proposta, slice L4)

```logic
minimum = BRL 100
discount = total * 10%
```

- `Money` é tipo semântico de primeira classe, com moeda: `BRL 100`, `USD 20.50`;
- `BRL 100 + USD 10` é **erro de tipo**; conversão exige operação explícita;
- `Percentage` é tipo próprio: `Money * Percentage → Money`, `Percentage + Percentage → Percentage`;
- política de escala e arredondamento vive em `harpia.yaml`, nunca na Business Spec:

```yaml
money:
  defaultCurrency: BRL
  scale: 2
  rounding: HALF_UP
```

- `round(value, 2)` existe como built-in, mas a política global é o default. A gramática não terá
  dezenas de rounding modes.

## 17. Coleções e datas (proposta, slices L6 e L7)

Coleções: `List<T>` com `sum count min max average any all none first distinct contains filter map`.

Para filtro, duas formas:

```logic
activeItems = items where active
activeItems = filter(items, item.active)
```

**Decisão preliminar: `filter(items, item.active)`** — forma de função, com o elemento ligado a um
nome explícito. `items where active` é mais bonito mas introduz um escopo implícito não nomeado, o
que é ambíguo em expressões aninhadas (`sum(items.price * items.quantity)` precisa saber a que
elemento `price` pertence) e difícil de tipar. A projeção implícita `items.price` será açúcar
definido formalmente como `map(items, item.price)` — **não** como escopo mágico.

Datas: `Date`, `DateTime`, `Duration`; `today`, `now`; `now + 30m`, `today + 5d`;
`daysBetween/monthsBetween/yearsBetween`. Sufixos `ms s m h d w` mapeiam para `java.time.Duration`.
`mo`/mês **não** é `Duration` (mês não tem comprimento fixo) e mapeia para `java.time.Period`, com
tipo distinto — não serão unificados.

`for each` existe apenas em `Flow`, onde há efeito colateral. Em `Logic`, a forma canônica é
`map`/`filter`/agregação.

Pattern matching (`match payment.status`) é planejado para depois de `Enum` e do sistema de
expressões estáveis, e mapeará para `switch` Java.

## 18. Escape hatch

```md
## Logic CalculateComplexRisk

### Input
- customer: Customer

### Output
Risk

### Implementation
custom ComplexRiskCalculator
```

Gera uma interface Java pura implementada pelo usuário em `custom/`. Essa fronteira é o que permite
manter a gramática pequena: tudo que é algoritmo específico, recursivo ou de baixo nível sai da
linguagem sem sair do produto.

## 19. Roadmap de slices

| Slice | Conteúdo | Estado |
|---|---|---|
| **L1** | declaração `Logic`, literais, variáveis, `+ - * /`, parênteses, `return`, inferência, type checking, geração Java | **implementado** |
| **L2** | `if`/`else`, comparações, `and`/`or`/`not`, análise de retorno definitivo | **implementado** |
| **L9** | `## Scenario` e geração de JUnit puro para computações | **implementado** |
| L3 | `Formula` | planejado |
| L4 | `Money`, `Percentage`, `Currency`, `round` | planejado |
| L5 | `Decision`, tipos nominais como parâmetro, acesso a membro | planejado |
| L6 | `List<T>` e agregadores | planejado |
| L7 | `Date`, `DateTime`, `Duration` | planejado |
| L8 | `Flow → Logic` com type check cruzado | **bloqueado por E0.7** |
| L9 | `Scenario` sobre Logic e geração de JUnit | **implementado** |
| L10 | `Scenario` sobre Formula e Decision | planejado |

### 19.1 Dependências de fora da camada Logic

`L8` não é uma escolha de prioridade: o corpo do service **ainda não é gerado** (`EmitterPipeline`
emite pom, `Application.java` e `application.yaml`). Chamar Logic a partir de Flow pode ser
especificado, parseado, tipado e validado, mas não pode produzir Java antes de E0.7 emitir o
service. Marcar `Flow → Logic` como suportado antes disso violaria a regra de cobertura do projeto.

`harpia build` já escreve em disco por meio do `OutputWriter` e de seu manifesto. A prova de que o
Java gerado compila e executa existe tanto in-memory (`GeneratedLogicCompilesTest`) quanto no gate
Maven do target (`GeneratedMavenProjectTest`).

### 19.2 Regras de dependência entre construções

```text
Formula  → Formula
Decision → Formula, Decision
Logic    → Formula, Decision, Logic
Flow     → Formula, Decision, Logic + operações com efeito
Command  → Flow
Query    → Flow / Logic
```

Proibido e verificado pelo compilador:

```text
Logic    → Flow
Logic    → Command
Formula  → Integration
Decision → Database
```

## 20. Critério de estabilização

Uma construção de Logic sai de draft quando o mesmo change set contém gramática normativa, AST com
localização, símbolos, validação e diagnostics, IR de negócio, Application IR, geração Java, teste
de compilação do Java gerado, golden determinístico e atualização de `LANGUAGE_COVERAGE.md`.
