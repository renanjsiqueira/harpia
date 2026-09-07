# Referência — o target Java/Spring

Documento de entrada para quem vai mexer no gerador Java/Spring. Assume que você não participou das
fases anteriores.

O E0 fechou: este documento deixou de descrever uma tarefa e passou a descrever a arquitetura do
target, que é durável. A seção 3 aponta o próximo incremento.

Última revisão: 2026-09-06. Estado verificado do working tree, não aspiração.

---

## 1. O que é o Harpia

Compilador que transforma especificações de software escritas em Markdown estruturado
(`specs/*.harpia.md`) em um projeto Java/Spring. Determinístico, offline, sem LLM em nenhum ponto
do pipeline.

```bash
mvn package                                   # compila o compilador
./bin/harpia validate --dir examples/customer # valida sem escrever
./bin/harpia build    --dir examples/customer # escreve em examples/customer/generated
./bin/harpia targets                          # lista os targets conhecidos
mvn test                                      # 250 testes, todos verdes hoje
```

Leia antes de codificar, nesta ordem:

| Documento | Para quê |
|---|---|
| `ARCHITECTURE.md` | camadas, direção de dependência, a fronteira de target |
| `docs/spec/harpia-language.md` | a linguagem V0; §9 fixa os status de erro |
| `TARGETS.md` | por que Java/Spring é um target e não a linguagem |
| `docs/roadmap.md` | onde o E0.9 se encaixa |
| `LANGUAGE_COVERAGE.md` | o que pode e o que não pode ser marcado como suportado |

---

## 2. Estado atual

`harpia build` para `examples/customer` escreve **14 arquivos** e o projeto resultante é uma
aplicação Spring Boot que compila:

```
generated/.harpia-manifest
generated/pom.xml
generated/src/main/java/com/example/customer/CustomerServiceApplication.java
generated/src/main/java/com/example/customer/domain/Customer.java              entity JPA
generated/src/main/java/com/example/customer/repository/CustomerRepository.java Spring Data
generated/src/main/java/com/example/customer/dto/CustomerResponse.java
generated/src/main/java/com/example/customer/dto/CreateCustomerRequest.java     Bean Validation
generated/src/main/java/com/example/customer/dto/UpdateCustomerRequest.java
generated/src/main/java/com/example/customer/service/CustomerService.java       corpo dos flows
generated/src/main/java/com/example/customer/web/CustomerController.java        binding HTTP
generated/src/main/java/com/example/customer/error/NotFoundException.java
generated/src/main/java/com/example/customer/error/ApiError.java
generated/src/main/java/com/example/customer/error/ApiExceptionHandler.java
generated/src/main/resources/application.yaml
generated/src/main/resources/db/migration/V1__init.sql
```

Mais uma classe Java pura por `## Logic` declarada (ver `examples/business-logic/pricing`).

Três testes provam isso em camadas diferentes:

- `JavaSpringGoldenTest` — a árvore inteira, byte a byte, contra `src/test/resources/fixtures/targets/java-spring/customer`;
- `GeneratedSourcesCompileTest` — `javac` sobre todo o Java gerado, contra JPA, Spring MVC e Bean Validation reais;
- `GeneratedMavenProjectTest` — `mvn -o test` dentro do projeto gerado.

Mais dois arquivos de teste gerados por entidade, em `src/test/java`: um teste de service por flow
(Mockito) e um teste web por endpoint e por falha declarada (`@WebMvcTest` + MockMvc). Para
`examples/customer` são 20 testes, e eles executam de verdade — quebrar o status do controller, o
casamento da constraint no advice ou a ordenação do `list` faz quatro deles falharem.

---

## 3. Próximo incremento

O gate do E0 fechou. O que resta, em ordem:

1. **E0.10 — medição de SCR.** O benchmark que sustenta a tese do produto; pequeno e inexistente.
2. **F1 — fundação semântica V1.** `SourceRange` com fim de intervalo, `ModuleAst`/`ProjectAst`,
   SymbolTable determinística em duas passagens e `harpia inspect --stage ast|symbols|business-ir|application-ir`.
   `inspect` é a prova externa das fronteiras arquiteturais e deve vir antes de qualquer construção
   nova da linguagem.
3. **S1 — primeiro slice além de CRUD:** `## Command` + `## Event` + `emit`.

Uma lacuna conhecida no que já existe, se você quiser fechá-la primeiro:

- `Generated ID` e `Defaults` chegam ao Java gerado mas **nenhum teste gerado os observa**; por isso
  seguem `PARTIAL` no mapa de cobertura. O teste de duplicate injeta o nome de constraint vindo de
  `SqlConstraintNames`, a mesma fonte que o schema e o handler usam: isso prova que a lógica do
  handler funciona, **não** que os três concordariam se alguém mudasse o formato.

### Testes gerados

Três transformers produzem teste hoje:

```
JavaSpringServiceTestTransformer      um teste por flow, com repository mockado
JavaSpringControllerTestTransformer   um por endpoint e um por falha declarada, com @WebMvcTest
JavaSpringLogicTestTransformer        um por `## Scenario`, JUnit puro sem contexto
```

Os dois primeiros derivam entradas de amostra da spec (`JavaSampleValues`) e asseveram binding,
status e efeito — não valor de negócio, porque para CRUD o valor é o que entrou. O terceiro é o
único com oráculo real: o valor esperado vem do `## Scenario` que uma pessoa escreveu. **O
compilador nunca avalia a Logic para adivinhar o esperado** — isso exigiria manter uma segunda
implementação da semântica em paralelo com o generator.

## 4. A fronteira que você não pode violar

Regra mais importante do repositório, imposta por `ArchitectureBoundaryTest`.

```
source → ast → parse → logic → validate → model → capability → application
                                                                    ↓
                                                            TargetResolver     ← a fronteira
                                                                    ↓
                                                        target.javaspring      ← você trabalha aqui
```

Nenhum pacote acima da fronteira pode mencionar Spring, JPA, Jakarta, Maven, `Repository`,
`Controller` ou nome de tipo Java — **nem em comentário**. O teste lê o código-fonte de produção e
falha se isso acontecer.

Você **não deveria precisar mudar a Application IR**. Ela já carrega tudo: operações, failures
declaradas, status, request/response models, fronteira transacional. Se você concluir que precisa
alterá-la, pare e reavalie. As fixtures `src/test/resources/fixtures/semantic/customer/*.txt` são
golden e não devem mudar.

---

## 5. Como o target Java/Spring funciona hoje

Esta é a parte que mudou recentemente. **Não existe mais um template Mustache por artefato Java.**
Java é construído como um modelo estruturado e depois renderizado.

```
Application IR
    ↓
transformer/     Application IR  →  modelo Java
    ↓
model/           JavaSourceFile, JavaTypeModel, JavaMethodModel, …
    ↓
renderer/        JavaSourceRenderer + JavaImportResolver  →  texto
    ↓
GeneratedTree
```

Mustache sobreviveu apenas para artefatos que não são Java: `pom.xml`, `application.yaml` e a
migration SQL.

### Os transformers

Em `dev.harpia.target.javaspring.transformer`, todos orquestrados por
`JavaSpringProjectTransformer`:

```
JavaSpringBootstrapTransformer     classe principal Spring Boot
JavaSpringEntityTransformer        entity JPA
JavaSpringRepositoryTransformer    interface Spring Data
JavaSpringDtoTransformer           records de request e response
JavaSpringServiceTransformer       service com o corpo dos flows
JavaSpringControllerTransformer    controller REST
JavaSpringErrorTransformer         NotFoundException, ApiError, ApiExceptionHandler
JavaSpringLogicTransformer         classes puras de `## Logic`
JavaSpringMigrationTransformer     modelo SQL da migration
```

Um transformer novo é registrado em `JavaSpringProjectTransformer.transform`. É provavelmente onde
você vai adicionar `JavaSpringTestTransformer`.

Atenção: `JavaSourceFile.relativePath` decide onde o arquivo cai. Testes gerados vão para
`src/test/java/...`, não `src/main/java/...` — `JavaLayout.sourcePath` monta caminhos de `main`, e
você vai precisar do equivalente para `test`.

### O modelo Java

Em `dev.harpia.target.javaspring.model`:

```java
record JavaSourceFile(String relativePath, JavaTypeModel type, Optional<SourceRef> source)

record JavaTypeModel(Kind kind,               // CLASS, INTERFACE, RECORD
                     String packageName, String name,
                     JavaVisibility visibility, Set<JavaModifier> modifiers,
                     Optional<String> documentation,
                     List<JavaAnnotationModel> annotations,
                     List<JavaImportModel> explicitImports,
                     List<JavaTypeRef> superTypes,
                     List<JavaFieldModel> fields,        // componentes, quando kind == RECORD
                     List<JavaConstructorModel> constructors,
                     List<JavaMethodModel> methods,
                     Optional<SourceRef> source)

record JavaMethodModel(String name, JavaTypeRef returnType, JavaVisibility visibility,
                       Set<JavaModifier> modifiers, List<JavaAnnotationModel> annotations,
                       List<JavaParameterModel> parameters,
                       List<String> statements,          // uma linha de Java por entrada
                       Optional<SourceRef> source)

JavaTypeRef.of("java.util.UUID")
JavaTypeRef.parameterized("java.util.List", elemento)
JavaAnnotationModel.marker("org.springframework.stereotype.Service")
JavaAnnotationModel.of("...ExceptionHandler", new Attribute("value", "Foo.class"))
```

Regras que economizam tempo:

- **`statements` é uma linha por entrada.** O record rejeita `\n`. Para um `if`, emita várias
  entradas com a indentação embutida na string; o renderer já indenta 8 espaços.
- **Imports saem do modelo, não de regex.** `JavaImportResolver` percorre tipos de campos,
  parâmetros, retornos, anotações e supertipos. Um tipo que aparece **só dentro de um statement**
  precisa entrar em `explicitImports` — veja `Sort` em `JavaSpringServiceTransformer`.
- **`Kind.RECORD` usa `fields` como componentes** e não os repete no corpo.
- **Anotação com um único atributo `value` sai na forma curta**: `@PostMapping("/customers")`.
- `JavaVisibility.PACKAGE_PRIVATE` é o que se usa para componente de record.

### Os mappers

Em `dev.harpia.target.javaspring.mapping` — reuse, não reimplemente:

```java
JavaTypeMapper.map(ApplicationScalarType)   // DECIMAL -> BigDecimal
JavaTypeMapper.map(LogicType)
SpringValidationMapper.map(ApplicationField) // required/Email -> @NotNull/@NotBlank/@Email
SpringPersistenceMapper                      // @Entity, @Table, @Column, @Id
JavaDefaultValueMapper.map(ApplicationField) // `default` -> inicializador de campo
SqlConstraintNames.unique(tabela, coluna)    // uq_customer_email
PostgresTypes.column(ApplicationScalarType)  // varchar(320)
```

`SqlConstraintNames` existe porque o schema **cria** a constraint e o `ApiExceptionHandler` a
**reconhece**. Se os dois divergissem, um duplicate viraria 500 em vez do 409 declarado e nada
falharia até produção. Se você gerar um teste de duplicate, use a mesma fonte.

---

## 6. Decisões já tomadas que valem para o seu código

- **`validate input` não gera statement no service.** É realizado no limite HTTP: o record de
  request carrega as constraints, e o controller marca o corpo `@Valid` **apenas** quando o flow
  declara `validate input`. Um flow que não declara não valida.
- **Duplicate é reconhecido pela constraint, não pré-checado.** Um pré-check corre com o insert, e
  com JPA o insert só é enviado no commit — depois do método do service ter retornado. Por isso o
  repository não tem `existsBy...`.
- **O construtor sem argumentos da entity é `public`**, não `protected`: JPA aceita os dois, e o
  service precisa instanciá-la de outro pacote para `create ... from input`.
- **`default` do Harpia é default de aplicação**, materializado como inicializador de campo, nunca
  como `DEFAULT` no SQL — assim entity e schema não podem divergir sob `ddl-auto: validate`.
- **`list` ordena por `id`**, porque a especificação da linguagem exige ordem estável.
- **Nada de método gerado sem consumidor.** Código morto é rejeitado por convenção.
- **Tipos boxed** (`Integer`, `Boolean`); `Decimal` é sempre `BigDecimal` com construtor de `String`.

---

## 7. Como verificar

```bash
mvn test                                     # tudo verde
./bin/harpia build --dir examples/customer
```

Quatro testes vão exigir atenção quando a árvore crescer — a quebra é esperada, não bug:

- `JavaSpringGoldenTest` — a golden precisa ser regerada
- `ValidateExampleTest` e `EmitterPipelineTest` — afirmam a lista exata de arquivos
- `CliExitCodeTest` — afirma as contagens no texto do `build`

O `pom.xml` do compilador declara, **test-scoped**, as APIs contra as quais o código gerado é
compilado. Note o `spring-boot-starter-test`: ele é a mesma fonte que o projeto gerado usa, então
os testes gerados compilam contra exatamente as versões com que vão rodar. Escolher versões de
Mockito à mão não funciona offline — o `byte-buddy-agent` correspondente pode não estar no
repositório local.

```
org.springframework:spring-web / spring-webmvc / spring-tx : 6.1.15
org.springframework.boot:spring-boot-autoconfigure         : 3.3.6
org.springframework.boot:spring-boot-starter-test          : 3.3.6
org.springframework.data:spring-data-jpa                   : 3.3.6
jakarta.persistence:jakarta.persistence-api                : 3.1.0
jakarta.validation:jakarta.validation-api                  : 3.0.2
```

Escreva seus testes em `src/test/java/dev/harpia/target/javaspring/`. Nunca afirme sobre Spring em
um teste de Business IR ou Application IR.

---

## 8. Armadilhas conhecidas

1. **Determinismo é obrigatório.** `DeterminismTest` compila cada exemplo duas vezes e compara
   hash SHA-256. Nada de `HashMap` na ordem de saída, nada de relógio, nada de `Set` não ordenado.
2. **`GeneratedTree.put` recusa path duplicado** — dois transformers escrevendo o mesmo arquivo é
   exceção, não sobrescrita silenciosa.
3. **`mvn clean` está quebrado nesta máquina** (plugin ausente no repositório offline). Use
   `rm -rf target && mvn -o test`.
4. **Todos os `mvn` precisam de `-o`**; não há rede.
5. **Surefire às vezes roda classes de teste velhas** depois de uma compilação que falhou, e
   `mvn -q compile` pode reportar sucesso sem ter recompilado o que você mudou. Se um erro não
   fizer sentido — ou se um sucesso não fizer —, `rm -rf target` antes de acreditar nele.
6. `examples/*/generated/` está no `.gitignore`. Se alguém já rodou Maven lá dentro, o `target/`
   aparece como arquivo desconhecido (`HRP5001`) a cada build — é o writer se recusando a ser dono
   do que não gerou, não um bug.
7. Não marque nada como `SUPPORTED` no `LANGUAGE_COVERAGE.md` antes do gate correspondente passar.

---

## 9. Definição de pronto, para qualquer incremento aqui

- [ ] `mvn test` verde, incluindo `ArchitectureBoundaryTest` e `DeterminismTest`
- [ ] `GeneratedSourcesCompileTest` e `GeneratedMavenProjectTest` verdes
- [ ] golden regerada; fixtures semânticas em `fixtures/semantic/` **não** mudaram
- [ ] `LANGUAGE_COVERAGE.md`, `docs/roadmap.md` e `docs/current-architecture.md` atualizados
- [ ] nada marcado `SUPPORTED` sem um teste que observe o comportamento, não só a compilação

---

## 10. Princípios do projeto

> **Flow descreve o que acontece. Logic descreve como valores de negócio são calculados.**

> **Generated code belongs to the target. Software intent belongs to Harpia.**

> **AI is probabilistic. Harpia compilation is deterministic.**

> Harpia não é uma linguagem para escrever menos caracteres. É uma linguagem para expressar mais
> significado de software com menos código-fonte.

Cada construção nova atualiza, no mesmo change set: gramática, AST, símbolos, diagnostics, IRs,
generator, goldens, cobertura e documentação.
