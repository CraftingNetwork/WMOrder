## 1.0.3

- Fixed resource packaging so `plugin.yml` is always copied to the JAR root.
- GitHub Actions now validates the final JAR and uploads only `release/WMOrder.jar`.
- Builds fail before artifact upload when the plugin descriptor is missing or invalid.

# Changelog

## 1.0.0

- Initial original WMOrder implementation.
- Funded buy orders, partial fulfillment, persistent collection, cancellation, expiration, and recovery ledger.
- SQLite and MySQL/MariaDB migrations.
- Paper GUI, commands, notifications, PlaceholderAPI, admin tools, public API, tests, and documentation.
