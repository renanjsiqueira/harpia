# PurchaseOrder

A customer purchase order.

## Data

- id: UUID generated
- customerId: Reference<Customer> required
- orderNumber: String required unique
- total: Decimal required default 0
- status: String required default "pending"

## Create PurchaseOrder

### Endpoint

POST /purchase-orders

### Access

public

### Input

- customerId: Reference<Customer> required
- orderNumber: String required

### Rules

The customerId and orderNumber are required, and each orderNumber identifies at most one purchase order.

### Flow

```flow
validate input
po = create PurchaseOrder from input
save po
return po
```

### Output

201 PurchaseOrder

### Errors

- invalid input -> 400
- duplicate orderNumber -> 409

## Get PurchaseOrder

### Endpoint

GET /purchase-orders/{id}

### Access

public

### Flow

```flow
po = load PurchaseOrder by id
return po
```

### Output

200 PurchaseOrder

### Errors

- not found -> 404

## List PurchaseOrders

### Endpoint

GET /purchase-orders

### Access

public

### Flow

```flow
pos = list PurchaseOrder
return pos
```

### Output

200 List<PurchaseOrder>

## Update PurchaseOrder

### Endpoint

PUT /purchase-orders/{id}

### Access

public

### Input

- status: String required

### Rules

Status is required.

### Flow

```flow
validate input
po = load PurchaseOrder by id
update po from input
save po
return po
```

### Output

200 PurchaseOrder

### Errors

- invalid input -> 400
- not found -> 404

## Delete PurchaseOrder

### Endpoint

DELETE /purchase-orders/{id}

### Access

public

### Flow

```flow
po = load PurchaseOrder by id
delete po
return nothing
```

### Output

204 nothing

### Errors

- not found -> 404
