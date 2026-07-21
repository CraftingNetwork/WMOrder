# Transaction and recovery model

## Why a saga is required

A Vault economy mutation and a SQL commit cannot share one distributed transaction. WMOrder uses durable intent plus compensation instead of pretending both operations are atomically committed.

## Creation

1. Validate draft and calculate normalized deposit.
2. Insert unique prepared `ORDER_DEPOSIT` ledger entry.
3. Withdraw on the server thread.
4. Insert order and commit ledger state in SQL.
5. If SQL fails after withdrawal, attempt an immediate Vault refund.
6. If refund is uncertain or fails, keep a recoverable ledger/delivery record.

## Fulfillment

1. Acquire the in-process order guard.
2. Load and validate active order.
3. Fire cancellable pre-event on the server thread.
4. Reserve quantity with `WHERE id=? AND version=? AND status IN (...)`.
5. Re-scan the seller inventory and create an exact removal plan.
6. Apply inventory mutation on the server thread.
7. Store buyer delivery, order quantities, escrow decrement, history, and seller ledger.
8. Deposit seller payout; unresolved payout becomes persistent collection/recovery.
9. Invalidate summaries and notify participants.

The SQL reservation prevents two JVM threads or network nodes from selling the same version. The per-order guard reduces local contention but is not the sole correctness mechanism.

## Cancellation and expiration

The status change and unused-reserve delivery are written together. Already fulfilled value is not refunded. Expiration queries only due indexed rows and processes a bounded batch. Restart uses absolute epoch timestamps.

## Collection

Entries move from `READY` to `CLAIMED` with a random claim token. Inventory capacity is simulated, then safe subsets are inserted. Completed entries are finalized by matching token. Remainders are released back to ready state. Stale claims are recovered after restart.

## Ambiguous economy results

A timeout may occur after an economy provider changed a balance. Blind retry could duplicate money. Such outcomes are marked for review rather than automatically repeated unless the operation is provably safe and idempotent.
