package com.wildmare.wmorder.item;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.util.OperationResult;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InventoryItemService {
    private final ConfigManager configs;
    private final ItemMatcher matcher;
    public InventoryItemService(ConfigManager configs, ItemMatcher matcher){this.configs=configs;this.matcher=matcher;}

    public long count(PlayerInventory inventory, ItemStack requested, long maximum) {
        return plan(inventory,requested,maximum).quantity();
    }

    public InventorySalePlan plan(PlayerInventory inventory,ItemStack requested,long maximum){
        if(maximum<=0)return new InventorySalePlan(0,List.of());
        String expected=matcher.identity(requested).fingerprint();
        long remaining=maximum;List<InventorySalePlan.Mutation> mutations=new ArrayList<>();
        ItemStack[] storage=inventory.getStorageContents();
        for(int slot=0;slot<storage.length&&remaining>0;slot++){
            ItemStack item=storage[slot];if(item==null||item.getType().isAir())continue;
            String outerFingerprint=matcher.identity(item).fingerprint();
            if(matcher.matches(requested,item)){
                int take=(int)Math.min(remaining,item.getAmount());mutations.add(new InventorySalePlan.Mutation(slot,-1,take,outerFingerprint,expected));remaining-=take;continue;
            }
            if(configs.itemSettings().scanShulkerContents()&&configs.itemSettings().maximumContainerDepth()>=1&&item.getItemMeta() instanceof BlockStateMeta block&&block.getBlockState() instanceof Container container){
                ItemStack[] nested=container.getInventory().getContents();
                for(int nestedSlot=0;nestedSlot<nested.length&&remaining>0;nestedSlot++){
                    ItemStack inside=nested[nestedSlot];if(inside==null||inside.getType().isAir()||!matcher.matches(requested,inside))continue;
                    int take=(int)Math.min(remaining,inside.getAmount());mutations.add(new InventorySalePlan.Mutation(slot,nestedSlot,take,outerFingerprint,expected));remaining-=take;
                }
            }
        }
        return new InventorySalePlan(maximum-remaining,mutations);
    }

    public OperationResult<RemovalReceipt> apply(PlayerInventory inventory,ItemStack requested,InventorySalePlan plan){
        if(plan.quantity()<=0)return OperationResult.failure("no_items","No matching items");
        Map<Integer,ItemStack> originals=new HashMap<>();
        for(InventorySalePlan.Mutation mutation:plan.mutations()){
            ItemStack outer=inventory.getItem(mutation.inventorySlot());
            if(outer==null||outer.getType().isAir()||!matcher.identity(outer).fingerprint().equals(mutation.expectedOuterFingerprint()))
                return OperationResult.failure("inventory_changed","Inventory changed before sale");
            originals.putIfAbsent(mutation.inventorySlot(),outer.clone());
            if(!mutation.nested()){
                if(!matcher.matches(requested,outer)||outer.getAmount()<mutation.amount())return OperationResult.failure("inventory_changed","Matching stack changed");
            }else{
                if(!(outer.getItemMeta() instanceof BlockStateMeta block)||!(block.getBlockState() instanceof Container container))return OperationResult.failure("inventory_changed","Container changed");
                ItemStack inside=container.getInventory().getItem(mutation.nestedSlot());
                if(inside==null||!matcher.matches(requested,inside)||inside.getAmount()<mutation.amount())return OperationResult.failure("inventory_changed","Container contents changed");
            }
        }
        try{
            for(InventorySalePlan.Mutation mutation:plan.mutations()){
                ItemStack outer=inventory.getItem(mutation.inventorySlot());
                if(!mutation.nested()){
                    int left=outer.getAmount()-mutation.amount();if(left<=0)inventory.setItem(mutation.inventorySlot(),null);else{outer.setAmount(left);inventory.setItem(mutation.inventorySlot(),outer);}
                }else{
                    BlockStateMeta block=(BlockStateMeta)outer.getItemMeta();Container container=(Container)block.getBlockState();ItemStack inside=container.getInventory().getItem(mutation.nestedSlot());
                    int left=inside.getAmount()-mutation.amount();container.getInventory().setItem(mutation.nestedSlot(),left<=0?null:withAmount(inside,left));block.setBlockState(container);outer.setItemMeta(block);inventory.setItem(mutation.inventorySlot(),outer);
                }
            }
            ItemStack template=requested.clone();template.setAmount(1);return OperationResult.success(new RemovalReceipt(template,plan.quantity(),originals));
        }catch(Throwable throwable){originals.forEach(inventory::setItem);return OperationResult.failure("remove_failed",throwable.getMessage());}
    }

    public long simulatedCapacity(PlayerInventory inventory, ItemStack template, long requested) {
        long capacity = 0;
        int max = template.getMaxStackSize();
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) capacity += max;
            else if (current.isSimilar(template)) capacity += Math.max(0, max - current.getAmount());
            if (capacity >= requested) return requested;
        }
        return Math.min(capacity, requested);
    }

    public void restoreOriginalSlots(PlayerInventory inventory, RemovalReceipt receipt) {
        receipt.originalSlots().forEach((slot, stack) -> inventory.setItem(slot, stack.clone()));
    }

    public List<ItemStack> restore(PlayerInventory inventory,RemovalReceipt receipt){
        List<ItemStack> leftovers=new ArrayList<>();long remaining=receipt.quantity();int max=receipt.template().getMaxStackSize();
        while(remaining>0){int amount=(int)Math.min(remaining,max);ItemStack stack=receipt.template().clone();stack.setAmount(amount);Map<Integer,ItemStack> overflow=inventory.addItem(stack);leftovers.addAll(overflow.values());remaining-=amount;}
        return leftovers;
    }

    public long insertAsMuch(PlayerInventory inventory,ItemStack template,long quantity){
        long inserted=0;long remaining=quantity;int max=template.getMaxStackSize();
        while(remaining>0){int amount=(int)Math.min(remaining,max);ItemStack stack=template.clone();stack.setAmount(amount);Map<Integer,ItemStack> overflow=inventory.addItem(stack);int notInserted=overflow.values().stream().mapToInt(ItemStack::getAmount).sum();inserted+=amount-notInserted;remaining-=amount;if(notInserted>0)break;}
        return inserted;
    }
    private ItemStack withAmount(ItemStack item,int amount){ItemStack copy=item.clone();copy.setAmount(amount);return copy;}
    public record RemovalReceipt(ItemStack template,long quantity,Map<Integer,ItemStack> originalSlots){public RemovalReceipt{template=template.clone();originalSlots=Map.copyOf(originalSlots);} @Override public ItemStack template(){return template.clone();}}
}
