# Configuration guide

WMOrder creates six YAML files. Every file has `config-version`. Invalid enum values and unsafe ranges are rejected or normalized during startup.

## `config.yml`

Contains order durations, expiration batch behavior, retention, transaction caps, bounded executor settings, cache sizes, economy rules, notifications, and permission profiles.

Recommended relationships:

- `database-threads <= pool.maximum-size` for SQL servers;
- SQLite should retain one pooled writer;
- `browser-page-size <= 45` for a 54-slot menu;
- expiration batches should be large enough to catch up but small enough to keep transactions brief;
- keep serialized item and nested-container limits conservative.

## `database.yml`

Database settings require a restart. Passwords are never written to logs. JDBC parameters are configuration, not concatenated user input.

## `messages.yml`

All player-facing messages and help lines. Templates support MiniMessage. Runtime values such as player names, item names, errors, and queries are inserted as unparsed placeholders to prevent tag injection.

## `gui.yml`

Titles, sizes, order slots, icons, names, lore, and sounds. Menu sizes are rounded to valid chest dimensions and clamped to 9–54.

## `categories.yml`

Maps category IDs to display icon, MiniMessage name, and material membership. Unknown or removed categories are displayed safely and can be cleared from the browser filter.

## `items.yml`

Controls matching mode and security restrictions. `EXACT` is recommended for public servers. Ignoring fields weakens identity and should be deliberate.

Reload command reloads messages, GUI, categories, item rules, and reload-safe application settings. It does not replace the database pool, rerun plugin bootstrap, or pretend to be a full server reload.
