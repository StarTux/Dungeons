package com.cavetale.dungeons;

import com.cavetale.core.struct.Cuboid;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Called after a previously unraided dungeon is looted for the first
 * time.
 */
@Getter @RequiredArgsConstructor
public final class DungeonLootEvent extends Event {
    private final Block block;
    private final Player player;
    private final Dungeon dungeon;
    private final Cuboid boundingBox;
    private final List<ItemStack> loot;

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
        loot.add(item);
        return true;
    }
}
