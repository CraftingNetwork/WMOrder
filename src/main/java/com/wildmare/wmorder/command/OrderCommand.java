package com.wildmare.wmorder.command;

import com.wildmare.wmorder.config.*;
import com.wildmare.wmorder.database.*;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.gui.MenuManager;
import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.order.service.*;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.recovery.RecoveryService;
import com.wildmare.wmorder.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderCommand implements CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;private final ConfigManager configs;private final Messages messages;private final MenuManager menus;
    private final OrderQueryService queries;private final OrderService orders;private final MarketplaceTransactionRepository transactions;private final OrderRepository orderRepository;
    private final DatabaseManager database;private final RecoveryService recovery;private final CsvExporter exporter;private final DebugMetrics metrics;
    private final AdminConfirmationService confirmations;private final NotificationService notifications;private final AtomicBoolean ready;

    public OrderCommand(JavaPlugin plugin,ConfigManager configs,Messages messages,MenuManager menus,OrderQueryService queries,OrderService orders,
                        MarketplaceTransactionRepository transactions,OrderRepository orderRepository,DatabaseManager database,RecoveryService recovery,
                        CsvExporter exporter,DebugMetrics metrics,AdminConfirmationService confirmations,NotificationService notifications,AtomicBoolean ready){
        this.plugin=plugin;this.configs=configs;this.messages=messages;this.menus=menus;this.queries=queries;this.orders=orders;this.transactions=transactions;
        this.orderRepository=orderRepository;this.database=database;this.recovery=recovery;this.exporter=exporter;this.metrics=metrics;this.confirmations=confirmations;this.notifications=notifications;this.ready=ready;
    }

    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,String[] args){
        if(!ready.get()){messages.send(sender,"plugin-loading");return true;}
        if(args.length==0)return player(sender,"wmorder.browse",menus::openBrowser);
        String sub=args[0].toLowerCase(Locale.ROOT);
        return switch(sub){
            case "browse"->player(sender,"wmorder.browse",menus::openBrowser);
            case "create"->player(sender,"wmorder.create",menus::openCreate);
            case "sell"->sell(sender,args);
            case "my"->player(sender,"wmorder.browse",menus::openMyOrders);
            case "collect"->player(sender,"wmorder.collect",menus::openCollection);
            case "history"->player(sender,"wmorder.history",p->menus.openHistory(p,0));
            case "search"->search(sender,args);
            case "cancel"->cancel(sender,args);
            case "info"->info(sender,args);
            case "notify"->notifyToggle(sender);
            case "help"->help(sender);
            case "admin"->admin(sender,args);
            default->help(sender);
        };
    }

    private boolean sell(CommandSender sender,String[] args){if(!permission(sender,"wmorder.sell"))return true;if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(args.length<2){OrderQuery q=new OrderQuery("",null,null,null,Set.of(OrderStatus.ACTIVE,OrderStatus.PARTIALLY_FILLED),OrderSort.HIGHEST_PRICE,true,0,configs.settings().performance().browserPageSize());menus.openBrowser(player,q,com.wildmare.wmorder.gui.session.MenuType.BROWSER);return true;}resolve(sender,args[1],id->menus.openDetails(player,id));return true;}
    private boolean search(CommandSender sender,String[] args){if(!permission(sender,"wmorder.search"))return true;if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(args.length<2){messages.send(sender,"admin-action-failed",Map.of("reason","Usage: /order search <query>"));return true;}String text=String.join(" ",Arrays.copyOfRange(args,1,args.length));OrderQuery q=OrderQuery.browser(0,configs.settings().performance().browserPageSize()).withSearch(text);menus.openBrowser(player,q,com.wildmare.wmorder.gui.session.MenuType.BROWSER);return true;}
    private boolean cancel(CommandSender sender,String[] args){if(!permission(sender,"wmorder.cancel"))return true;if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(args.length<2){messages.send(sender,"admin-action-failed",Map.of("reason","Usage: /order cancel <order-id>"));return true;}resolve(sender,args[1],id->orders.cancel(player,id,false,"Cancelled by buyer command").thenAccept(result->MainThread.run(plugin,()->{if(result.success())messages.send(player,"order-cancelled",Map.of("order",OrderService.shortId(id)));else messages.send(player,"admin-action-failed",Map.of("reason",result.detail()));})));return true;}
    private boolean info(CommandSender sender,String[] args){if(!permission(sender,"wmorder.browse"))return true;if(args.length<2){messages.send(sender,"admin-action-failed",Map.of("reason","Usage: /order info <order-id>"));return true;}resolve(sender,args[1],id->{if(sender instanceof Player player)menus.openDetails(player,id);else queries.find(id).thenAccept(order->MainThread.run(plugin,()->order.ifPresentOrElse(value->printOrder(sender,value),()->messages.send(sender,"invalid-order",Map.of("order",args[1])))));});return true;}
    private boolean notifyToggle(CommandSender sender){if(!permission(sender,"wmorder.notify"))return true;if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}notifications.toggle(player.getUniqueId()).thenAccept(value->MainThread.run(plugin,()->messages.send(player,value?"notifications-enabled":"notifications-disabled"))).exceptionally(error->{MainThread.run(plugin,()->messages.send(player,"admin-action-failed",Map.of("reason",root(error).getMessage())));return null;});return true;}
    private boolean help(CommandSender sender){for(var line:messages.renderList("help",Map.of()))sender.sendMessage(line);return true;}

    private boolean admin(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin"))return true;if(args.length<2){sender.sendMessage(messages.renderRaw("<gold>WMOrder admin:</gold> reload, inspect, inspectplayer, cancel, freeze, unfreeze, refund, delete, history, migrate, database, stats, cleanup, recover, export, debug",Map.of()));return true;}String sub=args[1].toLowerCase(Locale.ROOT);return switch(sub){
        case "reload"->adminReload(sender);case "inspect"->adminInspect(sender,args);case "inspectplayer"->adminInspectPlayer(sender,args);case "cancel"->adminCancel(sender,args);case "freeze"->adminFreeze(sender,args,true);case "unfreeze"->adminFreeze(sender,args,false);case "refund"->adminRefund(sender,args);case "delete"->adminDelete(sender,args);case "history"->adminHistory(sender,args);case "migrate"->adminMigrate(sender);case "database"->adminDatabase(sender);case "stats"->adminStats(sender);case "cleanup"->adminCleanup(sender,args);case "recover"->adminRecover(sender);case "export"->adminExport(sender,args);case "debug"->adminDebug(sender);default->help(sender);};}

    private boolean adminReload(CommandSender sender){if(!permission(sender,"wmorder.admin.reload"))return true;configs.reloadSafe();queries.invalidate();messages.send(sender,"reload-complete");return true;}
    private boolean adminInspect(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.inspect"))return true;if(args.length<3)return usage(sender,"/order admin inspect <order-id>");resolve(sender,args[2],id->{if(sender instanceof Player player)menus.openDetails(player,id);else queries.find(id).thenAccept(order->MainThread.run(plugin,()->order.ifPresentOrElse(value->printOrder(sender,value),()->messages.send(sender,"invalid-order",Map.of("order",args[2])))));});return true;}
    private boolean adminInspectPlayer(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.inspect"))return true;if(args.length<3)return usage(sender,"/order admin inspectplayer <player>");OrderQuery q=new OrderQuery(args[2],null,null,null,Set.of(OrderStatus.values()),OrderSort.NEWEST,false,0,configs.settings().performance().browserPageSize());if(sender instanceof Player player)menus.openBrowser(player,q,com.wildmare.wmorder.gui.session.MenuType.ADMIN_INSPECT);else queries.query(q).thenAccept(page->MainThread.run(plugin,()->page.entries().forEach(order->sender.sendMessage(order.id()+" "+order.buyerName()+" "+order.itemDisplayName()+" "+order.status()))));return true;}
    private boolean adminCancel(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.cancel"))return true;if(args.length<3)return usage(sender,"/order admin cancel <order-id> [confirm]");boolean confirm=args.length>3&&args[3].equalsIgnoreCase("confirm");String action="cancel:"+args[2];if(!confirmations.confirm(sender,action,confirm)){messages.send(sender,"confirmation-required");return true;}resolve(sender,args[2],id->orders.cancel(sender,id,true,"Administrative cancellation").thenAccept(result->MainThread.run(plugin,()->resultMessage(sender,result.success(),result.detail()))));return true;}
    private boolean adminFreeze(CommandSender sender,String[] args,boolean freeze){if(!permission(sender,"wmorder.admin.freeze"))return true;if(args.length<3)return usage(sender,"/order admin "+(freeze?"freeze":"unfreeze")+" <order-id> [confirm]");boolean confirm=args.length>3&&args[3].equalsIgnoreCase("confirm");String action=(freeze?"freeze:":"unfreeze:")+args[2];if(!confirmations.confirm(sender,action,confirm)){messages.send(sender,"confirmation-required");return true;}resolve(sender,args[2],id->database.supplyAsync(()->transactions.setFrozen(id,freeze,identity(sender),"Administrative action")).thenAccept(result->MainThread.run(plugin,()->resultMessage(sender,result.success(),result.detail()))));return true;}
    private boolean adminRefund(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.refund"))return true;if(args.length<4)return usage(sender,"/order admin refund <order-id> <amount> [confirm]");BigDecimal amount;try{amount=new BigDecimal(args[3]);}catch(NumberFormatException e){messages.send(sender,"invalid-number");return true;}boolean confirm=args.length>4&&args[4].equalsIgnoreCase("confirm");String action="refund:"+args[2]+":"+amount;if(!confirmations.confirm(sender,action,confirm)){messages.send(sender,"confirmation-required");return true;}resolve(sender,args[2],id->queries.find(id).thenAccept(optional->{if(optional.isEmpty()){MainThread.run(plugin,()->messages.send(sender,"invalid-order",Map.of("order",args[2])));return;}UUID target=optional.get().buyerUuid();database.supplyAsync(()->transactions.adminRefund(id,target,amount,identity(sender),"Administrative refund")).thenAccept(result->MainThread.run(plugin,()->resultMessage(sender,result.success(),result.detail())));}));return true;}
    private boolean adminDelete(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.delete"))return true;if(args.length<3)return usage(sender,"/order admin delete <order-id> [confirm]");boolean confirm=args.length>3&&args[3].equalsIgnoreCase("confirm");String action="delete:"+args[2];if(!confirmations.confirm(sender,action,confirm)){messages.send(sender,"confirmation-required");return true;}resolve(sender,args[2],id->database.supplyAsync(()->transactions.deleteTerminal(id,identity(sender),"Administrative deletion")).thenAccept(result->MainThread.run(plugin,()->resultMessage(sender,result.success(),result.detail()))));return true;}
    private boolean adminHistory(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.inspect"))return true;if(args.length<3)return usage(sender,"/order admin history <player-uuid>");UUID player;try{player=UUID.fromString(args[2]);}catch(IllegalArgumentException e){messages.send(sender,"admin-action-failed",Map.of("reason","Use the player's UUID"));return true;}queries.history(player,0,20).thenAccept(lines->MainThread.run(plugin,()->lines.forEach(line->sender.sendMessage(line.createdAt()+" "+line.eventType()+" order="+OrderService.shortId(line.orderId())+" amount="+line.amount()))));return true;}
    private boolean adminMigrate(CommandSender sender){if(!permission(sender,"wmorder.admin.migrate"))return true;database.runAsync(()->new MigrationRunner(plugin,database).migrate()).thenAccept(v->MainThread.run(plugin,()->messages.send(sender,"admin-action-complete"))).exceptionally(e->{MainThread.run(plugin,()->messages.send(sender,"admin-action-failed",Map.of("reason",root(e).getMessage())));return null;});return true;}
    private boolean adminDatabase(CommandSender sender){if(!permission(sender,"wmorder.admin.stats"))return true;database.supplyAsync(database::isHealthy).thenAccept(healthy->MainThread.run(plugin,()->sender.sendMessage("WMOrder DB healthy="+healthy+", dialect="+database.dialect()+", queue="+database.queueSize()+", avgQueryMs="+String.format(Locale.ROOT,"%.2f",database.averageQueryMillis()))));return true;}
    private boolean adminStats(CommandSender sender){if(!permission(sender,"wmorder.admin.stats"))return true;recovery.pendingCount().thenAccept(pending->MainThread.run(plugin,()->{sender.sendMessage("WMOrder metrics: queries="+database.queryCount()+", avgQueryMs="+String.format(Locale.ROOT,"%.2f",database.averageQueryMillis())+", queue="+database.queueSize()+", pendingRecovery="+pending);metrics.snapshot().forEach((name,value)->sender.sendMessage(" - "+name+": "+value));}));return true;}
    private boolean adminCleanup(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.stats"))return true;boolean confirm=args.length>2&&args[2].equalsIgnoreCase("confirm");if(!confirmations.confirm(sender,"cleanup",confirm)){messages.send(sender,"confirmation-required");return true;}Instant before=Instant.now().minusSeconds(configs.settings().orders().historyRetentionDays()*86400L);database.supplyAsync(()->orderRepository.cleanupHistory(before,1000)).thenAccept(count->MainThread.run(plugin,()->sender.sendMessage("Removed "+count+" history rows.")));return true;}
    private boolean adminRecover(CommandSender sender){if(!permission(sender,"wmorder.admin.recover"))return true;recovery.recover().thenAccept(report->MainThread.run(plugin,()->sender.sendMessage("Recovery inspected="+report.inspected()+", recovered="+report.recovered()+", review="+report.review()+", staleClaims="+report.staleClaims())));return true;}
    private boolean adminExport(CommandSender sender,String[] args){if(!permission(sender,"wmorder.admin.stats"))return true;String name=args.length>2?args[2].replaceAll("[^A-Za-z0-9._-]","_"):"orders-"+System.currentTimeMillis()+".csv";if(!name.endsWith(".csv"))name+=".csv";File file=new File(plugin.getDataFolder(),"exports/"+name);database.supplyAsync(()->exporter.exportOrders(file)).thenAccept(rows->MainThread.run(plugin,()->sender.sendMessage("Exported "+rows+" orders to "+file.getPath())));return true;}
    private boolean adminDebug(CommandSender sender){if(!permission(sender,"wmorder.admin.debug"))return true;sender.sendMessage("WMOrder debug metrics "+(metrics.toggle()?"enabled":"disabled"));return true;}

    private void resolve(CommandSender sender,String text,java.util.function.Consumer<UUID> consumer){queries.resolveId(text).thenAccept(optional->MainThread.run(plugin,()->optional.ifPresentOrElse(consumer,()->messages.send(sender,"invalid-order",Map.of("order",text)))));}
    private boolean player(CommandSender sender,String permission,java.util.function.Consumer<Player> action){if(!permission(sender,permission))return true;if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}action.accept(player);return true;}
    private boolean permission(CommandSender sender,String node){if(sender.hasPermission(node))return true;messages.send(sender,"no-permission");return false;}
    private boolean usage(CommandSender sender,String usage){messages.send(sender,"admin-action-failed",Map.of("reason","Usage: "+usage));return true;}
    private void resultMessage(CommandSender sender,boolean success,String detail){if(success)messages.send(sender,"admin-action-complete");else messages.send(sender,"admin-action-failed",Map.of("reason",detail));}
    private String identity(CommandSender sender){return sender instanceof Player p?p.getUniqueId().toString():"CONSOLE";}
    private void printOrder(CommandSender sender,BuyOrder order){sender.sendMessage("WMOrder "+order.id()+" buyer="+order.buyerName()+" item="+order.itemDisplayName()+" requested="+order.requestedQuantity()+" remaining="+order.remainingQuantity()+" price="+order.pricePerItem()+" reserved="+order.remainingReservedBalance()+" status="+order.status()+" version="+order.version()+" expires="+order.expiresAt());}
    private static Throwable root(Throwable t){Throwable v=t;while(v instanceof java.util.concurrent.CompletionException&&v.getCause()!=null)v=v.getCause();return v;}

    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,String[] args){
        if(args.length==1)return match(args[0],List.of("browse","create","sell","my","collect","history","search","cancel","info","notify","help","admin"));
        if(args.length==2&&args[0].equalsIgnoreCase("admin"))return match(args[1],List.of("reload","inspect","inspectplayer","cancel","freeze","unfreeze","refund","delete","history","migrate","database","stats","cleanup","recover","export","debug"));
        if(args.length>=3&&args[0].equalsIgnoreCase("admin")&&Set.of("cancel","freeze","unfreeze","delete","cleanup").contains(args[1].toLowerCase(Locale.ROOT)))return match(args[args.length-1],List.of("confirm"));
        return List.of();
    }
    private List<String> match(String prefix,List<String> values){String p=prefix.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.startsWith(p)).toList();}
}
