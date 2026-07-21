package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public final class AdminAuditRepository {
    private final DatabaseManager database;
    public AdminAuditRepository(DatabaseManager database){this.database=database;}
    public void add(String admin,String action,UUID orderId,UUID target,String previous,String next,String reason){
        database.transaction(connection->{add(connection,admin,action,orderId,target,previous,next,reason);return null;});
    }
    void add(Connection connection,String admin,String action,UUID orderId,UUID target,String previous,String next,String reason){
        try(PreparedStatement ps=connection.prepareStatement("INSERT INTO wm_admin_audit(admin_identity,action,order_id,target_uuid,previous_state,new_state,reason,created_at) VALUES(?,?,?,?,?,?,?,?)")){
            ps.setString(1,admin);ps.setString(2,action);Jdbc.uuid(ps,3,orderId);Jdbc.uuid(ps,4,target);ps.setString(5,previous);ps.setString(6,next);ps.setString(7,reason);ps.setLong(8,Instant.now().toEpochMilli());ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }
}
