# Product

Product in the catalog.

## Data

- id: UUID generated
- name: String required
- description: String required
- price: Decimal required
- stock: Int required default 0

## Create Product

### Endpoint

POST /products

### Access

public

### Input

- name: String required
- description: String required
- price: Decimal required
- stock: Int required

### Rules

Product name and description are required.

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

## Get Product

### Endpoint

GET /products/{id}

### Access

public

### Flow

```flow
product = load Product by id
return product
```

### Output

200 Product

### Errors

- not found -> 404

## List Products

### Endpoint

GET /products

### Access

public

### Flow

```flow
products = list Product
return products
```

### Output

200 List<Product>

## Update Product

### Endpoint

PUT /products/{id}

### Access

public

### Input

- name: String required
- description: String required
- price: Decimal required
- stock: Int required

### Rules

All fields are required.

### Flow

```flow
validate input
product = load Product by id
update product from input
save product
return product
```

### Output

200 Product

### Errors

- invalid input -> 400
- not found -> 404

## Delete Product

### Endpoint

DELETE /products/{id}

### Access

public

### Flow

```flow
product = load Product by id
delete product
return nothing
```

### Output

204 nothing

### Errors

- not found -> 404
