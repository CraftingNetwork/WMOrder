# Backup and migration guidance

## SQLite

The safest method is a clean server stop followed by copying the database. When WAL is enabled, copying a live database requires the database, `-wal`, and `-shm` files or a proper SQLite backup/checkpoint operation.

## MySQL/MariaDB

Use a transaction-consistent logical dump or storage snapshot. Include all `wm_*` tables. Test restores separately.

## Schema migrations

Migrations run synchronously inside the database executor before WMOrder accepts marketplace operations. Each successful migration is recorded in `wm_schema_version`. Migration SQL is packaged by dialect.

Before upgrading:

1. stop new transactions;
2. take a consistent backup;
3. retain the previous plugin JAR and configuration;
4. start the new version and review migration logs;
5. run `/order admin database` and `/order admin stats`;
6. test create, partial fill, cancellation, and collection with small values.

Never delete recovery or ledger rows merely to make an error disappear.
