package com.wildmare.wmorder.config;

public record DatabaseSettings(DatabaseType type, String sqliteFile, boolean sqliteWal, int sqliteBusyTimeoutMillis,
                               String host, int port, String database, String username, String password,
                               String parameters, int maximumPoolSize, int minimumIdle,
                               long connectionTimeoutMillis, long validationTimeoutMillis,
                               long idleTimeoutMillis, long maxLifetimeMillis) {
    public enum DatabaseType { SQLITE, MYSQL, MARIADB }
}
