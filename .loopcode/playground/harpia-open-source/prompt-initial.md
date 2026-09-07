# Harpia — MVP Open Source

> **Atualização de direção (2026-09-06):** este arquivo preserva a visão original. A evolução para
> uma Semantic Software Specification Language está consolidada em `docs/vision.md`, e a cobertura
> real vive em `LANGUAGE_COVERAGE.md`. O recorte executável atual é definido por
> `docs/spec/harpia-language.md`, `docs/requirements.md` e `docs/design.md`. Quando uma ideia futura
> deste prompt conflitar com esses documentos, o compilador deve seguir o recorte V0 documentado.

Quero que você projete e implemente o MVP de um novo projeto open source chamado **Harpia**.

Antes de escrever código, leia toda esta especificação, analise criticamente a proposta, identifique ambiguidades e riscos técnicos, proponha a arquitetura mínima e só depois comece a implementação.

O objetivo não é implementar muitas features.

O objetivo do MVP é provar uma tese técnica de forma simples, determinística e mensurável.

---

# 1. Visão do produto

Harpia é uma **Executable Software Specification Language** baseada em Markdown.

Mais precisamente, Harpia é uma linguagem formal de alto nível que usa Markdown como sintaxe de
autoria e compila para Java convencional. Ela funciona como uma abstração orientada a
especificações sobre Java e seu ecossistema: o autor declara dados, contratos, fluxos e regras; o
compilador materializa classes, tipos, integração com framework e boilerplate.

O arquivo `*.harpia.md` não é documentação que ocasionalmente gera código. Ele é código-fonte
Harpia: legível como Markdown, validado por gramática e com semântica determinística.

Harpia NÃO é uma nova linguagem de programação general-purpose.

Harpia NÃO quer substituir Java, Kotlin, Spring ou outras linguagens/frameworks.

A ideia é permitir que humanos e LLMs descrevam **a intenção do software** através de uma especificação pequena, estruturada e fácil de compreender.

Harpia atende deliberadamente dois perfis de autoria:

* pessoas não técnicas ou pouco técnicas, que conseguem revisar e expressar requisitos dentro de
  construções guiadas, sem precisar dominar o boilerplate de Java e Spring;
* agentes e LLMs, que produzem a representação Harpia compacta em vez de repetir uma grande árvore
  de código de framework.

Isso não significa aceitar linguagem natural irrestrita nem prometer que qualquer sistema possa ser
construído sem conhecimento técnico. A acessibilidade vem de vocabulário pequeno, exemplos,
diagnósticos e progressive disclosure; a precisão vem da gramática formal.

Depois, um compilador determinístico transforma essa especificação em código convencional.

Conceito:

```text
Human / LLM
     ↓
*.harpia.md
     ↓
Harpia Compiler
     ↓
Java + Spring Boot
```

A frase central do projeto é:

> Humans and AI describe software intent. Harpia compiles the implementation.

Também:

> LLMs write intent. Harpia writes code.

E:

> Compile intent, don't prompt boilerplate.

---

# 2. Problema que queremos resolver

Hoje uma LLM pode receber uma tarefa relativamente simples, como:

```text
Create Customer registration with:
- REST endpoint
- DTO
- validation
- service
- repository
- JPA entity
- error handling
- Flyway migration
- tests
```

e produzir milhares de tokens de Java.

Grande parte desses tokens representa boilerplate de framework e não lógica de negócio.

A economia de tokens ocorre na etapa de autoria: humano ou LLM cria e altera a especificação curta,
enquanto o compilador expande localmente a implementação. O build em si consome zero tokens de LLM.
Essa tese deve ser medida comparando a especificação com o Java equivalente em linhas, caracteres e,
quando um tokenizer for explicitamente escolhido, tokens. Linhas e caracteres são proxies e nunca
devem ser apresentados como contagem exata de tokens.

Com Harpia queremos transformar isso em algo parecido com:

````md
# Customer

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean default true

## Create Customer

### Endpoint

POST /customers

### Access

public

### Input

- name: String required
- email: Email required

### Flow

```flow
validate input
customer = create Customer from input
save customer
return customer
````

### Output

201 Customer

### Errors

* invalid input -> 400
* duplicate email -> 409

````

Depois:

```bash
harpia build
````

E Harpia gera deterministicamente o projeto Spring.

A LLM produz a intenção.

O compilador produz o boilerplate.

---

# 3. Princípio fundamental

Nenhuma LLM deve participar do processo de compilação.

Estes comandos:

```bash
harpia validate
harpia build
```

devem funcionar:

* localmente;
* offline;
* deterministicamente;
* sem API key;
* sem OpenAI;
* sem Anthropic;
* sem Gemini;
* sem qualquer modelo externo;
* sem consumo de tokens.

Uma LLM pode criar ou modificar a especificação Harpia.

Depois disso, o compilador assume.

---

# 4. Source of truth

O source of truth do comportamento da aplicação é:

```text
*.harpia.md
```

A configuração tecnológica é:

```text
harpia.yaml
```

O código Java gerado NÃO é source of truth.

Fluxo:

```text
*.harpia.md
       +
harpia.yaml
       ↓
Harpia Compiler
       ↓
Generated Java
```

Nunca implementar:

```text
Harpia ↔ Java
```

A direção é somente:

```text
Harpia → Java
```

Código gerado é descartável.

Alterações devem ser feitas na spec ou configuração e depois recompiladas.

---

# 5. Escopo do MVP

Implementar SOMENTE:

* Java 21+
* Spring Boot 3.x+
* Maven
* REST API
* Spring Web
* Jakarta Validation
* Spring Data JPA
* PostgreSQL
* Flyway
* Spring Security
* JWT básico
* Email SMTP
* eventos locais Spring
* JUnit 5
* Spring Boot Test

Não implementar agora:

* Java 17 ou inferior
* Kotlin
* TypeScript
* JavaScript
* Python
* Ruby
* Go
* C#
* Gradle
* frontend
* GraphQL
* gRPC
* WebSocket
* Kafka
* RabbitMQ
* SQS
* SNS
* SES
* Cognito
* S3
* Azure Blob
* Azure Service Bus
* AWS
* Azure
* GCP
* Kubernetes
* Terraform
* geração direta de bytecode
* runtime próprio
* LLM dentro do compilador
* interpretação livre de linguagem natural
* reverse engineering Java → Harpia
* sincronização bidirecional
* linguagem Turing-complete

A arquitetura pode permitir evolução futura.

Mas não implementar abstrações especulativas desnecessárias.

---

# 6. Java 21+

A versão Java deve ser configurável.

Exemplo:

```yaml
target:
  type: spring
  java: 21
```

Também deve ser possível configurar versões posteriores:

```yaml
java: 22
java: 23
java: 24
java: 25
```

A arquitetura não deve ter uma enum rígida limitada apenas às versões existentes no momento.

Criar algo como:

```text
JavaVersion
```

com validação:

```text
java >= 21
```

Se o usuário configurar:

```yaml
java: 17
```

retornar:

```text
HARP001: Unsupported Java version 17.
Harpia requires Java 21 or newer.
```

Inicialmente o código gerado pode usar apenas features compatíveis com Java 21.

O `pom.xml`, porém, deve refletir a versão escolhida.

---

# 7. Spring Boot

Target inicial:

```text
Java + Spring Boot
```

Exemplo:

```yaml
target:
  type: spring
  java: 21
  springBoot: default
```

Permitir versão Spring Boot configurável futuramente:

```yaml
springBoot: 3.x.x
```

Mas não criar agora uma matriz complexa de compatibilidade.

Definir uma versão default centralizada.

---

# 8. Arquitetura conceitual

A arquitetura deve seguir aproximadamente:

```text
*.harpia.md
      ↓
Markdown Parser
      ↓
AST
      ↓
Semantic Analyzer
      ↓
Business IR
      ↓

harpia.yaml
      ↓
Configuration Parser
      ↓

Business IR + Configuration
      ↓
Capability Resolver
      ↓
Application IR
      ↓
Spring Generator
      ↓
Generated Java
      ↓
Maven
      ↓
JVM
```

Muito importante:

O parser não deve gerar Java diretamente.

Separar:

```text
Parser
↓
AST
↓
Business IR
↓
Application IR
↓
Generator
```

---

# 9. Três camadas conceituais

Harpia deve nascer com uma separação explícita entre:

```text
1. Business Specification
2. Application Capabilities
3. Infrastructure Providers
```

Essa separação é um requisito arquitetural importante.

---

# 10. Business Specification

O `.harpia.md` descreve o negócio.

Pode conter:

* entidades;
* campos;
* regras;
* casos de uso;
* endpoints;
* inputs;
* outputs;
* erros;
* autorização;
* eventos;
* envio lógico de email;
* persistência lógica;
* fluxos.

Exemplo:

````md
# Customer

Represents a registered customer.

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean default true
- createdAt: DateTime generated

## Create Customer

### Endpoint

POST /customers

### Access

public

### Input

- name: String required
- email: Email required

### Rules

- name is required
- email is required
- email must be unique

### Flow

```flow
validate input

customer = create Customer from input

save customer

send WelcomeEmail to customer.email

emit CustomerCreated(customer.id)

return customer
````

### Output

201 Customer

### Errors

* invalid input -> 400
* duplicate email -> 409

````

A Business Spec NÃO pode conhecer:

```text
JpaRepository
Hibernate
PostgreSQL
SMTP
JavaMailSender
ApplicationEventPublisher
JWT library
AWS
Azure
SES
Cognito
Kafka
S3
Spring annotations
````

---

# 11. Markdown não é linguagem natural irrestrita

Markdown serve como interface estrutural.

Não usar uma LLM para interpretar frases arbitrárias.

Isto:

```text
Customer registered in the platform.
```

é documentação.

Isto:

```md
## Data

- email: Email required unique
```

é estrutura semântica.

Isto:

```flow
customer = find Customer by id
```

é código Harpia formal.

Não interpretar automaticamente frases como:

```text
Procure o cliente e, se tiver algum problema, faça o melhor possível.
```

A linguagem precisa ser formal, pequena e determinística.

---

# 12. DSL Flow

Criar uma DSL mínima.

Operações prioritárias:

```text
validate
create
find
save
require
return
emit
send
fail
```

Depois:

```text
set
delete
if
else
transaction
call
store
```

Não implementar todas inicialmente.

Exemplo:

```flow
validate input

customer = create Customer from input

save customer

return customer
```

Exemplo:

```flow
customer = find Customer by id

require customer exists otherwise 404

return customer
```

Exemplo:

```flow
send WelcomeEmail to customer.email

emit CustomerCreated(customer.id)
```

A gramática deve ser formalmente documentada.

---

# 13. Tipos Harpia

Tipos iniciais:

```text
String
Text
Int
Long
Decimal
Boolean
UUID
Email
Date
DateTime
```

Futuro:

```text
Enum
List<T>
T?
```

Criar um type system próprio.

Não colocar diretamente tipos Java dentro da Business IR.

Exemplos de mapeamento:

```text
Harpia UUID
→ java.util.UUID

Harpia Decimal
→ java.math.BigDecimal

Harpia Date
→ java.time.LocalDate

Harpia DateTime
→ java.time.Instant
```

Escolhas devem ser documentadas.

---

# 14. harpia.yaml

A configuração tecnológica deve ficar fora da Business Spec.

Exemplo:

```yaml
project:
  name: customer-service
  group: com.example
  artifact: customer-service
  package: com.example.customer

target:
  type: spring
  java: 21
  springBoot: default

build:
  tool: maven

capabilities:

  persistence:
    enabled: true

  authentication:
    enabled: true

  email:
    enabled: true

  events:
    enabled: true

providers:

  persistence:
    type: postgresql

  authentication:
    type: jwt

  email:
    type: smtp

  events:
    type: local

generation:
  migrations: true
  tests: true
  openapi: true
```

Regra:

```text
.harpia.md = WHAT
harpia.yaml = HOW
```

---

# 15. Capabilities

Criar formalmente o conceito:

```text
Capability
```

Capabilities representam necessidades abstratas da aplicação.

Inicialmente:

```text
persistence
authentication
email
events
```

Arquitetura futura pode permitir:

```text
objectStorage
cache
messaging
scheduling
search
payments
observability
```

Mas não implementar agora.

---

# 16. Providers

Providers implementam capabilities.

MVP:

```text
persistence:
  postgresql

authentication:
  none
  jwt

email:
  none
  smtp

events:
  local
```

Exemplo:

```yaml
providers:
  persistence:
    type: postgresql

  email:
    type: smtp
```

No futuro, sem alterar a Business Spec:

```yaml
email:
  type: ses
```

ou:

```yaml
authentication:
  type: cognito
```

ou:

```yaml
events:
  type: kafka
```

Mas NÃO implementar esses providers agora.

---

# 17. Regra de abstração

A spec deve dizer:

```flow
send WelcomeEmail to customer.email
```

Nunca:

```text
send WelcomeEmail using AWS SES
```

A spec deve dizer:

```flow
emit CustomerCreated(customer.id)
```

Nunca:

```text
publish CustomerCreated into Kafka
```

A spec deve dizer:

```flow
save customer
```

Nunca:

```text
repository.save using PostgreSQL
```

A spec deve dizer:

```md
### Access

- authenticated
- role ADMIN
```

Nunca:

```text
use Cognito ADMIN group
```

---

# 18. Capability Resolver

Criar componente:

```text
CapabilityResolver
```

Responsabilidades:

* analisar capabilities utilizadas pela Business IR;
* verificar se estão habilitadas;
* verificar provider configurado;
* validar compatibilidade;
* produzir Application IR;
* gerar diagnósticos claros.

Exemplo:

Spec:

```flow
send WelcomeEmail to customer.email
```

mas email desabilitado:

```text
HARP250: Capability `email` is required by `CreateCustomer`
but is disabled in harpia.yaml.
```

Email habilitado sem provider:

```text
HARP251: Capability `email` has no provider configured.
```

Provider não suportado:

```text
HARP252: Unsupported email provider `ses`.

Available providers:
- smtp
```

---

# 19. Business IR

Business IR precisa permanecer independente de Spring.

Exemplos de conceitos:

```text
Entity
Field
UseCase
Endpoint
Input
Output
AccessRule
Validation
Find
Create
Save
SendEmail
EmitEvent
Return
Fail
```

Nunca colocar em Business IR:

```text
@RestController
JpaRepository
JavaMailSender
ApplicationEventPublisher
SecurityFilterChain
```

---

# 20. Application IR

Depois do CapabilityResolver, gerar uma representação mais próxima da aplicação.

Conceitualmente:

```text
Business IR

SendEmail(
  message = WelcomeEmail,
  recipient = customer.email
)

+

provider email = smtp

↓

Application IR

EmailOperation(
  provider = SMTP,
  message = WelcomeEmail,
  recipient = customer.email
)
```

Não precisa usar JSON internamente.

Preferir tipos/classes imutáveis.

---

# 21. Persistence

Na Business Spec:

```flow
save customer
```

e:

```flow
customer = find Customer by id
```

Configuração:

```yaml
capabilities:
  persistence:
    enabled: true

providers:
  persistence:
    type: postgresql
```

Gerar:

* JPA entity;
* Spring Data repository;
* datasource configuration;
* Flyway migrations.

---

# 22. Authentication

Business Spec:

```md
### Access

public
```

ou:

```md
### Access

authenticated
```

ou:

```md
### Access

- authenticated
- role ADMIN
```

Configuração:

```yaml
capabilities:
  authentication:
    enabled: true

providers:
  authentication:
    type: jwt
```

Gerar Spring Security.

MVP:

```text
none
jwt
```

OAuth2 Resource Server pode entrar depois.

Não implementar Cognito, Auth0, Keycloak ou Entra ID agora.

---

# 23. Email

Business Spec:

```md
## Email WelcomeEmail

### Subject

Welcome

### Template

welcome-customer
```

Flow:

```flow
send WelcomeEmail to customer.email
```

Configuração:

```yaml
capabilities:
  email:
    enabled: true

providers:
  email:
    type: smtp
```

Gerar algo semelhante a:

```text
EmailSender
SmtpEmailSender
WelcomeEmail
```

Utilizar Spring Mail ou solução Spring convencional.

Não colocar credenciais diretamente no código.

---

# 24. Events

Business Spec:

```flow
emit CustomerCreated(customer.id)
```

Configuração:

```yaml
capabilities:
  events:
    enabled: true

providers:
  events:
    type: local
```

MVP pode utilizar:

```text
Spring ApplicationEventPublisher
```

Mas Business IR não deve saber disso.

No futuro:

```text
Kafka
SQS
Service Bus
```

podem ser providers.

---

# 25. Secrets

Nunca gerar secrets reais.

Exemplo SMTP:

```text
SMTP_HOST
SMTP_PORT
SMTP_USERNAME
SMTP_PASSWORD
```

JWT:

```text
JWT_SECRET
```

Banco:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

Generated `application.yml` deve usar environment variables.

Exemplo:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

---

# 26. Código gerado

Um exemplo completo pode gerar:

```text
generated/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/customer/
│   │   │       ├── CustomerApplication.java
│   │   │       ├── domain/
│   │   │       │   └── Customer.java
│   │   │       ├── repository/
│   │   │       │   └── CustomerRepository.java
│   │   │       ├── service/
│   │   │       │   └── CustomerService.java
│   │   │       ├── web/
│   │   │       │   ├── CustomerController.java
│   │   │       │   └── dto/
│   │   │       │       ├── CreateCustomerRequest.java
│   │   │       │       └── CustomerResponse.java
│   │   │       ├── security/
│   │   │       │   └── SecurityConfiguration.java
│   │   │       ├── email/
│   │   │       │   ├── EmailSender.java
│   │   │       │   └── SmtpEmailSender.java
│   │   │       ├── event/
│   │   │       │   └── CustomerCreated.java
│   │   │       └── exception/
│   │   │           └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V1__create_customer.sql
│   └── test/
│       └── ...
```

---

# 27. Qualidade do código gerado

O output precisa parecer Java/Spring convencional.

Priorizar:

* constructor injection;
* Java records quando apropriado;
* Spring conventions;
* código simples;
* tipos explícitos;
* baixo acoplamento;
* testes legíveis;
* package structure consistente.

Evitar:

* reflection desnecessária;
* runtime Harpia;
* metaprogramação obscura;
* classes gigantes;
* service locator;
* generated runtime DSL;
* dependência Harpia no projeto final.

Idealmente:

```text
Harpia = build-time dependency only
```

Depois de gerado, o projeto Spring deve funcionar independentemente do Harpia.

---

# 28. Determinismo

Mesmo:

```text
Harpia version
+
harpia.yaml
+
spec files
```

deve produzir o mesmo output.

Não gerar:

* timestamps;
* UUID aleatório;
* ordenação aleatória;
* headers variáveis.

Ordenar deterministicamente:

* imports;
* fields;
* methods;
* entities;
* files;
* endpoints.

---

# 29. Idempotência

Executar:

```bash
harpia build
harpia build
harpia build
```

sem alterar nada deve gerar exatamente o mesmo resultado.

Não duplicar:

* migrations;
* endpoints;
* classes;
* fields;
* handlers;
* imports.

---

# 30. Estratégia de geração

Para o MVP, avaliar:

* templates;
* JavaPoet;
* geração de AST Java.

Não construir um compilador Java completo.

Templates são aceitáveis inicialmente se forem:

* pequenos;
* organizados;
* determinísticos;
* testáveis;
* separados da Business IR.

Não deixar a IR depender do template engine.

---

# 31. Parser Markdown

Usar biblioteca Markdown consolidada em vez de implementar Markdown do zero.

Precisamos reconhecer:

```text
Heading
Paragraph
List
Code block
```

Estrutura como:

```md
# Customer
## Data
## Create Customer
### Endpoint
### Access
### Input
### Rules
### Flow
### Output
### Errors
```

deve virar AST.

Texto livre que não corresponde a construção executável é documentação.

---

# 32. Flow parser

Criar parser próprio pequeno para:

```flow
validate input
customer = create Customer from input
save customer
return customer
```

Não executar essas strings diretamente.

Criar tokens / AST de Flow.

Exemplo:

```text
ValidateOperation
CreateOperation
SaveOperation
ReturnOperation
```

---

# 33. Diagnostics

Criar sistema de diagnostics desde o início.

Exemplos:

```text
HARP001 Unsupported Java version

HARP100 Invalid Markdown structure
HARP101 Unknown section
HARP102 Unknown type
HARP103 Duplicate field
HARP104 Unknown reference

HARP200 Invalid flow operation
HARP201 Invalid assignment
HARP202 Invalid access rule

HARP250 Missing capability
HARP251 Missing provider
HARP252 Unsupported provider

HARP300 Generator failure
```

Sempre que possível incluir:

* arquivo;
* linha;
* coluna;
* trecho;
* sugestão.

Exemplo:

```text
spec/customer.harpia.md:24

HARP104: Unknown field `customerId`.

24 | customer = find Customer by customerId
                                   ^^^^^^^^^^

Available fields:
- id
- name
- email
```

Diagnostics fazem parte importante da experiência da linguagem.

---

# 34. CLI

Criar inicialmente:

```bash
harpia init
harpia validate
harpia build
harpia clean
harpia version
```

Também quero:

```bash
harpia inspect
```

Exemplos:

```bash
harpia inspect
```

ou:

```bash
harpia inspect customer.harpia.md
```

Possíveis opções:

```bash
harpia inspect --stage ast
harpia inspect --stage business-ir
harpia inspect --stage application-ir
```

Isso será extremamente útil para debugging da linguagem.

---

# 35. harpia init

Exemplo:

```bash
harpia init
```

Ou:

```bash
harpia init \
  --java 21 \
  --package com.example.customer \
  --database postgresql \
  --auth jwt \
  --email smtp
```

Gerar:

```text
harpia.yaml

spec/
  example.harpia.md
```

Pode inicialmente usar defaults.

Não deixar uma interface interativa sofisticada atrasar o MVP.

---

# 36. harpia validate

Exemplo esperado:

```text
$ harpia validate

Harpia 0.1.0

Parsing...
✓ customer.harpia.md

Semantic validation...

Entity Customer
  ✓ 5 fields

CreateCustomer
  ✓ endpoint POST /customers
  ✓ access public
  ✓ 2 input fields
  ✓ 5 flow operations

Capabilities:
  ✓ persistence
  ✓ email
  ✓ events

Specification valid.
```

---

# 37. harpia build

Exemplo:

```text
$ harpia build

Harpia 0.1.0

Parsing specifications...
✓ customer.harpia.md

Validating...
✓ Business specification valid

Resolving capabilities...
✓ persistence -> postgresql
✓ authentication -> jwt
✓ email -> smtp
✓ events -> local

Generating:
Java 21
Spring Boot
Maven

✓ Customer.java
✓ CustomerRepository.java
✓ CustomerService.java
✓ CustomerController.java
✓ CreateCustomerRequest.java
✓ CustomerResponse.java
✓ SecurityConfiguration.java
✓ SmtpEmailSender.java
✓ CustomerCreated.java
✓ V1__create_customer.sql

Generated 10 files.

No LLM.
No API.
0 tokens consumed.
```

---

# 38. Maven

O projeto gerado deve conseguir:

```bash
cd generated

./mvnw test
```

e:

```bash
./mvnw spring-boot:run
```

Quando banco externo não estiver disponível durante testes, considerar Testcontainers ou configuração apropriada para testes.

Não exigir infraestrutura externa para testes unitários simples.

---

# 39. Estrutura sugerida do Harpia

Avaliar algo semelhante a:

```text
harpia/
├── cli/
├── config/
├── markdown/
│   └── parser/
├── flow/
│   ├── lexer/
│   └── parser/
├── ast/
├── semantic/
├── types/
├── ir/
│   ├── business/
│   └── application/
├── capabilities/
│   ├── resolver/
│   ├── persistence/
│   ├── authentication/
│   ├── email/
│   └── events/
├── providers/
│   ├── postgresql/
│   ├── jwt/
│   ├── smtp/
│   └── local-events/
├── generator/
│   └── spring/
├── diagnostics/
└── tests/
```

Se fizer sentido usar Maven multi-module:

```text
harpia-core
harpia-parser
harpia-generator-spring
harpia-cli
```

Mas não dividir artificialmente.

Preferir arquitetura simples.

---

# 40. Provider architecture

No MVP os providers podem ser internos.

Mas não criar uma arquitetura que impeça futuramente:

```text
harpia-provider-aws
harpia-provider-azure
harpia-provider-gcp
harpia-provider-kafka
harpia-provider-auth0
harpia-provider-sendgrid
```

Provider conceitualmente pode possuir:

```text
supported capability
configuration
validation
dependencies
generator contribution
```

Não criar SPI/plugin system completo agora.

Apenas manter boundaries apropriados.

---

# 41. V0 — Entity + Persistence

Primeiro milestone.

Input:

```md
# Customer

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean default true
```

Configuração:

```yaml
project:
  name: customer-service
  group: com.example
  artifact: customer-service
  package: com.example.customer

target:
  type: spring
  java: 21

capabilities:
  persistence:
    enabled: true

providers:
  persistence:
    type: postgresql
```

Gerar:

```text
CustomerApplication.java
Customer.java
CustomerRepository.java
V1__create_customer.sql
application.yml
pom.xml
```

O projeto deve compilar.

Não avançar antes de isso estar funcionando e testado.

---

# 42. V0.1 — REST

Adicionar:

````md
## Create Customer

### Endpoint

POST /customers

### Access

public

### Input

- name: String required
- email: Email required

### Flow

```flow
validate input
customer = create Customer from input
save customer
return customer
````

### Output

201 Customer

### Errors

* invalid input -> 400
* duplicate email -> 409

````

Gerar:

```text
CreateCustomerRequest
CustomerResponse
CustomerService
CustomerController
GlobalExceptionHandler
````

Adicionar testes.

---

# 43. V0.2 — Authentication

Adicionar:

```md
## Get Customer

### Endpoint

GET /customers/{id}

### Access

authenticated
```

Depois:

```md
### Access

- authenticated
- role ADMIN
```

Configuração:

```yaml
capabilities:
  authentication:
    enabled: true

providers:
  authentication:
    type: jwt
```

Gerar configuração Spring Security.

---

# 44. V0.3 — Email

Adicionar:

```md
## Email WelcomeEmail

### Subject

Welcome

### Template

welcome-customer
```

Flow:

```flow
send WelcomeEmail to customer.email
```

Config:

```yaml
capabilities:
  email:
    enabled: true

providers:
  email:
    type: smtp
```

Gerar:

```text
EmailSender
SmtpEmailSender
WelcomeEmail
```

---

# 45. V0.4 — Events

Flow:

```flow
emit CustomerCreated(customer.id)
```

Config:

```yaml
capabilities:
  events:
    enabled: true

providers:
  events:
    type: local
```

Gerar evento local Spring.

---

# 46. V0.5 — Business Flow

Depois implementar progressivamente:

```text
find
require
fail
set
if
else
transaction
delete
```

Exemplo futuro:

```flow
customer = find Customer by id

require customer exists otherwise 404

require customer.active otherwise 422

customer.name = input.name

save customer

return customer
```

Não tentar implementar uma linguagem completa.

---

# 47. Testes do compilador

Quero forte cobertura.

## Parser tests

```text
Markdown → AST
```

## Flow tests

```text
Flow source → Flow AST
```

## Semantic tests

```text
AST → Business IR
```

## Capability tests

```text
Business IR + config → Application IR
```

Testar:

* capability habilitada;
* capability ausente;
* provider ausente;
* provider inválido.

## Generator tests

```text
Application IR → Java
```

## Golden tests

Criar fixtures:

```text
fixtures/
├── customer-basic/
│   ├── spec/
│   ├── harpia.yaml
│   └── expected/
```

Comparar output com arquivos esperados.

## Compilation tests

Muito importante:

Gerar aplicação e executar:

```bash
./mvnw test
```

CI deve confirmar que o projeto produzido compila.

---

# 48. Token benchmark

O projeto precisa nascer preparado para demonstrar sua tese.

Criar exemplo:

```text
examples/customer-crud/
```

Medir:

```text
Harpia specification:
- lines
- characters
- estimated tokens

Generated Java:
- files
- lines
- characters
- estimated tokens
```

Calcular:

```text
compression ratio
```

Não afirmar que caracteres equivalem diretamente a tokens.

Tokenização depende do modelo.

A comparação principal é:

```text
LLM generating Harpia specification

vs

LLM generating equivalent Java/Spring implementation
```

---

# 49. Skill para agentes

Criar:

```text
skills/harpia/SKILL.md
```

Documentar de forma extremamente compacta como uma LLM deve trabalhar com Harpia.

Exemplo:

```text
When working with Harpia:

1. Read harpia.yaml.
2. Read all relevant *.harpia.md specs.
3. *.harpia.md is the business source of truth.
4. Never edit generated Java.
5. Keep infrastructure details out of business specs.
6. Use Harpia semantic operations.
7. Run `harpia validate`.
8. Run `harpia build`.
9. Run generated tests.
```

Adicionar:

* tipos;
* headings;
* flow grammar;
* exemplos;
* regras.

O objetivo é que Claude Code, Codex, Cursor ou outro agente consiga aprender Harpia consumindo poucos tokens.

---

# 50. README

README deve explicar imediatamente:

```text
# Harpia

Executable software specifications for humans and AI.
```

Depois:

```md
Write:

customer.harpia.md
```

```bash
harpia build
```

Resultado:

```text
Java 21+ / Spring Boot application
```

Destacar:

```text
Open Source
Local
Deterministic
No LLM during build
No API key
No token consumption during compilation
Java 21+
Spring Boot
Markdown-native
AI-friendly
```

---

# 51. Segurança

Nunca executar código arbitrário presente na spec.

Não permitir:

```text
shell
eval
javascript
java snippets
arbitrary scripts
```

Flow é interpretado somente pela gramática Harpia.

Tratar Markdown como input não confiável.

Validar paths para evitar path traversal durante geração.

Não permitir que nomes da spec sobrescrevam arquivos arbitrários fora do diretório generated.

---

# 52. Design principles

Em ordem:

1. Semantic clarity
2. Readability
3. Determinism
4. Simplicity
5. Business/infrastructure separation
6. Good diagnostics
7. Generated-code quality
8. LLM friendliness
9. Token efficiency
10. Extensibility

Quando linguagem natural conflitar com determinismo:

> Determinism wins.

Quando infraestrutura quiser entrar na Business Spec:

> Infrastructure stays outside the business spec.

Quando extensibilidade futura aumentar muito a complexidade atual:

> MVP simplicity wins.

---

# 53. Anti-goals

Harpia NÃO deve virar:

* um novo Java;
* um novo Kotlin;
* uma DSL general-purpose;
* pseudocódigo livre;
* prompt wrapper;
* AI coding agent;
* low-code visual;
* runtime proprietário;
* cloud framework;
* Terraform replacement;
* Kubernetes abstraction;
* framework distribuído;
* template engine exposto ao usuário.

---

# 54. Futuro

Não implementar agora, mas não bloquear arquiteturalmente:

```text
Harpia Business Spec
        ↓
    Business IR
        ↓
Capability Resolution
        ↓
   Application IR
        │
        ├── Java/Spring
        ├── TypeScript/Nest
        ├── Python/FastAPI
        ├── Ruby/Rails
        └── Go
```

E:

```text
Capabilities
    │
    ├── AWS providers
    ├── Azure providers
    ├── GCP providers
    └── Community providers
```

Mas não criar esses targets/providers antes de provar Java/Spring.

Java/Spring é o único backend normativo e o compromisso de produto atual. Outros targets são apenas
uma possibilidade de pesquisa: não devem introduzir abstrações, complexidade ou promessas públicas
antes de a tese ser comprovada com Java. Mesmo mantendo a Business IR livre de classes Java, toda
decisão do V0 deve priorizar a qualidade e a previsibilidade do código Java gerado.

---

# 55. Definition of Done do MVP

Considerarei o MVP bem-sucedido quando for possível:

```bash
git clone <repository>

cd harpia

./harpia init \
  --java 21 \
  --database postgresql \
  --auth jwt \
  --email smtp
```

Editar:

```text
spec/customer.harpia.md
```

Executar:

```bash
./harpia validate

./harpia build
```

Depois:

```bash
cd generated

./mvnw test

./mvnw spring-boot:run
```

E realizar:

```bash
curl -X POST http://localhost:8080/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Renan",
    "email": "renan@example.com"
  }'
```

Recebendo:

```json
{
  "id": "...",
  "name": "Renan",
  "email": "renan@example.com",
  "active": true
}
```

Tudo isso deve ter sido produzido deterministicamente a partir de:

```text
*.harpia.md
+
harpia.yaml
```

sem LLM durante o build.

---

# 56. Primeira tarefa

NÃO comece imediatamente implementando todos os milestones.

Primeiro faça uma fase de design.

Entregue:

1. análise crítica da proposta;
2. principais riscos;
3. ambiguidades identificadas;
4. arquitetura proposta;
5. estrutura do projeto;
6. gramática formal V0;
7. AST V0;
8. Business IR V0;
9. Application IR V0;
10. Type System;
11. formato definitivo do `harpia.yaml`;
12. CapabilityResolver;
13. provider contracts mínimos;
14. strategy para Java generation;
15. strategy para migrations;
16. strategy para diagnostics;
17. strategy para testes;
18. roadmap técnico;
19. lista explícita do que ficará fora do MVP.

Depois dessa análise, implemente SOMENTE:

```text
V0 — Entity + PostgreSQL persistence
```

Primeiro faça este fluxo funcionar completamente:

```text
customer.harpia.md
        ↓
parser
        ↓
AST
        ↓
semantic validation
        ↓
Business IR
        ↓
Capability Resolver
        ↓
Application IR
        ↓
Spring generator
        ↓
compilable Maven project
```

Somente depois avance incrementalmente:

```text
V0.1 REST
V0.2 Authentication
V0.3 Email
V0.4 Events
V0.5 Business Flow
```

Em cada etapa:

* implementar;
* testar;
* gerar fixture;
* compilar o projeto gerado;
* atualizar documentação;
* atualizar grammar;
* manter backward compatibility quando razoável.

Não fazer vários milestones ao mesmo tempo.

---

# 57. Critério principal de sucesso

O objetivo não é provar que conseguimos gerar Java a partir de Markdown.

Isso é fácil.

O objetivo é provar que:

> Uma representação de software pequena, estruturada, fácil para humanos entenderem e barata para LLMs produzirem pode ser compilada deterministicamente para software convencional de produção.

E que:

> Business intent remains independent from framework and infrastructure choices.

A arquitetura deve sempre proteger essas duas ideias.

Comece agora pela análise arquitetural e pelo desenho formal do V0 antes de implementar o compilador.
