package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class DeliveryRepository {
    private final DatabaseManager database;
    public DeliveryRepository(DatabaseManager database){this.database=database;}

    void insert(Connection connection, DeliveryEntry entry){
        String sql="INSERT INTO wm_order_deliveries(id,order_id,owner_uuid,delivery_type,item_blob,quantity,amount,status,claim_token,transaction_id,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=connection.prepareStatement(sql)){
            int i=1;Jdbc.uuid(ps,i++,entry.id());Jdbc.uuid(ps,i++,entry.orderId());Jdbc.uuid(ps,i++,entry.ownerUuid());ps.setString(i++,entry.type().name());
            ps.setBytes(i++,entry.itemBlob());ps.setLong(i++,entry.quantity());Jdbc.decimal(ps,i++,entry.amount());ps.setString(i++,entry.status().name());
            Jdbc.uuid(ps,i++,entry.claimToken());Jdbc.uuid(ps,i++,entry.transactionId());ps.setString(i++,entry.note());ps.setLong(i++,entry.createdAt().toEpochMilli());ps.setLong(i,entry.updatedAt().toEpochMilli());
            ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public List<DeliveryEntry> ready(UUID owner,int limit){
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement("SELECT * FROM wm_order_deliveries WHERE owner_uuid=? AND status='READY' ORDER BY created_at ASC LIMIT ?")){
            Jdbc.uuid(ps,1,owner);ps.setInt(2,limit);try(ResultSet rs=ps.executeQuery()){List<DeliveryEntry> list=new ArrayList<>();while(rs.next())list.add(map(rs));return list;}
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public List<DeliveryEntry> claim(UUID owner,int limit,UUID claimToken){
        return database.transaction(connection->{
            List<DeliveryEntry> selected=new ArrayList<>();
            try(PreparedStatement ps=connection.prepareStatement("SELECT * FROM wm_order_deliveries WHERE owner_uuid=? AND status='READY' ORDER BY created_at ASC LIMIT ?")){
                Jdbc.uuid(ps,1,owner);ps.setInt(2,limit);try(ResultSet rs=ps.executeQuery()){while(rs.next())selected.add(map(rs));}
            }catch(SQLException exception){throw Jdbc.error(exception);}
            List<DeliveryEntry> claimed=new ArrayList<>();
            try(PreparedStatement update=connection.prepareStatement("UPDATE wm_order_deliveries SET status='CLAIMED',claim_token=?,updated_at=? WHERE id=? AND status='READY'")){
                for(DeliveryEntry entry:selected){Jdbc.uuid(update,1,claimToken);update.setLong(2,Instant.now().toEpochMilli());Jdbc.uuid(update,3,entry.id());
                    if(update.executeUpdate()==1)claimed.add(new DeliveryEntry(entry.id(),entry.orderId(),entry.ownerUuid(),entry.type(),entry.itemBlob(),entry.quantity(),entry.amount(),DeliveryStatus.CLAIMED,claimToken,entry.transactionId(),entry.note(),entry.createdAt(),Instant.now()));}
            }catch(SQLException exception){throw Jdbc.error(exception);}
            return claimed;
        });
    }

    public void finalizeClaim(UUID deliveryId,UUID claimToken,long remainingQuantity,BigDecimal remainingAmount,DeliveryStatus status){
        database.transaction(connection->{
            try(PreparedStatement ps=connection.prepareStatement("UPDATE wm_order_deliveries SET quantity=?,amount=?,status=?,claim_token=NULL,updated_at=? WHERE id=? AND claim_token=? AND status='CLAIMED'")){
                ps.setLong(1,remainingQuantity);Jdbc.decimal(ps,2,remainingAmount);ps.setString(3,status.name());ps.setLong(4,Instant.now().toEpochMilli());
                Jdbc.uuid(ps,5,deliveryId);Jdbc.uuid(ps,6,claimToken);if(ps.executeUpdate()!=1)throw new DatabaseManager.DatabaseException(new SQLException("Stale delivery claim"));
            }catch(SQLException exception){throw Jdbc.error(exception);}return null;});
    }

    public void releaseClaim(UUID deliveryId,UUID claimToken,DeliveryStatus status,String note){
        database.transaction(connection->{try(PreparedStatement ps=connection.prepareStatement("UPDATE wm_order_deliveries SET status=?,claim_token=NULL,note=?,updated_at=? WHERE id=? AND claim_token=? AND status='CLAIMED'")){
            ps.setString(1,status.name());ps.setString(2,note);ps.setLong(3,Instant.now().toEpochMilli());Jdbc.uuid(ps,4,deliveryId);Jdbc.uuid(ps,5,claimToken);ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}return null;});
    }

    public long pendingCount(UUID owner){
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement("SELECT COUNT(*) FROM wm_order_deliveries WHERE owner_uuid=? AND status IN ('READY','RECOVERY_PENDING')")){
            Jdbc.uuid(ps,1,owner);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):0;}
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    public int markStaleClaimsForReview(Instant before,int limit){
        String sql=database.dialect()==com.wildmare.wmorder.database.SqlDialect.SQLITE
                ? "UPDATE wm_order_deliveries SET status='ADMIN_REVIEW',note='Stale claim after restart',updated_at=? WHERE id IN (SELECT id FROM wm_order_deliveries WHERE status='CLAIMED' AND updated_at<? LIMIT ?)"
                : "UPDATE wm_order_deliveries SET status='ADMIN_REVIEW',note='Stale claim after restart',updated_at=? WHERE status='CLAIMED' AND updated_at<? LIMIT ?";
        try(Connection connection=database.connection();PreparedStatement ps=connection.prepareStatement(sql)){
            ps.setLong(1,Instant.now().toEpochMilli());ps.setLong(2,before.toEpochMilli());ps.setInt(3,limit);return ps.executeUpdate();
        }catch(SQLException exception){throw Jdbc.error(exception);}
    }

    private DeliveryEntry map(ResultSet rs)throws SQLException{
        return new DeliveryEntry(Jdbc.uuid(rs,"id"),Jdbc.uuid(rs,"order_id"),Jdbc.uuid(rs,"owner_uuid"),DeliveryType.valueOf(rs.getString("delivery_type")),
                rs.getBytes("item_blob"),rs.getLong("quantity"),Jdbc.decimal(rs,"amount"),DeliveryStatus.valueOf(rs.getString("status")),Jdbc.uuid(rs,"claim_token"),
                Jdbc.uuid(rs,"transaction_id"),rs.getString("note"),Jdbc.instant(rs,"created_at"),Jdbc.instant(rs,"updated_at"));
    }
}
