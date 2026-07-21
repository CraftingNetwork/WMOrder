package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.SqlDialect;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public final class PlayerSettingsRepository {
    private final DatabaseManager database;
    public PlayerSettingsRepository(DatabaseManager database){this.database=database;}
    public boolean notificationsEnabled(UUID player){
        try(Connection c=database.connection();PreparedStatement ps=c.prepareStatement("SELECT notifications_enabled FROM wm_player_settings WHERE player_uuid=?")){Jdbc.uuid(ps,1,player);try(ResultSet rs=ps.executeQuery()){return !rs.next()||rs.getBoolean(1);}}
        catch(SQLException e){throw Jdbc.error(e);}
    }
    public void setNotifications(UUID player,boolean enabled){
        database.transaction(c->{String sql=database.dialect()== SqlDialect.SQLITE
                ?"INSERT INTO wm_player_settings(player_uuid,notifications_enabled,updated_at) VALUES(?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET notifications_enabled=excluded.notifications_enabled,updated_at=excluded.updated_at"
                :"INSERT INTO wm_player_settings(player_uuid,notifications_enabled,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE notifications_enabled=VALUES(notifications_enabled),updated_at=VALUES(updated_at)";
            try(PreparedStatement ps=c.prepareStatement(sql)){Jdbc.uuid(ps,1,player);ps.setBoolean(2,enabled);ps.setLong(3,Instant.now().toEpochMilli());ps.executeUpdate();}catch(SQLException e){throw Jdbc.error(e);}return null;});
    }
}
