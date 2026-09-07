# Visão da Harpia

Harpia é uma **Semantic Software Specification Language for Humans and AI**. Ela usa Markdown como
superfície de autoria formal e compila intenção de software em aplicações Java/Spring convencionais.

> Harpia covers software semantics. Java covers implementation freedom.

> Humans and LLMs specify. Harpia compiles.

> LLMs write intent. Harpia writes code.

> The intent is the source. Generated code is an artifact.

> Compile intent, do not prompt boilerplate.

> Token efficiency comes from semantic compression, not syntactic compression.

> AI-native does not mean AI-dependent.

> Formula computes. Decision chooses. Logic reasons. Flow acts.

## Tese

Harpia não procura abreviar Java. Ela representa mais significado com menos source porque uma
decisão semântica coerente pode produzir vários artefatos.

Por exemplo, `unique` pode contribuir para constraint de banco, detecção de conflito, resposta HTTP
e testes. `emit CustomerRegistered` pode contribuir para contrato do evento, payload, publicação,
provider e testes. Esse ganho é Semantic Compression, não sintaxe criptográfica.

O Semantic Compression Ratio (SCR) compara uma spec e o software convencional equivalente. Linhas,
caracteres e tokens são métricas distintas; qualquer contagem de tokens deve identificar o
tokenizer e nunca ser apresentada como economia universal de LLM.

## Fonte de verdade

```text
Business intent
      ↓
*.harpia.md + harpia.yaml
      ↓
Semantic model
      ↓
Application model
      ↓
Generated Java + Custom Java
      ↓
JVM
```

- a spec é a fonte de verdade da intenção;
- `harpia.yaml` seleciona decisões técnicas;
- Java gerado é descartável e nunca recebe edição manual;
- Custom Java é propriedade do usuário e nunca é sobrescrito;
- nenhuma LLM participa de `validate` ou `build`.

## Três níveis de cobertura

### A — Semantic Harpia

Construções recorrentes de negócio ou comportamento importante: Entity, ValueObject, Enum, Command,
Query, Rule, Invariant, Event, Policy, Scenario, Integration, State e Workflow.

### B — Capability/Provider

Decisões técnicas substituíveis: PostgreSQL, JWT, SMTP, local events, HTTP client, cache, storage e
observability. A Business IR declara a necessidade; o provider escolhe a implementação.

### C — Custom Java

Comportamento específico ou low-level: SDK proprietário, algoritmo especializado, JNI, imagem/PDF
avançado, hardware ou protocolo particular. Harpia gera um contrato; o usuário implementa Java.

Se uma proposta é apenas uma forma menor de escrever Java, ela não deve entrar na linguagem.

## Target atual

O único target real e normativo é Java 21+ com Spring Boot 3 e Maven. Outros targets não orientam a
arquitetura atual. Business IR continua livre de Spring/JPA para proteger significado e providers,
não para prometer geradores ainda inexistentes.

O objetivo de cobertura é a superfície de categorias relevantes de aplicações Spring Boot. Não é
cobrir toda classe, annotation ou extension point: Harpia modela significado da aplicação; o
provider Spring escolhe os mecanismos do framework.

## Princípios oficiais

1. Business-first.
2. Human-readable e AI-native.
3. Densidade semântica em vez de sintaxe críptica.
4. Compilação local, offline e determinística.
5. Gramática pequena, formal e estável.
6. Código Java convencional, sem runtime Harpia.
7. Business IR separada de capabilities, providers e source Java.
8. Diagnostics precisos e source mapping.
9. Generated Java descartável; Custom Java preservado.
10. Inferências documentadas e inspecionáveis.
11. Features só são suportadas quando fecham o pipeline e os testes.
12. Cobertura progressiva baseada em vertical slices.

## Anti-goals

Harpia não é Java reduzido, pseudocódigo, linguagem natural interpretada por IA, prompt wrapper,
agent, runtime proprietário, DSL de Spring/cloud, linguagem Turing-complete, sistema de templates
exposto, Terraform replacement ou executor de scripts arbitrários.

O status real de cada cenário vive em [`../LANGUAGE_COVERAGE.md`](../LANGUAGE_COVERAGE.md).
