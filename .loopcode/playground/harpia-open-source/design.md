<!-- Gerado pelo Playground do Loop Code — documento "design".
     Objetivo: # Harpia — MVP Open Source

Quero que você projete e implemente o MVP de um novo projeto open source chamado **Harpia**.

Antes de escrever código, leia toda esta especificação, analise criticamente a proposta, identifique ambiguidades e riscos técnicos, proponha a arquitetura mínima e só depois com
     Editar aqui é permitido: a etapa seguinte lê o arquivo, não a memória do agente. -->
…[truncado]…        List<FieldModel> fields, FieldModel idField,
                   List<UseCaseModel> useCases, SourceRef where) {}

record FieldModel(String name, String columnName, TypeRef type,
                  boolean required, boolean unique, boolean generated,
                  Optional<Literal> defaultValue, SourceRef where) {}

record UseCaseModel(String title, String baseName,        // "Create Customer" -> "CreateCustomer"
                    HttpBinding http, Access access,
                    List<FieldModel> input, FlowModel flow,
                    OutputModel output, List<ErrorMapping> errors, SourceRef where) {}

record HttpBinding(HttpMethod method, String path, boolean hasIdPathVar) {}
record OutputModel(int status, OutputShape shape) {}      // SINGLE | LIST | NONE
record ErrorMapping(ErrorCondition condition, int status, SourceRef where) {}

sealed interface FlowStep permits ValidateInput, CreateFrom, LoadById,
                                  UpdateFrom, ListAll, Save, Delete, Return {}
record FlowModel(List<FlowStep> steps, Map<String, VarType> vars) {}  // LinkedHashMap
```

Três invariantes que o Resolver garante antes de qualquer emissor rodar, e sobre as quais os templates podem confiar cegamente:

1. Toda entidade tem exatamente um `idField` com `generated`.
2. Todo `UseCaseModel.baseName` é um identificador Java válido e único no projeto.
3. Todo `FlowModel` termina em `Return` e todas as variáveis usadas foram definidas antes.

Um template logic-less só funciona se o IR já respondeu tudo. Por isso o IR carrega `tableName`/`columnName` já convertidos e `baseName` já normalizado — nenhum emissor faz string-munging.

---

## 5. Contratos de código (as interfaces que importam)

```java
public final class HarpiaCompiler {
    public CompileResult compile(CompileRequest request);   // pura: não escreve nada
}
public record CompileRequest(Path projectRoot) {}
public record CompileResult(Optional<GeneratedTree> tree, List<Diagnostic> diagnostics) {
    public boolean hasErrors();
}

public record Diagnostic(Severity severity, String code, String message,
                         SourceRef where, String hint) {}
public record SourceRef(Path file, int line, int column) {}   // 1-indexed

public interface Emitter {
    void emit(ProjectModel model, GeneratedTree out);
}

public final class GeneratedTree {          // TreeMap<String,String>, chave = path relativo com '/'
    public void put(String relativePath, String content);
    public SortedMap<String, String> files();
}

public final class OutputWriter {
    public WriteReport sync(Path outputDir, GeneratedTree tree) throws IOException;
}
```

`HarpiaCompiler.compile` é a costura testável: os golden tests batem em `GeneratedTree`, nunca no disco. `validate` e `build` chamam exatamente o mesmo `compile`; a única diferença é `build` prosseguir para o `OutputWriter`. Isso elimina por construção a classe de bug "validate passa e build quebra".

### 5.1 CLI

```
harpia validate [--dir .] [--quiet]
harpia build    [--dir .] [--clean] [--force]
harpia version
```

| Exit | Significado |
|---|---|
| 0 | Sucesso (build: escreveu; validate: spec válida) |
| 1 | Erros de compilação — diagnósticos emitidos, **nada escrito** |
| 2 | Uso inválido, `harpia.yaml` ausente, falha de I/O |

Diagnósticos em **stderr**, formato de compilador (clicável no terminal e parseável por CI):

```
specs/customer.harpia.md:14:3: error[HRP2007]: variável 'custome' não foi definida
  hint: você quis dizer 'customer'? variáveis definidas até aqui: customer
```

stdout carrega só o resumo (`10 arquivos gerados, 3 inalterados`). Ordenação dos diagnósticos: por arquivo, depois linha, depois coluna, depois código — total e estável.

### 5.2 Famílias de código de erro

`HRP1xxx` sintaxe · `HRP2xxx` semântica · `HRP3xxx` configuração · `HRP4xxx` não suportado no MVP · `HRP5xxx` I/O.

---

## 6. Emissores e templates

Um emissor por artefato, cada um com um `.mustache`. **Mustache logic-less para tudo** — Java, SQL, XML, YAML —, um único mecanismo em vez de dois.

| Emissor | Saída |
|---|---|
| `PomEmitter` | `pom.xml` com versões pinadas do `harpia.yaml` |
| `ApplicationEmitter` | `Application.java` |
| `AppConfigEmitter` | `application.yaml` (datasource via env vars, Flyway on, `ddl-auto: validate`) |
| `EntityEmitter` | `<Entity>.java` — `@Entity`, `@Table`, `@Column`, validação |
| `DtoEmitter` | `<UseCase>Request.java` por caso de uso + `<Entity>Response.java` |
| `RepositoryEmitter` | `<Entity>Repository extends JpaRepository<Entity, UUID>` |
| `ServiceEmitter` | Um método por caso de uso, corpo derivado dos `FlowStep` |
| `ControllerEmitter` | `@RestController` + um handler por caso de uso |
| `ErrorHandlingEmitter` | `ApiError`, `GlobalExceptionHandler`, exceções — uma vez por projeto |
| `MigrationEmitter` | `db/migration/V1__init.sql` |
| `TestEmitter` | `<Entity>ControllerTest` (`@WebMvcTest` + service mockado), `<Entity>ServiceTest` (repository mockado) |

**Por que os testes gerados não tocam banco:** `@DataJpaTest` exigiria H2 (dialeto divergente do DDL Postgres, gera falso verde) ou Testcontainers (Docker + rede, viola o princípio offline). Com mocks, `mvn -o test` roda em qualquer lugar. Cobertura de integração real é assunto de M2, declarado, não escondido.

**Cuidado de precisão:** `ddl-auto: validate` faz o Hibernate conferir o schema criado pelo Flyway contra as entidades no boot. É o teste mais barato de que o `MigrationEmitter` e o `EntityEmitter` não divergiram.

### 6.1 Pós-processamento uniforme

Toda saída passa por um normalizador antes de entrar na `GeneratedTree`: EOL `\n`, UTF-8 sem BOM, sem espaço em fim de linha, exatamente um `\n` final, no máximo uma linha em branco consecutiva. Isso neutraliza a pegadinha clássica de whitespace de standalone tag do Mustache e torna os golden diffs legíveis.

Cabeçalho em todo arquivo gerado — **sem data, sem versão, sem hostname**, senão o rebuild não é byte-idêntico:

```java
// Generated by Harpia. Do not edit.
// Source: specs/customer.harpia.md
```

---

## 7. Dependências externas e justificativa

### Compilador (runtime)

| Dependência | Por quê | Alternativa descartada |
|---|---|---|
| `info.picocli:picocli` 4.7.x | Subcomandos, `--help`, exit codes, sem dependências transitivas | Parsing manual: viável com 3 comandos, mas o custo de picocli é ~200 KB e zero manutenção |
| `org.commonmark:commonmark` 0.22.x | Parser CommonMark de referência na JVM, **com source spans** (`IncludeSourceSpans.BLOCKS`) — sem isso não há `file:line:col` nos diagnósticos. Zero transitivas | Regex sobre o arquivo: é exatamente onde o determinismo morre em casos de borda (listas aninhadas, blocos cercados dentro de citação) |
| `org.yaml:snakeyaml` 2.x | `harpia.yaml`, com `SafeConstructor` (sem instanciação arbitrária de classes) | `jackson-dataformat-yaml`: arrasta `jackson-databind` inteiro para ler ~12 chaves |
| `com.github.spullara.mustache.java:compiler` | Templates logic-less para os 4 formatos de saída | **JavaPoet**: garante Java sintaticamente válido e gerencia imports, mas só serve para Java — sobrariam pom/SQL/YAML em outro mecanismo. Com o IR completo, o conjunto de imports é conhecido; golden tests cobrem o resto. **Freemarker/Velocity**: permitem lógica no template, que é justamente o que queremos proibir |

### Compilador (teste)

JUnit 5 (`junit-jupiter`) — exigido pelos requisitos. AssertJ — diffs legíveis em comparação de árvores. **Nada mais**: sem Testcontainers, sem WireMock, sem ArchUnit (a checagem de offline do §8.3 é uma varredura de 20 linhas sobre os fontes).

### Build

`maven-compiler-plugin` (release 21), `maven-surefire-plugin`, `maven-shade-plugin` (fat JAR `harpia.jar` com `Main-Class`).

### Projeto gerado — declaradas no `pom.xml` emitido, **nunca resolvidas pelo compilador**

`spring-boot-starter-web`, `-data-jpa`, `-validation`, `-test`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`.

---

## 8. Determinismo e offline — como isso é garantido, não prometido

### 8.1 Regras de construção

- Descoberta de specs: `Files.walk` → mapeia para path relativo com separador `/` → **ordena por `String.compareTo`**. A ordem do filesystem nunca vaza.
- Nenhuma coleção não-ordenada em posição de saída: `TreeMap` na `GeneratedTree`, `LinkedHashMap` no resto.
- `toLowerCase`/`toUpperCase` **sempre com `Locale.ROOT`** (senão o locale turco quebra `I`/`ı` — bug real, difícil de achar).
- Zero `Instant.now()`, `System.currentTimeMillis()`, `Random`, `UUID.randomUUID()`, `InetAddress`, `System.getenv` no caminho de geração.
- Versões do Spring Boot e do Java vêm do `harpia.yaml`, nunca de resolução dinâmica.

### 8.2 O Writer

O compilador constrói a árvore inteira em memória; só então:

1. Lê `generated/.harpia-manifest` (lista ordenada `sha256  path`).
2. Escreve apenas arquivos cujo conteúdo mudou (evita churn de mtime e rebuilds do Maven).
3. Remove arquivos que estão no manifesto anterior mas não na árvore nova — **e só esses**. Arquivo dentro de `generated/` que o Harpia não conhece nunca é apagado; vira aviso `HRP5001`.
4. Reescreve o manifesto por último.

Cada arquivo é escrito em `.tmp` + `ATOMIC_MOVE`. Atomicidade por arquivo, não da árvore inteira — limitação declarada; a mitigação real é que erros de compilação impedem qualquer escrita.

### 8.3 Testes de garantia

- `DeterminismTest`: compila o mesmo caso duas vezes, compara `SortedMap` inteiro. Falha ao primeiro byte divergente.
- `OfflineGuardTest`: varre `src/main/java` procurando `java.net.`, `javax.net.`, `java.rmi`, `HttpClient`, `Socket`. Qualquer ocorrência em código de produção falha o build. Custo: zero dependências.

### 8.4 `ThesisMetricTest` (D4)

Sobre `examples/customer`: conta linhas não-vazias da spec e linhas não-vazias do Java gerado, assere razão ≥ 20. A tese do projeto vira um teste que quebra quando a tese deixa de valer.

---

## 9. Casos de borda e comportamento em falha

Todos com exit 1 (diagnóstico) salvo indicação de exit 2. Nenhum caso produz escrita parcial.

### Configuração — `HRP3xxx`

| Código | Situação | Comportamento |
|---|---|---|
| HRP3001 | `harpia.yaml` ausente | exit **2**, sugere `harpia init` |
| HRP3002 | YAML malformado | Erro com linha/coluna do SnakeYAML |
| HRP3003 | Chave desconhecida (`security:`, `email:`) | Erro apontando a chave + lista de chaves aceitas |
| HRP3004 | `package`/`group` não é pacote Java válido, ou usa palavra reservada | Erro |
| HRP3005 | `specs/` ausente ou sem `*.harpia.md` | exit **2** — nada a compilar não é sucesso |
| HRP3006 | `harpia:` ausente ou ≠ 1 | Erro |
| HRP3007 | `vendor` ≠ `postgres`, `javaVersion` ≠ 21 | Erro citando o escopo do MVP |

### Sintaxe — `HRP1xxx`

| Código | Situação |
|---|---|
| HRP1001 | Zero ou mais de um `# H1` no arquivo |
| HRP1002 | Nome de entidade fora de PascalCase |
| HRP1003 | `## Data` ausente ou duplicado |
| HRP1004 | Linha de campo não casa a gramática (mostra a linha crua) |
| HRP1005 | Tipo desconhecido (lista os 9 tipos válidos) |
| HRP1006 | Caso de uso sem `### Endpoint`/`### Access`/`### Flow`/`### Output` |
| HRP1007 | Linha de `flow` fora do vocabulário (lista os 8 comandos) |
| HRP1008 | Condição de erro desconhecida |
| HRP1009 | Título de caso de uso não mapeável para identificador Java |
| HRP1010 | Método HTTP inválido, ou path var diferente de `{id}` |

### Semântica — `HRP2xxx`

| Código | Situação |
|---|---|
| HRP2001 | Duas specs declarando a mesma entidade (aponta **os dois** locais) |
| HRP2002 | Campo duplicado em `## Data` |
| HRP2003 | Entidade sem exatamente um campo `generated` |
| HRP2004 | Literal de `default` incompatível com o tipo |
| HRP2005 | Dois casos de uso com o mesmo método+path |
| HRP2006 | Path tem `{id}` mas o flow não faz `load ... by id` (ou o inverso) |
| HRP2007 | Variável usada sem definição prévia |
| HRP2008 | Flow não termina em `return` |
| HRP2009 | Tipo retornado ≠ `### Output` declarado (ex.: `return nothing` com `200 Customer`) |
| HRP2010 | Flow referencia entidade diferente da do arquivo (uma entidade por arquivo) |
| HRP2011 | Campo de `### Input` inexistente em `## Data` |
| HRP2012 | Campo de `### Input` com tipo divergente do declarado em `## Data` |
| HRP2013 | `duplicate <campo> -> 409` num campo sem `unique` — o erro seria inalcançável |
| HRP2014 | Dois casos de uso com o mesmo título (colisão de nome de DTO) |
| HRP2015 | Campo com nome de palavra reservada Java |
| HRP2016 | Nome de tabela/coluna colide com palavra reservada do Postgres — erro pedindo renomeação, em vez de quotar identificadores silenciosamente |
| HRP2017 | `## Data` sem nenhum caso de uso — entidade órfã (aviso, não erro) |

### Fora do MVP — `HRP4xxx` (rejeição explícita, jamais geração parcial)

| Código | Situação |
|---|---|
| HRP4001 | `### Access: authenticated` / `role X` → "Spring Security/JWT não faz parte do MVP" |
| HRP4002 | Sintaxe de relacionamento (`-> Entity`, `List<Entity>` em `## Data`) → D2 |
| HRP4003 | Seções `## Events`, `## Email`, `## Security` |

Cada `HRP4xxx` cita a seção do README que declara o escopo. É a diferença entre "o compilador está quebrado" e "isso ainda não existe".

### I/O e encoding — `HRP5xxx`

| Código | Situação | Comportamento |
|---|---|---|
| HRP5001 | Arquivo desconhecido em `generated/` | **Aviso**; nunca apaga. `--clean` sem `--force` aborta se houver algum |
| HRP5002 | `generated/` sem permissão de escrita, disco cheio | exit **2** |
| HRP5003 | Spec não é UTF-8 válido | Erro |
| — | CRLF na spec | Normalizado para `\n` na leitura (mesma saída em Windows e macOS) |
| — | BOM UTF-8 | Removido silenciosamente na leitura |
| — | Symlink dentro de `specs/` | Não seguido; ignorado com aviso (evita ciclos e escape do workspace) |

---

## 10. Riscos técnicos conhecidos

1. **Markdown como sintaxe é sedutor e traiçoeiro.** O AST inline do CommonMark pode reinterpretar `->`, `{id}`, `_snake_case_`. **Mitigação estrutural:** o AST do CommonMark é usado **só para estrutura de blocos** (headings, listas, fenced code). Todo conteúdo de folha é lido do **texto-fonte cru** via source span e passado às gramáticas de linha. Nenhuma decisão semântica depende de parsing inline.

2. **Cobertura do `flow` vs. utilidade.** 8 comandos fecham CRUD e nada mais. A primeira coisa que um usuário real vai pedir é condicional ou regra de negócio, e a resposta será "não". Isso é o corte M1 aprovado funcionando, mas precisa estar no README como limite declarado, não descoberto.

3. **Sem relacionamentos (D2), o exemplo é fraco.** Um CRUD de entidade única é onde qualquer scaffolder já entrega. A tese do ≥20× se sustenta; a tese de "isso substitui prompts" fica mais fraca sem ao menos um `Order → Customer`. É o item que eu recomendaria promover primeiro se houver folga.

4. **Golden tests engessam formatação.** Toda mudança de template gera diff enorme. Mitigado pela flag `-Dharpia.golden.update=true`, com a disciplina de revisar o diff antes de commitar.

---

## 11. Ordem de implementação sugerida (5 iterações restantes)

| It. | Entrega | Critério de pronto |
|---|---|---|
| 2 | Esqueleto Maven + CLI + `config` + `source` + `diag` | `harpia validate` lê `harpia.yaml`, reporta HRP3xxx, exit codes corretos |
| 3 | `parse` + `ast` + `model` + `validate` | `harpia validate` no exemplo passa; todos os erros golden de HRP1xxx/HRP2xxx/HRP4xxx reportados de uma vez |
| 4 | `emit` (entity, DTO, repo, service, controller, advice, SQL, pom, app) + `write` | `harpia build` gera árvore completa; `DeterminismTest` verde |
| 5 | `TestEmitter` + golden completo + `OfflineGuardTest` + `ThesisMetricTest` | `mvn -o test` no projeto gerado passa; razão ≥ 20 |
| 6 | README, LICENSE, `examples/`, `bin/harpia`, polimento de diagnósticos | Alguém clona e roda `harpia build` sem ler código-fonte |

---

**Para destravar a próxima etapa preciso de você em D1 e D2.** Os outros (D3–D6) têm default razoável e são baratos de reverter; esses dois mudam o modelo de dados e os emissores.
