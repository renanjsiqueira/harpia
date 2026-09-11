# Harpia Catalog

Every entry below is a **whole specification**. Copy one into `specs/<name>.harpia.md`, put a
`harpia.yaml` next to it, and `harpia build` produces a project. Nothing here is a fragment, and
nothing here is prose about a feature: each block is compiled by `CatalogTest` on every run, so an
example that stopped working fails the build instead of misleading a reader.

Blocks marked with a diagnostic code are the other half of the same promise: they show what is
**refused**, and the refusal is verified too.

Unless a block says `v0`, it needs `languageVersion: 1`.

The normative grammar is [`spec/harpia-language.md`](spec/harpia-language.md). This file is the
short answer to "how do I say X".

---

## Entity and fields

An entity is one file's `# Name` plus `## Data`. Every entity has exactly one `id`.

````harpia v0
# Customer

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean default true
- notes: Text

## List Customers

### Endpoint

GET /customers

### Access

public

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

Field modifiers are `required`, `unique`, `generated`, `indexed`, and `default <literal>`. Types are
`String`, `Text`, `Email`, `Int`, `Long`, `Decimal`, `Boolean`, `UUID`, `Date`, `DateTime`.

## indexed

`indexed` asks for a non-unique index. A `unique` column is already indexed by its constraint, so
asking for both is refused rather than silently creating a second structure for the first one's job.

````harpia HRP1004
# Ticket

## Data

- id: UUID generated
- code: String required unique indexed

## List Tickets

### Endpoint

GET /tickets

### Access

public

### Flow

```flow
tickets = list Ticket
return tickets
```

### Output

200 List<Ticket>
````

## Enum

A declared enum is a closed set of names, written `lower_snake_case`.

````harpia
# Ticket

## Enum Status

- open
- awaiting_payment
- closed

## Data

- id: UUID generated
- status: Status required

## Query List Tickets

### Endpoint

GET /tickets

### Access

public

### Flow

```flow
tickets = list Ticket
return tickets
```

### Output

200 List<Ticket>
````

## Value

A value is fields compared by what they hold, not by identity.

````harpia
# Shipment

## Value Address

- street: String required
- city: String required
- postalCode: String required

## Data

- id: UUID generated
- shipTo: Address required

## Query List Shipments

### Endpoint

GET /shipments

### Access

public

### Flow

```flow
shipments = list Shipment
return shipments
```

### Output

200 List<Shipment>
````

## Optional and List fields

`Optional<T>` says the value may be absent; `List<T>` holds many. `Optional<T> required` is a
contradiction and is refused.

````harpia
# Article

## Data

- id: UUID generated
- title: String required
- subtitle: Optional<String>
- tags: List<String>

## Query List Articles

### Endpoint

GET /articles

### Access

public

### Flow

```flow
articles = list Article
return articles
```

### Output

200 List<Article>
````

## Reference

`Reference<Entity>` is typed identity: the foreign key and nothing else. It carries no lifecycle,
so nothing is cascaded and nothing is fetched.

````harpia
<!-- specs/customer.harpia.md -->
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /customers

### Access

public

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>

<!-- specs/invoice.harpia.md -->
# Invoice

## Data

- id: UUID generated
- customer: Reference<Customer> required
- amount: Decimal required

## Query List Invoices

### Endpoint

GET /invoices

### Access

public

### Flow

```flow
invoices = list Invoice
return invoices
```

### Output

200 List<Invoice>
````

## Command and Query

`## Command` writes and is transactional whatever its steps are. `## Query` never writes, and one
that mutates is refused.

````harpia HRP2120
# Customer

## Data

- id: UUID generated
- name: String required

## Query Wipe Customer

### Endpoint

DELETE /customers/{id}

### Access

public

### Flow

```flow
customer = load Customer by id
delete customer
return nothing
```

### Output

204 nothing
````

## An operation without an endpoint

An operation is application behaviour first. Without `### Endpoint` it is a service method and no
controller, no web test and no HTTP dependency is generated for it.

````harpia
# Customer

## Data

- id: UUID generated
- name: String required

## Command Refresh Customer

### Input

- name: String required

### Flow

```flow
validate input
customer = load Customer by id
update customer from input
save customer
return customer
```

### Output

200 Customer
````

## Path parameters

A path takes `{name}` in any segment. The name **is** the mapping: `{tenant}` is filled by the input
called `tenant`, and a parameter with no input behind it is refused.

````harpia HRP2135
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /tenants/{tenant}/customers

### Access

public

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

## Query parameters

`GET` and `DELETE` carry no body, so the inputs the path did not take become query parameters:
this one is `GET /customers?status=...`.

````harpia
# Customer

## Enum Status

- active
- blocked

## Data

- id: UUID generated
- name: String required
- status: Status required indexed

## Query List Customers

### Endpoint

GET /customers

### Access

public

### Input

- status: Status required

### Flow

```flow
customers = list Customer by status
return customers
```

### Output

200 List<Customer>
````

## PATCH

`PUT` states the whole resource, so a field the request leaves out is set to nothing. `PATCH`
states only the changes, so the same omission leaves the stored value alone. A `required` input
contradicts that and is refused.

````harpia
# Customer

## Data

- id: UUID generated
- name: String required
- email: Email required unique

## Command Patch Customer

### Endpoint

PATCH /customers/{id}

### Access

public

### Input

- name: String
- email: Email

### Flow

```flow
customer = load Customer by id
update customer from input
save customer
return customer
```

### Output

200 Customer

### Errors

- not found -> 404
````

## Sorting and pagination

A declared sort refines the stable order instead of replacing it: `id` stays last as the tiebreaker,
so a page is always a slice of one agreed ordering.

````harpia
# Ticket

## Data

- id: UUID generated
- title: String required
- priority: Int required

## Query List Tickets

### Endpoint

GET /tickets

### Access

public

### Input

- page: Int required
- size: Int required

### Flow

```flow
tickets = list Ticket sorted by priority desc and title paged
return tickets
```

### Output

200 Page<Ticket>
````

## require and fail

`require` raises unless a condition holds; `fail` raises when one does. Both name an error declared
under `### Errors`, and both read the operation's input: a guard is about what was asked for, not
about what is already stored.

````harpia
# Payment

## Data

- id: UUID generated
- amount: Decimal required
- currency: String required

## Command Create Payment

### Endpoint

POST /payments

### Access

public

### Input

- amount: Decimal required
- currency: String required

### Flow

```flow
validate input
fail invalid amount when amount <= 0
payment = create Payment from input
save payment
return payment
```

### Output

201 Payment

### Errors

- invalid input -> 400
- invalid amount -> 422
````

## find

`find ... by` answers with at most one row, so the field it searches has to be `unique`. Use
`list ... by` when many rows may match.

````harpia
# Customer

## Data

- id: UUID generated
- email: Email required unique
- name: String required

## Query Find Customer

### Endpoint

GET /customers/by-email/{email}

### Access

public

### Input

- email: Email required

### Flow

```flow
customer = find Customer by email
return customer
```

### Output

200 Customer

### Errors

- not found -> 404
````

## Rules and Invariants

`### Rules` constrains the input of **one** operation and answers 400. `## Invariants` constrains
the **entity** and is checked before every `save`, answering 422: a well-formed request asking for
a state the entity forbids is not invalid input.

````harpia
# Product

## Data

- id: UUID generated
- name: String required
- price: Decimal required

## Invariants

- price > 0

## Command Create Product

### Endpoint

POST /products

### Access

public

### Input

- name: String required
- price: Decimal required

### Rules

- price > 0

### Flow

```flow
validate input
product = create Product from input
save product
return product
```

### Output

201 Product

### Errors

- invalid input -> 400
````

## Access: authenticated

`### Access` says who reaches the endpoint. `authenticated` demands that the request carry an
identity, and says nothing about how it proves one: that is a provider's choice.

````harpia
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /customers

### Access

public

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>

## Command Delete Customer

### Endpoint

DELETE /customers/{id}

### Access

authenticated

### Flow

```flow
customer = load Customer by id
delete customer
return nothing
```

### Output

204 nothing

### Errors

- not found -> 404
````

Each operation becomes its own rule, keyed by method and path, so the `GET` above stays public even
though it shares a path with something protected. Anything nobody declared is denied.

V0 has only `public`:

````harpia v0 HRP4001
# Customer

## Data

- id: UUID generated
- name: String required

## Delete Customer

### Endpoint

DELETE /customers/{id}

### Access

authenticated

### Flow

```flow
customer = load Customer by id
delete customer
return nothing
```

### Output

204 nothing

### Errors

- not found -> 404
````

## Access: role and scope

`role` asks for a kind of person; `scope` asks for a permission. Several are written with `or`, and
the check is "any of them".

````harpia
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /customers

### Access

role admin or auditor

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

A scope comes from a token, so this entry declares `jwt`. With `security.provider: basic` there
would be no scope to carry, the rule would match nothing, and the endpoint would refuse every
request — which is why that combination is `HRP6002` rather than a silent closed door.

````harpia jwt
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /customers

### Access

scope customers:read or customers:admin

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

````harpia HRP6002
# Customer

## Data

- id: UUID generated
- name: String required

## Query List Customers

### Endpoint

GET /customers

### Access

scope customers:read

### Flow

```flow
customers = list Customer
return customers
```

### Output

200 List<Customer>
````

## Logic

Logic is a pure computation. It belongs to the project rather than to an entity, has no effects,
and is the only place an expression can be named and reused.

````harpia v0
# Pricing

## Logic CalculateDiscount

### Input

- total: Decimal
- vip: Boolean

### Output

Decimal

```logic
if vip
    return total * 0.20

if total >= 1000
    return total * 0.10

return 0
```

## Scenario VIP discount

### Given

- total: 100
- vip: true

### When

CalculateDiscount

### Then

- result: 20.00
````

## call a Logic

A flow names a Logic and binds its arguments by name. The result is a value, so it has to be
assigned to something.

````harpia
# Purchase

## Data

- id: UUID generated
- total: Decimal required
- vip: Boolean required

## Logic CalculateDiscount

### Input

- total: Decimal
- vip: Boolean

### Output

Decimal

```logic
if vip
    return total * 0.20

return 0
```

## Scenario No discount without vip

### Given

- total: 100
- vip: false

### When

CalculateDiscount

### Then

- result: 0

## Command Place Purchase

### Endpoint

POST /purchases

### Access

public

### Input

- total: Decimal required
- vip: Boolean required

### Flow

```flow
validate input
discount = call CalculateDiscount(total = total, vip = vip)
purchase = create Purchase from input
save purchase
return purchase
```

### Output

201 Purchase

### Errors

- invalid input -> 400
````

A call that spans several lines means the same thing, which is worth having when the argument list
is long:

```flow
discount = call CalculateDiscount(
    total = total,
    vip = vip
)
```

Dropping the result is refused: the Logic was asked a question and the answer has nowhere to go.

````harpia HRP2144
# Purchase

## Data

- id: UUID generated
- total: Decimal required

## Logic CalculateDiscount

### Input

- total: Decimal

### Output

Decimal

```logic
return total
```

## Scenario Identity

### Given

- total: 100

### When

CalculateDiscount

### Then

- result: 100

## Command Place Purchase

### Endpoint

POST /purchases

### Access

public

### Input

- total: Decimal required

### Flow

```flow
validate input
call CalculateDiscount(total = total)
purchase = create Purchase from input
save purchase
return purchase
```

### Output

201 Purchase

### Errors

- invalid input -> 400
````

## call an Integration operation

The target is dotted: the Integration and the Operation on it. An operation that returns `nothing`
is called without assignment, because there is nothing to assign.

````harpia
# Purchase

## Data

- id: UUID generated
- total: Decimal required

## Integration FraudService

### Operation CheckOrder

#### Input

- total: Decimal required

#### Output

Boolean

### Operation Notify

#### Input

- total: Decimal required

#### Output

nothing

## Command Place Purchase

### Endpoint

POST /purchases

### Access

public

### Input

- total: Decimal required

### Flow

```flow
validate input
approved = call FraudService.CheckOrder(total = total)
call FraudService.Notify(total = total)
purchase = create Purchase from input
save purchase
return purchase
```

### Output

201 Purchase

### Errors

- invalid input -> 400

<!-- bindings/http.harpia.md -->
# HTTP Bindings

## Base URL

https://fraud.example

## Bind FraudService.CheckOrder

### Endpoint

POST /checks

### Request

- input: body

### Response

output: body

## Bind FraudService.Notify

### Endpoint

POST /notifications

### Request

- input: body

### Response

none
````

Calling a port needs a binding that says how it is reached: declaring the port is free, but a call
without one is refused, because the generated code would have nowhere to send the request.

## Auth on an outbound binding

`## Auth` says how this file's calls prove who is calling. `bearer` sends the value in
`Authorization` behind `Bearer `; `api key <Header-Name>` sends it exactly as it was issued.

The credential itself is never written here. It differs per deployment and it is a secret, so the
generated project reads `harpia.integration.<port>.credential` — a placeholder in
`application.yaml`, and a value of its own in the generated test configuration.

````harpia
# Purchase

## Data

- id: UUID generated
- total: Decimal required

## Integration FraudService

### Operation CheckOrder

#### Input

- total: Decimal required

#### Output

Boolean

## Command Place Purchase

### Endpoint

POST /purchases

### Access

public

### Input

- total: Decimal required

### Flow

```flow
validate input
approved = call FraudService.CheckOrder(total = total)
purchase = create Purchase from input
save purchase
return purchase
```

### Output

201 Purchase

### Errors

- invalid input -> 400

<!-- bindings/http.harpia.md -->
# HTTP Bindings

## Base URL

https://fraud.example

## Auth

bearer

## Bind FraudService.CheckOrder

### Endpoint

POST /checks

### Request

- input: body

### Response

output: body
````

A scheme nobody implements is refused where it is written, rather than generating a client that
sends nothing and fails at the other end.

````harpia HRP1202
# Purchase

## Data

- id: UUID generated
- total: Decimal required

## Integration FraudService

### Operation CheckOrder

#### Input

- total: Decimal required

#### Output

Boolean

## Command Place Purchase

### Endpoint

POST /purchases

### Access

public

### Input

- total: Decimal required

### Flow

```flow
validate input
approved = call FraudService.CheckOrder(total = total)
purchase = create Purchase from input
save purchase
return purchase
```

### Output

201 Purchase

### Errors

- invalid input -> 400

<!-- bindings/http.harpia.md -->
# HTTP Bindings

## Base URL

https://fraud.example

## Auth

oauth2

## Bind FraudService.CheckOrder

### Endpoint

POST /checks

### Request

- input: body

### Response

output: body
````

## Integration

An outbound dependency starts as a business port: no HTTP, no library, no environment. Entities and
references do not cross it, because an integration exchanges values with a service that has none of
your tables.

````harpia
# Checkout

## Value FraudResult

- approved: Boolean required
- score: Decimal required

## Integration FraudService

### Operation CheckOrder

#### Input

- orderId: UUID required
- total: Decimal required

#### Output

FraudResult

#### Errors

- RateLimited
- ServiceUnavailable
````

## Event

An event is a fact: it happened, and nobody waits for it, so it has no output and no errors. It
announces **which** record something happened to, so the payload carries `Reference<T>` and not the
row, whose lifetime the reader does not share.

````harpia
# Purchase

## Enum Status

- placed
- cancelled

## Data

- id: UUID generated
- status: Status required
- total: Decimal required

## Event PurchasePlaced

### Payload

- purchase: Reference<Purchase> required
- status: Status required
- total: Decimal required

## Query Get Purchase

### Endpoint

GET /purchases/{id}

### Access

public

### Flow

```flow
purchase = load Purchase by id
return purchase
```

### Output

200 Purchase

### Errors

- not found -> 404
````

Announcing the entity itself is refused:

````harpia HRP2141
# Purchase

## Data

- id: UUID generated
- total: Decimal required

## Event PurchasePlaced

### Payload

- purchase: Purchase required

## Query Get Purchase

### Endpoint

GET /purchases/{id}

### Access

public

### Flow

```flow
purchase = load Purchase by id
return purchase
```

### Output

200 Purchase

### Errors

- not found -> 404
````

## What V0 does not have

`languageVersion: 0` is the MVP grammar: CRUD over one entity, `public` access, the four verbs, and
no declared types. A V1 heading under V0 is refused by name rather than misread as a use case whose
title happens to start with "Command".

````harpia v0 HRP1107
# Customer

## Data

- id: UUID generated
- name: String required

## Command Rename Customer

### Endpoint

PUT /customers/{id}

### Access

public

### Input

- name: String required

### Flow

```flow
validate input
customer = load Customer by id
update customer from input
save customer
return customer
```

### Output

200 Customer
````
