package com.wildmare.wmorder.recovery;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.order.model.BuyOrder;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RecoveryService {
    public record RecoveryReport(int inspected,int recovered,int review,int staleClaims){}
    private final DatabaseManager database;private final LedgerRepository ledger;private final DeliveryRepository deliveries;private final OrderRepository orders;private final MarketplaceTransactionRepository transactions;
    public RecoveryService(DatabaseManager database,LedgerRepository ledger,DeliveryRepository deliveries,OrderRepository orders,MarketplaceTransactionRepository transactions){this.database=database;this.ledger=ledger;this.deliveries=deliveries;this.orders=orders;this.transactions=transactions;}
    public CompletableFuture<RecoveryReport> recover(){return database.supplyAsync(()->{
        int stale=deliveries.markStaleClaimsForReview(Instant.now().minus(Duration.ofMinutes(5)),500);List<LedgerEntry> unresolved=ledger.unresolved(1000);int recovered=0,review=0;
        for(LedgerEntry entry:unresolved){
            if(entry.state()==TransactionState.ECONOMY_APPLIED&&entry.type()==TransactionType.ORDER_DEPOSIT){Optional<BuyOrder> order=entry.orderId()==null?Optional.empty():orders.find(entry.orderId());if(order.isPresent()){ledger.updateState(entry.id(),TransactionState.COMMITTED,entry.economyResponse(),"startup-found-order");recovered++;}else{transactions.queueCreationRefund(entry.id(),entry.playerUuid(),entry.net(),entry.economyResponse(),"Startup recovery: order missing");recovered++;}}
            else if(entry.state()==TransactionState.PREPARED||entry.state()==TransactionState.ITEMS_SECURED){ledger.updateState(entry.id(),TransactionState.ADMIN_REVIEW,entry.economyResponse(),"Ambiguous startup state; conservative review required");review++;}
            else if(entry.state()==TransactionState.RECOVERY_PENDING||entry.state()==TransactionState.ADMIN_REVIEW)review++;
        }
        return new RecoveryReport(unresolved.size(),recovered,review,stale);
    });}
    public CompletableFuture<Long> pendingCount(){return database.supplyAsync(ledger::unresolvedCount);}
}
