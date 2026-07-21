# Database design

## Tables

- `wm_orders`: domain state, escrow balance, timestamps, status, fingerprint, and optimistic version.
- `wm_order_deliveries`: persistent collection entries and claim tokens.
- `wm_transactions`: economy ledger and recovery state.
- `wm_player_settings`: notification preferences.
- `wm_order_history`: append-oriented lifecycle history.
- `wm_admin_audit`: administrative mutation before/after records.
- `wm_schema_version`: migration history.

## Important indexes

Indexes cover active status, due expiration, buyer/status, fingerprint/status, creation time, remaining quantity, delivery owner/status, transaction recovery state, and idempotency keys.

## SQL rules

Repositories use prepared statements for values. Dynamic sort clauses are selected only from internal enums. Mutations use explicit transactions. Fulfillment and status changes include expected `version` and allowed-status predicates. Unique idempotency constraints prevent duplicate order deposits and payouts.

SQLite enables WAL, foreign keys, busy timeout, and a single writer pool. MySQL/MariaDB use HikariCP with configurable timeouts and pool bounds.

## Example schema

See:

```text
src/main/resources/db/migration/sqlite/V1__initial.sql
src/main/resources/db/migration/mysql/V1__initial.sql
```
