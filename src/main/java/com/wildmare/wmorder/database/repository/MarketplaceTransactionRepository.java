package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;
import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.util.OperationResult;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class MarketplaceTransactionRepository {
    private final DatabaseManager database;
    private final OrderRepository orders;
    private final LedgerRepository ledger;
    private final DeliveryRepository deliveries;
    private final HistoryRepository history;
    private final AdminAuditRepository audit;

    public MarketplaceTransactionRepository(DatabaseManager database, OrderRepository orders, LedgerRepository ledger,
                                            DeliveryRepository deliveries, HistoryRepository history, AdminAuditRepository audit) {
        this.database = database; this.orders = orders; this.ledger = ledger; this.deliveries = deliveries;
        this.history = history; this.audit = audit;
    }

    public void commitCreatedOrder(BuyOrder order, UUID ledgerId, String economyResponse) {
        database.transaction(connection -> {
            orders.insert(connection, order);
            ledger.updateState(connection, ledgerId, TransactionState.COMMITTED, economyResponse, "order-created");
            history.add(connection, order.id(), order.buyerUuid(), "ORDER_CREATED", null, order.status().name(),
                    order.requestedQuantity(), order.originalTotal(), null);
            return null;
        });
    }

    public OperationResult<OrderReservation> reserveFulfillment(UUID orderId, UUID sellerId, long requestedQuantity,
                                                                MoneyBreakdown payout, UUID transactionId,
                                                                String idempotencyKey, Instant now) {
        return database.transaction(connection -> {
            Optional<BuyOrder> optional = orders.find(connection, orderId);
            if (optional.isEmpty()) return OperationResult.failure("order_not_found", "Order not found");
            BuyOrder order = optional.get();
            if (!order.status().acceptsFulfillment()) return OperationResult.failure("order_inactive", order.status().name());
            if (!order.expiresAt().isAfter(now)) return OperationResult.failure("order_expired", "Order has expired");
            if (order.buyerUuid().equals(sellerId)) return OperationResult.failure("self_fulfillment", "Buyer cannot sell to own order");
            long quantity = Math.min(requestedQuantity, order.remainingQuantity());
            if (quantity <= 0) return OperationResult.failure("nothing_remaining", "No remaining quantity");
            BigDecimal expectedGross = order.pricePerItem().multiply(BigDecimal.valueOf(quantity));
            if (expectedGross.compareTo(payout.gross()) != 0) return OperationResult.failure("price_changed", "Payout does not match order price");

            LedgerEntry entry = new LedgerEntry(transactionId, idempotencyKey, order.id(), sellerId,
                    TransactionType.SELLER_PAYOUT, payout.gross(), payout.totalFee(), payout.netOrDeposit(),
                    null, TransactionState.PREPARED, "fulfillment-reservation", now, now);
            if (!ledger.insertPrepared(connection, entry)) return OperationResult.failure("duplicate_transaction", "Duplicate fulfillment request");

            String sql = "UPDATE wm_orders SET remaining_quantity=remaining_quantity-?, fulfilled_quantity=fulfilled_quantity+?, " +
                    "remaining_reserved_balance=CAST(remaining_reserved_balance AS DECIMAL(30,8))-CAST(? AS DECIMAL(30,8)), " +
                    "status=CASE WHEN remaining_quantity-?=0 THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END, updated_at=?, version=version+1 " +
                    "WHERE id=? AND version=? AND status IN ('ACTIVE','PARTIALLY_FILLED') AND remaining_quantity>=? " +
                    "AND CAST(remaining_reserved_balance AS DECIMAL(30,8))>=CAST(? AS DECIMAL(30,8))";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i=1; ps.setLong(i++, quantity); ps.setLong(i++, quantity); Jdbc.decimal(ps,i++,payout.gross());
                ps.setLong(i++,quantity); ps.setLong(i++,now.toEpochMilli()); Jdbc.uuid(ps,i++,order.id()); ps.setLong(i++,order.version());
                ps.setLong(i++,quantity); Jdbc.decimal(ps,i,payout.gross());
                if (ps.executeUpdate()!=1) throw new DatabaseManager.DatabaseException(new SQLException("Optimistic lock conflict"));
            } catch (SQLException exception) { throw Jdbc.error(exception); }
            boolean filled = order.remainingQuantity() == quantity;
            history.add(connection, order.id(), sellerId, "FULFILLMENT_RESERVED", order.status().name(),
                    filled ? OrderStatus.FILLED.name() : OrderStatus.PARTIALLY_FILLED.name(), quantity, payout.gross(), transactionId.toString());
            return OperationResult.success(new OrderReservation(transactionId, order.id(), order.buyerUuid(), sellerId,
                    order.itemBlob(), order.itemFingerprint(), order.itemDisplayName(), quantity, payout.gross(), payout.totalFee(),
                    payout.netOrDeposit(), order.status(), order.version(), order.version()+1, filled));
        });
    }

    public void securePurchasedItems(OrderReservation reservation, byte[] serializedItem) {
        database.transaction(connection -> {
            Instant now=Instant.now();
            DeliveryEntry delivery = new DeliveryEntry(UUID.randomUUID(), reservation.orderId(), reservation.buyerUuid(),
                    DeliveryType.PURCHASED_ITEM, serializedItem, reservation.quantity(), BigDecimal.ZERO, DeliveryStatus.READY,
                    null, reservation.transactionId(), "Purchased from seller " + reservation.sellerUuid(), now, now);
            deliveries.insert(connection, delivery);
            ledger.updateState(connection, reservation.transactionId(), TransactionState.ITEMS_SECURED, null, "buyer-delivery-ready");
            history.add(connection,reservation.orderId(),reservation.sellerUuid(),"ITEMS_SECURED",null,null,reservation.quantity(),reservation.gross(),reservation.transactionId().toString());
            return null;
        });
    }

    public boolean rollbackReservation(OrderReservation reservation, String reason) {
        return database.transaction(connection -> {
            String newStatus = reservation.previousStatus()==OrderStatus.PARTIALLY_FILLED ? OrderStatus.PARTIALLY_FILLED.name() : OrderStatus.ACTIVE.name();
            String sql="UPDATE wm_orders SET remaining_quantity=remaining_quantity+?,fulfilled_quantity=fulfilled_quantity-?,"+
                    "remaining_reserved_balance=CAST(remaining_reserved_balance AS DECIMAL(30,8))+CAST(? AS DECIMAL(30,8)),status=?,updated_at=?,version=version+1 "+
                    "WHERE id=? AND version=? AND fulfilled_quantity>=?";
            try(PreparedStatement ps=connection.prepareStatement(sql)){
                int i=1;ps.setLong(i++,reservation.quantity());ps.setLong(i++,reservation.quantity());Jdbc.decimal(ps,i++,reservation.gross());
                ps.setString(i++,newStatus);ps.setLong(i++,Instant.now().toEpochMilli());Jdbc.uuid(ps,i++,reservation.orderId());ps.setLong(i++,reservation.newVersion());ps.setLong(i,reservation.quantity());
                if(ps.executeUpdate()!=1){ledger.updateState(connection,reservation.transactionId(),TransactionState.ADMIN_REVIEW,null,"rollback-conflict: "+reason);return false;}
            }catch(SQLException exception){throw Jdbc.error(exception);}
            ledger.updateState(connection,reservation.transactionId(),TransactionState.COMPENSATED,null,"reservation-rolled-back: "+reason);
            history.add(connection,reservation.orderId(),reservation.sellerUuid(),"FULFILLMENT_ROLLED_BACK",null,newStatus,reservation.quantity(),reservation.gross(),reason);
            return true;
        });
    }

    public void completeSellerPayout(OrderReservation reservation,String economyResponse){
        database.transaction(connection->{ledger.updateState(connection,reservation.transactionId(),TransactionState.COMMITTED,economyResponse,"seller-paid");
            history.add(connection,reservation.orderId(),reservation.sellerUuid(),"SELLER_PAID",null,null,reservation.quantity(),reservation.net(),economyResponse);return null;});
    }

    public void queueSellerPayout(OrderReservation reservation,String economyResponse){
        database.transaction(connection->{Instant now=Instant.now();
            DeliveryEntry payment=new DeliveryEntry(UUID.randomUUID(),reservation.orderId(),reservation.sellerUuid(),DeliveryType.SELLER_PAYMENT,null,0,reservation.net(),DeliveryStatus.READY,null,reservation.transactionId(),"Vault payout failed; collect or recover",now,now);
            deliveries.insert(connection,payment);ledger.updateState(connection,reservation.transactionId(),TransactionState.RECOVERY_PENDING,economyResponse,"seller-payment-delivery");
            history.add(connection,reservation.orderId(),reservation.sellerUuid(),"SELLER_PAYMENT_QUEUED",null,null,0,reservation.net(),economyResponse);return null;});
    }

    public OperationResult<BigDecimal> cancel(UUID orderId, UUID actor, boolean admin, MoneyBreakdown refund, String reason) {
        return database.transaction(connection -> {
            Optional<BuyOrder> optional=orders.find(connection,orderId);
            if(optional.isEmpty())return OperationResult.failure("order_not_found","Order not found");
            BuyOrder order=optional.get();
            if(!admin&&!order.buyerUuid().equals(actor))return OperationResult.failure("not_owner","Not the order owner");
            if(!order.status().cancellable()&&order.status()!=OrderStatus.ADMIN_FROZEN)return OperationResult.failure("not_cancellable",order.status().name());
            if(refund.gross().compareTo(order.remainingReservedBalance())!=0)return OperationResult.failure("refund_changed","Reserved balance changed");
            UUID tx=UUID.randomUUID();Instant now=Instant.now();
            LedgerEntry entry=new LedgerEntry(tx,"cancel:"+order.id()+":"+order.version(),order.id(),order.buyerUuid(),TransactionType.BUYER_REFUND,refund.gross(),refund.totalFee(),refund.netOrDeposit(),null,TransactionState.ITEMS_SECURED,"refund-delivery",now,now);
            if(!ledger.insertPrepared(connection,entry))return OperationResult.failure("duplicate_transaction","Cancellation already processed");
            String sql="UPDATE wm_orders SET remaining_quantity=0,remaining_reserved_balance='0',status='CANCELLED',updated_at=?,version=version+1 WHERE id=? AND version=? AND status IN ('ACTIVE','PARTIALLY_FILLED','ADMIN_FROZEN')";
            try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setLong(1,now.toEpochMilli());Jdbc.uuid(ps,2,order.id());ps.setLong(3,order.version());if(ps.executeUpdate()!=1)throw new DatabaseManager.DatabaseException(new SQLException("Cancellation conflict"));}
            catch(SQLException exception){throw Jdbc.error(exception);}
            if(refund.netOrDeposit().signum()>0){deliveries.insert(connection,new DeliveryEntry(UUID.randomUUID(),order.id(),order.buyerUuid(),DeliveryType.CANCELLED_REFUND,null,0,refund.netOrDeposit(),DeliveryStatus.READY,null,tx,reason,now,now));}
            history.add(connection,order.id(),actor,"ORDER_CANCELLED",order.status().name(),OrderStatus.CANCELLED.name(),order.remainingQuantity(),refund.netOrDeposit(),reason);
            if(admin)audit.add(connection,actor==null?"CONSOLE":actor.toString(),"CANCEL",order.id(),order.buyerUuid(),order.status().name(),OrderStatus.CANCELLED.name(),reason);
            return OperationResult.success(refund.netOrDeposit());
        });
    }

    public OperationResult<BigDecimal> expire(UUID orderId, long expectedVersion, Instant now) {
        return database.transaction(connection -> {
            Optional<BuyOrder> optional=orders.find(connection,orderId);if(optional.isEmpty())return OperationResult.failure("order_not_found","not found");
            BuyOrder order=optional.get();if(!order.status().acceptsFulfillment()||order.version()!=expectedVersion||order.expiresAt().isAfter(now))return OperationResult.failure("stale","Order changed");
            BigDecimal refund=order.remainingReservedBalance();UUID tx=UUID.randomUUID();
            LedgerEntry entry=new LedgerEntry(tx,"expire:"+order.id()+":"+order.version(),order.id(),order.buyerUuid(),TransactionType.BUYER_REFUND,refund,BigDecimal.ZERO,refund,null,TransactionState.ITEMS_SECURED,"expiration-refund",now,now);
            if(!ledger.insertPrepared(connection,entry))return OperationResult.failure("duplicate_transaction","Expiration already processed");
            try(PreparedStatement ps=connection.prepareStatement("UPDATE wm_orders SET remaining_quantity=0,remaining_reserved_balance='0',status='EXPIRED',updated_at=?,version=version+1 WHERE id=? AND version=? AND status IN ('ACTIVE','PARTIALLY_FILLED') AND expires_at<=?")){
                ps.setLong(1,now.toEpochMilli());Jdbc.uuid(ps,2,order.id());ps.setLong(3,order.version());ps.setLong(4,now.toEpochMilli());if(ps.executeUpdate()!=1)throw new DatabaseManager.DatabaseException(new SQLException("Expiration conflict"));
            }catch(SQLException exception){throw Jdbc.error(exception);}
            if(refund.signum()>0)deliveries.insert(connection,new DeliveryEntry(UUID.randomUUID(),order.id(),order.buyerUuid(),DeliveryType.EXPIRED_REFUND,null,0,refund,DeliveryStatus.READY,null,tx,"Order expired",now,now));
            history.add(connection,order.id(),null,"ORDER_EXPIRED",order.status().name(),OrderStatus.EXPIRED.name(),order.remainingQuantity(),refund,null);
            return OperationResult.success(refund);
        });
    }


    public void queueCreationRefund(UUID ledgerId, UUID player, BigDecimal amount, String economyResponse, String reason) {
        database.transaction(connection -> {
            Instant now = Instant.now();
            deliveries.insert(connection, new DeliveryEntry(UUID.randomUUID(), null, player, DeliveryType.BUYER_REFUND,
                    null, 0, amount, DeliveryStatus.READY, null, ledgerId, reason, now, now));
            ledger.updateState(connection, ledgerId, TransactionState.RECOVERY_PENDING, economyResponse, "creation-refund-delivery");
            return null;
        });
    }

    public void queueRecoveryItem(UUID orderId, UUID owner, byte[] itemBlob, long quantity, UUID transactionId, String reason) {
        database.transaction(connection -> {
            Instant now = Instant.now();
            deliveries.insert(connection, new DeliveryEntry(UUID.randomUUID(), orderId, owner, DeliveryType.RECOVERY_ITEM,
                    itemBlob, quantity, BigDecimal.ZERO, DeliveryStatus.READY, null, transactionId, reason, now, now));
            if (transactionId != null) ledger.updateState(connection, transactionId, TransactionState.ADMIN_REVIEW, null, "recovery-item-created");
            return null;
        });
    }

    public void completeMoneyDelivery(DeliveryEntry delivery, UUID claimToken, String economyResponse) {
        database.transaction(connection -> {
            try(PreparedStatement ps=connection.prepareStatement("UPDATE wm_order_deliveries SET amount='0',status='DELIVERED',claim_token=NULL,updated_at=? WHERE id=? AND claim_token=? AND status='CLAIMED'")){
                ps.setLong(1,Instant.now().toEpochMilli());Jdbc.uuid(ps,2,delivery.id());Jdbc.uuid(ps,3,claimToken);if(ps.executeUpdate()!=1)throw new DatabaseManager.DatabaseException(new SQLException("Stale money claim"));
            }catch(SQLException exception){throw Jdbc.error(exception);}
            if (delivery.transactionId()!=null) {
                TransactionState finalState = TransactionState.COMMITTED;
                try (PreparedStatement type = connection.prepareStatement("SELECT transaction_type FROM wm_transactions WHERE id=?")) {
                    Jdbc.uuid(type, 1, delivery.transactionId());
                    try (ResultSet rs = type.executeQuery()) {
                        if (rs.next() && TransactionType.ORDER_DEPOSIT.name().equals(rs.getString(1))) finalState = TransactionState.COMPENSATED;
                    }
                } catch (SQLException exception) { throw Jdbc.error(exception); }
                ledger.updateState(connection,delivery.transactionId(),finalState,economyResponse,"collection-paid");
            }
            return null;
        });
    }

    public OperationResult<OrderStatus> setFrozen(UUID orderId, boolean frozen, String admin, String reason) {
        return database.transaction(connection->{Optional<BuyOrder> optional=orders.find(connection,orderId);if(optional.isEmpty())return OperationResult.failure("order_not_found","not found");BuyOrder order=optional.get();
            OrderStatus next=frozen?OrderStatus.ADMIN_FROZEN:(order.remainingQuantity()<order.requestedQuantity()?OrderStatus.PARTIALLY_FILLED:OrderStatus.ACTIVE);
            if(frozen&&!order.status().acceptsFulfillment())return OperationResult.failure("invalid_state",order.status().name());
            if(!frozen&&order.status()!=OrderStatus.ADMIN_FROZEN)return OperationResult.failure("invalid_state",order.status().name());
            try(PreparedStatement ps=connection.prepareStatement("UPDATE wm_orders SET status=?,updated_at=?,version=version+1 WHERE id=? AND version=?")){ps.setString(1,next.name());ps.setLong(2,Instant.now().toEpochMilli());Jdbc.uuid(ps,3,order.id());ps.setLong(4,order.version());if(ps.executeUpdate()!=1)return OperationResult.failure("stale","changed");}catch(SQLException e){throw Jdbc.error(e);}
            audit.add(connection,admin,frozen?"FREEZE":"UNFREEZE",order.id(),order.buyerUuid(),order.status().name(),next.name(),reason);history.add(connection,order.id(),null,frozen?"ADMIN_FROZE":"ADMIN_UNFROZE",order.status().name(),next.name(),0,BigDecimal.ZERO,reason);return OperationResult.success(next);});
    }

    public OperationResult<BigDecimal> adminRefund(UUID orderId, UUID target, BigDecimal amount, String admin, String reason) {
        if(amount.signum()<=0)return OperationResult.failure("invalid_amount","Amount must be positive");
        return database.transaction(connection->{Instant now=Instant.now();UUID tx=UUID.randomUUID();
            LedgerEntry entry=new LedgerEntry(tx,"admin-refund:"+tx,orderId,target,TransactionType.ADMIN_REFUND,amount,BigDecimal.ZERO,amount,null,TransactionState.ITEMS_SECURED,"admin-refund",now,now);
            ledger.insertPrepared(connection,entry);deliveries.insert(connection,new DeliveryEntry(UUID.randomUUID(),orderId,target,DeliveryType.ADMIN_REFUND,null,0,amount,DeliveryStatus.READY,null,tx,reason,now,now));
            audit.add(connection,admin,"REFUND",orderId,target,null,null,reason+" amount="+amount);history.add(connection,orderId,null,"ADMIN_REFUND",null,null,0,amount,reason);return OperationResult.success(amount);});
    }

    public OperationResult<Void> deleteTerminal(UUID orderId,String admin,String reason){
        return database.transaction(connection->{Optional<BuyOrder> optional=orders.find(connection,orderId);if(optional.isEmpty())return OperationResult.failure("order_not_found","not found");BuyOrder order=optional.get();
            if(!order.status().terminal()&&order.status()!=OrderStatus.FILLED)return OperationResult.failure("invalid_state","Only terminal orders can be deleted");
            try(PreparedStatement pending=connection.prepareStatement("SELECT COUNT(*) FROM wm_order_deliveries WHERE order_id=? AND status<>'DELIVERED'")){Jdbc.uuid(pending,1,orderId);try(ResultSet rs=pending.executeQuery()){if(rs.next()&&rs.getLong(1)>0)return OperationResult.failure("pending_delivery","Order has pending deliveries");}}
            catch(SQLException e){throw Jdbc.error(e);}try(PreparedStatement ps=connection.prepareStatement("DELETE FROM wm_orders WHERE id=? AND version=?")){Jdbc.uuid(ps,1,orderId);ps.setLong(2,order.version());if(ps.executeUpdate()!=1)return OperationResult.failure("stale","changed");}catch(SQLException e){throw Jdbc.error(e);}
            audit.add(connection,admin,"DELETE",orderId,order.buyerUuid(),order.status().name(),"DELETED",reason);return OperationResult.success(null);});
    }
}
