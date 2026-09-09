# Arquitetura atual da Harpia

> Para status, prioridade e dependências de produto, consulte o backlog canônico em
> [`../BACKLOG.md`](../BACKLOG.md).

Este caminho é mantido para compatibilidade com links antigos. A descrição normativa e atual está
em [`../ARCHITECTURE.md`](../ARCHITECTURE.md); catálogo, lifecycle e regras dos target packs estão
em [`../TARGETS.md`](../TARGETS.md).

## Resumo verificável

```text
*.harpia.md + harpia.yaml
        ↓
Markdown parser + declaration parser registry
        ↓
ModuleAst[] / ProjectAst
        ↓
SymbolTable + Semantic Analysis
        ↓
Business IR
        ↓
Capability requirements + target resolution
        ↓
Application IR + logical providers
        ↓
JavaSpringTarget
        ↓
JavaSpringProjectTransformer + target mappings
        ↓
Java Target Model
        ↓
JavaSourceRenderer + declarative templates
        ↓
TargetGenerationResult / GeneratedFile[]
        ↓
GeneratedTree / OutputWriter
```

Estado comprovado em 2026-09-08:

- somente `java-spring` é `SUPPORTED`; os demais targets do catálogo são `NOT_SUPPORTED` e não
  possuem generator;
- cada source é um `ModuleAst`, o projeto inteiro é um `ProjectAst` ordenado por path e declarações
  são despachadas por `DeclarationParserRegistry`, não por uma cadeia de `if` no `SpecParser`;
- bindings HTTP externos são descobertos sob `paths.bindings`, parseados em `BindingAst`, resolvidos
  contra símbolos de operação e validados antes da Business IR;
- `harpia.schemaVersion`, `harpia.languageVersion` e `target.language.version` são dimensões
  independentes; a versão Harpia tipada seleciona o registry, pertence ao `ProjectAst` e aparece no
  inspect de AST;
- a `SymbolTable` declara nomes deterministicamente por namespace e a resolução semântica usa a
  mesma tabela em todo o projeto;
- `SourceRef` possui fim exclusivo opcional, diagnostics possuem related locations estruturadas e
  `harpia inspect --stage` expõe AST, symbols, Business IR e Application IR reais do pipeline;
- Business IR e Application IR não carregam tipo Java, annotation Spring/JPA, dependência Maven ou
  layout de source;
- bootstrap, entity JPA, repository Spring Data, DTOs, services, controllers, error handler,
  migration Flyway, configuração, POM, testes e Harpia Logic são gerados deterministicamente;
- Java source passa por um modelo estruturado; Mustache é usado apenas em `pom.xml`,
  `application.yaml` e migration SQL;
- `GeneratedFile` carrega categoria, origem principal e um source map imutável por símbolo/range;
  métodos de controller expostos externamente apontam para a linha do endpoint no binding;
- `build` grava com manifesto e ownership; golden, determinismo, `javac` e `mvn -o test` do projeto
  gerado estão verdes;
- 195 arquivos Java de produção e 59 arquivos de teste passam por 330 testes verdes.

O V1 atual possui `Command` e `Query` explícitos e independentes de HTTP. Operações internas
permanecem nos IRs e geram service/teste sem capability, controller ou dependência web; bindings
externos podem expô-las depois. O próximo slice P0 completa os mappings HTTP de `BIND-006` sobre
essa fundação rastreável.

## Decisões preservadas da auditoria original

- CommonMark fornece estrutura; parsers Harpia interpretam apenas blocos explícitos.
- `validate` e `build` compartilham o mesmo compilador e os mesmos diagnostics.
- Capability, provider e target são conceitos distintos.
- Java/Spring é implementação de target, não semântica da linguagem.
- Sem LLM, relógio, random ou rede no pipeline de compilação.
- Output gerado pertence ao compiler; implementação custom futura ficará fora dessa raiz.

O roadmap executável continua em [`roadmap.md`](roadmap.md).
