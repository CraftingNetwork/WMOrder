package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LedgerRepository {
    private final DatabaseManager database;
    public LedgerRepository(DatabaseManager database) { this.database = database; }

    public boolean insertPrepared(LedgerEntry entry) {
        return database.transaction(connection -> insertPrepared(connection, entry));
    }

    boolean insertPrepared(Connection connection, LedgerEntry entry) {
        String sql = "INSERT INTO wm_transactions(id,idempotency_key,order_id,player_uuid,transaction_type,gross_amount,fee_amount,net_amount,economy_response,state,metadata,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Jdbc.uuid(ps, 1, entry.id());
            ps.setString(2, entry.idempotencyKey());
            Jdbc.uuid(ps, 3, entry.orderId());
            Jdbc.uuid(ps, 4, entry.playerUuid());
            ps.setString(5, entry.type().name());
            Jdbc.decimal(ps, 6, entry.gross()); Jdbc.decimal(ps, 7, entry.fee()); Jdbc.decimal(ps, 8, entry.net());
            ps.setString(9, entry.economyResponse()); ps.setString(10, entry.state().name()); ps.setString(11, entry.metadata());
            ps.setLong(12, entry.createdAt().toEpochMilli()); ps.setLong(13, entry.updatedAt().toEpochMilli());
            return ps.executeUpdate() == 1;
        } catch (SQLIntegrityConstraintViolationException duplicate) { return false; }
        catch (SQLException exception) {
            String state = exception.getSQLState();
            if (state != null && (state.equals("23000") || state.equals("23505"))) return false;
            throw Jdbc.error(exception);
        }
    }

    public void updateState(UUID id, TransactionState state, String economyResponse, String metadata) {
        database.transaction(connection -> { updateState(connection, id, state, economyResponse, metadata); return null; });
    }

    void updateState(Connection connection, UUID id, TransactionState state, String economyResponse, String metadata) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE wm_transactions SET state=?, economy_response=?, metadata=?, updated_at=? WHERE id=?")) {
            ps.setString(1, state.name()); ps.setString(2, economyResponse); ps.setString(3, metadata);
            ps.setLong(4, Instant.now().toEpochMilli()); Jdbc.uuid(ps, 5, id); ps.executeUpdate();
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public Optional<LedgerEntry> findByIdempotency(String key) {
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement("SELECT * FROM wm_transactions WHERE idempotency_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public List<LedgerEntry> unresolved(int limit) {
        String sql = "SELECT * FROM wm_transactions WHERE state IN ('PREPARED','ECONOMY_APPLIED','ITEMS_SECURED','RECOVERY_PENDING','ADMIN_REVIEW') ORDER BY updated_at ASC LIMIT ?";
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<LedgerEntry> entries = new ArrayList<>(); while (rs.next()) entries.add(map(rs)); return entries;
            }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public long unresolvedCount() {
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM wm_transactions WHERE state IN ('PREPARED','ECONOMY_APPLIED','ITEMS_SECURED','RECOVERY_PENDING','ADMIN_REVIEW')"); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    private LedgerEntry map(ResultSet rs) throws SQLException {
        return new LedgerEntry(Jdbc.uuid(rs,"id"), rs.getString("idempotency_key"), Jdbc.uuid(rs,"order_id"), Jdbc.uuid(rs,"player_uuid"),
                TransactionType.valueOf(rs.getString("transaction_type")), Jdbc.decimal(rs,"gross_amount"), Jdbc.decimal(rs,"fee_amount"),
                Jdbc.decimal(rs,"net_amount"), rs.getString("economy_response"), TransactionState.valueOf(rs.getString("state")),
                rs.getString("metadata"), Jdbc.instant(rs,"created_at"), Jdbc.instant(rs,"updated_at"));
    }
}
