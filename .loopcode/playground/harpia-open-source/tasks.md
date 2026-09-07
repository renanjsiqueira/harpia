<!-- Gerado pelo Playground do Loop Code — documento "tasks".
     Objetivo: # Harpia — MVP Open Source

Quero que você projete e implemente o MVP de um novo projeto open source chamado **Harpia**.

Antes de escrever código, leia toda esta especificação, analise criticamente a proposta, identifique ambiguidades e riscos técnicos, proponha a arquitetura mínima e só depois com
     Editar aqui é permitido: a etapa seguinte lê o arquivo, não a memória do agente. -->
Li os dois documentos aprovados em `.loopcode/playground/harpia-open-source/`. Duas notas de ancoragem antes da lista:

- O `requirements.md` gravado contém **o resumo**, não os 41 critérios EARS numerados — então cada tarefa aponta o **grupo de critério** ("CLI e exit codes", "determinismo/offline", "parsing e validação", "harpia.yaml", "código gerado", "golden") mais o **código `HRP` / seção do design** correspondente, que é o que existe de verificável no disco.
- Sigo os defaults dos documentos aprovados: compilador em Java 21 (D1), corte M1 (D2), sem relacionamentos (`HRP4002`), métrica ≥20× (D4).

Pacote base do compilador: `dev.harpia`. Caminhos relativos a `/Users/renanjangermesiqueira/development/harpia`.

Estado em 2026-09-06: Iterações 2 e 3 concluídas e E0.2 entregue, com Application IR
generator-ready, contribuições HTTP/persistence e os três emissores básicos cobertos por 95 testes.
O próximo slice é E0.3 (entity, repository e Flyway). O plano vigente está em `docs/roadmap.md`;
esta lista permanece como rastreabilidade do plano V0 original.

## Tarefas

### Iteração 2 — esqueleto, diagnósticos, config, CLI

1. Esqueleto Maven do compilador: `release 21`, picocli 4.7.x, commonmark 0.22.x, snakeyaml 2.x, mustache.java, junit-jupiter, assertj, shade com `Main-Class` → `harpia.jar`. — `pom.xml`, `.gitignore`, `.editorconfig` — AC: §7 (dependências), §11 it.2
2. Persistir os documentos aprovados no repositório como fonte durável. — `docs/requirements.md`, `docs/design.md` — AC: rastreabilidade dos critérios
3. Congelar o vocabulário da linguagem por escrito antes de parsear: 10 tipos, 8 comandos de `flow`, condições de erro, gramática de cada linha. — `docs/spec/harpia-language.md` — AC: parsing e validação (HRP1005/HRP1007/HRP1008)
4. Pacote `diag`: `Severity`, `SourceRef` (1-indexed), `Diagnostic`, `ErrorCodes` (constantes HRP1–HRP5), `DiagnosticCollector` com ordenação total arquivo→linha→coluna→código. — `src/main/java/dev/harpia/diag/*.java` — AC: §5 contratos, §5.1 ordenação estável
5. **[TESTE]** Ordenação de diagnósticos é total e estável para entradas embaralhadas. — `src/test/java/dev/harpia/diag/DiagnosticOrderingTest.java` — AC: §5.1
6. Pacote `source`: `SourceFile` (UTF-8 estrito → HRP5003, CRLF→LF, strip de BOM) e `SpecDiscovery` (`Files.walk`, path relativo com `/`, `String.compareTo`, symlink ignorado com aviso). — `src/main/java/dev/harpia/source/*.java` — AC: determinismo/offline §8.1, HRP5003
7. **[TESTE]** Leitura normaliza CRLF e BOM; descoberta devolve ordem idêntica independente da ordem do filesystem; symlink não é seguido. — `src/test/java/dev/harpia/source/SourceFileTest.java`, `SpecDiscoveryTest.java` — AC: §8.1, §9 (I/O)
8. Pacote `config`: `HarpiaConfig` (record) e `ConfigLoader` com `SafeConstructor`, incluindo `JavaNames` (pacote válido, palavras reservadas). — `src/main/java/dev/harpia/config/*.java` — AC: grupo `harpia.yaml`
9. Validações de configuração HRP3002–HRP3007 (YAML malformado com linha/coluna, chave desconhecida, pacote inválido, `harpia:` ≠ 1, `vendor`/`javaVersion` fora do MVP). — `src/main/java/dev/harpia/config/ConfigValidator.java` — AC: §9 HRP3xxx
10. **[TESTE]** Um caso por código HRP3002–HRP3007, assertando código, mensagem e posição. — `src/test/java/dev/harpia/config/ConfigLoaderTest.java` — AC: §9 HRP3xxx
11. Costura do compilador: `HarpiaCompiler.compile` (pura, não escreve), `CompileRequest`, `CompileResult.hasErrors()`, `GeneratedTree` sobre `TreeMap`. — `src/main/java/dev/harpia/HarpiaCompiler.java`, `CompileRequest.java`, `CompileResult.java`, `emit/GeneratedTree.java` — AC: §5 contratos
12. CLI picocli: `validate [--dir] [--quiet]`, `build [--dir] [--clean] [--force]`, `version`; `DiagnosticPrinter` no formato `arquivo:linha:coluna: error[HRPxxxx]: msg` + `hint` em stderr, resumo em stdout. — `src/main/java/dev/harpia/cli/{Main,HarpiaCommand,ValidateCommand,BuildCommand,VersionCommand,DiagnosticPrinter,ExitCode}.java` — AC: CLI e exit codes, §5.1
13. Exit codes: 0 sucesso, 1 erros de compilação sem escrita, 2 uso inválido / `harpia.yaml` ausente (HRP3001) / `specs/` vazio (HRP3005) / I/O (HRP5002). — `src/main/java/dev/harpia/cli/ExitCode.java`, `BuildCommand.java` — AC: CLI e exit codes
14. **[TESTE]** Exit codes ponta a ponta por cenário (ok / erro de spec / yaml ausente / specs vazio), capturando stdout e stderr separadamente. — `src/test/java/dev/harpia/cli/CliExitCodeTest.java` — AC: CLI e exit codes

### Iteração 3 — parse, AST, modelo, validação semântica

15. Fixture canônica do exemplo: spec de `Customer` com CRUD completo cobrindo os 8 comandos de `flow`. — `examples/customer/harpia.yaml`, `examples/customer/specs/customer.harpia.md` — AC: §8.4 métrica, §11 it.3
16. Pacote `ast`: parse CommonMark com `IncludeSourceSpans.BLOCKS`, extração de headings/listas/fenced e recuperação do **texto cru** por span (nenhuma decisão semântica em nó inline). — `src/main/java/dev/harpia/ast/{MarkdownStructure,BlockNode,RawSpan}.java` — AC: §10 risco 1
17. **[TESTE]** `->`, `{id}` e `_snake_case_` sobrevivem intactos ao pipeline de extração de texto cru. — `src/test/java/dev/harpia/ast/RawSpanTest.java` — AC: §10 risco 1
18. Gramáticas de linha: campo de `## Data` (HRP1004/HRP1005), `### Endpoint` (HRP1010), `### Access` (HRP4001), linha de `flow` (HRP1007), `### Output`, linha de erro (HRP1008). — `src/main/java/dev/harpia/parse/{FieldLineParser,EndpointParser,AccessParser,FlowLineParser,OutputParser,ErrorLineParser}.java` — AC: parsing e validação
19. **[TESTE]** Tabela de casos válidos e inválidos por gramática, cada inválido assertando o código HRP esperado e a coluna. — `src/test/java/dev/harpia/parse/LineGrammarTest.java` — AC: parsing e validação
20. `SpecParser`: monta entidade + casos de uso a partir da estrutura de blocos, com HRP1001, HRP1002, HRP1003, HRP1006, HRP1009. — `src/main/java/dev/harpia/parse/SpecParser.java` — AC: §9 HRP1xxx
21. Rejeição explícita de fora-do-MVP: HRP4001 (`authenticated`/`role`), HRP4002 (`-> Entity`, `List<Entity>`), HRP4003 (`## Events`/`## Email`/`## Security`), cada uma citando a seção do README. — `src/main/java/dev/harpia/parse/UnsupportedFeatureDetector.java` — AC: §9 HRP4xxx
22. **[TESTE]** Cada HRP1xxx e HRP4xxx dispara em spec dedicada; um arquivo com múltiplos erros reporta **todos de uma vez**. — `src/test/java/dev/harpia/parse/SpecParserDiagnosticsTest.java` — AC: parsing e validação ("todos os erros de uma vez")
23. Pacote `model`: `ProjectModel`, `EntityModel`, `FieldModel`, `UseCaseModel`, `HttpBinding`, `OutputModel`, `ErrorMapping`, `TypeRef`, `FlowModel` e a sealed `FlowStep` com os 8 permits. — `src/main/java/dev/harpia/model/*.java` — AC: §4 IR
24. `Naming`: PascalCase→`tableName`/`columnName` em snake_case e título→`baseName`, sempre `Locale.ROOT`; nenhum emissor faz string-munging depois. — `src/main/java/dev/harpia/model/Naming.java` — AC: §4 invariante 2, §8.1
25. **[TESTE]** Naming estável sob `Locale.forLanguageTag("tr")` (armadilha `I`/`ı`) e casos de sigla/numeral. — `src/test/java/dev/harpia/model/NamingTest.java` — AC: §8.1
26. `Resolver`: constrói o IR e garante as três invariantes (id único `generated`, `baseName` válido e único, flow termina em `Return` com variáveis definidas antes do uso). — `src/main/java/dev/harpia/model/Resolver.java` — AC: §4 invariantes
27. `SemanticValidator` HRP2001–HRP2017, com tabelas de palavras reservadas Java e Postgres; HRP2017 é **aviso**, não erro. — `src/main/java/dev/harpia/validate/SemanticValidator.java`, `src/main/resources/reserved-java.txt`, `reserved-postgres.txt` — AC: §9 HRP2xxx
28. **[TESTE]** Um caso por código HRP2001–HRP2017; HRP2001 aponta os **dois** locais; HRP2017 sai como `warning` e mantém exit 0. — `src/test/java/dev/harpia/validate/SemanticValidatorTest.java` — AC: §9 HRP2xxx
29. Ligar o pipeline em `HarpiaCompiler`: config → discovery → parse → resolve → validate, agregando diagnósticos sem parar no primeiro erro. — `src/main/java/dev/harpia/HarpiaCompiler.java` — AC: parsing e validação
30. **[TESTE]** `harpia validate` em `examples/customer` passa com exit 0 e stderr vazio. — `src/test/java/dev/harpia/ValidateExampleTest.java` — AC: §11 it.3

### Iteração 4 — emissores e escrita

31. Infra de emissão: `Emitter`, `TemplateEngine` (Mustache logic-less), `OutputNormalizer` (LF, sem BOM, sem trailing space, um `\n` final, no máx. uma linha em branco) e cabeçalho `Generated by Harpia` sem data/versão/hostname. — `src/main/java/dev/harpia/emit/{Emitter,TemplateEngine,OutputNormalizer,GeneratedHeader}.java` — AC: §6.1
32. **[TESTE]** Normalizador é idempotente e neutraliza whitespace de standalone tag do Mustache. — `src/test/java/dev/harpia/emit/OutputNormalizerTest.java` — AC: §6.1
33. `PomEmitter`, `ApplicationEmitter`, `AppConfigEmitter` (datasource por env var, Flyway on, `ddl-auto: validate`) + templates. — `src/main/java/dev/harpia/emit/{PomEmitter,ApplicationEmitter,AppConfigEmitter}.java`, `src/main/resources/templates/{pom.xml,Application.java,application.yaml}.mustache` — AC: código gerado
34. `EntityEmitter`: `@Entity`, `@Table`, `@Column`, `@Id`, validação Jakarta a partir de `required`/`unique`/`default`. — `src/main/java/dev/harpia/emit/EntityEmitter.java`, `src/main/resources/templates/Entity.java.mustache` — AC: código gerado (entity)
35. `DtoEmitter`: `<UseCase>Request` por caso de uso e `<Entity>Response` por entidade. — `src/main/java/dev/harpia/emit/DtoEmitter.java`, `templates/{Request,Response}.java.mustache` — AC: código gerado (DTOs)
36. `RepositoryEmitter`: `extends JpaRepository<Entity, UUID>` + derived query por campo `unique` usado em `duplicate ... -> 409`. — `src/main/java/dev/harpia/emit/RepositoryEmitter.java`, `templates/Repository.java.mustache` — AC: código gerado (repository)
37. `ErrorHandlingEmitter`: `ApiError`, `GlobalExceptionHandler`, exceções de domínio — uma vez por projeto, mapeando as condições de `### Errors`. — `src/main/java/dev/harpia/emit/ErrorHandlingEmitter.java`, `templates/{ApiError,GlobalExceptionHandler,NotFoundException,DuplicateException}.java.mustache` — AC: código gerado (advice)
38. `ServiceEmitter`: um método por caso de uso, corpo derivado da sequência de `FlowStep` (cada um dos 8 comandos tem tradução fixa). — `src/main/java/dev/harpia/emit/ServiceEmitter.java`, `templates/Service.java.mustache` — AC: código gerado (service), §4 IR
39. `ControllerEmitter`: `@RestController`, um handler por caso de uso, status de `### Output`, `@Valid`, `{id}` como `@PathVariable UUID`. — `src/main/java/dev/harpia/emit/ControllerEmitter.java`, `templates/Controller.java.mustache` — AC: código gerado (controller)
40. `MigrationEmitter`: `V1__init.sql` com mapeamento de tipo Harpia→Postgres, `NOT NULL`, `UNIQUE`, `DEFAULT`, alinhado ao `ddl-auto: validate`. — `src/main/java/dev/harpia/emit/MigrationEmitter.java`, `templates/V1__init.sql.mustache` — AC: código gerado (Flyway)
41. `EmitterPipeline`: registro em ordem fixa, populando a `GeneratedTree`; `build` reusa exatamente o `compile` de `validate`. — `src/main/java/dev/harpia/emit/EmitterPipeline.java` — AC: §5 (mesma costura)
42. **[TESTE]** Golden por emissor sobre `examples/customer`, com `-Dharpia.golden.update=true` para regravar. — `src/test/java/dev/harpia/emit/GoldenEmitterTest.java`, `src/test/resources/golden/customer/**` — AC: grupo golden, §10 risco 4
43. Pacote `write`: `OutputWriter.sync` com manifesto `sha256 path` ordenado, escrita só do que mudou, `.tmp` + `ATOMIC_MOVE`, remoção restrita ao manifesto anterior, HRP5001 (aviso, nunca apaga) e HRP5002 (exit 2); `--clean` exige `--force` se houver arquivo desconhecido. — `src/main/java/dev/harpia/write/{OutputWriter,Manifest,WriteReport}.java` — AC: §8.2, HRP5001/HRP5002
44. **[TESTE]** Writer: segundo `sync` não reescreve nada; arquivo removido da árvore some; arquivo desconhecido sobrevive e gera HRP5001; diretório somente-leitura dá exit 2. — `src/test/java/dev/harpia/write/OutputWriterTest.java` — AC: §8.2
45. **[TESTE]** `DeterminismTest`: dois `compile` do mesmo projeto produzem `SortedMap` byte-idêntico. — `src/test/java/dev/harpia/DeterminismTest.java` — AC: determinismo/offline, §8.3

### Iteração 5 — testes gerados e provas de tese

46. `TestEmitter`: `<Entity>ControllerTest` (`@WebMvcTest` + service mockado) e `<Entity>ServiceTest` (repository mockado) — sem banco, sem Docker. — `src/main/java/dev/harpia/emit/TestEmitter.java`, `templates/{ControllerTest,ServiceTest}.java.mustache` — AC: código gerado (testes), §6
47. **[TESTE]** Golden da árvore **completa** de `examples/customer`, incluindo `pom.xml`, SQL, YAML e os testes gerados. — `src/test/resources/golden/customer/**`, `src/test/java/dev/harpia/FullTreeGoldenTest.java` — AC: grupo golden
48. **[TESTE]** `OfflineGuardTest`: varre `src/main/java` por `java.net.`, `javax.net.`, `java.rmi`, `HttpClient`, `Socket`; qualquer ocorrência em produção falha o build. — `src/test/java/dev/harpia/OfflineGuardTest.java` — AC: determinismo/offline, §8.3
49. **[TESTE]** `ThesisMetricTest`: linhas não-vazias da spec vs. do Java gerado em `examples/customer`, razão ≥ 20. — `src/test/java/dev/harpia/ThesisMetricTest.java` — AC: §8.4 (D4)
50. **[TESTE / verificação externa]** Script que gera o exemplo e roda `mvn -o test` no projeto emitido, provando que ele compila e passa offline. — `scripts/verify-generated.sh` — AC: §11 it.5 (fora do surefire do compilador; é o passo de aceitação manual/CI)

### Iteração 6 — empacotamento e documentação

51. Distribuição: shade finalizado + wrapper shell resolvendo o JAR, com `harpia version` lendo versão de recurso estático (nunca do relógio). — `bin/harpia`, `pom.xml`, `src/main/resources/harpia-version.properties` — AC: CLI, §8.1
52. `README.md`: tese, pipeline, quickstart, **limites declarados** (8 comandos de flow, sem relacionamentos, sem Security/SMTP/eventos) e índice de códigos HRP. — `README.md`, `docs/errors.md` — AC: §10 riscos 2 e 3, §9 HRP4xxx
53. Licença Apache-2.0 e guia de contribuição incluindo a disciplina de revisão de diff golden. — `LICENSE`, `CONTRIBUTING.md` — AC: §10 risco 4
54. `examples/customer/README.md` e política de versionamento de `generated/` explicitada no `.gitignore` do exemplo. — `examples/customer/README.md`, `examples/customer/.gitignore` — AC: D5 (generated/ versionado?)
55. Polimento de diagnósticos: hints "você quis dizer" para variável (HRP2007), tipo (HRP1005) e comando de flow (HRP1007), via distância de edição determinística. — `src/main/java/dev/harpia/diag/DidYouMean.java` — AC: §5.1
56. **[TESTE]** Hints são determinísticos e escolhem o mesmo candidato sob empate. — `src/test/java/dev/harpia/diag/DidYouMeanTest.java` — AC: determinismo

---

**Duas coisas que valem sua decisão antes da iteração 2**, ambas baratas agora e caras depois:

- **Tarefa 15 é o gargalo escondido.** A fixture `examples/customer` é dependência de 6 tarefas de teste (30, 42, 45, 47, 49, 50). Se o CRUD dela não exercitar os 8 comandos de `flow`, os golden nascem com cobertura falsa.
- **Tarefa 50 não roda dentro do surefire do compilador.** `mvn -o test` no projeto gerado precisa das dependências Spring no repositório local — é offline só depois de um `mvn dependency:go-offline` prévio. Deixei como script de aceitação em vez de fingir que é um teste unitário; se você quiser que seja bloqueante em CI, isso vira uma tarefa de workflow que ainda não está na lista.
