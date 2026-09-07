# Harpia Logic — análise da implementação atual

> Snapshot histórico da introdução de Harpia Logic. Estado atual do compiler e do target pack:
> [`../../ARCHITECTURE.md`](../../ARCHITECTURE.md) e [`../../TARGETS.md`](../../TARGETS.md).

Status: baseline de design que orientou L1/L2; implementação revalidada em 2026-09-06. O working
tree atual possui 101 arquivos de produção, 23 classes de teste e 141 testes verdes. As seções
descrevem a lacuna original e as decisões preservadas pela implementação.

Este documento responde aos itens 1–8 da primeira tarefa do brief **Harpia Logic**: o que existe
hoje em Flow, parser, AST, type system, SymbolTable e Business IR, e o que pode ser reaproveitado
pela camada de computação de negócio. A proposta formal está em
[`../spec/harpia-logic-v1-draft.md`](../spec/harpia-logic-v1-draft.md).

## 1. Como Flow funciona hoje

Flow é uma **lista fechada de oito comandos de linha**, sem expressões.

```text
### Flow  ->  exatamente um fenced block com info string `flow`
                 ↓ uma linha = um comando
        FlowLineParser (8 regex fechadas)
                 ↓
        SpecAst.FlowStatement (sealed, 8 records)
                 ↓ Resolver
        model.FlowStep (sealed, 8 records) + FlowModel.variables
                 ↓ ApplicationModelBuilder
        ApplicationOperation.FlowInstruction (command + variable? + entity?)
                 ↓
        (nenhum emissor — o corpo do service ainda não é gerado)
```

Fatos relevantes para Logic:

| Propriedade | Estado |
|---|---|
| Unidade sintática | uma linha inteira, casada por regex ancorada |
| Expressões | **não existem**; não há lexer, precedência, nem árvore de expressão |
| Variáveis | apenas `ENTITY` e `LIST`; nunca escalares |
| Tipagem de variável | `FlowModel.ValueType(kind, entityName)` — não usa `TypeRef` |
| Escopo | mapa local por use case, dentro de `SemanticValidator.validateFlow` |
| Atribuição | só nas formas `x = create/load/list`; não há `=` genérico |
| Condicional | não existe |
| Efeitos | implícitos: `CapabilityAnalyzer` classifica `LoadById/ListAll/Save/Delete` como `persistence` |
| Java gerado | **nenhum**; `EmitterPipeline` emite apenas pom, `Application.java` e `application.yaml` |

Consequência direta: **não existe nada em Flow que possa ser reaproveitado como motor de
expressões.** Logic não é uma extensão do Flow; é uma camada nova, e o Flow passará a consumi-la.

A observação mais importante é a última linha da tabela. Como o corpo do service ainda não é
gerado, `Flow → Logic` (item 88 do brief) pode ser especificado e validado, mas **não pode ser
gerado em Java** antes de E0.7. Isso condiciona o recorte da primeira entrega.

## 2. Parser

`SpecParser` é um parser de seções por heading, determinístico e bem testado:

- `MarkdownStructure` usa CommonMark **apenas para estrutura de blocos**;
- `RawSpan` recupera o texto exato do source normalizado, com linha/coluna 1-indexadas;
- `BlockNode.fencedBodyLines()` já devolve cada linha do fenced block com posição correta;
- `sectionsAtLevel(blocks, level)` agrupa um heading com todo o conteúdo até o próximo heading de
  nível menor ou igual;
- gramáticas de linha (`FieldLineParser`, `FlowLineParser`, `EndpointParser`, `OutputParser`,
  `ErrorLineParser`) são fechadas e não executam conteúdo arbitrário.

Limites que Logic encontra:

1. `SpecParser.parse` exige **exatamente um** `## Data`. Um módulo puramente computacional não tem
   entidade e hoje seria rejeitado.
2. Todo `## X` que não seja `Data` vira use case. `## Logic CalculateDiscount` casa
   `USE_CASE_TITLE` (`[A-Z][A-Za-z0-9]*( +[A-Z][A-Za-z0-9]*)*`) e falharia por falta de
   `### Endpoint`. É obrigatório interceptar o prefixo de declaração **antes** do ramo de use case.
3. `LineSyntax.at(start, raw, charOffset)` já converte offset de caractere em coluna contando code
   points — é exatamente o que um lexer de expressão precisa para localizar tokens.

Reaproveitável sem alteração: `MarkdownStructure`, `BlockNode`, `RawSpan`, `LineSyntax`,
`sectionsAtLevel`. A ser estendido: o despacho de declaração em `SpecParser`.

## 3. AST

`SpecAst` é uma AST plana por arquivo, com `SourceRef` (**início apenas**, sem fim de intervalo) em
todo nó relevante. Não há `ProjectAst`, `ModuleAst`, namespaces nem referências cross-file.

Para Logic isso significa:

- a AST de expressão precisa ser criada do zero, em nível de nó, não de linha;
- `SourceRef` é suficiente para a primeira entrega (o brief exige localização exata, e o início do
  token entrega `file:line:column`); `SourceRange` continua sendo trabalho de F1 e não deve ser
  antecipado dentro deste slice;
- `SpecAst` precisa ganhar uma lista de declarações de Logic e tolerar ausência de `## Data`.

## 4. Type System

Hoje o sistema de tipos é uma **enum de dez escalares**:

```java
enum TypeRef { STRING, TEXT, INT, LONG, DECIMAL, BOOLEAN, UUID, EMAIL, DATE, DATE_TIME }
```

`ApplicationScalarType` é a enum-espelho com o nome Java (`DECIMAL -> BigDecimal`).

O que falta para Logic:

- não há **relação entre tipos** (nem sub-tipagem, nem alargamento `Int → Long → Decimal`);
- não há construtores de tipo (`List<T>`, `Optional<T>`, `Money`, `Percentage`);
- a enum não comporta os tipos paramétricos das fases 5–7 sem virar uma enum crescente, exatamente
  o que o draft V1 proíbe.

Decisão consequente: Logic introduz uma **álgebra selada** (`LogicType`) que hoje embrulha
`TypeRef` e amanhã recebe `ListOf`, `MoneyOf`, `Percentage` por adição, sem migração.

## 5. SymbolTable

**Não existe SymbolTable.** A validação faz buscas ad hoc:

- entidades duplicadas: `LinkedHashMap<String, SpecAst>` local;
- rotas e nomes de use case: mapas acumulados ao longo do loop de `validate`;
- variáveis de flow: `LinkedHashMap<String, ValueType>` local ao use case;
- referências a entidade: comparação direta com o nome do arquipélago (`validateEntityReference`),
  que **proíbe** referência cross-file.

Logic exige o oposto: uma Logic declarada em um arquivo deve ser chamável de qualquer outro. Isso
obriga a primeira tabela de símbolos real do projeto — em duas passagens, como o draft V1 já previa.
Ela pode nascer pequena (apenas o namespace de computações) e crescer em F1.

## 6. Business IR

`ProjectModel(List<EntityModel>)` é literalmente uma lista de entidades. Não há lugar para uma
declaração de projeto que não pertença a uma entidade.

`Resolver` converte `SpecAst → ProjectModel` sem validar (assume validação anterior) e já produz
nomes canônicos (`tableName`, `columnName`, `baseName`) para que nenhum emissor faça string-munging.
Esse contrato deve ser preservado: a Logic IR precisa chegar ao generator com nome de classe, nome
de método e tipos Java já decididos — mas decididos na Application IR, não na Business IR.

## 7. Application IR e generator

`ApplicationProject(settings, entities, capabilities)` também não tem lugar para declarações
independentes de entidade.

`EmitterPipeline.standard()` roda três emissores em ordem fixa sobre `ApplicationProject` e escreve
em `GeneratedTree` (TreeMap, paths validados, colisão proibida). `OutputNormalizer` canonicaliza
CRLF, espaços finais, linhas em branco duplicadas e força newline final. `TemplateEngine` é Mustache
logic-less lido do classpath.

Duas observações determinantes:

1. **Não existe `OutputWriter`.** `build` compila a árvore em memória e reporta a contagem; nada é
   escrito em disco. Qualquer promessa de "rode `harpia build` e compile o Java" depende de E0.8.
2. Templates Mustache são ótimos para arquivos de forma fixa (pom, application.yaml) e ruins para
   **código com estrutura recursiva**. Um corpo de Logic com `if/else` aninhado não cabe em um
   template logic-less; precisa de um writer que percorra a árvore.

## 8. O que é reaproveitado

| Componente | Uso em Logic | Alteração |
|---|---|---|
| `SourceFile`, `SpecDiscovery` | leitura e ordem determinística | nenhuma |
| `MarkdownStructure`, `BlockNode`, `RawSpan` | corpo do fenced block linha a linha, com posição | nenhuma |
| `LineSyntax.at` | coluna do token a partir do offset de caractere | nenhuma |
| `DiagnosticCollector`, `Diagnostic`, `DiagnosticOrdering` | todos os erros de Logic | nenhuma |
| `ErrorCodes` | novas famílias `HRP11xx`/`HRP21xx` | adição |
| `SpecParser` | despacho por prefixo de declaração; `## Data` opcional | extensão |
| `SpecAst` | lista de declarações de Logic | adição |
| `TypeRef` | escalares embrulhados por `LogicType` | nenhuma |
| `SemanticValidator` | delega a análise do corpo ao analisador de Logic | extensão |
| `ProjectModel` | ganha `List<LogicModel>` | adição |
| `CapabilityAnalyzer` | Logic é `PURE` e **não** gera requirement | nenhuma |
| `ApplicationProject` | ganha `List<ApplicationLogic>` | adição |
| `GeneratedTree`, `OutputNormalizer`, `EmitterPipeline` | emissão determinística | nenhuma |
| `TemplateEngine` | invólucro da classe; corpo vem de um writer recursivo | uso parcial |

Nada precisa ser reescrito. Logic entra por adição em oito pontos e por extensão em três.

## 9. Riscos identificados antes de codificar

| Risco | Mitigação adotada |
|---|---|
| `## Logic X` ser confundido com use case | despacho por prefixo de declaração antes do ramo de use case |
| Módulo sem entidade ser rejeitado | `## Data` passa a ser opcional (0 ou 1) |
| Expressão virar string no IR | AST de expressão obrigatória, com `SourceRef` por nó |
| Enum de tipos crescer sem controle | álgebra selada `LogicType` desde o primeiro slice |
| `%` significar módulo e percentual | `%` é reservado exclusivamente para Percentage; módulo vira built-in |
| `/` inteiro truncar silenciosamente | `/` sempre produz `Decimal` |
| Reatribuição exigir SSA e inferência instável | atribuição única no V1; reatribuição só com Money/Decision |
| Template Mustache não expressar `if` aninhado | writer recursivo `JavaLogicWriter`, template só para o invólucro |
| Prometer end-to-end sem `OutputWriter` | slice fecha em `GeneratedTree` + compilação in-memory com `javax.tools` |
| `Flow → Logic` sem service gerado | especificado e agendado; **não** implementado antes de E0.7 |
