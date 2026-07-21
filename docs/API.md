# Developer API and events

## Obtaining the API

```java
RegisteredServiceProvider<WMOrderApi> registration =
        Bukkit.getServicesManager().getRegistration(WMOrderApi.class);
if (registration == null) return;
WMOrderApi api = registration.getProvider();

api.queryOrders(OrderQuery.browser(0, 45)).thenAccept(page -> {
    // This callback may be off-thread. Marshal Bukkit work to the server thread.
    getServer().getScheduler().runTask(this, () ->
            getLogger().info("Active summaries: " + page.entries().size()));
});
```

The public API exposes immutable records or defensive copies. Full item blobs returned by `BuyOrder` are copied on construction and access.

## Event example

```java
public final class MarketListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onCreate(OrderCreateEvent event) {
        // Synchronous server-thread pre-event. Bukkit API is safe here.
        if (event.draft().quantity() > 100_000L) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFulfilled(OrderFulfilledEvent event) {
        // Synchronous post-event after the durable order mutation phase.
        getLogger().info("Order " + event.orderId() + " sold " + event.quantity());
    }
}
```

## Threading contract

- Service query futures normally complete on the bounded database executor.
- Player inventory and Vault economy phases are marshalled to the server thread.
- All lifecycle events are fired synchronously on the server thread.
- Pre-events are cancellable and run before their corresponding sensitive mutation.
- Post-events run after the documented durable phase; listeners must not mutate internal entities.
- Event handlers must not block on database futures.

Events included: create/created, fulfill/fulfilled, cancel/cancelled, expire/expired, collect/collected.
