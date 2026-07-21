package com.wildmare.wmorder.database;

import com.wildmare.wmorder.config.DatabaseSettings;
import com.wildmare.wmorder.config.PluginSettings;
import com.wildmare.wmorder.util.NamedThreadFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DatabaseManager implements AutoCloseable {
    private final JavaPlugin plugin;
    private final DatabaseSettings settings;
    private final PluginSettings.PerformanceSettings performance;
    private final SqlDialect dialect;
    private final ThreadPoolExecutor executor;
    private final AtomicLong queries = new AtomicLong();
    private final AtomicLong totalQueryNanos = new AtomicLong();
    private volatile HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, DatabaseSettings settings, PluginSettings.PerformanceSettings performance) {
        this.plugin = Objects.requireNonNull(plugin);
        this.settings = Objects.requireNonNull(settings);
        this.performance = Objects.requireNonNull(performance);
        this.dialect = SqlDialect.from(settings.type());
        this.executor = new ThreadPoolExecutor(performance.databaseThreads(), performance.databaseThreads(),
                30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(performance.databaseQueueCapacity()),
                new NamedThreadFactory("WMOrder-DB"), new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(false);
    }

    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("WMOrderPool");
        config.setConnectionTimeout(settings.connectionTimeoutMillis());
        config.setValidationTimeout(settings.validationTimeoutMillis());
        config.setIdleTimeout(settings.idleTimeoutMillis());
        config.setMaxLifetime(settings.maxLifetimeMillis());
        config.setMinimumIdle(settings.minimumIdle());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setAutoCommit(true);
        if (dialect == SqlDialect.SQLITE) {
            File file = new File(plugin.getDataFolder(), settings.sqliteFile());
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
        } else {
            String scheme = settings.type() == DatabaseSettings.DatabaseType.MYSQL ? "jdbc:mariadb" : "jdbc:mariadb";
            String parameters = settings.parameters().isBlank() ? "" : "?" + settings.parameters();
            config.setJdbcUrl(scheme + "://" + settings.host() + ":" + settings.port() + "/" + settings.database() + parameters);
            config.setUsername(settings.username());
            config.setPassword(settings.password());
            config.setDriverClassName("org.mariadb.jdbc.Driver");
        }
        dataSource = new HikariDataSource(config);
        if (dialect == SqlDialect.SQLITE) configureSqlite();
        new MigrationRunner(plugin, this).migrate();
    }

    private void configureSqlite() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=" + Math.max(0, settings.sqliteBusyTimeoutMillis()));
            if (settings.sqliteWal()) statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
    }

    public Connection connection() throws SQLException {
        HikariDataSource source = dataSource;
        if (source == null) throw new SQLException("Database not initialized");
        Connection connection = source.getConnection();
        connection.setNetworkTimeout(Runnable::run, performance.queryTimeoutSeconds() * 1000);
        return connection;
    }

    public <T> T transaction(Function<Connection, T> operation) {
        try (Connection connection = connection()) {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = operation.apply(connection);
                connection.commit();
                return result;
            } catch (Throwable throwable) {
                try { connection.rollback(); } catch (SQLException rollback) { throwable.addSuppressed(rollback); }
                throw throwable instanceof RuntimeException runtime ? runtime : new DatabaseException(throwable);
            } finally {
                connection.setAutoCommit(auto);
            }
        } catch (SQLException exception) {
            throw new DatabaseException(exception);
        }
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                long start = System.nanoTime();
                try { future.complete(supplier.get()); }
                catch (Throwable throwable) { future.completeExceptionally(throwable); }
                finally {
                    long elapsed = System.nanoTime() - start;
                    queries.incrementAndGet();
                    totalQueryNanos.addAndGet(elapsed);
                    if (TimeUnit.NANOSECONDS.toMillis(elapsed) >= performance.slowQueryMillis()) {
                        plugin.getLogger().warning("Slow database operation: " + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms");
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(new DatabaseException("Database queue is full", exception));
        }
        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return supplyAsync(() -> { runnable.run(); return null; });
    }

    public SqlDialect dialect() { return dialect; }
    public long queryCount() { return queries.get(); }
    public double averageQueryMillis() {
        long count = queries.get();
        return count == 0 ? 0 : (totalQueryNanos.get() / 1_000_000.0) / count;
    }
    public int queueSize() { return executor.getQueue().size(); }
    public boolean isHealthy() {
        try (Connection connection = connection()) { return connection.isValid(2); }
        catch (SQLException exception) { return false; }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(performance.shutdownGraceSeconds(), TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        HikariDataSource source = dataSource;
        if (source != null) source.close();
    }

    public static final class DatabaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DatabaseException(Throwable cause) { super(cause); }
        public DatabaseException(String message, Throwable cause) { super(message, cause); }
    }
}
