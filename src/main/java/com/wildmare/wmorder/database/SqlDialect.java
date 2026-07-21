package com.wildmare.wmorder.database;

import com.wildmare.wmorder.config.DatabaseSettings;

public enum SqlDialect {
    SQLITE,
    MYSQL;

    public static SqlDialect from(DatabaseSettings.DatabaseType type) {
        return type == DatabaseSettings.DatabaseType.SQLITE ? SQLITE : MYSQL;
    }
}
