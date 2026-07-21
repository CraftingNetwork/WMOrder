# Permissions and limits

The complete default nodes are declared in `plugin.yml`. `wmorder.admin` grants the administrative children.

## Limit profiles

Profiles under `limits` in `config.yml` are evaluated by permission and descending priority. The highest matching profile controls:

- maximum active orders;
- maximum quantity per order;
- maximum total value;
- maximum duration;
- creation and fulfillment cooldowns;
- tax reduction percentage;
- listing-fee exemption.

Example:

```yaml
limits:
  elite:
    permission: wmorder.limit.elite
    priority: 20
    max-active-orders: 25
    max-quantity-per-order: 20000
    max-total-value: "100000000.00"
    duration: "21d"
    creation-cooldown-seconds: 1
    fulfillment-cooldown-seconds: 1
    tax-reduction-percent: "50"
    listing-fee-exempt: true
```

`wmorder.bypass.limit` bypasses ordinary count/value/quantity restrictions where the validation service permits it. `wmorder.bypass.cooldown` bypasses player transaction cooldowns. Do not grant bypass nodes broadly.
