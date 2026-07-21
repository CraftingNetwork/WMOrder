package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.order.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class OrderRepository {
    private final DatabaseManager database;
    public OrderRepository(DatabaseManager database) { this.database = database; }

    public Optional<BuyOrder> find(UUID id) {
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement("SELECT * FROM wm_orders WHERE id=?")) {
            Jdbc.uuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(); }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    Optional<BuyOrder> find(Connection connection, UUID id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM wm_orders WHERE id=?")) {
            Jdbc.uuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(); }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public Optional<UUID> resolveId(String input) {
        if (input == null || input.length() < 8 || input.length() > 36 || !input.matches("[0-9a-fA-F-]+")) return Optional.empty();
        if (input.length() == 36) {
            try { UUID id = UUID.fromString(input); return find(id).isPresent() ? Optional.of(id) : Optional.empty(); }
            catch (IllegalArgumentException ignored) { return Optional.empty(); }
        }
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement("SELECT id FROM wm_orders WHERE id LIKE ? ORDER BY created_at DESC LIMIT 2")) {
            ps.setString(1, input.toLowerCase(Locale.ROOT) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID first = UUID.fromString(rs.getString(1));
                if (rs.next()) return Optional.empty();
                return Optional.of(first);
            }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public long countActive(UUID buyer) {
        try (Connection connection = database.connection(); PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM wm_orders WHERE buyer_uuid=? AND status IN ('ACTIVE','PARTIALLY_FILLED','ADMIN_FROZEN')")) {
            Jdbc.uuid(ps,1,buyer); try (ResultSet rs=ps.executeQuery()) { return rs.next()?rs.getLong(1):0; }
        } catch (SQLException exception) { throw Jdbc.error(exception); }
    }

    public OrderPage query(OrderQuery query) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (!query.statuses().isEmpty()) {
            where.append(" AND status IN (");
            int i=0; for (OrderStatus status:query.statuses()) { if (i++>0) where.append(','); where.append('?'); parameters.add(status.name()); }
            where.append(')');
        }
        if (query.buyerUuid()!=null) { where.append(" AND buyer_uuid=?"); parameters.add(query.buyerUuid().toString()); }
        if (query.category()!=null) { where.append(" AND category=?"); parameters.add(query.category()); }
        if (query.material()!=null) { where.append(" AND item_material=?"); parameters.add(query.material()); }
        if (!query.search().isBlank()) {
            where.append(" AND (LOWER(item_display_name) LIKE ? OR LOWER(item_material) LIKE ? OR LOWER(buyer_name) LIKE ?)");
            String term="%"+query.search().toLowerCase(Locale.ROOT)+"%"; parameters.add(term); parameters.add(term); parameters.add(term);
        }
        if (query.fulfillableOnly()) where.append(" AND remaining_quantity>0 AND expires_at>?");
        if (query.fulfillableOnly()) parameters.add(Instant.now().toEpochMilli());
        String orderBy = switch (query.sort()) {
            case HIGHEST_PRICE -> " ORDER BY CAST(price_per_item AS DECIMAL(30,8)) DESC, created_at DESC";
            case HIGHEST_TOTAL_VALUE -> " ORDER BY (CAST(price_per_item AS DECIMAL(30,8))*remaining_quantity) DESC, created_at DESC";
            case NEWEST -> " ORDER BY created_at DESC";
            case OLDEST -> " ORDER BY created_at ASC";
            case EXPIRING_SOON -> " ORDER BY expires_at ASC";
            case LARGEST_REMAINING -> " ORDER BY remaining_quantity DESC, created_at DESC";
        };
        String sql="SELECT * FROM wm_orders"+where+orderBy+" LIMIT ? OFFSET ?";
        parameters.add(query.pageSize()+1); parameters.add(query.page()*query.pageSize());
        try (Connection connection=database.connection(); PreparedStatement ps=connection.prepareStatement(sql)) {
            for (int i=0;i<parameters.size();i++) ps.setObject(i+1,parameters.get(i));
            try(ResultSet rs=ps.executeQuery()) {
                List<OrderSummary> entries=new ArrayList<>(); while(rs.next()) entries.add(mapSummary(rs));
                boolean next=entries.size()>query.pageSize(); if(next) entries=entries.subList(0,query.pageSize());
                return new OrderPage(entries,query.page(),query.pageSize(),next);
            }
        } catch(SQLException exception){ throw Jdbc.error(exception); }
    }

    public List<BuyOrder> dueExpirations(Instant now, int limit) {
        String sql="SELECT * FROM wm_orders WHERE status IN ('ACTIVE','PARTIALLY_FILLED') AND expires_at<=? ORDER BY expires_at ASC LIMIT ?";
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement(sql)){
            ps.setLong(1,now.toEpochMilli()); ps.setInt(2,limit);
            try(ResultSet rs=ps.executeQuery()){List<BuyOrder> list=new ArrayList<>();while(rs.next())list.add(mapOrder(rs));return list;}
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    void insert(Connection connection, BuyOrder order) {
        String sql="INSERT INTO wm_orders(id,buyer_uuid,buyer_name,item_fingerprint,item_material,item_display_name,item_blob,requested_quantity,remaining_quantity,fulfilled_quantity,price_per_item,original_total,remaining_reserved_balance,created_at,expires_at,updated_at,status,category,server_id,version,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=connection.prepareStatement(sql)){
            int i=1; Jdbc.uuid(ps,i++,order.id()); Jdbc.uuid(ps,i++,order.buyerUuid()); ps.setString(i++,order.buyerName());
            ps.setString(i++,order.itemFingerprint());ps.setString(i++,order.itemMaterial());ps.setString(i++,order.itemDisplayName());ps.setBytes(i++,order.itemBlob());
            ps.setLong(i++,order.requestedQuantity());ps.setLong(i++,order.remainingQuantity());ps.setLong(i++,order.fulfilledQuantity());
            Jdbc.decimal(ps,i++,order.pricePerItem());Jdbc.decimal(ps,i++,order.originalTotal());Jdbc.decimal(ps,i++,order.remainingReservedBalance());
            ps.setLong(i++,order.createdAt().toEpochMilli());ps.setLong(i++,order.expiresAt().toEpochMilli());ps.setLong(i++,order.updatedAt().toEpochMilli());
            ps.setString(i++,order.status().name());ps.setString(i++,order.category());ps.setString(i++,order.serverId());ps.setLong(i++,order.version());ps.setString(i,order.idempotencyKey());
            if(ps.executeUpdate()!=1)throw new DatabaseManager.DatabaseException(new SQLException("Order insert affected no rows"));
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public int cleanupHistory(Instant before, int limit) {
        String sql = database.dialect()==com.wildmare.wmorder.database.SqlDialect.SQLITE
                ? "DELETE FROM wm_order_history WHERE id IN (SELECT id FROM wm_order_history WHERE created_at<? ORDER BY id LIMIT ?)"
                : "DELETE FROM wm_order_history WHERE created_at<? ORDER BY id LIMIT ?";
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement(sql)){
            ps.setLong(1,before.toEpochMilli());ps.setInt(2,limit);return ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    static BuyOrder mapOrder(ResultSet rs)throws SQLException{
        return new BuyOrder(Jdbc.uuid(rs,"id"),Jdbc.uuid(rs,"buyer_uuid"),rs.getString("buyer_name"),rs.getString("item_fingerprint"),
                rs.getString("item_material"),rs.getString("item_display_name"),rs.getBytes("item_blob"),rs.getLong("requested_quantity"),
                rs.getLong("remaining_quantity"),rs.getLong("fulfilled_quantity"),Jdbc.decimal(rs,"price_per_item"),Jdbc.decimal(rs,"original_total"),
                Jdbc.decimal(rs,"remaining_reserved_balance"),Jdbc.instant(rs,"created_at"),Jdbc.instant(rs,"expires_at"),Jdbc.instant(rs,"updated_at"),
                OrderStatus.valueOf(rs.getString("status")),rs.getString("category"),rs.getString("server_id"),rs.getLong("version"),rs.getString("idempotency_key"));
    }
    static OrderSummary mapSummary(ResultSet rs)throws SQLException{
        long remaining=rs.getLong("remaining_quantity"); java.math.BigDecimal price=Jdbc.decimal(rs,"price_per_item");
        return new OrderSummary(Jdbc.uuid(rs,"id"),Jdbc.uuid(rs,"buyer_uuid"),rs.getString("buyer_name"),rs.getString("item_fingerprint"),
                rs.getString("item_material"),rs.getString("item_display_name"),rs.getBytes("item_blob"),remaining,rs.getLong("fulfilled_quantity"),price,
                price.multiply(java.math.BigDecimal.valueOf(remaining)),Jdbc.instant(rs,"created_at"),Jdbc.instant(rs,"expires_at"),
                OrderStatus.valueOf(rs.getString("status")),rs.getString("category"),rs.getLong("version"));
    }
}
