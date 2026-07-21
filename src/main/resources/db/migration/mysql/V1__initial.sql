CREATE TABLE IF NOT EXISTS wm_schema_version (
  version INT PRIMARY KEY,
  description VARCHAR(255) NOT NULL,
  installed_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_orders (
  id CHAR(36) PRIMARY KEY,
  buyer_uuid CHAR(36) NOT NULL,
  buyer_name VARCHAR(64) NOT NULL,
  item_fingerprint CHAR(64) NOT NULL,
  item_material VARCHAR(64) NOT NULL,
  item_display_name VARCHAR(255) NOT NULL,
  item_blob LONGBLOB NOT NULL,
  requested_quantity BIGINT NOT NULL,
  remaining_quantity BIGINT NOT NULL,
  fulfilled_quantity BIGINT NOT NULL,
  price_per_item DECIMAL(30,8) NOT NULL,
  original_total DECIMAL(30,8) NOT NULL,
  remaining_reserved_balance DECIMAL(30,8) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  category VARCHAR(64),
  server_id VARCHAR(64),
  version BIGINT NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  INDEX idx_wm_orders_status(status),
  INDEX idx_wm_orders_expires(expires_at, status),
  INDEX idx_wm_orders_buyer_status(buyer_uuid, status),
  INDEX idx_wm_orders_fingerprint_status(item_fingerprint, status),
  INDEX idx_wm_orders_created(created_at),
  INDEX idx_wm_orders_remaining(remaining_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_transactions (
  id CHAR(36) PRIMARY KEY,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  order_id CHAR(36),
  player_uuid CHAR(36) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  gross_amount DECIMAL(30,8) NOT NULL,
  fee_amount DECIMAL(30,8) NOT NULL,
  net_amount DECIMAL(30,8) NOT NULL,
  economy_response VARCHAR(512),
  state VARCHAR(32) NOT NULL,
  metadata LONGTEXT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  INDEX idx_wm_transactions_state(state, updated_at),
  INDEX idx_wm_transactions_order(order_id),
  INDEX idx_wm_transactions_player(player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_order_deliveries (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36),
  owner_uuid CHAR(36) NOT NULL,
  delivery_type VARCHAR(32) NOT NULL,
  item_blob LONGBLOB,
  quantity BIGINT NOT NULL DEFAULT 0,
  amount DECIMAL(30,8) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  claim_token CHAR(36),
  transaction_id CHAR(36),
  note VARCHAR(512),
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  INDEX idx_wm_deliveries_owner_status(owner_uuid, status, created_at),
  INDEX idx_wm_deliveries_claim(claim_token),
  INDEX idx_wm_deliveries_transaction(transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_order_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  actor_uuid CHAR(36),
  event_type VARCHAR(64) NOT NULL,
  previous_status VARCHAR(32),
  new_status VARCHAR(32),
  quantity BIGINT NOT NULL DEFAULT 0,
  amount DECIMAL(30,8) NOT NULL DEFAULT 0,
  details LONGTEXT,
  created_at BIGINT NOT NULL,
  INDEX idx_wm_history_order(order_id, created_at),
  INDEX idx_wm_history_actor(actor_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_player_settings (
  player_uuid CHAR(36) PRIMARY KEY,
  notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS wm_admin_audit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_identity VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  order_id CHAR(36),
  target_uuid CHAR(36),
  previous_state VARCHAR(32),
  new_state VARCHAR(32),
  reason VARCHAR(512),
  created_at BIGINT NOT NULL,
  INDEX idx_wm_audit_order(order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
