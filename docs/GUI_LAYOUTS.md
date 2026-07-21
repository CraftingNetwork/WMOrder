# Text GUI layouts

Slots are zero-based internally. Icons, names, lore, sounds, titles, sizes, and browser item slots are configurable in `gui.yml`.

## Browser (54)

```text
[ order summaries: slots 0–44 ]
[prev] [search] [sort] [filter] [refresh] [my orders] [collection] [history] [next]
```

Each summary shows item, buyer, unit price, remaining/requested quantity, total remaining value, expiration, category, and status. Partially filled orders have distinct status lore.

## Create (27)

```text
[filler ...]
[quantity -] [requested item] [quantity +]
[price input] [cancel] [continue]
```

The confirmation menu shows item, quantity, unit price, gross reserve, listing/creation fees, final deposit, and expiration.

## Fulfill/details (27)

```text
[buyer/item/order summary]
[sell 1] [sell stack] [sell all matching]
[back]
```

The confirmation view displays remaining order amount, seller matching quantity, selected amount, gross payout, tax, net payout, and expiry.

## Collection (54)

Persistent entries render as item, refund, or payout cards. Collection first claims records, simulates capacity, inserts only safe items, finalizes delivered records, and leaves overflow stored.
