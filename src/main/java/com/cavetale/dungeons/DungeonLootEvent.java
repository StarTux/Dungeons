package com.cavetale.dungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Called after a previously unraided dungeon is looted for the first
 * time.
 */
@Getter @RequiredArgsConstructor
public final class DungeonLootEvent extends Event {
    private final Block block;
    private final Inventory inventory;
    private final Player player;
    private final Dungeon dungeon;

    @Getter private static HandlerList handlerList = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    /**
     * Add a new loot item to a random empty item slot.  Fail if there
     * is no empty slot.  Do not attempt to stack any slots.
     * @param item the item
     * @return true if the item was added, false otherwise
     */
    public boolean addItem(@NonNull ItemStack item) {
        List<Integer> slots = new ArrayList<>(inventory.getSize());
        for (int i = 0; i < inventory.getSize(); i += 1) {
            ItemStack slotItem = inventory.getItem(i);
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) return false;
        int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
        inventory.setItem(slot, item);
        return true;
    }
}
