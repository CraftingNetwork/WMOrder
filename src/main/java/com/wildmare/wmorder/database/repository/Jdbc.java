package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

final class Jdbc {
    private Jdbc() {}
    static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }
    static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }
    static Instant instant(ResultSet rs, String column) throws SQLException { return Instant.ofEpochMilli(rs.getLong(column)); }
    static void uuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value.toString());
    }
    static void decimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException { ps.setString(index, value.toPlainString()); }
    static DatabaseManager.DatabaseException error(SQLException exception) { return new DatabaseManager.DatabaseException(exception); }
}
