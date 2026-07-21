package com.wildmare.wmorder.database;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationScriptTest {
    private static final List<String> CRITICAL_TABLES = List.of(
            "wm_orders",
            "wm_order_deliveries",
            "wm_transactions",
            "wm_player_settings",
            "wm_order_history",
            "wm_admin_audit"
    );

    private static final List<String> CRITICAL_COLUMNS = List.of(
            "item_fingerprint",
            "expires_at",
            "version",
            "idempotency_key"
    );

    @Test
    void sqliteAndMysqlCreateAllCriticalTablesAndIndexes() throws Exception {
        for (String family : List.of("sqlite", "mysql")) {
            String path = "/db/migration/" + family + "/V1__initial.sql";

            try (InputStream input = Objects.requireNonNull(
                    getClass().getResourceAsStream(path),
                    "Missing migration resource on the test classpath: " + path
            )) {
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

                assertThat(sql)
                        .as("%s migration should contain CREATE TABLE statements", family)
                        .containsIgnoringCase("CREATE TABLE");

                for (String table : CRITICAL_TABLES) {
                    assertThat(sql)
                            .as("%s migration should create table %s", family, table)
                            .contains(table);
                }

                for (String column : CRITICAL_COLUMNS) {
                    assertThat(sql)
                            .as("%s migration should contain critical column %s", family, column)
                            .contains(column);
                }

                List<String> statements = MigrationRunner.split(sql);
                assertThat(statements)
                        .as("%s migration should split into executable statements", family)
                        .hasSizeGreaterThanOrEqualTo(7)
                        .allSatisfy(statement -> assertThat(statement).isNotBlank());
            }
        }
    }
}
