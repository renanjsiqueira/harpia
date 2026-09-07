# Design do Harpia MVP

## 1. Arquitetura

O compilador é uma biblioteca pura cercada por adaptadores de entrada e saída:

```text
harpia.yaml + specs/*.harpia.md
              │
              ▼
 config → source → Markdown estrutural → gramáticas de linha
              │
              ▼
             AST → validação semântica → Resolver → Business IR
                                      │
                                      ▼
                         requirements de capabilities
                                      +
                                harpia.yaml
                                      │
                                      ▼
                          CapabilityResolver
                                      │
                                      ▼
                              Application IR
                                      │
                                      ▼
                              emissores Mustache
                                      │
                                      ▼
                                GeneratedTree
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
                    validate                    build
                    sem escrita            OutputWriter.sync
```

O Markdown fornece apenas estrutura de blocos. Headings, listas e blocos cercados são localizados
com CommonMark, mas o texto executável é recuperado diretamente do source span e entregue a
gramáticas próprias. Assim, parsing inline nunca altera `->`, `{id}` ou `_snake_case_`.

## 2. Pacotes

| Pacote | Responsabilidade |
|---|---|
| `dev.harpia.diag` | Diagnósticos, posições e ordenação total. |
| `dev.harpia.source` | Leitura UTF-8 estrita, normalização e descoberta segura. |
| `dev.harpia.config` | Leitura segura e validação de `harpia.yaml`. |
| `dev.harpia.ast` | Estrutura Markdown e source spans, sem semântica de negócio. |
| `dev.harpia.parse` | Gramáticas de linha e construção da AST Harpia. |
| `dev.harpia.model` | IR resolvida, tipos e nomes canônicos. |
| `dev.harpia.validate` | Regras semânticas e rejeição de recursos não suportados. |
| `dev.harpia.capability` | Inferência de requirements e resolução de providers. |
| `dev.harpia.application` | Application IR com target/settings e capabilities resolvidas. |
| `dev.harpia.emit` | Emissores determinísticos e árvore gerada em memória. |
| `dev.harpia.write` | Sincronização segura da árvore no disco. |
| `dev.harpia.cli` | Picocli, apresentação de diagnósticos e códigos de saída. |

Dependências apontam para o centro: CLI e writer conhecem o compilador; parser e emissores conhecem
o modelo; o modelo não conhece Spring, Maven, filesystem ou Picocli.

## 3. Configuração V0

Formato definitivo da configuração:

```yaml
harpia: 1

project:
  name: customer-service
  group: com.example
  artifact: customer-service
  package: com.example.customer

target:
  type: spring
  javaVersion: 21
  springBootVersion: 3.3.2

database:
  vendor: postgres

paths:
  specs: specs
  output: generated

generation:
  migrations: true
  tests: true
```

Todas as chaves são obrigatórias no V0, exceto `paths`, cujos defaults são `specs` e `generated`.
Chaves desconhecidas ou duplicadas são erros. Os únicos valores aceitos são `harpia: 1`,
`target.type: spring`, `target.javaVersion: 21`, `database.vendor: postgres`, migrations e tests
habilitados. A versão de Spring Boot é uma string explícita e pinada; não existe resolução de
`latest` ou `default`.

Paths são relativos à raiz do projeto, não podem ser absolutos e, depois de normalizados, não podem
escapar da raiz. A descoberta aceita somente arquivos regulares terminados em `.harpia.md` e não
segue symlinks.

## 4. Modelo intermediário

O parser produz uma AST fiel ao texto; o Resolver produz uma IR completa para emissão:

```java
record ProjectModel(List<EntityModel> entities) {}

record EntityModel(String name, String tableName,
                   List<FieldModel> fields, FieldModel idField,
                   List<UseCaseModel> useCases, SourceRef where) {}

record FieldModel(String name, String columnName, TypeRef type,
                  boolean required, boolean unique, boolean generated,
                  Optional<Literal> defaultValue, SourceRef where) {}

record UseCaseModel(String title, String baseName, HttpBinding http,
                    List<FieldModel> input, FlowModel flow,
                    OutputModel output, List<ErrorMapping> errors,
                    SourceRef where) {}

sealed interface FlowStep permits ValidateInput, CreateFrom, LoadById,
                                  UpdateFrom, ListAll, Save, Delete, Return {}
```

Depois da resolução de capabilities, E0.2 reduz esse modelo para uma Application IR própria:
`ApplicationEntity`, `ApplicationField` e `ApplicationOperation`. A operação já contém nome de
método, classificação CRUD, endpoint, request/result, falhas, instruções de flow e fronteira
transacional. O generator não recebe `ProjectModel` nem tipos do parser.

Antes da emissão, a validação semântica e o Resolver garantem em conjunto:

1. cada entidade possui exatamente `id: UUID generated` e nenhum outro campo `generated`;
2. todo nome Java, nome SQL e `baseName` é válido, canônico e único no escopo relevante;
3. cada variável de flow é definida antes do uso e mantém um tipo conhecido;
4. todo flow termina em `Return`, coerente com o output;
5. endpoint, input, flow e erros só referenciam a entidade declarada no arquivo.

Conversões para PascalCase e snake_case acontecem uma vez no Resolver com `Locale.ROOT`. Templates
não fazem transformação de nomes nem decisões de negócio.

## 5. Contratos principais

```java
public final class HarpiaCompiler {
    public CompileResult compile(CompileRequest request);
}

public record CompileRequest(Path projectRoot) {}

public record CompileResult(Optional<GeneratedTree> tree,
                            List<Diagnostic> diagnostics) {
    public boolean hasErrors();
}

public interface Emitter {
    void emit(ApplicationProject project, GeneratedTree output);
}
```

`compile` não escreve no disco. Ele acumula erros independentes e só oferece uma `GeneratedTree`
quando a entrada inteira é válida. `validate` e `build` chamam esse mesmo método; apenas `build`
entrega a árvore ao writer.

`GeneratedTree` usa `TreeMap<String, String>`. Suas chaves são paths relativos com `/`, validados
contra `..`, paths absolutos e colisões. Seu conteúdo já passou pelo normalizador de saída.

## 6. Diagnósticos e falhas

Famílias: `HRP1xxx` sintaxe, `HRP2xxx` semântica, `HRP3xxx` configuração, `HRP4xxx` recurso fora do
V0 e `HRP5xxx` I/O.

O formato externo é:

```text
specs/customer.harpia.md:14:3: error[HRP2007]: variável 'custome' não foi definida
  hint: você quis dizer 'customer'?
```

Diagnósticos sem posição vêm primeiro. Os demais são ordenados por arquivo, linha, coluna, código,
mensagem, severidade e hint. A ordenação é total e independe da ordem de descoberta.

Erros esperados de entrada nunca usam exceções como interface pública. Exceções ficam reservadas a
bugs; falhas de I/O são convertidas para `HRP5xxx`. Exit 0 significa sucesso, 1 erro de compilação e
2 uso/entrada estrutural/I/O que impede compilação.

## 7. Emissão e escrita

Há um emissor por artefato. Mustache é logic-less; toda decisão já está na IR. Todo conteúdo é
normalizado para LF, sem BOM ou espaço final, no máximo uma linha em branco consecutiva e exatamente
um newline final. Cabeçalhos não incluem data, hostname ou versão dinâmica. E0.2 implementa esse
contrato para pom, classe principal e `application.yaml`; os artefatos de domínio entram nos slices
seguintes de E0.

O writer mantém `generated/.harpia-manifest`, ordenado por path, no formato `sha256  path`. Ele:

1. escreve somente bytes alterados em arquivo temporário seguido de `ATOMIC_MOVE` quando suportado;
2. remove somente paths presentes no manifesto anterior e ausentes da nova árvore;
3. preserva arquivo desconhecido e emite `HRP5001`;
4. grava o novo manifesto por último.

Falhas antes da escrita não alteram `generated/`. A atomicidade é por arquivo, não pela árvore
inteira.

## 8. Segurança, determinismo e offline

- nunca executar shell, Java, script, template ou expressão fornecida pela spec;
- nunca resolver paths de saída fora de `paths.output`;
- usar coleções ordenadas quando a ordem chegar à saída;
- usar `Locale.ROOT` para conversões de caixa;
- não usar relógio, aleatoriedade, hostname, ambiente ou rede durante compilação;
- nunca seguir symlinks na árvore de specs;
- manter versões de dependência explícitas na configuração e nos templates.

O compilador não baixa dependências. “Projeto gerado compila offline” pressupõe que o repositório
Maven local tenha sido previamente preparado.

## 9. Estratégia de testes

- unitários por gramática e por código de diagnóstico;
- testes de source para UTF-8, BOM, CRLF, ordenação e symlinks;
- validação semântica com todos os erros acumulados;
- goldens por emissor e da árvore completa;
- compilação dupla comparando todos os bytes;
- guarda estática contra APIs de rede em produção;
- métrica da tese sobre `examples/customer`;
- script externo que gera o exemplo e executa `mvn -o test` no resultado.

A atualização intencional de golden exige flag explícita e revisão do diff.

## 10. Evolução

Recursos futuros entram adicionando construções à linguagem, tipos à IR e emissores, sem colocar
classes Spring na Business IR. A ordem sugerida após o CRUD é: relacionamentos, autenticação, email,
eventos e somente então flow condicional. Compatibilidade retroativa é preservada quando não conflita
com clareza semântica ou determinismo.

O inventário posterior e o desenho incremental da evolução semântica estão em
[`current-architecture.md`](current-architecture.md), [`roadmap.md`](roadmap.md) e
[`spec/harpia-language-v1-draft.md`](spec/harpia-language-v1-draft.md). Esses documentos não alteram
silenciosamente a gramática normativa V0.
