# WMOrder

WMOrder is an original, transaction-safe buy-order marketplace for Paper servers. Buyers fund orders in advance, sellers deliver matching items into those orders, and all purchased items, refunds, and recoverable payouts are stored in a persistent collection queue.

The project recreates only the general idea of a player-driven buy-order market. It does not contain DonutOrder source, assets, messages, configuration, menu layouts, branding, or proprietary logic.

## Highlights

- Funded buy orders with partial and complete fulfillment.
- `BigDecimal` money calculations with configurable scale and rounding.
- SQLite by default; MySQL/MariaDB through HikariCP.
- Fine-grained per-order guards plus database optimistic locking.
- Idempotency keys and a persistent transaction ledger.
- Durable item/refund/payout collection; no direct offline inventory delivery.
- `MATERIAL_ONLY`, `SIMILAR`, and normalized `EXACT` matching.
- Item fingerprint indexes for fast market lookups.
- Async SQL, pagination, short-lived search caching, bounded caches, and batch expiration.
- Adventure/MiniMessage messages with unparsed player-controlled placeholders.
- Vault economy integration and optional PlaceholderAPI support.
- Configurable GUIs backed by custom inventory holders and session IDs.
- Admin inspection, freeze, cancellation, refund, recovery, cleanup, statistics, and CSV export.
- Public asynchronous API and cancellable lifecycle events.
- Unit and concurrency tests for the core invariants.

## Requirements

- Paper 1.21.x
- Java 21
- Vault
- A Vault-compatible economy plugin
- PlaceholderAPI is optional

Spigot is not a supported target. Folia is not currently declared supported because economy and inventory transaction sequencing is designed around Paper's primary server thread.

## Installation

1. Build or download `WMOrder-1.0.0.jar`.
2. Install Vault and a compatible economy provider.
3. Put the WMOrder JAR in `plugins/`.
4. Start the server once.
5. Review `plugins/WMOrder/config.yml`, `database.yml`, `items.yml`, `gui.yml`, `messages.yml`, and `categories.yml`.
6. Restart after changing database settings. Reload-safe files can be refreshed with `/order admin reload`.

## Building from source

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

The shaded JAR is written to:

```text
target/WMOrder-1.0.0.jar
```

The build uses Maven, Java 21, and the Paper Maven repository. CI is included in `.github/workflows/build.yml`.

## Economy setup

WMOrder discovers the registered Vault economy provider at startup. The plugin disables itself if Vault or an economy provider is unavailable.

Money rules are defined under `economy` in `config.yml`:

- minimum and maximum price per item;
- minimum and maximum order total;
- flat and percentage listing fees;
- creation tax;
- seller tax;
- cancellation fee;
- permission-group tax reductions;
- listing-fee exemptions.

Vault exposes monetary values as `double`, so WMOrder normalizes every boundary value immediately into configured `BigDecimal` scale. Business calculations and persisted values never use floating point.

## Database setup

### SQLite

SQLite is the default. The database is created at `plugins/WMOrder/wmorder.db`. WMOrder enables foreign keys, busy timeout, WAL, and normal synchronous mode by default. SQLite uses one pooled writer connection to reduce lock contention.

### MySQL or MariaDB

Set `type: MYSQL` or `type: MARIADB` in `database.yml`, then configure host, port, database, username, password, and JDBC parameters. WMOrder uses the MariaDB JDBC driver for both modes.

Use a dedicated database user with only the privileges required for the WMOrder schema. Never commit production credentials.

## Order lifecycle

1. The buyer holds a representative item and opens `/order create`.
2. Quantity and price are selected.
3. WMOrder validates item restrictions, limits, price, duration, and balance.
4. A prepared ledger entry is persisted.
5. Vault withdraws the funded amount and fees on the server thread.
6. The order and committed ledger state are inserted transactionally.
7. Sellers browse the order, choose an amount, and confirm.
8. WMOrder reserves the quantity through a version-checked SQL mutation.
9. The seller inventory is revalidated and the exact item plan is applied.
10. Purchased items are stored for the buyer, and seller payout is completed or persisted for recovery.
11. Filled, cancelled, and expired orders leave unused funds and purchased items in collection.
12. Collection claims entries with a unique token before delivering anything, preventing duplicate clicks.

The database and Vault cannot participate in one distributed ACID transaction. WMOrder therefore uses a durable saga: prepared ledger records, idempotency keys, conditional SQL updates, explicit compensation, and persistent recovery states.

## Item matching

`items.yml` controls matching:

- `MATERIAL_ONLY`: only the material must match.
- `SIMILAR`: normalized Bukkit `ItemStack#isSimilar` semantics, ignoring amount.
- `EXACT`: SHA-256 fingerprint of a normalized serialized item.

The normalizer can retain or ignore display name, lore, custom model data, enchantments, attributes, damage, persistent data, and container contents. Exact mode is the safe default. The stored order item is always amount `1`, while quantities are stored separately as `long`.

Restrictions include material lists, custom-model-data blacklist, enchantment restrictions, damage policy, unstackable policy, container policy, nested depth, metadata limits, and maximum serialized size.

## Commands

Player commands:

```text
/order
/order browse
/order create
/order sell [order-id]
/order my
/order collect
/order history
/order search <query>
/order cancel <order-id>
/order info <order-id>
/order notify
/order help
```

Aliases: `/orders`, `/wmorder`, `/buyorders`.

Admin commands:

```text
/order admin reload
/order admin inspect <order-id>
/order admin inspectplayer <player>
/order admin cancel <order-id> [confirm]
/order admin freeze <order-id> [confirm]
/order admin unfreeze <order-id> [confirm]
/order admin refund <order-id> <amount> [confirm]
/order admin delete <order-id> [confirm]
/order admin history <player-uuid>
/order admin migrate
/order admin database
/order admin stats
/order admin cleanup [confirm]
/order admin recover
/order admin export [file.csv]
/order admin debug
```

Dangerous commands use a short-lived confirmation token. See `docs/COMMANDS.md`.

## Permissions

Core permissions are listed in `plugin.yml`, including:

```text
wmorder.use
wmorder.browse
wmorder.create
wmorder.sell
wmorder.collect
wmorder.history
wmorder.cancel
wmorder.search
wmorder.notify
wmorder.bypass.cooldown
wmorder.bypass.limit
wmorder.admin.*
```

Permission-based limit profiles are selected by highest configured priority. See `docs/PERMISSIONS.md`.

## GUI behavior

Menus use a `WMInventoryHolder` containing a random session ID. A click is accepted only when the open inventory, holder, player, session, slot action, and session freshness all match.

The listener cancels inventory clicks and drags, including shift-clicks, number-key swaps, offhand swaps, double-click collection, hotbar replacement, and stale session interactions. Transactions use an additional session `begin()` guard and player transaction gate.

Menu snapshots are loaded asynchronously and rendered once on the server thread. They are refreshed only after mutations, explicit refresh, or controlled navigation; no menu is rebuilt every tick.

## Search and performance

- Pagination is performed in SQL.
- Search, status, fingerprint, buyer, timestamps, expiration, quantity, and recovery fields are indexed.
- Search results are short-lived and bounded.
- Full item blobs are loaded only where order detail or transaction validation needs them.
- Expiration selects only due rows and processes configurable batches.
- SQL work runs on a bounded executor and Hikari pool.
- Slow operations and queue size are visible through admin statistics.
- Placeholder values come from async-refreshed caches and never issue synchronous SQL.

For very large networks, use MariaDB, keep browser pages near 45 entries, size the DB executor below the connection pool, and retain history only as long as operationally necessary.

## Transaction recovery

Ledger states identify prepared, committed, pending, compensated, and review-required operations. Startup recovery inspects unfinished records and stale collection claims. Safe deterministic operations are resumed. Ambiguous economy outcomes are not blindly retried because some Vault providers do not expose transaction IDs; those records remain visible for controlled admin recovery.

Use:

```text
/order admin recover
/order admin stats
/order admin database
```

Review `docs/TRANSACTION_MODEL.md` before manually changing database rows.

## PlaceholderAPI

Available placeholders are served from the async statistics cache:

```text
%wmorder_active_orders%
%wmorder_completed_orders%
%wmorder_pending_collection%
%wmorder_total_spent%
%wmorder_total_earned%
%wmorder_highest_order%
%wmorder_market_volume%
```

## Developer API

Retrieve the service from Bukkit's service manager:

```java
RegisteredServiceProvider<WMOrderApi> registration =
        Bukkit.getServicesManager().getRegistration(WMOrderApi.class);
WMOrderApi api = registration == null ? null : registration.getProvider();
```

Queries return `CompletableFuture`. Lifecycle events run synchronously on the server thread. Pre-events are cancellable. Post-events fire after the documented durable mutation phase. Internal mutable database entities are not exposed.

See `docs/API.md` for a complete listener example and threading notes.

## Backups and migration

Stop the server or use a database-native consistent backup before copying SQLite files. With WAL enabled, include the main database plus `-wal` and `-shm` files unless SQLite has been checkpointed. For MySQL/MariaDB, use a transaction-consistent dump.

Schema changes are versioned in `wm_schema_version`; migrations run before the market becomes ready. Never lower the schema version by hand. See `docs/BACKUP_AND_MIGRATION.md`.

## Troubleshooting

Common startup failures:

- **Vault provider missing:** install Vault and an economy plugin, then restart.
- **Database queue full:** reduce expensive traffic or raise the bounded queue and pool carefully.
- **SQLite busy:** avoid network filesystems, keep WAL enabled, and do not open the live database with a writing desktop tool.
- **Item rejected:** inspect `items.yml` and serialized-size/name/lore/container limits.
- **Payout pending:** restore the economy provider and run `/order admin recover`.
- **Placeholder shows zero:** allow one async refresh cycle and confirm PlaceholderAPI loaded before WMOrder.

More detail is in `docs/TROUBLESHOOTING.md`.

## Security notes

- All user SQL values use prepared statements.
- Player-controlled MiniMessage values are inserted with `Placeholder.unparsed`.
- Quantities use checked validation and `long`; money uses bounded `BigDecimal`.
- Order mutation uses a per-order guard, expected version, status predicate, and unique idempotency key.
- Collection uses claim tokens and state transitions.
- No item or money failure is intentionally discarded; unresolved outcomes receive a persistent record.
- Admin mutations are logged with actor, target, before/after state, timestamp, and reason.

See `docs/SECURITY_AUDIT.md`.

## Project layout

```text
com.wildmare.wmorder
├── api
├── command
├── config
├── database
│   ├── migration
│   ├── model
│   └── repository
├── economy
├── gui
│   ├── input
│   └── session
├── item
├── listener
├── notification
├── order
│   ├── model
│   ├── service
│   ├── transaction
│   └── validation
├── permission
├── placeholder
├── recovery
├── scheduler
├── util
└── WMOrderPlugin.java
```

## License

MIT. See `LICENSE`.
