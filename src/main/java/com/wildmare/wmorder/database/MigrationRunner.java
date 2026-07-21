package com.wildmare.wmorder.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MigrationRunner {
    private static final int LATEST = 1;
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public MigrationRunner(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void migrate() {
        database.transaction(connection -> {
            ensureVersionTable(connection);
            int current = currentVersion(connection);
            for (int version = current + 1; version <= LATEST; version++) apply(connection, version);
            return null;
        });
    }

    private void ensureVersionTable(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS wm_schema_version (version INTEGER PRIMARY KEY, description VARCHAR(255) NOT NULL, installed_at BIGINT NOT NULL)");
        } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
    }

    private int currentVersion(Connection connection) {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM wm_schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
    }

    private void apply(Connection connection, int version) {
        String family = database.dialect() == SqlDialect.SQLITE ? "sqlite" : "mysql";
        String resource = "db/migration/" + family + "/V" + version + "__initial.sql";
        String script = read(resource);
        List<String> statements = split(script);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) if (!sql.isBlank()) statement.execute(sql);
        } catch (SQLException exception) {
            throw new DatabaseManager.DatabaseException("Migration V" + version + " failed", exception);
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO wm_schema_version(version, description, installed_at) VALUES(?,?,?)")) {
            insert.setInt(1, version);
            insert.setString(2, "initial");
            insert.setLong(3, Instant.now().toEpochMilli());
            insert.executeUpdate();
        } catch (SQLException exception) { throw new DatabaseManager.DatabaseException(exception); }
        plugin.getLogger().info("Applied WMOrder database migration V" + version);
    }

    private String read(String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) throw new IllegalStateException("Missing migration resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    static List<String> split(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--") && !trimmed.startsWith("#")) cleaned.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || cleaned.charAt(i - 1) != '\\')) {
                if (!quoted) { quoted = true; quote = c; }
                else if (quote == c) quoted = false;
            }
            if (c == ';' && !quoted) {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
