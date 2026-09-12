# Types

Enums and shared value objects for ecommerce.

## Enum Category

- electronics
- clothing
- books
- home
- sports

## Enum OrderStatus

- pending
- confirmed
- shipped
- delivered
- cancelled

## Enum PaymentMethod

- credit_card
- debit_card
- bank_transfer
- cash

## Enum CustomerTier

- bronze
- silver
- gold
- platinum

## Value Location

- street: String required
- city: String required
- state: String required
- country: String required default "Brazil"

## Value ContactDetails

- email: Email required
- phone: String
- mobile: String required
