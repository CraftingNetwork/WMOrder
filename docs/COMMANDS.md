# Commands

All player messages use MiniMessage templates from `messages.yml`. Commands validate permissions, player/console context, numbers, UUID/order IDs, readiness, cooldowns, and current transaction state. Exceptions are logged; raw stack traces are never sent to players.

## Player commands

| Command | Permission | Description |
|---|---|---|
| `/order`, `/order browse` | `wmorder.browse` | Open paginated active orders. |
| `/order create` | `wmorder.create` | Start a funded order using the main-hand item. |
| `/order sell [id]` | `wmorder.sell` | Open fulfillable orders or a specific order. |
| `/order my` | `wmorder.browse` | Show the player's orders. |
| `/order collect` | `wmorder.collect` | Claim purchased items, refunds, and pending payments. |
| `/order history` | `wmorder.history` | Load historical events asynchronously. |
| `/order search <query>` | `wmorder.search` | Search item display/material/fingerprint summary and buyer name. |
| `/order cancel <id>` | `wmorder.cancel` | Cancel an owned active order and queue unused escrow. |
| `/order info <id>` | `wmorder.browse` | Inspect an order. |
| `/order notify` | `wmorder.notify` | Toggle nonessential notifications. |
| `/order help` | `wmorder.use` | Show help. |

Order IDs can be supplied as full UUIDs or unique short prefixes. Ambiguous prefixes are rejected.

## Admin commands

| Command | Permission | Notes |
|---|---|---|
| `admin reload` | `wmorder.admin.reload` | Reloads only reload-safe configuration. |
| `admin inspect <id>` | `wmorder.admin.inspect` | Opens details or prints to console. |
| `admin inspectplayer <name>` | `wmorder.admin.inspect` | Searches all statuses for the player. |
| `admin cancel <id> [confirm]` | `wmorder.admin.cancel` | Safe cancellation; does not refund committed sales. |
| `admin freeze/unfreeze <id> [confirm]` | `wmorder.admin.freeze` | Blocks or restores transaction eligibility. |
| `admin refund <id> <amount> [confirm]` | `wmorder.admin.refund` | Creates a controlled persistent refund entry. |
| `admin delete <id> [confirm]` | `wmorder.admin.delete` | Terminal-only deletion with audit trail. |
| `admin history <uuid>` | `wmorder.admin.inspect` | Prints transaction history. |
| `admin migrate` | `wmorder.admin.migrate` | Re-runs idempotent pending schema migrations. |
| `admin database` | `wmorder.admin.stats` | Health, dialect, queue, timing. |
| `admin stats` | `wmorder.admin.stats` | Query and recovery metrics. |
| `admin cleanup [confirm]` | `wmorder.admin.stats` | Deletes old history in a bounded batch. |
| `admin recover` | `wmorder.admin.recover` | Inspects and resumes safe recoveries. |
| `admin export [file.csv]` | `wmorder.admin.stats` | Async CSV export into `plugins/WMOrder/exports`. |
| `admin debug` | `wmorder.admin.debug` | Toggles in-memory debug timings. |

Dangerous actions require the same actor to repeat the exact action with `confirm` within 30 seconds.
