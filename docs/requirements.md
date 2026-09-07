# Requisitos do Harpia MVP

> Este documento continua normativo para a linguagem V0. A visão semântica de longo prazo está em
> [`vision.md`](vision.md), e o status real de cada recurso está em
> [`../LANGUAGE_COVERAGE.md`](../LANGUAGE_COVERAGE.md). Recursos planejados nesses documentos não
> fazem parte da V0 automaticamente.

## 1. Propósito

Harpia é uma linguagem formal de especificação executável baseada em Markdown e uma abstração de
alto nível sobre a construção de aplicações Java. O compilador transforma `harpia.yaml` e arquivos
`specs/**/*.harpia.md` em um projeto Java 21 com Spring Boot 3, Maven, REST, Jakarta Validation,
Spring Data JPA, PostgreSQL, Flyway e testes JUnit 5.

Os arquivos `*.harpia.md` são código-fonte Harpia, não prosa interpretada livremente. Sua estrutura
deve ser simples o bastante para pessoas não técnicas revisarem e para LLMs produzirem com poucos
tokens, mantendo gramática, tipos e semântica explícitos.

A compilação é local, offline e determinística. Ela não usa LLM, não consome APIs e não executa
conteúdo arbitrário presente nas especificações.

O objetivo do MVP é provar que uma representação pequena e legível da intenção de negócio pode
gerar, de forma reprodutível, software convencional que compila e roda.

## 2. Decisões congeladas para o V0

| ID | Decisão |
|---|---|
| D1 | O compilador é escrito em Java 21 e construído com Maven. |
| D2 | O MVP cobre CRUD de uma entidade por arquivo, sem relacionamentos. |
| D3 | O `flow` possui exatamente os oito comandos documentados em `docs/spec/harpia-language.md`. |
| D4 | A fixture canônica deve produzir ao menos 20 linhas não vazias de Java por linha não vazia de spec. |
| D5 | `generated/` é produto de build e não é versionado; goldens de teste são versionados. |
| D6 | O projeto usa licença Apache-2.0. |
| D7 | Java/Spring é o único backend normativo; outros targets não orientam o design do V0. |
| D8 | A economia de tokens acontece na autoria da spec; o compilador nunca usa LLM. |

O V0 fixa Java em 21, usa PostgreSQL e não inclui autenticação, email, eventos, relacionamentos,
condicionais ou extensões. Pedidos por essas construções devem gerar diagnóstico explícito, nunca
saída parcial.

## 3. Layout do projeto de entrada

```text
project/
├── harpia.yaml
├── specs/
│   └── customer.harpia.md
└── generated/
```

- `harpia.yaml` seleciona tecnologia e caminhos (HOW).
- `*.harpia.md` descreve intenção de negócio (WHAT).
- `generated/` contém somente o projeto emitido e o manifesto do compilador.

## 4. Critérios de aceitação

Os requisitos abaixo usam a forma EARS. “O compilador” inclui a biblioteca e a CLI quando o
contexto assim exigir.

### CLI e códigos de saída

1. Quando o usuário executar `harpia validate`, o compilador deve validar configuração e specs sem escrever arquivos.
2. Quando o usuário executar `harpia build`, o compilador deve usar o mesmo pipeline de `validate` antes de escrever.
3. Quando a compilação não tiver erros, a CLI deve terminar com código 0.
4. Quando houver erro de sintaxe ou semântica, a CLI deve terminar com código 1 e não escrever nada.
5. Quando houver uso inválido, entrada obrigatória ausente ou falha de I/O, a CLI deve terminar com código 2.

### Determinismo e operação offline

6. Para a mesma entrada e versão do compilador, o compilador deve produzir a mesma árvore de paths e bytes.
7. Quando descobrir specs, o compilador deve ordená-las pelo path relativo normalizado com `/`.
8. O compilador não deve incorporar relógio, hostname, valores aleatórios ou locale da máquina na saída.
9. O compilador não deve abrir conexões de rede durante `validate` ou `build`.
10. Quando ler texto, o compilador deve normalizar BOM UTF-8 e CRLF para uma representação interna canônica.
11. Quando emitir texto, o compilador deve usar UTF-8 sem BOM, LF, sem espaços finais e com um newline final.

### Parsing e validação

12. Quando uma spec válida contiver um H1 e `## Data`, o compilador deve construir uma entidade.
13. Quando houver zero ou mais de um H1, o compilador deve emitir `HRP1001`.
14. Quando o nome de entidade não estiver em PascalCase, o compilador deve emitir `HRP1002`.
15. Quando `## Data` estiver ausente ou duplicado, o compilador deve emitir `HRP1003`.
16. Quando uma linha de campo for inválida, o compilador deve emitir `HRP1004` com posição da fonte.
17. Quando um tipo não pertencer ao conjunto de dez tipos V0, o compilador deve emitir `HRP1005`.
18. Quando uma linha de `flow` não pertencer aos oito comandos V0, o compilador deve emitir `HRP1007`.
19. Quando houver múltiplos erros independentes, o compilador deve reportá-los juntos em ordem estável.
20. Quando a spec solicitar recurso fora do V0, o compilador deve emitir um `HRP4xxx` específico.

### Configuração

21. Quando `harpia.yaml` não existir, o compilador deve emitir `HRP3001` e terminar com código 2.
22. Quando o YAML for malformado, o compilador deve emitir `HRP3002` com linha e coluna.
23. Quando houver chave desconhecida ou duplicada, o compilador deve emitir `HRP3003`.
24. Quando `project.group` ou `project.package` não for pacote Java válido, o compilador deve emitir `HRP3004`.
25. Quando `specs/` não existir ou não contiver `*.harpia.md`, o compilador deve emitir `HRP3005` e terminar com código 2.
26. Quando `harpia` não for 1, o compilador deve emitir `HRP3006`.
27. Quando Java, target ou banco estiver fora do V0, o compilador deve emitir `HRP3007`.

### Projeto gerado

28. Para uma entrada válida, o compilador deve gerar um projeto Maven com versões pinadas.
29. Para cada entidade, o compilador deve gerar entidade JPA alinhada à migração Flyway.
30. Para cada entidade, o compilador deve gerar um repositório Spring Data JPA.
31. Para cada caso de uso, o compilador deve gerar um método de serviço derivado do `flow`.
32. Para cada endpoint, o compilador deve gerar um handler REST com validação e status declarados.
33. Para cada input, o compilador deve gerar um DTO de request com Jakarta Validation.
34. Para cada entidade retornável, o compilador deve gerar um DTO de response.
35. Para erros declarados, o compilador deve gerar exceções e um handler HTTP uniforme.
36. Para o conjunto de entidades, o compilador deve gerar `V1__init.sql` para PostgreSQL.
37. O projeto gerado deve configurar Flyway e `hibernate.ddl-auto=validate`.
38. O projeto gerado deve conter testes unitários de controller e service que não dependam de rede, banco ou Docker.

### Escrita e provas

39. Quando `build` for repetido sem mudança, o writer não deve reescrever arquivos inalterados.
40. Quando houver arquivo desconhecido em `generated/`, o writer deve preservá-lo e emitir `HRP5001`.
41. A fixture `examples/customer` deve passar por validação, golden, teste de determinismo, métrica ≥20× e build Maven offline quando as dependências estiverem previamente disponíveis.
42. Quando uma contagem de tokens for publicada, a medição deve identificar o tokenizer; linhas e caracteres devem ser apresentados apenas como métricas distintas ou proxies.

## 5. Fora do escopo

- runtime próprio, bytecode direto ou linguagem Turing-complete;
- interpretação livre de linguagem natural ou LLM no build;
- edição, merge ou reverse engineering do Java gerado;
- targets diferentes de Java/Spring Boot e build tools diferentes de Maven;
- Java diferente de 21 no V0;
- bancos diferentes de PostgreSQL;
- frontend, GraphQL, gRPC, WebSocket e mensageria externa;
- autenticação/JWT, SMTP, eventos, cloud, Docker, Kubernetes e IaC;
- relacionamentos, enums, coleções em campos, nullable explícito e regras condicionais;
- plugins, LSP e integração de IDE;
- migrações incrementais após `V1__init.sql`.

## 6. Fonte normativa

A sintaxe normativa está em [`spec/harpia-language.md`](spec/harpia-language.md). A arquitetura e os
contratos internos estão em [`design.md`](design.md). Em caso de divergência com os documentos do
playground, estes arquivos em `docs/` prevalecem.
