# Customer

Represents a customer registered in the platform.

## Data

- id: UUID generated
- name: String required
- email: Email required unique
- active: Boolean required default true

## Create Customer

### Endpoint

POST /customers

### Access

public

### Input

- name: String required
- email: Email required

### Rules

The name and email are required, and each email identifies at most one customer.

### Flow

```flow
validate input
customer = create Customer from input
save customer
return customer
```

### Output

201 Customer

### Errors

- invalid input -> 400
- duplicate email -> 409

## Get Customer

### Endpoint

GET /customers/{id}

### Access

public

### Flow

```flow
customer = load Customer by id
return customer
```

### Output

200 Customer

### Errors

- not found -> 404

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

## Update Customer

### Endpoint

PUT /customers/{id}

### Access

public

### Input

- name: String required
- email: Email required
- active: Boolean required

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

### Errors

- invalid input -> 400
- not found -> 404
- duplicate email -> 409

## Delete Customer

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

### Errors

- not found -> 404
