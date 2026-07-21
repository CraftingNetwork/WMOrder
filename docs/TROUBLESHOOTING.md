# Troubleshooting

## Plugin disables at startup

Check for Java 21, Paper 1.21.x, Vault, and an active economy provider. Then inspect the first WMOrder error, not only the final disable message.

## Database connection failure

Verify credentials, host reachability, grants, and JDBC parameters. WMOrder never logs the configured password. For SQLite, verify that the plugin directory is writable and not on a network filesystem.

## `Database queue is full`

This is intentional backpressure. Investigate expensive searches, database latency, connection-pool starvation, or overaggressive expiration batches before raising limits.

## Order accepts no items

Compare `matching-mode` and ignored fields. In exact mode, renamed, damaged, enchanted, custom-model, PDC, potion, trim, container, and other serialized differences remain significant unless configured otherwise.

## Seller items were accepted but payment is pending

The durable item/order mutation succeeded while Vault payment did not. Restore the economy provider and run `/order admin recover`. Do not manually duplicate the payout.

## Collection inventory is full

Only fitting stacks are delivered. Remaining quantities stay in collection. Item dropping is intentionally not the default recovery path.

## Expiration appears wrong

WMOrder stores absolute UTC epoch timestamps. Verify the host clock and avoid changing time backward. GUI countdowns are derived from server timestamps and survive restart.

## Placeholder stays at zero

PlaceholderAPI values use async caches. Confirm PlaceholderAPI loaded and allow a refresh cycle. Placeholder requests deliberately never query SQL synchronously.
