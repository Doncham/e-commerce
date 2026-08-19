# Order / Payment State Transition

## Order

| Current | Event | Next |
|---|---|---|
| DRAFT | Payment Prepare | PAYMENT_PENDING |
| PAYMENT_PENDING | Payment Success | PAID |
| PAYMENT_PENDING | Payment Failure | DRAFT |

## Payment

| Current | Event | Next |
|---|---|---|
| READY | Approval Start | CONFIRMING |
| CONFIRMING | PG Success | SUCCESS |
| CONFIRMING | PG Definite Failure | FAILED |
| CONFIRMING | Timeout | CONFIRMING |
| READY | Order Changed | RESET |
