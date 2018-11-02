package com.cavetale.dungeons;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;

@Getter @RequiredArgsConstructor
public final class DungeonLootEvent extends Event {
    private final Block block;
    private final Inventory inventory;
    private final Player player;
    // Dungeon.isRaided() will yield the value from before this event.
    private final Dungeon dungeon;

    // Event Stuff
    @Getter private static HandlerList handlerList = new HandlerList();
    @Override public HandlerList getHandlers() {
        return handlerList;
    }
}
