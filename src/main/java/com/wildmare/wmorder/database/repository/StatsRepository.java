package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.PlayerStatistics;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

public final class StatsRepository {
    private final DatabaseManager database;
    public StatsRepository(DatabaseManager database){this.database=database;}

    public PlayerStatistics player(UUID player){
        try(Connection c=database.connection()){
            long active=scalarLong(c,"SELECT COUNT(*) FROM wm_orders WHERE buyer_uuid=? AND status IN ('ACTIVE','PARTIALLY_FILLED','ADMIN_FROZEN')",player);
            long completed=scalarLong(c,"SELECT COUNT(*) FROM wm_orders WHERE buyer_uuid=? AND status IN ('FILLED','COMPLETED')",player);
            long pending=scalarLong(c,"SELECT COUNT(*) FROM wm_order_deliveries WHERE owner_uuid=? AND status IN ('READY','RECOVERY_PENDING')",player);
            BigDecimal spent=scalarDecimal(c,"SELECT COALESCE(SUM(CAST(gross_amount AS DECIMAL(30,8))),0) FROM wm_transactions WHERE player_uuid=? AND transaction_type='ORDER_DEPOSIT' AND state='COMMITTED'",player);
            BigDecimal earned=scalarDecimal(c,"SELECT COALESCE(SUM(CAST(net_amount AS DECIMAL(30,8))),0) FROM wm_transactions WHERE player_uuid=? AND transaction_type='SELLER_PAYOUT' AND state='COMMITTED'",player);
            BigDecimal highest=scalarDecimal(c,"SELECT COALESCE(MAX(CAST(original_total AS DECIMAL(30,8))),0) FROM wm_orders WHERE buyer_uuid=?",player);
            BigDecimal volume=marketVolume(c);
            return new PlayerStatistics(active,completed,pending,spent,earned,highest,volume);
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public BigDecimal marketVolume(){try(Connection c=database.connection()){return marketVolume(c);}catch(SQLException e){throw Jdbc.error(e);}}
    private BigDecimal marketVolume(Connection c)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(CAST(gross_amount AS DECIMAL(30,8))),0) FROM wm_transactions WHERE transaction_type='SELLER_PAYOUT' AND state='COMMITTED'");ResultSet rs=ps.executeQuery()){return rs.next()?new BigDecimal(rs.getString(1)):BigDecimal.ZERO;}
    }
    private long scalarLong(Connection c,String sql,UUID player)throws SQLException{try(PreparedStatement ps=c.prepareStatement(sql)){Jdbc.uuid(ps,1,player);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0;}}}
    private BigDecimal scalarDecimal(Connection c,String sql,UUID player)throws SQLException{try(PreparedStatement ps=c.prepareStatement(sql)){Jdbc.uuid(ps,1,player);try(ResultSet rs=ps.executeQuery()){String v=rs.next()?rs.getString(1):"0";return v==null?BigDecimal.ZERO:new BigDecimal(v);}}}
}
