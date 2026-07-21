package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class HistoryRepository {
    public record HistoryLine(long id,UUID orderId,UUID actor,String eventType,String previousStatus,String newStatus,long quantity,BigDecimal amount,String details,Instant createdAt){}
    private final DatabaseManager database;
    public HistoryRepository(DatabaseManager database){this.database=database;}

    void add(Connection connection,UUID orderId,UUID actor,String eventType,String previousStatus,String newStatus,long quantity,BigDecimal amount,String details){
        try(PreparedStatement ps=connection.prepareStatement("INSERT INTO wm_order_history(order_id,actor_uuid,event_type,previous_status,new_status,quantity,amount,details,created_at) VALUES(?,?,?,?,?,?,?,?,?)")){
            Jdbc.uuid(ps,1,orderId);Jdbc.uuid(ps,2,actor);ps.setString(3,eventType);ps.setString(4,previousStatus);ps.setString(5,newStatus);ps.setLong(6,quantity);Jdbc.decimal(ps,7,amount);ps.setString(8,details);ps.setLong(9,Instant.now().toEpochMilli());ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public List<HistoryLine> playerHistory(UUID player,int page,int size){
        String sql="SELECT h.* FROM wm_order_history h LEFT JOIN wm_orders o ON o.id=h.order_id WHERE h.actor_uuid=? OR o.buyer_uuid=? ORDER BY h.created_at DESC LIMIT ? OFFSET ?";
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement(sql)){
            Jdbc.uuid(ps,1,player);Jdbc.uuid(ps,2,player);ps.setInt(3,size);ps.setInt(4,page*size);
            try(ResultSet rs=ps.executeQuery()){List<HistoryLine> list=new ArrayList<>();while(rs.next())list.add(new HistoryLine(rs.getLong("id"),Jdbc.uuid(rs,"order_id"),Jdbc.uuid(rs,"actor_uuid"),rs.getString("event_type"),rs.getString("previous_status"),rs.getString("new_status"),rs.getLong("quantity"),Jdbc.decimal(rs,"amount"),rs.getString("details"),Jdbc.instant(rs,"created_at")));return list;}
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }
}
