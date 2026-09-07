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
Parser / Semantic Analysis
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

Estado comprovado em 2026-09-06:

- somente `java-spring` é `SUPPORTED`; os demais targets do catálogo são `NOT_SUPPORTED` e não
  possuem generator;
- Business IR e Application IR não carregam tipo Java, annotation Spring/JPA, dependência Maven ou
  layout de source;
- bootstrap, entity JPA, repository Spring Data, migration Flyway, configuração, POM e Harpia Logic
  são gerados deterministicamente;
- Java source passa por um modelo estruturado; Mustache é usado apenas em `pom.xml`,
  `application.yaml` e migration SQL;
- `GeneratedFile` carrega categoria e origem Harpia principal; source map por símbolo/linha ainda é
  futuro;
- `build` grava com manifesto e ownership; golden, determinismo, `javac` e `mvn -o test` do projeto
  gerado estão verdes;
- DTOs, services, controllers, error handler e testes gerados são o próximo slice E0.6/E0.7/E0.9.

## Decisões preservadas da auditoria original

- CommonMark fornece estrutura; parsers Harpia interpretam apenas blocos explícitos.
- `validate` e `build` compartilham o mesmo compilador e os mesmos diagnostics.
- Capability, provider e target são conceitos distintos.
- Java/Spring é implementação de target, não semântica da linguagem.
- Sem LLM, relógio, random ou rede no pipeline de compilação.
- Output gerado pertence ao compiler; implementação custom futura ficará fora dessa raiz.

O roadmap executável continua em [`roadmap.md`](roadmap.md).
