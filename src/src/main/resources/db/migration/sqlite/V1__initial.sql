CREATE TABLE IF NOT EXISTS wm_schema_version (
  version INTEGER PRIMARY KEY,
  description TEXT NOT NULL,
  installed_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS wm_orders (
  id TEXT PRIMARY KEY,
  buyer_uuid TEXT NOT NULL,
  buyer_name TEXT NOT NULL,
  item_fingerprint TEXT NOT NULL,
  item_material TEXT NOT NULL,
  item_display_name TEXT NOT NULL,
  item_blob BLOB NOT NULL,
  requested_quantity BIGINT NOT NULL,
  remaining_quantity BIGINT NOT NULL,
  fulfilled_quantity BIGINT NOT NULL,
  price_per_item TEXT NOT NULL,
  original_total TEXT NOT NULL,
  remaining_reserved_balance TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  status TEXT NOT NULL,
  category TEXT,
  server_id TEXT,
  version BIGINT NOT NULL DEFAULT 0,
  idempotency_key TEXT NOT NULL UNIQUE
);
CREATE INDEX IF NOT EXISTS idx_wm_orders_status ON wm_orders(status);
CREATE INDEX IF NOT EXISTS idx_wm_orders_expires ON wm_orders(expires_at, status);
CREATE INDEX IF NOT EXISTS idx_wm_orders_buyer_status ON wm_orders(buyer_uuid, status);
CREATE INDEX IF NOT EXISTS idx_wm_orders_fingerprint_status ON wm_orders(item_fingerprint, status);
CREATE INDEX IF NOT EXISTS idx_wm_orders_created ON wm_orders(created_at);
CREATE INDEX IF NOT EXISTS idx_wm_orders_remaining ON wm_orders(remaining_quantity);

CREATE TABLE IF NOT EXISTS wm_transactions (
  id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  order_id TEXT,
  player_uuid TEXT NOT NULL,
  transaction_type TEXT NOT NULL,
  gross_amount TEXT NOT NULL,
  fee_amount TEXT NOT NULL,
  net_amount TEXT NOT NULL,
  economy_response TEXT,
  state TEXT NOT NULL,
  metadata TEXT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wm_transactions_state ON wm_transactions(state, updated_at);
CREATE INDEX IF NOT EXISTS idx_wm_transactions_order ON wm_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_wm_transactions_player ON wm_transactions(player_uuid, created_at);

CREATE TABLE IF NOT EXISTS wm_order_deliveries (
  id TEXT PRIMARY KEY,
  order_id TEXT,
  owner_uuid TEXT NOT NULL,
  delivery_type TEXT NOT NULL,
  item_blob BLOB,
  quantity BIGINT NOT NULL DEFAULT 0,
  amount TEXT NOT NULL DEFAULT '0',
  status TEXT NOT NULL,
  claim_token TEXT,
  transaction_id TEXT,
  note TEXT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wm_deliveries_owner_status ON wm_order_deliveries(owner_uuid, status, created_at);
CREATE INDEX IF NOT EXISTS idx_wm_deliveries_claim ON wm_order_deliveries(claim_token);
CREATE INDEX IF NOT EXISTS idx_wm_deliveries_transaction ON wm_order_deliveries(transaction_id);

CREATE TABLE IF NOT EXISTS wm_order_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL,
  actor_uuid TEXT,
  event_type TEXT NOT NULL,
  previous_status TEXT,
  new_status TEXT,
  quantity BIGINT NOT NULL DEFAULT 0,
  amount TEXT NOT NULL DEFAULT '0',
  details TEXT,
  created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wm_history_order ON wm_order_history(order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wm_history_actor ON wm_order_history(actor_uuid, created_at);

CREATE TABLE IF NOT EXISTS wm_player_settings (
  player_uuid TEXT PRIMARY KEY,
  notifications_enabled INTEGER NOT NULL DEFAULT 1,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS wm_admin_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  admin_identity TEXT NOT NULL,
  action TEXT NOT NULL,
  order_id TEXT,
  target_uuid TEXT,
  previous_state TEXT,
  new_state TEXT,
  reason TEXT,
  created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wm_audit_order ON wm_admin_audit(order_id, created_at);
