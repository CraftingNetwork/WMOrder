package com.wildmare.wmorder;

import com.wildmare.wmorder.api.*;
import com.wildmare.wmorder.command.*;
import com.wildmare.wmorder.config.*;
import com.wildmare.wmorder.database.*;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.economy.*;
import com.wildmare.wmorder.gui.*;
import com.wildmare.wmorder.gui.input.ChatInputManager;
import com.wildmare.wmorder.gui.session.GuiSessionRegistry;
import com.wildmare.wmorder.item.*;
import com.wildmare.wmorder.listener.*;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.order.service.*;
import com.wildmare.wmorder.order.transaction.OrderGuard;
import com.wildmare.wmorder.order.validation.OrderValidationService;
import com.wildmare.wmorder.permission.*;
import com.wildmare.wmorder.placeholder.*;
import com.wildmare.wmorder.recovery.RecoveryService;
import com.wildmare.wmorder.scheduler.ExpirationService;
import com.wildmare.wmorder.util.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WMOrderPlugin extends JavaPlugin {
    private final AtomicBoolean ready=new AtomicBoolean(false);private final AtomicBoolean accepting=new AtomicBoolean(false);
    private ConfigManager configs;private Messages messages;private DatabaseManager database;private ExpirationService expirations;private StatsCache stats;
    private MenuManager menus;private ChatInputManager inputs;private PlayerTransactionGate gate;private RecoveryService recovery;private WMOrderExpansion expansion;private WMOrderApi api;
    private final DebugMetrics metrics=new DebugMetrics();private final AdminConfirmationService adminConfirmations=new AdminConfirmationService();

    @Override public void onEnable(){
        try{configs=new ConfigManager(this);configs.initialize();messages=new Messages(configs);}catch(Throwable error){getLogger().severe("Configuration startup failed: "+root(error).getMessage());Bukkit.getPluginManager().disablePlugin(this);return;}
        PluginCommand command=getCommand("order");if(command!=null)command.setExecutor((sender,cmd,label,args)->{messages.send(sender,"plugin-loading");return true;});
        Economy vault=resolveEconomy();if(vault==null){getLogger().severe("Vault and a compatible economy provider are required. WMOrder has been disabled.");Bukkit.getPluginManager().disablePlugin(this);return;}
        MoneyMath money=new MoneyMath(configs.settings().economy().currencyScale(),configs.settings().economy().roundingMode());EconomyService economy=new VaultEconomyService(vault,money);
        database=new DatabaseManager(this,configs.databaseSettings(),configs.settings().performance());
        database.supplyAsync(()->{try{database.initialize();return null;}catch(java.sql.SQLException error){throw new DatabaseManager.DatabaseException(error);}}).thenCompose(v->MainThread.run(this,()->finishEnable(economy,money))).exceptionally(error->{getLogger().severe("Database startup failed: "+root(error).getMessage());MainThread.run(this,()->Bukkit.getPluginManager().disablePlugin(this));return null;});
    }

    private void finishEnable(EconomyService economy,MoneyMath money){
        OrderRepository orderRepo=new OrderRepository(database);LedgerRepository ledgerRepo=new LedgerRepository(database);DeliveryRepository deliveryRepo=new DeliveryRepository(database);
        HistoryRepository historyRepo=new HistoryRepository(database);StatsRepository statsRepo=new StatsRepository(database);AdminAuditRepository auditRepo=new AdminAuditRepository(database);
        PlayerSettingsRepository playerSettingsRepo=new PlayerSettingsRepository(database);MarketplaceTransactionRepository txRepo=new MarketplaceTransactionRepository(database,orderRepo,ledgerRepo,deliveryRepo,historyRepo,auditRepo);
        CsvExporter exporter=new CsvExporter(database);

        ItemSerializer serializer=new ItemSerializer();ItemNormalizer normalizer=new ItemNormalizer();ItemMatcher matcher=new ItemMatcher(configs,serializer,normalizer);
        ItemRestrictionService restrictions=new ItemRestrictionService(configs,serializer);InventoryItemService inventoryItems=new InventoryItemService(configs,matcher);
        LimitService limits=new LimitService(configs);PriceCalculator prices=new PriceCalculator(configs,money);CooldownService cooldowns=new CooldownService();OrderGuard guards=new OrderGuard();gate=new PlayerTransactionGate();
        OrderQueryService queries=new OrderQueryService(database,orderRepo,deliveryRepo,historyRepo,configs);NotificationService notifications=new NotificationService(this,configs,messages,database,playerSettingsRepo);
        OrderValidationService validation=new OrderValidationService(configs,restrictions,money);
        OrderService orderService=new OrderService(this,configs,database,queries,ledgerRepo,txRepo,economy,matcher,validation,prices,limits,cooldowns,notifications,money,accepting);
        FulfillmentService fulfillment=new FulfillmentService(this,configs,database,queries,txRepo,economy,serializer,inventoryItems,prices,limits,cooldowns,guards,gate,notifications,accepting);
        CollectionService collection=new CollectionService(this,configs,database,deliveryRepo,txRepo,economy,serializer,inventoryItems,gate);

        GuiSessionRegistry sessions=new GuiSessionRegistry();inputs=new ChatInputManager(this);GuiItemFactory guiItems=new GuiItemFactory(configs,messages);
        menus=new MenuManager(this,configs,messages,guiItems,sessions,inputs,queries,orderService,fulfillment,collection,serializer,inventoryItems,limits,prices,money);
        getServer().getPluginManager().registerEvents(inputs,this);getServer().getPluginManager().registerEvents(new MenuListener(this,sessions,menus),this);
        getServer().getPluginManager().registerEvents(new TransactionSafetyListener(gate),this);

        stats=new StatsCache(this,database,statsRepo);getServer().getPluginManager().registerEvents(new PlayerJoinListener(database,deliveryRepo,notifications,stats),this);
        recovery=new RecoveryService(database,ledgerRepo,deliveryRepo,orderRepo,txRepo);expirations=new ExpirationService(this,configs,database,orderRepo,txRepo,queries,guards,notifications);
        AdminConfirmationService confirms=adminConfirmations;OrderCommand executor=new OrderCommand(this,configs,messages,menus,queries,orderService,txRepo,orderRepo,database,recovery,exporter,metrics,confirms,notifications,ready);
        PluginCommand command=getCommand("order");if(command==null)throw new IllegalStateException("Command 'order' is missing from plugin.yml");command.setExecutor(executor);command.setTabCompleter(executor);

        api=new WMOrderApiImpl(queries,orderService,fulfillment,database,deliveryRepo,ready);getServer().getServicesManager().register(WMOrderApi.class,api,this, ServicePriority.Normal);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new WMOrderExpansion(stats,getPluginMeta().getVersion());expansion.register();}

        recovery.recover().thenAccept(report->MainThread.run(this,()->{
            stats.start();expirations.start();accepting.set(true);ready.set(true);
            getLogger().info("WMOrder enabled: database="+database.dialect()+", economy="+economy.providerName()+", recoveryPending="+report.review()+", staleClaims="+report.staleClaims());
        })).exceptionally(error->{getLogger().severe("Startup recovery failed: "+root(error).getMessage());MainThread.run(this,()->Bukkit.getPluginManager().disablePlugin(this));return null;});
    }

    private Economy resolveEconomy(){RegisteredServiceProvider<Economy> provider=getServer().getServicesManager().getRegistration(Economy.class);return provider==null?null:provider.getProvider();}

    @Override public void onDisable(){
        accepting.set(false);ready.set(false);if(expirations!=null)expirations.stop();if(stats!=null)stats.stop();if(menus!=null)menus.closeAll();if(inputs!=null)inputs.clear();adminConfirmations.clear();
        if(gate!=null&&gate.size()>0)getLogger().warning("Shutdown began with "+gate.size()+" player transactions active; their durable ledger records will be inspected on next startup.");
        if(expansion!=null)expansion.unregister();if(api!=null)getServer().getServicesManager().unregister(WMOrderApi.class,api);if(database!=null)database.close();if(gate!=null)gate.clear();
        getLogger().info("WMOrder shutdown complete.");
    }

    public boolean isReady(){return ready.get();}public WMOrderApi api(){return api;}
    private static Throwable root(Throwable t){Throwable value=t;while(value instanceof CompletionException&&value.getCause()!=null)value=value.getCause();return value;}
}
