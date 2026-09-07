# Pricing

Business computation module. It declares no entity: pricing rules are pure functions, so they
live in Logic and never touch the database.

## Logic CalculateDiscount

Returns the discount amount for an order total.

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

## Logic CalculateTotal

Applies the discount, caps it at 15% of the subtotal and adds shipping.

### Input

- subtotal: Decimal
- vip: Boolean
- shipping: Decimal

### Output

Decimal

```logic
discount = CalculateDiscount(total = subtotal, vip = vip)
capped = min(discount, subtotal * 0.15)

return subtotal - capped + shipping
```

## Scenario VIP discount

A VIP gets twenty percent whatever the total is.

### Given

- total: 100
- vip: true

### When

CalculateDiscount

### Then

- result: 20.00

## Scenario Large order discount

Ten percent starts exactly at one thousand.

### Given

- total: 1000
- vip: false

### When

CalculateDiscount

### Then

- result: 100.00

## Scenario Small order gets nothing

### Given

- total: 999.99
- vip: false

### When

CalculateDiscount

### Then

- result: 0

## Scenario Capped total

The discount is capped at fifteen percent of the subtotal, then shipping is added.

### Given

- subtotal: 100
- vip: true
- shipping: 10

### When

CalculateTotal

### Then

- result: 95.00
