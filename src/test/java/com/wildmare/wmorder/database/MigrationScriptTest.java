package com.wildmare.wmorder.database;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MigrationScriptTest {
    @Test void sqliteAndMysqlCreateAllCriticalTablesAndIndexes() throws Exception {
        for (String family : List.of("sqlite", "mysql")) {
            String path = "/db/migration/" + family + "/V1__initial.sql";
            try (InputStream in = getClass().getResourceAsStream(path)) {
                assertThat(in).as(path).isNotNull();
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String table : List.of("wm_orders", "wm_order_deliveries", "wm_transactions", "wm_player_settings", "wm_order_history", "wm_admin_audit")) {
                    assertThat(sql).containsIgnoringCase("CREATE TABLE").contains(table);
                }
                assertThat(sql).contains("item_fingerprint").contains("expires_at").contains("version").contains("idempotency_key");
                assertThat(MigrationRunner.split(sql)).hasSizeGreaterThanOrEqualTo(7).noneMatch(String::isBlank);
            }
        }
    }
}
